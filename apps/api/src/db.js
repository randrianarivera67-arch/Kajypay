const toArg = v => v === null || v === undefined ? { type: "null" } : typeof v === "number" ? (Number.isInteger(v) ? { type: "integer", value: String(v) } : { type: "float", value: v }) : { type: "text", value: String(v) };
const conv = res => ({ rows: res.rows.map(row => Object.fromEntries(row.map((c, i) => [res.cols[i].name, c.type === "integer" ? Number(c.value) : c.value]))), changes: res.affected_row_count });
const stmt = s => ({ sql: s.sql, args: (s.args || []).map(toArg) });
async function envoyer(env, requests) {
  const endpoint = env.TURSO_DATABASE_URL.replace(/^libsql:\/\//, "https://") + "/v2/pipeline";
  const res = await fetch(endpoint, { method: "POST", headers: { Authorization: `Bearer ${env.TURSO_AUTH_TOKEN}`, "Content-Type": "application/json" }, body: JSON.stringify({ requests }) });
  if (!res.ok) throw new Error("Turso HTTP " + res.status);
  return res.json();
}
export async function db(env, stmts) {
  const data = await envoyer(env, [...stmts.map(s => ({ type: "execute", stmt: stmt(s) })), { type: "close" }]);
  return data.results.slice(0, stmts.length).map(r => {
    if (r.type === "error") throw new Error(r.error.message);
    return conv(r.response.result);
  });
}
export async function dbTx(env, stmts) {
  const steps = [{ stmt: { sql: "BEGIN IMMEDIATE" } }];
  stmts.forEach((s, i) => steps.push({ stmt: stmt(s), condition: { type: "ok", step: i } }));
  const n = steps.length;
  steps.push({ stmt: { sql: "COMMIT" }, condition: { type: "ok", step: n - 1 } });
  steps.push({ stmt: { sql: "ROLLBACK" }, condition: { type: "not", cond: { type: "ok", step: n } } });
  const data = await envoyer(env, [{ type: "batch", batch: { steps } }, { type: "close" }]);
  const r = data.results[0];
  if (r.type === "error") throw new Error(r.error.message);
  const err = r.response.result.step_errors.find(e => e);
  if (err) throw new Error(err.message);
  return r.response.result.step_results.slice(1, n).map(conv);
}
