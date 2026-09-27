package mg.kajypay.gateway;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** Liaison retrait : recupere un retrait, delegue l'envoi USSD a UssdEngine, rend le resultat. */
public final class RetraitUssd {
    private static final String TAG = "RetraitUssd";
    private static volatile boolean enCours = false;

    public static boolean estEnCours() { return enCours; }

    public static synchronized void traiterUn(Context c) {
        if (enCours) return;
        Store s = new Store(c);
        if (!s.estAppaire() || s.pause() || !UssdReader.isEnabled(c)) return;
        try {
            JSONObject r = new JSONObject(Api.post(s.api() + "/gateway/retraits-attente", "{}", "Appareil " + s.jeton()));
            if (!r.optBoolean("ok")) return;
            JSONArray arr = r.optJSONArray("retraits");
            if (arr == null || arr.length() == 0) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ret = arr.getJSONObject(i);
                if (ret.optString("retrait_code", "").isEmpty()) continue;
                if (ret.optString("pin_chiffre", "").isEmpty()) continue;
                String id = ret.getString("id");
                JSONObject pr = new JSONObject(Api.post(s.api() + "/gateway/retrait-prendre",
                    new JSONObject().put("retrait_id", id).toString(), "Appareil " + s.jeton()));
                if (!pr.optBoolean("ok")) continue;
                executer(c, s, ret);
                return;
            }
        } catch (Exception e) { Log.e(TAG, "traiterUn: " + e.getMessage()); }
    }

    private static void executer(Context c, Store s, JSONObject ret) {
        enCours = true;
        String id = ret.optString("id"), op = ret.optString("operateur");
        String num = ret.optString("numero_beneficiaire"), pin = ret.optString("pin_chiffre");
        long montant = ret.optLong("montant_ar");
        int slotNo = ret.optInt("sim_slot", 0);
        boolean pinSepare = ret.optInt("retrait_pin_separe", 0) == 1;
        int steps = ret.optInt("retrait_max_steps", 1);
        String m = String.valueOf(montant);
        String code = subst(ret.optString("retrait_code", ""), num, m, pin);
        // separer dial et menu ; injecter le PIN separe en fin de menu si demande
        String dial = code, menu = "";
        if (code.indexOf('|') >= 0) {
            String[] p = code.split("\\|");
            dial = p[0];
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < p.length; i++) { if (sb.length() > 0) sb.append('|'); sb.append(p[i]); }
            menu = sb.toString();
            if (steps < p.length - 1) steps = p.length - 1;
        }
        final String pinArme = pinSepare ? pin : "";
        UssdEngine.sendUssdInteractive(c, id, dial, opMaj(op), pinArme, menu, Math.max(steps, 1),
            (rid, success, response) -> {
                String statut = success ? "envoye" : "echoue";
                String motif = success ? "Transfert initié (confirmation par SMS)" : ("Échec : " + response);
                rendre(c, s, rid, statut, slotNo, response, motif);
                enCours = false;
            });
    }

    private static void rendre(Context c, Store s, String id, String statut, int slot, String texte, String motif) {
        try {
            JSONObject o = new JSONObject().put("retrait_id", id).put("statut", statut);
            if (slot > 0) o.put("sim_slot", slot);
            if (texte != null) o.put("texte", texte);
            if (motif != null) o.put("motif", motif);
            Api.post(s.api() + "/gateway/retrait-resultat", o.toString(), "Appareil " + s.jeton());
        } catch (Exception e) { Log.e(TAG, "rendre: " + e.getMessage()); }
    }

    private static String subst(String md, String num, String montant, String pin) {
        if (md == null) return "";
        return md.replace("{numero}", num).replace("{num}", num)
                 .replace("{montant}", montant).replace("{mnt}", montant).replace("{pin}", pin);
    }
    private static String opMaj(String op) {
        if ("orange".equals(op)) return "ORANGE";
        if ("mvola".equals(op)) return "MVOLA";
        if ("airtel".equals(op)) return "AIRTEL";
        return op;
    }
}
