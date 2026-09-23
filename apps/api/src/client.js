import { db, dbTx } from "./db.js";
import { json, lireCorps } from "./http.js";
const ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const OPS = ["mvola", "orange", "airtel"];
const codeAleatoire = () => [...crypto.getRandomValues(new Uint8Array(8))].map(b => ALPHA[b % 32]).join("");
const debutJourMada = () => { const d = new Date(Date.now() + 3 * 3600e3); d.setUTCHours(0, 0, 0, 0); return d.getTime() - 3 * 3600e3; };
const txt = (v, n) => typeof v === "string" && v.trim() ? v.trim().slice(0, n) : null;
export async function gererClient(request, env, url, u) {
  const m = request.method, p = url.pathname, cid = u.client_id;
  if (m === "GET" && p === "/client/resume") {
    const debut = debutJourMada();
    const [c, j, s] = await db(env, [
      { sql: "SELECT nom, solde_ar, tarif_sms_ar, statut FROM clients WHERE id = ?", args: [cid] },
      { sql: "SELECT COUNT(*) AS nombre, COALESCE(SUM(montant_ar), 0) AS total_ar FROM sms WHERE client_id = ? AND statut = 'reconnu' AND facture = 1 AND recu_le >= ?", args: [cid, debut] },
      { sql: "SELECT sim_slot, operateur, COUNT(*) AS nombre, COALESCE(SUM(montant_ar), 0) AS total_ar FROM sms WHERE client_id = ? AND statut = 'reconnu' AND facture = 1 AND recu_le >= ? GROUP BY sim_slot, operateur ORDER BY sim_slot", args: [cid, debut] }
    ]);
    const cl = c.rows[0];
    return json({ ok: true, client: cl, aujourdhui: j.rows[0], par_sim: s.rows, sms_restants: Math.floor(cl.solde_ar / cl.tarif_sms_ar) });
  }
  if (m === "GET" && p === "/client/sms") {
    const limite = Math.min(Math.max(parseInt(url.searchParams.get("limite") || "50", 10) || 50, 1), 200);
    const sim = parseInt(url.searchParams.get("sim") || "", 10);
    const appareil = url.searchParams.get("appareil");
    let sql = "SELECT id, appareil_id, sim_slot, operateur, montant_ar, numero_payeur, nom_payeur, reference, facture, recu_le FROM sms WHERE client_id = ? AND statut = 'reconnu'";
    const args = [cid];
    if (sim === 1 || sim === 2) { sql += " AND sim_slot = ?"; args.push(sim); }
    if (appareil && /^[0-9a-f-]{36}$/.test(appareil)) { sql += " AND appareil_id = ?"; args.push(appareil); }
    sql += " ORDER BY recu_le DESC LIMIT ?"; args.push(limite);
    const [r] = await db(env, [{ sql, args }]);
    return json({ ok: true, sms: r.rows.map(s => s.facture ? s : { id: s.id, appareil_id: s.appareil_id, sim_slot: s.sim_slot, operateur: s.operateur, facture: 0, recu_le: s.recu_le, masque: true }) });
  }
  if (m === "GET" && p === "/client/appareils") {
    const [r, l] = await db(env, [
      { sql: "SELECT id, nom, statut, dernier_contact FROM appareils WHERE client_id = ? ORDER BY cree_le DESC", args: [cid] },
      { sql: "SELECT l.appareil_id, l.slot, l.operateur, l.numero, l.actif, l.code_ussd_solde, l.solde_operateur_ar, l.solde_texte, l.solde_maj FROM lignes_sim l JOIN appareils a ON a.id = l.appareil_id WHERE a.client_id = ? ORDER BY l.slot", args: [cid] }
    ]);
    const now = Date.now();
    return json({ ok: true, appareils: r.rows.map(a => ({ ...a, en_ligne: a.statut === "actif" && !!a.dernier_contact && now - a.dernier_contact < 180000, sims: l.rows.filter(x => x.appareil_id === a.id).map(({ appareil_id, ...x }) => x) })) });
  }
  if (m === "POST" && p === "/client/appareils") {
    if (u.role !== "client_admin") return json({ ok: false, erreur: "interdit" }, 403);
    const b = await lireCorps(request);
    if (typeof b.nom !== "string" || b.nom.trim().length < 2) return json({ ok: false, erreur: "nom invalide" }, 400);
    const sims = Array.isArray(b.sims) ? b.sims : (b.operateur ? [{ slot: 1, operateur: b.operateur, numero: b.numero }] : []);
    if (sims.length < 1 || sims.length > 2) return json({ ok: false, erreur: "1 ou 2 SIM requises" }, 400);
    const slots = new Set();
    for (const s of sims) {
      if (!(s?.slot === 1 || s?.slot === 2) || slots.has(s.slot)) return json({ ok: false, erreur: "slot invalide (1 ou 2, sans doublon)" }, 400);
      if (!OPS.includes(s.operateur)) return json({ ok: false, erreur: "operateur invalide (mvola, orange, airtel)" }, 400);
      slots.add(s.slot);
    }
    const appareilId = crypto.randomUUID(), code = codeAleatoire(), now = Date.now();
    await dbTx(env, [
      { sql: "INSERT INTO appareils (id, client_id, nom, operateur, numero, jeton_hash, dernier_contact, statut, cree_le) VALUES (?, ?, ?, ?, ?, NULL, NULL, 'en_attente', ?)", args: [appareilId, cid, b.nom.trim().slice(0, 60), sims[0].operateur, txt(sims[0].numero, 20), now] },
      ...sims.map(s => ({ sql: "INSERT INTO lignes_sim (id, appareil_id, slot, operateur, numero, actif, cree_le) VALUES (?, ?, ?, ?, ?, 1, ?)", args: [crypto.randomUUID(), appareilId, s.slot, s.operateur, txt(s.numero, 20), now] })),
      { sql: "INSERT INTO codes_appairage (code, appareil_id, expire_le, utilise) VALUES (?, ?, ?, 0)", args: [code, appareilId, now + 15 * 60 * 1000] }
    ]);
    return json({ ok: true, appareil_id: appareilId, code_appairage: code, expire_dans_min: 15, qr: JSON.stringify({ api: url.origin, code }) }, 201);
  }
  const mu = p.match(/^\/client\/appareils\/([0-9a-f-]{36})\/sims\/([12])\/ussd$/);
  if (m === "POST" && mu) {
    if (u.role !== "client_admin") return json({ ok: false, erreur: "interdit" }, 403);
    const appareilId = mu[1], slot = Number(mu[2]);
    const b = await lireCorps(request);
    const code = txt(b.code_ussd_solde, 60);
    const [a] = await db(env, [{ sql: "SELECT 1 AS x FROM appareils WHERE id = ? AND client_id = ?", args: [appareilId, cid] }]);
    if (!a.rows.length) return json({ ok: false, erreur: "appareil introuvable" }, 404);
    await db(env, [{ sql: "UPDATE lignes_sim SET code_ussd_solde = ? WHERE appareil_id = ? AND slot = ?", args: [code, appareilId, slot] }]);
    return json({ ok: true });
  }
  const ms = p.match(/^\/client\/appareils\/([0-9a-f-]{36})\/sims\/([12])$/);
  if (m === "POST" && ms) {
    if (u.role !== "client_admin") return json({ ok: false, erreur: "interdit" }, 403);
    const appareilId = ms[1], slot = Number(ms[2]);
    const b = await lireCorps(request);
    if (b.operateur !== undefined && !OPS.includes(b.operateur)) return json({ ok: false, erreur: "operateur invalide" }, 400);
    const actif = b.actif === undefined ? null : (b.actif ? 1 : 0);
    const [a, l] = await db(env, [
      { sql: "SELECT id FROM appareils WHERE id = ? AND client_id = ?", args: [appareilId, cid] },
      { sql: "SELECT id FROM lignes_sim WHERE appareil_id = ? AND slot = ?", args: [appareilId, slot] }
    ]);
    if (!a.rows.length) return json({ ok: false, erreur: "appareil introuvable" }, 404);
    if (l.rows.length) {
      await db(env, [{ sql: "UPDATE lignes_sim SET actif = COALESCE(?, actif), operateur = COALESCE(?, operateur), numero = COALESCE(?, numero) WHERE appareil_id = ? AND slot = ?", args: [actif, b.operateur ?? null, txt(b.numero, 20), appareilId, slot] }]);
    } else {
      if (!b.operateur) return json({ ok: false, erreur: "operateur requis pour une nouvelle SIM" }, 400);
      await db(env, [{ sql: "INSERT INTO lignes_sim (id, appareil_id, slot, operateur, numero, actif, cree_le) VALUES (?, ?, ?, ?, ?, ?, ?)", args: [crypto.randomUUID(), appareilId, slot, b.operateur, txt(b.numero, 20), actif ?? 1, Date.now()] }]);
    }
    const [r] = await db(env, [{ sql: "SELECT slot, operateur, numero, actif FROM lignes_sim WHERE appareil_id = ? ORDER BY slot", args: [appareilId] }]);
    return json({ ok: true, sims: r.rows });
  }
  return json({ ok: false, erreur: "introuvable" }, 404);
}
