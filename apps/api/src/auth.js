const enc = new TextEncoder();
const b64u = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
const fromB64u = s => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), c => c.charCodeAt(0));
const ITER = 50000;
async function deriver(mdp, sel, iter) {
  const cle = await crypto.subtle.importKey("raw", enc.encode(mdp), "PBKDF2", false, ["deriveBits"]);
  return crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: sel, iterations: iter }, cle, 256);
}
export async function hacherMotDePasse(mdp) {
  const sel = crypto.getRandomValues(new Uint8Array(16));
  return `pbkdf2$${ITER}$${b64u(sel)}$${b64u(await deriver(mdp, sel, ITER))}`;
}
export async function verifierMotDePasse(mdp, stocke) {
  const [algo, iter, sel, h] = (stocke || "").split("$");
  if (algo !== "pbkdf2") return false;
  const calc = new Uint8Array(await deriver(mdp, fromB64u(sel), Number(iter)));
  const attendu = fromB64u(h);
  return calc.length === attendu.length && crypto.subtle.timingSafeEqual(calc, attendu);
}
export function egalSecret(a, b) {
  const x = enc.encode(a || ""), y = enc.encode(b || "");
  return x.length > 0 && x.length === y.length && crypto.subtle.timingSafeEqual(x, y);
}
async function cleHmac(secret) {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}
export async function signerJwt(payload, secret, dureeSec = 7 * 24 * 3600) {
  const now = Math.floor(Date.now() / 1000);
  const entete = b64u(enc.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const corps = b64u(enc.encode(JSON.stringify({ ...payload, iat: now, exp: now + dureeSec })));
  const sig = await crypto.subtle.sign("HMAC", await cleHmac(secret), enc.encode(`${entete}.${corps}`));
  return `${entete}.${corps}.${b64u(sig)}`;
}
export async function verifierJwt(jeton, secret) {
  const p = (jeton || "").split(".");
  if (p.length !== 3) return null;
  const ok = await crypto.subtle.verify("HMAC", await cleHmac(secret), fromB64u(p[2]), enc.encode(`${p[0]}.${p[1]}`));
  if (!ok) return null;
  const data = JSON.parse(new TextDecoder().decode(fromB64u(p[1])));
  return data.exp > Date.now() / 1000 ? data : null;
}
