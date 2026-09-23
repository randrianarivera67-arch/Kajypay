import assert from "node:assert/strict";
import { analyserSms, numero } from "../src/parser.js";
const cas = [
  ["Vous avez recu un transfert de 19500Ar venant du 0370000001 Nouveau Solde: 44881Ar", null, { operateur: "orange", montant_ar: 19500, numero_payeur: "0370000001", nom_payeur: null }],
  ["Vous avez reçu un transfert de 1 000 Ar venant du 0320000002 Nouveau Solde: 5000Ar", null, { operateur: "orange", montant_ar: 1000, numero_payeur: "0320000002" }],
  ["21 250 Ar recu de Rakoto Jean 0340000003 le 04/06/26. Solde: 1 785 889 Ar", null, { operateur: "mvola", montant_ar: 21250, numero_payeur: "0340000003", nom_payeur: "Rakoto Jean" }],
  ["Vous avez recu Ar 90300 de Rabe 330000004. Solde: Ar 3437519.5", null, { operateur: "airtel", montant_ar: 90300, numero_payeur: "0330000004", nom_payeur: "Rabe" }],
  ["21 250 Ar recu de Rakoto 0340000003 le 04/06/26. Solde: 1 785 889 Ar", "orange", null],
  ["Vous avez envoye 5000 Ar a Rakoto 0340000003. Solde: 1000 Ar", null, null],
  ["Promo: gagnez 1000 Ar de bonus!", null, null]
];
let ok = 0;
for (const [texte, op, attendu] of cas) {
  const r = analyserSms(texte, op);
  if (attendu === null) assert.equal(r.reconnu, false, texte);
  else for (const [k, v] of Object.entries(attendu)) assert.equal(r[k], v, `${k} :: ${texte}`);
  ok++;
}
assert.equal(numero("261340000003"), "0340000003");
assert.equal(numero("12345"), null);
console.log(`OK parser: ${ok} cas + 2 numeros`);
