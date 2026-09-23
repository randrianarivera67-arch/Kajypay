const sansAccents = s => String(s || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "");
const montant = s => { const n = parseFloat(String(s).replace(/\s/g, "").replace(",", ".")); return Number.isFinite(n) && n > 0 ? Math.round(n) : null; };
export function numero(s) {
  let d = String(s || "").replace(/\D/g, "");
  if (d.startsWith("261")) d = "0" + d.slice(3);
  if (d.length === 9) d = "0" + d;
  return /^03\d{8}$/.test(d) ? d : null;
}
const REF = /(?:ref(?:erence)?|trans(?:action)?\s*id)\s*[:.]?\s*([A-Z0-9][A-Z0-9.\-]{3,})/i;
const MODELES = [
  { operateur: "orange", re: /recu un transfert de\s*([\d\s.,]+?)\s*Ar\s+venant du\s*(\+?\d[\d\s]{7,14}\d)/i, champs: m => ({ montant: m[1], nom: null, numero: m[2] }) },
  { operateur: "mvola", re: /^\s*([\d\s.,]+?)\s*Ar\s+recu de\s+(.+?)\s+(\+?\d{9,12})\b/i, champs: m => ({ montant: m[1], nom: m[2], numero: m[3] }) },
  { operateur: "airtel", re: /recu\s+Ar\s*([\d\s.,]+?)\s+de\s+(.+?)\s+(\+?\d{9,12})\b/i, champs: m => ({ montant: m[1], nom: m[2], numero: m[3] }) }
];
export function analyserSms(texte, operateurAttendu) {
  const t = sansAccents(texte).replace(/\s+/g, " ").trim();
  for (const mod of MODELES) {
    if (operateurAttendu && mod.operateur !== operateurAttendu) continue;
    const m = t.match(mod.re);
    if (!m) continue;
    const c = mod.champs(m);
    const vola = montant(c.montant);
    if (!vola) continue;
    const ref = t.match(REF);
    return { reconnu: true, operateur: mod.operateur, montant_ar: vola, numero_payeur: numero(c.numero), nom_payeur: c.nom ? c.nom.trim().slice(0, 80) : null, reference: ref ? ref[1] : null };
  }
  return { reconnu: false };
}
