import { db, dbTx } from "./db.js";
import { hacherMotDePasse } from "./auth.js";
import { json, lireCorps, emailValide } from "./http.js";
export async function gererAdmin(request, env, url) {
  const m = request.method, p = url.pathname;
  if (m === "GET" && p === "/admin/clients") {
    const [r] = await db(env, [{ sql: "SELECT c.id, c.nom, c.telephone, c.solde_ar, c.tarif_sms_ar, c.minimum_mensuel_ar, c.statut, c.retrait_autorise, c.cree_le, (SELECT email FROM utilisateurs u WHERE u.client_id = c.id AND u.role = 'client_admin' LIMIT 1) AS email_admin, (SELECT COUNT(*) FROM appareils a WHERE a.client_id = c.id) AS appareils FROM clients c ORDER BY c.cree_le DESC" }]);
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
  if (m === "GET" && p === "/admin/versions") {
    const [r] = await db(env, [{ sql: "SELECT app, version_code, version_nom, url, notes, obligatoire, maj FROM versions_app ORDER BY app" }]);
    return json({ ok: true, versions: r.rows });
  }
  if (m === "POST" && p === "/admin/versions") {
    const { app, version_code, version_nom, url, notes, obligatoire } = await lireCorps(request);
    if (typeof app !== "string" || !/^[a-z0-9_-]{2,20}$/.test(app)) return json({ ok: false, erreur: "app invalide" }, 400);
    if (!Number.isInteger(version_code) || version_code < 1) return json({ ok: false, erreur: "version_code invalide" }, 400);
    if (typeof version_nom !== "string" || !version_nom.trim()) return json({ ok: false, erreur: "version_nom requis" }, 400);
    if (typeof url !== "string" || !/^https:\/\//.test(url)) return json({ ok: false, erreur: "url https requise" }, 400);
    await db(env, [{ sql: "INSERT INTO versions_app (app, version_code, version_nom, url, notes, obligatoire, maj) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(app) DO UPDATE SET version_code = excluded.version_code, version_nom = excluded.version_nom, url = excluded.url, notes = excluded.notes, obligatoire = excluded.obligatoire, maj = excluded.maj", args: [app, version_code, version_nom.trim().slice(0, 20), url.slice(0, 300), typeof notes === "string" ? notes.slice(0, 300) : null, obligatoire ? 1 : 0, Date.now()] }]);
    return json({ ok: true });
  }
  if (m === "GET" && p === "/admin/modeles-retrait") {
    const [r] = await db(env, [{ sql: "SELECT operateur, code, pin_separe, max_steps, maj FROM modeles_retrait" }]);
    return json({ ok: true, modeles: r.rows });
  }
  if (m === "POST" && p === "/admin/modeles-retrait") {
    const { operateur, code, pin_separe, max_steps } = await lireCorps(request);
    if (!["mvola", "orange", "airtel"].includes(operateur)) return json({ ok: false, erreur: "operateur invalide" }, 400);
    if (typeof code !== "string" || code.trim().length < 2) return json({ ok: false, erreur: "code requis" }, 400);
    const ms = Number.isInteger(max_steps) && max_steps > 0 ? max_steps : 1;
    await db(env, [{ sql: "INSERT INTO modeles_retrait (operateur, code, pin_separe, max_steps, maj) VALUES (?, ?, ?, ?, ?) ON CONFLICT(operateur) DO UPDATE SET code = excluded.code, pin_separe = excluded.pin_separe, max_steps = excluded.max_steps, maj = excluded.maj", args: [operateur, code.trim().slice(0, 120), pin_separe ? 1 : 0, ms, Date.now()] }]);
    return json({ ok: true });
  }
  const ra = p.match(/^\/admin\/clients\/([0-9a-f-]{36})\/retrait-autorise$/);
  if (m === "POST" && ra) {
    const { autorise } = await lireCorps(request);
    await db(env, [{ sql: "UPDATE clients SET retrait_autorise = ? WHERE id = ?", args: [autorise ? 1 : 0, ra[1]] }]);
    return json({ ok: true, retrait_autorise: autorise ? 1 : 0 });
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
