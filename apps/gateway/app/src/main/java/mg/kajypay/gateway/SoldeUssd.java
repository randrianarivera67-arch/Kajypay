package mg.kajypay.gateway;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consultation du solde Mobile Money par USSD, pour une SIM donnée.
 *
 * Deux voies, comme dans l'ancienne passerelle :
 *  1) voie SILENCIEUSE (sendUssdRequest) : le solde remonte sans afficher de
 *     boîte, si l'opérateur clôt la session et renvoie un montant exploitable ;
 *  2) sinon, LECTURE D'ÉCRAN via UssdReader : une boîte apparaît brièvement,
 *     on lit le texte, on la ferme. Les menus multi-étapes (code avec '|')
 *     passent directement par cette voie.
 *
 * KajyPay ne fait que consulter : aucune saisie de PIN, aucun envoi d'argent.
 */
public final class SoldeUssd {
    private static final String TAG = "SoldeUssd";

    public interface Callback { void onResult(boolean ok, Long montantAr, String texte); }

    static boolean exploitable(String txt) {
        if (txt == null) return false;
        String t = txt.trim();
        if (t.isEmpty()) return false;
        String b = t.toLowerCase(Locale.ROOT);
        if (b.contains("unknown application") || b.contains("invalid") || b.contains("not available")
                || b.contains("try again") || b.contains("indisponible")) return false;
        if (Pattern.compile("(\\d[\\d\\s.,]{1,})\\s*(ar|mga|ariary|fc)", Pattern.CASE_INSENSITIVE).matcher(t).find()) return true;
        return (b.contains("solde") || b.contains("balance")) && Pattern.compile("\\d{2,}").matcher(t).find();
    }

    /** Extrait un montant en ariary depuis le texte du solde. */
    public static Long montant(String txt) {
        if (txt == null) return null;
        Matcher m = Pattern.compile("(\\d[\\d\\s.,]{2,})\\s*(ar|mga|ariary)", Pattern.CASE_INSENSITIVE).matcher(txt);
        Long best = null;
        while (m.find()) {
            try {
                String n = m.group(1).replaceAll("[\\s.,]", "");
                long v = Long.parseLong(n);
                if (best == null || v > best) best = v;
            } catch (Exception ignore) { }
        }
        return best;
    }

    @SuppressLint("MissingPermission")
    static int subIdPourSlot(Context c, int slot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return -1;
        try {
            SubscriptionManager sm = (SubscriptionManager) c.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return -1;
            List<SubscriptionInfo> l = sm.getActiveSubscriptionInfoList();
            if (l == null) return -1;
            for (SubscriptionInfo i : l) if (i.getSimSlotIndex() + 1 == slot) return i.getSubscriptionId();
        } catch (Exception ignore) { }
        return -1;
    }

    /**
     * @param slot 1 ou 2
     * @param code code USSD (peut contenir '|' pour une séquence multi-étape)
     */
    @SuppressLint("MissingPermission")
    public static void consulter(Context context, int slot, String code, Callback cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { cb.onResult(false, null, "Android trop ancien"); return; }
        if (code == null || code.trim().isEmpty()) { cb.onResult(false, null, "Aucun code USSD configuré"); return; }
        final int subId = subIdPourSlot(context, slot);
        if (subId < 0) { cb.onResult(false, null, "SIM " + slot + " introuvable"); return; }

        // Code multi-étape -> lecture d'écran directe
        if (code.indexOf('|') >= 0) { lireParEcran(context, subId, slot, code, cb); return; }

        final boolean[] repondu = { false };
        try {
            TelephonyManager base = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            TelephonyManager tm = base.createForSubscriptionId(subId);
            tm.sendUssdRequest(code, new TelephonyManager.UssdResponseCallback() {
                @Override public void onReceiveUssdResponse(TelephonyManager t, String req, CharSequence msg) {
                    if (repondu[0]) return; repondu[0] = true;
                    String txt = msg == null ? "" : msg.toString().trim();
                    if (exploitable(txt)) cb.onResult(true, montant(txt), txt);
                    else lireParEcran(context, subId, slot, code, cb);
                }
                @Override public void onReceiveUssdResponseFailed(TelephonyManager t, String req, int c) {
                    if (repondu[0]) return; repondu[0] = true;
                    lireParEcran(context, subId, slot, code, cb);
                }
            }, new Handler(Looper.getMainLooper()));
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (repondu[0]) return; repondu[0] = true;
                lireParEcran(context, subId, slot, code, cb);
            }, 15_000L);
        } catch (Exception e) {
            if (repondu[0]) return; repondu[0] = true;
            lireParEcran(context, subId, slot, code, cb);
        }
    }

    @SuppressLint("MissingPermission")
    private static void lireParEcran(Context context, int subId, int slot, String code, Callback cb) {
        if (!UssdReader.estVivant(context)) {
            cb.onResult(false, null, "Service d'accessibilité KajyPay désactivé");
            return;
        }
        String dial = code;
        String seq = "";
        int steps = 0;
        if (code.indexOf('|') >= 0) {
            String[] parts = code.split("\\|");
            java.util.List<String> clean = new java.util.ArrayList<>();
            for (String x : parts) if (x != null && !x.trim().isEmpty()) clean.add(x.trim());
            if (clean.size() >= 2) {
                dial = clean.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < clean.size(); i++) { if (sb.length() > 0) sb.append('|'); sb.append(clean.get(i)); }
                seq = sb.toString();
                steps = clean.size() - 1;
            }
        }
        final String ref = "solde-" + slot + "-" + System.currentTimeMillis();
        if (!UssdReader.armer(ref, seq, steps)) { cb.onResult(false, null, "Une autre lecture USSD est en cours"); return; }
        if (!composer(context, dial, subId)) {
            UssdReader.desarmer();
            cb.onResult(false, null, "Impossible de composer le code (application Téléphone par défaut ?)");
            return;
        }
        final Handler hh = new Handler(Looper.getMainLooper());
        final boolean[] conclu = { false };
        final long debut = System.currentTimeMillis();
        final long INACTIVITE = 60_000L, ABSOLU = 180_000L;
        final Runnable conclure = () -> {
            if (conclu[0]) return; conclu[0] = true;
            hh.removeCallbacksAndMessages(null);
            String texte = UssdReader.getTexteLu();
            UssdReader.desarmer();
            boolean ok = texte != null && !texte.trim().isEmpty();
            cb.onResult(ok, ok ? montant(texte) : null, ok ? texte : "Aucune boîte USSD lisible");
        };
        final Runnable sonde = new Runnable() {
            @Override public void run() {
                if (conclu[0]) return;
                if (UssdReader.lectureTerminee()) { hh.postDelayed(conclure, 800L); return; }
                long now = System.currentTimeMillis();
                long prog = UssdReader.getLastProgressAt();
                if (prog <= 0) prog = debut;
                if (now - debut >= ABSOLU || now - prog >= INACTIVITE) { conclure.run(); return; }
                hh.postDelayed(this, 1000L);
            }
        };
        hh.postDelayed(sonde, 1500L);
    }

    @SuppressLint("MissingPermission")
    private static boolean composer(Context context, String code, int subId) {
        android.net.Uri uri = android.net.Uri.parse("tel:" + android.net.Uri.encode(code));
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_CALL, uri);
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            android.telecom.TelecomManager tcm = (android.telecom.TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            String dialer = tcm != null ? tcm.getDefaultDialerPackage() : null;
            if (dialer != null) i.setPackage(dialer);
            android.telecom.PhoneAccountHandle h = compte(context, tcm, subId);
            if (h != null) i.putExtra(android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, h);
            int slot = slotDe(context, subId);
            if (slot >= 0) { i.putExtra("com.android.phone.extra.slot", slot); i.putExtra("simSlot", slot); i.putExtra("slot", slot); }
            i.putExtra("subscription", subId);
            context.startActivity(i);
            return true;
        } catch (Exception e) { Log.e(TAG, "composer: " + e.getMessage()); return false; }
    }

    @SuppressLint("MissingPermission")
    private static android.telecom.PhoneAccountHandle compte(Context c, android.telecom.TelecomManager tcm, int subId) {
        if (tcm == null || subId < 0) return null;
        try {
            List<android.telecom.PhoneAccountHandle> l = tcm.getCallCapablePhoneAccounts();
            if (l == null) return null;
            String cible = String.valueOf(subId);
            for (android.telecom.PhoneAccountHandle h : l) if (cible.equals(h.getId())) return h;
        } catch (Exception ignore) { }
        return null;
    }

    @SuppressLint("MissingPermission")
    private static int slotDe(Context c, int subId) {
        try {
            SubscriptionManager sm = (SubscriptionManager) c.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if (sm == null) return -1;
            List<SubscriptionInfo> l = sm.getActiveSubscriptionInfoList();
            if (l != null) for (SubscriptionInfo i : l) if (i.getSubscriptionId() == subId) return i.getSimSlotIndex();
        } catch (Exception ignore) { }
        return -1;
    }
}
