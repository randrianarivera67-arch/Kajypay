import { db, dbTx } from "./db.js";
import { hacherMotDePasse } from "./auth.js";
import { json, lireCorps, emailValide } from "./http.js";
export async function gererAdmin(request, env, url) {
  const m = request.method, p = url.pathname;
  if (m === "GET" && p === "/admin/clients") {
    const [r] = await db(env, [{ sql: "SELECT c.id, c.nom, c.telephone, c.solde_ar, c.tarif_sms_ar, c.minimum_mensuel_ar, c.statut, c.cree_le, (SELECT email FROM utilisateurs u WHERE u.client_id = c.id AND u.role = 'client_admin' LIMIT 1) AS email_admin, (SELECT COUNT(*) FROM appareils a WHERE a.client_id = c.id) AS appareils FROM clients c ORDER BY c.cree_le DESC" }]);
    return json({ ok: true, clients: r.rows });
  }
  if (m === "POST" && p === "/admin/clients") {
    const { nom, telephone, email_admin, mot_de_passe_admin, minimum_mensuel_ar } = await lireCorps(request);
    if (typeof nom !== "string" || nom.trim().length < 2) return json({ ok: false, erreur: "nom invalide" }, 400);
    if (!emailValide(email_admin)) return json({ ok: false, erreur: "email admin invalide" }, 400);
    if (typeof mot_de_passe_admin !== "string" || mot_de_passe_admin.length < 10) return json({ ok: false, erreur: "mot de passe admin: 10 caracteres minimum" }, 400);
    const minimum = Number.isInteger(minimum_mensuel_ar) && minimum_mensuel_ar >= 0 ? minimum_mensuel_ar : 0;
    const email = email_admin.toLowerCase();
    const [ex] = await db(env, [{ sql: "SELECT 1 AS x FROM utilisateurs WHERE email = ?", args: [email] }]);
    if (ex.rows.length) return json({ ok: false, erreur: "email deja utilise" }, 409);
    const clientId = crypto.randomUUID(), now = Date.now();
    await dbTx(env, [
      { sql: "INSERT INTO clients (id, nom, telephone, email, solde_ar, tarif_sms_ar, minimum_mensuel_ar, statut, cree_le) VALUES (?, ?, ?, ?, 0, 20, ?, 'actif', ?)", args: [clientId, nom.trim(), typeof telephone === "string" ? telephone.trim() : null, email, minimum, now] },
      { sql: "INSERT INTO utilisateurs (id, client_id, email, mot_de_passe_hash, role, actif, cree_le) VALUES (?, ?, ?, ?, 'client_admin', 1, ?)", args: [crypto.randomUUID(), clientId, email, await hacherMotDePasse(mot_de_passe_admin), now] }
    ]);
    return json({ ok: true, client_id: clientId }, 201);
  }
  const rech = p.match(/^\/admin\/clients\/([0-9a-f-]{36})\/recharge$/);
  if (m === "POST" && rech) {
    const clientId = rech[1];
    const { montant_ar, note } = await lireCorps(request);
    if (!Number.isInteger(montant_ar) || montant_ar < 1 || montant_ar > 100000000) return json({ ok: false, erreur: "montant invalide" }, 400);
    const [c] = await db(env, [{ sql: "SELECT id FROM clients WHERE id = ?", args: [clientId] }]);
    if (!c.rows.length) return json({ ok: false, erreur: "client introuvable" }, 404);
    await dbTx(env, [
      { sql: "UPDATE clients SET solde_ar = solde_ar + ? WHERE id = ?", args: [montant_ar, clientId] },
      { sql: "INSERT INTO mouvements_credit (id, client_id, type, montant_ar, sms_id, note, cree_le) VALUES (?, ?, 'recharge', ?, NULL, ?, ?)", args: [crypto.randomUUID(), clientId, montant_ar, typeof note === "string" ? note.slice(0, 200) : null, Date.now()] }
    ]);
    const [s] = await db(env, [{ sql: "SELECT solde_ar FROM clients WHERE id = ?", args: [clientId] }]);
    return json({ ok: true, solde_ar: s.rows[0].solde_ar });
  }
  return json({ ok: false, erreur: "introuvable" }, 404);
}
