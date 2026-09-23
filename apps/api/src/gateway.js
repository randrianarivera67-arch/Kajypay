import { db, dbTx } from "./db.js";
import { analyserSms } from "./parser.js";
import { json, lireCorps } from "./http.js";
const hex = buf => [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, "0")).join("");
export const sha256Hex = async s => hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(s)));
async function appareilCourant(request, env) {
  const h = request.headers.get("Authorization") || "";
  if (!h.startsWith("Appareil ")) return null;
  const [r] = await db(env, [{ sql: "SELECT a.id, a.client_id FROM appareils a JOIN clients c ON c.id = a.client_id WHERE a.jeton_hash = ? AND a.statut = 'actif' AND c.statut = 'actif'", args: [await sha256Hex(h.slice(9).trim())] }]);
  return r.rows[0] || null;
}
async function lignes(env, appareilId) {
  const [r] = await db(env, [{ sql: "SELECT slot, operateur, numero, actif FROM lignes_sim WHERE appareil_id = ? ORDER BY slot", args: [appareilId] }]);
  return r.rows;
}
function choisirLigne(texte, slot, ls) {
  const actives = ls.filter(l => l.actif === 1);
  if (slot === 1 || slot === 2) {
    const l = actives.find(x => x.slot === slot);
    if (!l) return { a: { reconnu: false }, raison: "sim_inactive" };
    const a = analyserSms(texte, l.operateur);
    if (a.reconnu) return { a, slot };
    return { a: { reconnu: false }, raison: analyserSms(texte).reconnu ? "sim_incoherente" : null };
  }
  const a = analyserSms(texte);
  if (!a.reconnu) return { a };
  const cand = actives.filter(x => x.operateur === a.operateur);
  if (cand.length !== 1) return { a: { reconnu: false }, raison: cand.length ? "sim_ambigue" : "operateur_absent" };
  return { a, slot: cand[0].slot };
}
export async function gererGateway(request, env, url) {
  const p = url.pathname;
  if (request.method !== "POST") return json({ ok: false, erreur: "introuvable" }, 404);
  if (p === "/gateway/appairer") {
    const { code } = await lireCorps(request);
    const c = typeof code === "string" ? code.trim().toUpperCase() : "";
    if (!/^[A-Z0-9]{8}$/.test(c)) return json({ ok: false, erreur: "code invalide" }, 400);
    const [r] = await db(env, [{ sql: "SELECT k.appareil_id, a.nom FROM codes_appairage k JOIN appareils a ON a.id = k.appareil_id WHERE k.code = ? AND k.utilise = 0 AND k.expire_le > ?", args: [c, Date.now()] }]);
    const k = r.rows[0];
    if (!k) return json({ ok: false, erreur: "code invalide ou expire" }, 400);
    const jeton = hex(crypto.getRandomValues(new Uint8Array(32)));
    await dbTx(env, [
      { sql: "UPDATE codes_appairage SET utilise = 1 WHERE code = ?", args: [c] },
      { sql: "UPDATE appareils SET jeton_hash = ?, statut = 'actif', dernier_contact = ? WHERE id = ?", args: [await sha256Hex(jeton), Date.now(), k.appareil_id] }
    ]);
    return json({ ok: true, jeton_appareil: jeton, appareil_id: k.appareil_id, nom: k.nom, sims: await lignes(env, k.appareil_id) });
  }
  const app = await appareilCourant(request, env);
  if (!app) return json({ ok: false, erreur: "appareil non autorise" }, 401);
  if (p === "/gateway/ping") {
    await db(env, [{ sql: "UPDATE appareils SET dernier_contact = ? WHERE id = ?", args: [Date.now(), app.id] }]);
    return json({ ok: true, sims: await lignes(env, app.id) });
  }
  if (p === "/gateway/sms") {
    const { sms } = await lireCorps(request);
    if (!Array.isArray(sms) || sms.length < 1 || sms.length > 50) return json({ ok: false, erreur: "liste sms invalide (1 a 50)" }, 400);
    const ls = await lignes(env, app.id);
    const resultats = [];
    for (const s of sms) {
      const texte = typeof s?.texte === "string" ? s.texte.slice(0, 1000) : "";
      const expediteur = typeof s?.expediteur === "string" ? s.expediteur.slice(0, 40) : "";
      const recuLe = Number.isInteger(s?.recu_le) ? s.recu_le : Date.now();
      const slotRecu = Number.isInteger(s?.slot) ? s.slot : null;
      const { a, slot, raison } = choisirLigne(texte, slotRecu, ls);
      if (!a.reconnu) { resultats.push(raison ? { statut: "ignore", raison } : { statut: "ignore" }); continue; }
      const smsId = crypto.randomUUID(), now = Date.now();
      const empreinte = await sha256Hex(`${expediteur}|${texte}|${recuLe}`);
      try {
        await dbTx(env, [
          { sql: "INSERT INTO sms (id, client_id, appareil_id, operateur, expediteur, texte, empreinte, montant_ar, reference, numero_payeur, nom_payeur, sim_slot, statut, facture, recu_le, cree_le) SELECT ?, c.id, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'reconnu', CASE WHEN c.solde_ar >= c.tarif_sms_ar THEN 1 ELSE 0 END, ?, ? FROM clients c WHERE c.id = ?", args: [smsId, app.id, a.operateur, expediteur, texte, empreinte, a.montant_ar, a.reference, a.numero_payeur, a.nom_payeur, slot, recuLe, now, app.client_id] },
          { sql: "UPDATE clients SET solde_ar = solde_ar - tarif_sms_ar WHERE id = ? AND EXISTS (SELECT 1 FROM sms WHERE id = ? AND facture = 1)", args: [app.client_id, smsId] },
          { sql: "INSERT INTO mouvements_credit (id, client_id, type, montant_ar, sms_id, note, cree_le) SELECT ?, c.id, 'sms', -c.tarif_sms_ar, ?, NULL, ? FROM clients c WHERE c.id = ? AND EXISTS (SELECT 1 FROM sms WHERE id = ? AND facture = 1)", args: [crypto.randomUUID(), smsId, now, app.client_id, smsId] }
        ]);
        const [f] = await db(env, [{ sql: "SELECT facture FROM sms WHERE id = ?", args: [smsId] }]);
        resultats.push({ statut: "reconnu", sim: slot, operateur: a.operateur, facture: f.rows[0]?.facture === 1, montant_ar: a.montant_ar });
      } catch (e) {
        if (String(e.message).includes("UNIQUE")) { resultats.push({ statut: "doublon" }); continue; }
        throw e;
      }
    }
    await db(env, [{ sql: "UPDATE appareils SET dernier_contact = ? WHERE id = ?", args: [Date.now(), app.id] }]);
    return json({ ok: true, resultats });
  }
  return json({ ok: false, erreur: "introuvable" }, 404);
}
