const toArg = v => v === null || v === undefined ? { type: "null" } : typeof v === "number" ? (Number.isInteger(v) ? { type: "integer", value: String(v) } : { type: "float", value: v }) : { type: "text", value: String(v) };
export async function db(env, stmts) {
  const endpoint = env.TURSO_DATABASE_URL.replace(/^libsql:\/\//, "https://") + "/v2/pipeline";
  const requests = stmts.map(s => ({ type: "execute", stmt: { sql: s.sql, args: (s.args || []).map(toArg) } }));
  requests.push({ type: "close" });
  const res = await fetch(endpoint, { method: "POST", headers: { Authorization: `Bearer ${env.TURSO_AUTH_TOKEN}`, "Content-Type": "application/json" }, body: JSON.stringify({ requests }) });
  if (!res.ok) throw new Error("Turso HTTP " + res.status);
  const data = await res.json();
  return data.results.slice(0, stmts.length).map(r => {
    if (r.type === "error") throw new Error(r.error.message);
    const { cols, rows, affected_row_count } = r.response.result;
    return { rows: rows.map(row => Object.fromEntries(row.map((c, i) => [cols[i].name, c.type === "integer" ? Number(c.value) : c.value]))), changes: affected_row_count };
  });
}
