import { db, dbTx } from "./db.js";
import { json, lireCorps } from "./http.js";
const ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const codeAleatoire = () => [...crypto.getRandomValues(new Uint8Array(8))].map(b => ALPHA[b % 32]).join("");
const debutJourMada = () => { const d = new Date(Date.now() + 3 * 3600e3); d.setUTCHours(0, 0, 0, 0); return d.getTime() - 3 * 3600e3; };
export async function gererClient(request, env, url, u) {
  const m = request.method, p = url.pathname, cid = u.client_id;
  if (m === "GET" && p === "/client/resume") {
    const [c, j] = await db(env, [
      { sql: "SELECT nom, solde_ar, tarif_sms_ar, statut FROM clients WHERE id = ?", args: [cid] },
      { sql: "SELECT COUNT(*) AS nombre, COALESCE(SUM(montant_ar), 0) AS total_ar FROM sms WHERE client_id = ? AND statut = 'reconnu' AND facture = 1 AND recu_le >= ?", args: [cid, debutJourMada()] }
    ]);
    const cl = c.rows[0];
    return json({ ok: true, client: cl, aujourdhui: j.rows[0], sms_restants: Math.floor(cl.solde_ar / cl.tarif_sms_ar) });
  }
  if (m === "GET" && p === "/client/sms") {
    const limite = Math.min(Math.max(parseInt(url.searchParams.get("limite") || "50", 10) || 50, 1), 200);
    const [r] = await db(env, [{ sql: "SELECT id, operateur, montant_ar, numero_payeur, nom_payeur, reference, facture, recu_le FROM sms WHERE client_id = ? AND statut = 'reconnu' ORDER BY recu_le DESC LIMIT ?", args: [cid, limite] }]);
    return json({ ok: true, sms: r.rows.map(s => s.facture ? s : { id: s.id, operateur: s.operateur, facture: 0, recu_le: s.recu_le, masque: true }) });
  }
  if (m === "GET" && p === "/client/appareils") {
    const [r] = await db(env, [{ sql: "SELECT id, nom, operateur, numero, statut, dernier_contact FROM appareils WHERE client_id = ? ORDER BY cree_le DESC", args: [cid] }]);
    const now = Date.now();
    return json({ ok: true, appareils: r.rows.map(a => ({ ...a, en_ligne: a.statut === "actif" && !!a.dernier_contact && now - a.dernier_contact < 180000 })) });
  }
  if (m === "POST" && p === "/client/appareils") {
    if (u.role !== "client_admin") return json({ ok: false, erreur: "interdit" }, 403);
    const { nom, operateur, numero } = await lireCorps(request);
    if (typeof nom !== "string" || nom.trim().length < 2) return json({ ok: false, erreur: "nom invalide" }, 400);
    if (!["mvola", "orange", "airtel"].includes(operateur)) return json({ ok: false, erreur: "operateur invalide (mvola, orange, airtel)" }, 400);
    const appareilId = crypto.randomUUID(), code = codeAleatoire(), now = Date.now();
    await dbTx(env, [
      { sql: "INSERT INTO appareils (id, client_id, nom, operateur, numero, jeton_hash, dernier_contact, statut, cree_le) VALUES (?, ?, ?, ?, ?, NULL, NULL, 'en_attente', ?)", args: [appareilId, cid, nom.trim().slice(0, 60), operateur, typeof numero === "string" ? numero.trim().slice(0, 20) : null, now] },
      { sql: "INSERT INTO codes_appairage (code, appareil_id, expire_le, utilise) VALUES (?, ?, ?, 0)", args: [code, appareilId, now + 15 * 60 * 1000] }
    ]);
    return json({ ok: true, appareil_id: appareilId, code_appairage: code, expire_dans_min: 15, qr: JSON.stringify({ api: url.origin, code }) }, 201);
  }
  return json({ ok: false, erreur: "introuvable" }, 404);
}
