package mg.kajypay.gateway;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/** Retrait : recupere un ordre, l'envoie via la FILE USSD (jamais en collision avec le solde), rend le resultat. */
public final class RetraitUssd {
    private static final String TAG = "RetraitUssd";

    public static boolean estEnCours() { return UssdQueue.enCours() != null; }

    public static synchronized void traiterUn(Context c) {
        Store s = new Store(c);
        if (!s.estAppaire() || s.pause() || !UssdReader.isEnabled(c)) return;
        if (UssdQueue.enCours() != null) return; // une session USSD a la fois
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
        final String id = ret.optString("id"), op = ret.optString("operateur");
        final String num = ret.optString("numero_beneficiaire"), pin = ret.optString("pin_chiffre");
        long montant = ret.optLong("montant_ar");
        final int slotNo = ret.optInt("sim_slot", 0);
        boolean pinSepare = ret.optInt("retrait_pin_separe", 0) == 1;
        int steps = ret.optInt("retrait_max_steps", 1);
        String code = subst(ret.optString("retrait_code", ""), num, String.valueOf(montant), pin);
        String dial = code, menu = "";
        if (code.indexOf('|') >= 0) {
            String[] p = code.split("\\|");
            dial = p[0];
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < p.length; i++) { if (sb.length() > 0) sb.append('|'); sb.append(p[i]); }
            menu = sb.toString();
            if (steps < p.length - 1) steps = p.length - 1;
        }
        String pinArme = pinSepare ? pin : "";
        UssdQueue.Job job = new UssdQueue.Job(id, dial, opMaj(op), pinArme, menu, Math.max(steps, 1),
            (rid, success, response) -> {
                String statut = success ? "envoye" : "echoue";
                String motif = success ? "Transfert initié (confirmation par SMS)" : ("Échec : " + response);
                rendre(c, s, id, statut, slotNo, response, motif);
                if (success && slotNo > 0) BalanceScheduler.apresMouvement(c, slotNo);
            });
        UssdQueue.enqueue(c, job);
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
