export const CORS = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Init-Key", "Access-Control-Allow-Methods": "GET, POST, OPTIONS" };
export const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json", ...CORS } });
export const lireCorps = async req => { try { return await req.json(); } catch { return {}; } };
export const emailValide = e => typeof e === "string" && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
