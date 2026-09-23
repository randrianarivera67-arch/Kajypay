import { readdirSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
const url = process.env.TURSO_DATABASE_URL, token = process.env.TURSO_AUTH_TOKEN;
if (!url || !token) { console.error("Secrets Turso manquants"); process.exit(1); }
const endpoint = url.replace(/^libsql:\/\//, "https://") + "/v2/pipeline";
async function pipeline(requests) {
  const res = await fetch(endpoint, { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: JSON.stringify({ requests }) });
  if (!res.ok) { console.error("HTTP", res.status, await res.text()); process.exit(1); }
  return res.json();
}
const q = sql => ({ type: "execute", stmt: { sql } });
let d = await pipeline([q("CREATE TABLE IF NOT EXISTS _migrations (nom TEXT PRIMARY KEY, applique_le INTEGER NOT NULL)"), q("SELECT nom FROM _migrations"), { type: "close" }]);
if (d.results.some(r => r.type === "error")) { console.error(JSON.stringify(d.results)); process.exit(1); }
const faits = new Set(d.results[1].response.result.rows.map(r => r[0].value));
const dir = join(dirname(fileURLToPath(import.meta.url)), "..", "migrations");
for (const f of readdirSync(dir).filter(f => f.endsWith(".sql")).sort()) {
  if (faits.has(f)) { console.log("deja applique", f); continue; }
  const stmts = readFileSync(join(dir, f), "utf8").split(";").map(s => s.trim()).filter(Boolean);
  const steps = [{ stmt: { sql: "BEGIN" } }];
  stmts.forEach((sql, i) => steps.push({ stmt: { sql }, condition: { type: "ok", step: i } }));
  steps.push({ stmt: { sql: "INSERT INTO _migrations (nom, applique_le) VALUES (?, ?)", args: [{ type: "text", value: f }, { type: "integer", value: String(Date.now()) }] }, condition: { type: "ok", step: steps.length - 1 } });
  const n = steps.length;
  steps.push({ stmt: { sql: "COMMIT" }, condition: { type: "ok", step: n - 1 } });
  steps.push({ stmt: { sql: "ROLLBACK" }, condition: { type: "not", cond: { type: "ok", step: n } } });
  d = await pipeline([{ type: "batch", batch: { steps } }, { type: "close" }]);
  const r = d.results[0];
  if (r.type === "error") { console.error(f, r.error.message); process.exit(1); }
  const err = r.response.result.step_errors.find(e => e);
  if (err) { console.error(f, err.message); process.exit(1); }
  console.log("OK", f, stmts.length, "instructions");
}
