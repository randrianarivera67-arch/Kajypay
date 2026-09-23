import { db } from "./db.js";
import { hacherMotDePasse, verifierMotDePasse, egalSecret, signerJwt, verifierJwt } from "./auth.js";
import { gererAdmin } from "./admin.js";
import { gererGateway } from "./gateway.js";
import { gererClient } from "./client.js";
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Init-Key", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...CORS } });
const lireCorps = async req => { try { return await req.json(); } catch { return {}; } };
const emailValide = e => typeof e === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
async function utilisateurCourant(request, env) {
  const h = request.headers.get("Authorization") || "";
  return h.startsWith("Bearer ") ? verifierJwt(h.slice(7), env.JWT_SECRET) : null;
}
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const route = `${request.method} ${url.pathname}`;
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    try {
      if (route === "GET /sante") {
        const [r] = await db(env, [{ sql: "SELECT COUNT(*) AS tables FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'" }]);
        return json({ ok: true, service: "kajypay-api", tables: r.rows[0].tables });
      }
      if (route === "POST /auth/init") {
        if (!egalSecret(request.headers.get("X-Init-Key"), env.INIT_KEY)) return json({ ok: false, erreur: "interdit" }, 403);
        const { email, mot_de_passe } = await lireCorps(request);
        if (!emailValide(email) || typeof mot_de_passe !== "string" || mot_de_passe.length < 10) return json({ ok: false, erreur: "email ou mot de passe invalide (10 caracteres minimum)" }, 400);
        const [existe] = await db(env, [{ sql: "SELECT COUNT(*) AS n FROM utilisateurs WHERE role = 'super_admin'" }]);
        if (existe.rows[0].n > 0) return json({ ok: false, erreur: "super admin deja cree" }, 409);
        await db(env, [{ sql: "INSERT INTO utilisateurs (id, client_id, email, mot_de_passe_hash, role, actif, cree_le) VALUES (?, NULL, ?, ?, 'super_admin', 1, ?)", args: [crypto.randomUUID(), email.toLowerCase(), await hacherMotDePasse(mot_de_passe), Date.now()] }]);
        return json({ ok: true, message: "super admin cree" }, 201);
      }
      if (route === "POST /auth/connexion") {
        const { email, mot_de_passe } = await lireCorps(request);
        if (!emailValide(email) || typeof mot_de_passe !== "string") return json({ ok: false, erreur: "identifiants invalides" }, 401);
        const [r] = await db(env, [{ sql: "SELECT id, client_id, role, mot_de_passe_hash FROM utilisateurs WHERE email = ? AND actif = 1", args: [email.toLowerCase()] }]);
        const u = r.rows[0];
        if (!u || !(await verifierMotDePasse(mot_de_passe, u.mot_de_passe_hash))) return json({ ok: false, erreur: "identifiants invalides" }, 401);
        const jeton = await signerJwt({ sub: u.id, role: u.role, client_id: u.client_id }, env.JWT_SECRET);
        return json({ ok: true, jeton, role: u.role, client_id: u.client_id });
      }
      if (route === "GET /moi") {
        const u = await utilisateurCourant(request, env);
        if (!u) return json({ ok: false, erreur: "non connecte" }, 401);
        return json({ ok: true, utilisateur: { id: u.sub, role: u.role, client_id: u.client_id } });
      }
      if (url.pathname.startsWith("/gateway/")) return gererGateway(request, env, url);
      if (url.pathname.startsWith("/client/")) {
        const u = await utilisateurCourant(request, env);
        if (!u) return json({ ok: false, erreur: "non connecte" }, 401);
        if (!["client_admin", "client_staff"].includes(u.role) || !u.client_id) return json({ ok: false, erreur: "interdit" }, 403);
        return gererClient(request, env, url, u);
      }
      if (url.pathname.startsWith("/admin/")) {
        const u = await utilisateurCourant(request, env);
        if (!u) return json({ ok: false, erreur: "non connecte" }, 401);
        if (u.role !== "super_admin") return json({ ok: false, erreur: "interdit" }, 403);
        return gererAdmin(request, env, url);
      }
      return json({ ok: false, erreur: "introuvable" }, 404);
    } catch (e) {
      console.error(e);
      return json({ ok: false, erreur: "erreur interne" }, 500);
    }
  }
};
