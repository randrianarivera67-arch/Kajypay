import { readdirSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const url = process.env.TURSO_DATABASE_URL;
const token = process.env.TURSO_AUTH_TOKEN;
if (!url || !token) { console.error("Secrets Turso manquants"); process.exit(1); }
const endpoint = url.replace(/^libsql:\/\//, "https://") + "/v2/pipeline";
const dir = join(dirname(fileURLToPath(import.meta.url)), "..", "migrations");
const files = readdirSync(dir).filter(f => f.endsWith(".sql")).sort();
for (const f of files) {
  const stmts = readFileSync(join(dir, f), "utf8").split(";").map(s => s.trim()).filter(Boolean);
  const requests = stmts.map(sql => ({ type: "execute", stmt: { sql } }));
  requests.push({ type: "close" });
  const res = await fetch(endpoint, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ requests }) });
  if (!res.ok) { console.error(f, "HTTP", res.status, await res.text()); process.exit(1); }
  const data = await res.json();
  const errs = data.results.filter(r => r.type === "error");
  if (errs.length) { console.error(f, JSON.stringify(errs)); process.exit(1); }
  console.log("OK", f, stmts.length, "instructions");
}
