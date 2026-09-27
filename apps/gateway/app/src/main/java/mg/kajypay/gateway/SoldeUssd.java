package mg.kajypay.gateway;

import android.content.Context;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liaison consultation de solde : delegue a UssdEngine (moteur repris de l'ancienne passerelle). */
public final class SoldeUssd {
    public interface Callback { void onResult(boolean ok, Long montantAr, String texte); }

    public static Long montant(String txt) {
        if (txt == null) return null;
        Matcher m = Pattern.compile("(\\d[\\d\\s.,]{2,})\\s*(ar|mga|ariary)", Pattern.CASE_INSENSITIVE).matcher(txt);
        Long best = null;
        while (m.find()) {
            try { long v = Long.parseLong(m.group(1).replaceAll("[\\s.,]", "")); if (best == null || v > best) best = v; }
            catch (Exception ignore) { }
        }
        return best;
    }

    /** code peut contenir '|' (UssdEngine gere le multi-etape lui-meme). */
    public static void consulter(Context c, int slot, String code, Callback cb) {
        if (code == null || code.trim().isEmpty()) { cb.onResult(false, null, "Aucun code USSD configuré"); return; }
        String op = operateurDeSlot(c, slot);
        UssdEngine.lireSoldeUssd(c, "solde-" + slot + "-" + System.currentTimeMillis(), code, op,
            (ref, success, response) -> cb.onResult(success, success ? montant(response) : null, response));
    }

    static String operateurDeSlot(Context c, int slot) {
        try {
            org.json.JSONArray a = new org.json.JSONArray(new Store(c).sims());
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                if (o.optInt("slot") == slot) return o.optString("operateur");
            }
        } catch (Exception ignore) { }
        return "";
    }
}
