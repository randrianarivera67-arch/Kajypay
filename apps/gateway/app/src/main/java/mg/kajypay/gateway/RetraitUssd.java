package mg.kajypay.gateway;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Moteur de retrait (envoi d'argent par USSD), repris de l'ancienne passerelle.
 * KajyPay ne fait un retrait QUE si le compte est autorisé côté serveur et si un
 * PIN est enregistré localement pour l'opérateur. Le PIN ne quitte jamais le
 * téléphone. Un seul retrait à la fois.
 */
public final class RetraitUssd {
    private static final String TAG = "RetraitUssd";
    private static volatile boolean enCours = false;

    public static boolean estEnCours() { return enCours; }

    /** Traite au plus un retrait en attente. Appelé par le service périodiquement. */
    public static synchronized void traiterUn(Context c) {
        if (enCours) return;
        Store s = new Store(c);
        if (!s.estAppaire() || s.pause() || !s.retraitAutorise()) return;
        if (!UssdReader.estVivant(c)) return;
        try {
            JSONObject r = new JSONObject(Api.post(s.api() + "/gateway/retraits-attente", "{}", "Appareil " + s.jeton()));
            if (!r.optBoolean("ok")) return;
            JSONArray arr = r.optJSONArray("retraits");
            if (arr == null || arr.length() == 0) return;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ret = arr.getJSONObject(i);
                String op = ret.optString("operateur");
                if (ret.optString("pin_chiffre", "").isEmpty()) continue; // pas de PIN fourni : on saute
                String id = ret.getString("id");
                // réserver côté serveur
                JSONObject pr = new JSONObject(Api.post(s.api() + "/gateway/retrait-prendre", new JSONObject().put("retrait_id", id).toString(), "Appareil " + s.jeton()));
                if (!pr.optBoolean("ok")) continue; // déjà pris par un autre appareil
                executer(c, s, ret);
                return; // un seul à la fois
            }
        } catch (Exception e) { Log.e(TAG, "traiterUn: " + e.getMessage()); }
    }

    private static void executer(Context c, Store s, JSONObject ret) {
        enCours = true;
        String id = ret.optString("id");
        String op = ret.optString("operateur");
        String num = ret.optString("numero_beneficiaire");
        long montant = ret.optLong("montant_ar");
        int slotNo = ret.optInt("sim_slot", slotDe(c, op));
        // Gabarit fourni par le serveur : jamais inventé côté APK.
        String code = ret.optString("retrait_code", "");
        String menu = ret.optString("retrait_menu", "");
        boolean pinSepareMode = ret.optInt("retrait_pin_separe", 0) == 1;
        int steps = ret.optInt("retrait_max_steps", 1);
        String pin = ret.optString("pin_chiffre", "");
        // Substitution des jetons {num} {montant} {pin} dans le code ET la séquence.
        String mStr = String.valueOf(montant);
        code = subst(code, num, mStr, pin);
        menu = subst(menu, num, mStr, pin);
        // Orange : PIN saisi à l'invite (séparé). MVola : tout dans le code (1 envoi).
        // Airtel : séquence tapée écran par écran (menu), comme la lecture de solde.
        String pinSepare = pinSepareMode ? pin : "";
        String dial = code;
        String seq = menu;
        if (code.isEmpty()) { rendre(c, s, id, "echoue", slotNo, null, "Aucun modèle USSD retrait configuré pour " + op); enCours = false; return; }
        if (!pinSepareMode && !menu.isEmpty()) steps = Math.max(steps, menu.split("\\|").length);
        final String ref = "retrait-" + id;
        if (!UssdReader.armerRetrait(ref, pinSepare, seq, steps)) { rendre(c, s, id, "echoue", slotNo, null, "Un autre retrait est en cours"); enCours = false; return; }
        if (!SoldeUssd_composerPublic(c, dial, SoldeUssd.subIdPourSlot(c, slotNo))) {
            UssdReader.desarmer();
            rendre(c, s, id, "echoue", slotNo, null, "Composition impossible (application Téléphone par défaut ?)");
            enCours = false; return;
        }
        final Handler hh = new Handler(Looper.getMainLooper());
        final long debut = System.currentTimeMillis();
        final long INACTIVITE = 60_000L, ABSOLU = 240_000L;
        final boolean[] fini = { false };
        final Runnable conclure = new Runnable() {
            @Override public void run() {
                if (fini[0]) return; fini[0] = true;
                hh.removeCallbacksAndMessages(null);
                boolean initiee = UssdReader.retraitInitiee();
                boolean echouee = UssdReader.retraitEchouee();
                boolean pinOk = UssdReader.retraitPinSubmitted();
                String texte = UssdReader.retraitTexte();
                UssdReader.desarmer();
                String statut; String motif = "";
                if (echouee) { statut = "echoue"; motif = "Refus opérateur"; }
                else if (initiee) { statut = "envoye"; motif = "Transfert initié (confirmation par SMS)"; }
                else if (pinOk) { statut = "envoye"; motif = "PIN saisi, en attente de confirmation SMS"; }
                else { statut = "echoue"; motif = "Aucune confirmation (écran non traité)"; }
                rendre(c, s, id, statut, slotNo, texte, motif);
                enCours = false;
            }
        };
        final Runnable sonde = new Runnable() {
            @Override public void run() {
                if (fini[0]) return;
                if (UssdReader.retraitConclu()) { hh.postDelayed(conclure, 1200L); return; }
                long now = System.currentTimeMillis();
                long prog = UssdReader.getLastProgressAt();
                if (prog <= 0) prog = debut;
                if (now - debut >= ABSOLU || now - prog >= INACTIVITE) { conclure.run(); return; }
                hh.postDelayed(this, 1000L);
            }
        };
        hh.postDelayed(sonde, 2000L);
    }

    private static String subst(String modele, String num, String montant, String pin) {
        if (modele == null) return "";
        return modele
            .replace("{numero}", num).replace("{num}", num)
            .replace("{montant}", montant).replace("{mnt}", montant)
            .replace("{pin}", pin);
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

    @SuppressLint("MissingPermission")
    private static int slotDe(Context c, String op) {
        try {
            JSONArray a = new JSONArray(new Store(c).sims());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (op.equals(o.optString("operateur")) && o.optInt("actif", 1) == 1) return o.optInt("slot");
            }
        } catch (Exception ignore) { }
        return 1;
    }

    // pont vers la composition de SoldeUssd (même logique placeCall + fallbacks)
    static boolean SoldeUssd_composerPublic(Context c, String code, int subId) {
        return SoldeUssd.composerPublic(c, code, subId);
    }
}
