import { db } from "./db.js";
const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type, Authorization", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...CORS } });
export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { headers: CORS });
    try {
      if (url.pathname === "/sante") {
        const [r] = await db(env, [{ sql: "SELECT COUNT(*) AS tables FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'" }]);
        return json({ ok: true, service: "kajypay-api", tables: r.rows[0].tables });
      }
      return json({ ok: false, erreur: "introuvable" }, 404);
    } catch (e) {
      return json({ ok: false, erreur: e.message }, 500);
    }
  }
};
