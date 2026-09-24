package mg.kajypay.gateway;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service d'accessibilité : lit l'écran des boîtes USSD pour relever le solde
 * Mobile Money, et ferme sans jamais rien saisir les menus parasites (offres,
 * achat de forfait) — protection issue de l'ancienne passerelle SMS.
 *
 * KajyPay ne fait que CONSULTER : aucune saisie de PIN, aucun envoi d'argent.
 * Une seule séquence de menu (facultative) peut être tapée avant lecture, pour
 * les codes multi-étapes (ex. Airtel "*436#|6|2|2011").
 */
public class UssdReader extends AccessibilityService {
    private static final String TAG = "UssdReader";
    private static final long ARM_TIMEOUT_MS = 90_000L;
    private static final long MIN_ACTION_INTERVAL_MS = 800L;
    private static final long ATTENTE_REPEAT_MS = 3000L;

    private static volatile UssdReader INSTANCE = null;
    private static volatile boolean actif = false;
    private static volatile String reference = null;
    private static volatile String menuReply = "";
    private static volatile int maxSteps = 0, menuReplyIndex = 0;
    private static volatile long armedAt = 0L, lastProgressAt = 0L, lastAttenteAt = 0L;
    private static volatile boolean lectureFaite = false;
    private static volatile String texteLu = "", lastAttenteSignature = "", lastHandledSignature = "";
    private static volatile int attenteClics = 0;
    private long lastActionAt = 0L;
    // --- mode retrait ---
    private static volatile boolean modeRetrait = false;
    private static volatile String armedPin = null;
    private static volatile int stepsDone = 0;
    private static volatile boolean pinSubmitted = false, transactionInitiee = false, transactionEchouee = false;
    private static volatile String postSubmitText = "", ecranNonTraite = "";

    private static final String[] BOITE_PARASITE = {
        "hampiditra tolotra", "achat recharge et offre", "acheter", "forfait",
        "mon compte/mot de passe", "services/factures", "offre"
    };
    private static final String[] ATTENTE_MARKERS = {
        "ampanatontosana", "fangatahana", "andraso", "mahandrasa", "tsindrio ny ok",
        "en cours de traitement", "traitement en cours", "veuillez patienter",
        "patientez", "please wait", "processing"
    };
    private static final String[] RESULTAT_MARKERS = {
        "toe bola", "toe-bola", "solde", "trans id", "reference", "ref:",
        "ariary", " ar ", "mga", "montant", "vola voaray", "balance"
    };
    private static final String[] ECRAN_TRANSITOIRE = {
        "execution du code ussd", "ex\u00e9cution du code", "envoi de la demande",
        "running ussd", "connexion en cours", "mandefa ny fangatahana"
    };
    private static final String[] SEND_LABELS = {
        "envoyer", "send", "ok", "alefa", "valider", "confirmer", "confirm",
        "continuer", "continue", "suivant", "next", "yes", "eny", "submit", "envoi"
    };
    private static final String[] CANCEL_LABELS = {
        "annuler", "cancel", "aoka", "fermer", "close", "non", "no", "tsia",
        "retour", "back", "dismiss", "quitter"
    };
    private static final int ATTENTE_CLICS_MAX = 6;
    private static final String[] FIN_TRANSACTION = {
        "transfert initie", "transfert initi", "vous allez recevoir une confirmation",
        "est reussi", "est r\u00e9ussi", "transaction a reussi", "transaction a r\u00e9ussi",
        "repertoire mvola", "r\u00e9pertoire mvola", "comme favori", "enregistrer ce numero",
        "transaction en cours", "nahomby", "vita soa aman-tsara"
    };
    private static final String[] ECHEC_MALGRE_POSITIF = {
        "n'a pas reussi", "pas reussi", "pas r\u00e9ussi", "non reussi", "echoue", "\u00e9chou\u00e9", "echec", "\u00e9chec", "tsy nahomby"
    };
    private static final String[] ECHEC_TERMINAL = {
        "insuffisant", "code secret incorrect", "code incorrect", "code errone", "numero incorrect",
        "numero invalide", "transaction impossible", "operation impossible", "service indisponible",
        "reessayez", "montant invalide", "compte bloque", "une erreur", "erreur est survenue",
        "an error occurred", "ihm non valide", "code ihm", "unknown application", "try again",
        "insufficient", "invalid", "incorrect", "tsy ampy", "kaody diso", "tsy mety", "andramo indray"
    };
    private static final String[] PIN_PROMPTS = {
        "kaody miafina", "code secret", "code pin", "votre pin", "code confidentiel",
        "mot de passe", "enter your pin", "enter pin", "secret code"
    };

    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        try {
            String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(on)) return false;
            String cible = ctx.getPackageName() + "/" + UssdReader.class.getName();
            String court = ctx.getPackageName() + "/.UssdReader";
            for (String p : on.split(":")) if (p.trim().equalsIgnoreCase(cible) || p.trim().equalsIgnoreCase(court)) return true;
        } catch (Exception ignore) { }
        return false;
    }

    public static boolean estVivant(Context ctx) { return isEnabled(ctx) && INSTANCE != null; }

    public static synchronized boolean armer(String ref, String menuSeq, int steps) {
        if (estArme() && reference != null && !reference.equals(ref)) return false;
        actif = true;
        reference = ref;
        menuReply = menuSeq == null ? "" : menuSeq;
        maxSteps = steps;
        menuReplyIndex = 0;
        lectureFaite = false;
        texteLu = "";
        lastAttenteSignature = "";
        lastHandledSignature = "";
        attenteClics = 0;
        armedAt = System.currentTimeMillis();
        lastProgressAt = armedAt;
        return true;
    }

    public static synchronized boolean armerRetrait(String ref, String pin, String menuSeq, int steps) {
        if (estArme() && reference != null && !reference.equals(ref)) return false;
        actif = true; modeRetrait = true; reference = ref;
        armedPin = pin == null ? null : pin.trim();
        menuReply = menuSeq == null ? "" : menuSeq;
        maxSteps = steps < 1 ? 1 : steps;
        menuReplyIndex = 0; stepsDone = 0;
        pinSubmitted = false; transactionInitiee = false; transactionEchouee = false;
        postSubmitText = ""; ecranNonTraite = "";
        lectureFaite = false; texteLu = "";
        lastAttenteSignature = ""; lastHandledSignature = ""; attenteClics = 0;
        armedAt = System.currentTimeMillis(); lastProgressAt = armedAt;
        return true;
    }
    public static boolean retraitPinSubmitted() { return pinSubmitted; }
    public static boolean retraitInitiee() { return transactionInitiee; }
    public static boolean retraitEchouee() { return transactionEchouee; }
    public static int retraitSteps() { return stepsDone; }
    public static String retraitTexte() { return postSubmitText != null && !postSubmitText.trim().isEmpty() ? postSubmitText : texteLu; }
    public static String retraitEcranNonTraite() { return ecranNonTraite; }
    public static boolean retraitConclu() { return transactionInitiee || transactionEchouee; }

    public static void desarmer() { actif = false; modeRetrait = false; reference = null; armedAt = 0L; armedPin = null; }
    public static boolean lectureTerminee() { return lectureFaite; }
    public static String getTexteLu() { return texteLu; }
    public static long getLastProgressAt() { return lastProgressAt; }

    private static boolean estArme() {
        if (!actif) return false;
        if (System.currentTimeMillis() - armedAt >= ARM_TIMEOUT_MS) return false;
        if (modeRetrait) return !(transactionInitiee || transactionEchouee);
        return !lectureFaite;
    }

    @Override protected void onServiceConnected() { super.onServiceConnected(); INSTANCE = this; Log.d(TAG, "connecté"); }
    @Override public boolean onUnbind(android.content.Intent i) { INSTANCE = null; return super.onUnbind(i); }
    @Override public void onDestroy() { INSTANCE = null; super.onDestroy(); }
    private void traiterRetrait(AccessibilityNodeInfo root, String text) {
        try {
            // échec définitif annoncé par l'opérateur
            if (echecTerminal(text)) {
                if (!transactionEchouee) { transactionEchouee = true; postSubmitText = text; fermer(300L, false); }
                return;
            }
            // transfert déjà parti : on ferme par ANNULER
            if (finTransaction(text)) {
                if (!transactionInitiee) { transactionInitiee = true; postSubmitText = text; fermer(300L, true); }
                return;
            }
            if (stepsDone >= maxSteps) return;
            long now = System.currentTimeMillis();
            if (now - lastActionAt < MIN_ACTION_INTERVAL_MS) return;
            AccessibilityNodeInfo edit = findEditable(root);
            if (edit == null) {
                if (ecranTransitoire(text)) return;
                if (ecranDattente(text) && peutCliquerAttente(text)) { lastActionAt = now; fermer(250L, false); }
                return;
            }
            String sig = TextUtils.isEmpty(text) ? "<vide>" : text;
            if (sig.equals(lastHandledSignature)) return;
            boolean pin = demandePin(text);
            boolean pinArme = armedPin != null && !armedPin.isEmpty();
            final String value;
            if (pin && pinArme) value = armedPin;
            else if (!menuReply.isEmpty()) {
                String[] rep = menuReply.split("\\|");
                if (menuReplyIndex >= rep.length) return;
                value = rep[menuReplyIndex];
            } else {
                if (ecranNonTraite.isEmpty()) ecranNonTraite = sig;
                return;
            }
            if (value == null || value.isEmpty()) return;
            lastActionAt = now;
            final boolean utilisePin = pin && pinArme;
            final String s2 = sig;
            ecrireEtValider(value, () -> {
                lastProgressAt = System.currentTimeMillis();
                stepsDone++;
                if (!utilisePin) menuReplyIndex++;
                lastHandledSignature = s2;
                if (pin) pinSubmitted = true;
            });
        } catch (Exception e) { Log.e(TAG, "traiterRetrait: " + e.getMessage()); }
    }

    @Override public void onInterrupt() { }

    private static boolean contient(String t, String[] cles) {
        if (TextUtils.isEmpty(t)) return false;
        String b = t.toLowerCase(Locale.ROOT);
        for (String c : cles) if (b.contains(c)) return true;
        return false;
    }
    private static boolean ecranResultat(String t) { return contient(t, RESULTAT_MARKERS); }
    private static boolean echecTerminal(String t) { return contient(t, ECHEC_TERMINAL); }
    private static boolean finTransaction(String t) {
        if (TextUtils.isEmpty(t)) return false;
        String b = t.toLowerCase(Locale.ROOT);
        for (String m : ECHEC_MALGRE_POSITIF) if (b.contains(m)) return false;
        for (String m : FIN_TRANSACTION) if (b.contains(m)) return true;
        return false;
    }
    private static boolean ressembleMenu(String t) {
        int n = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*\\d\\s*[.)\\-]\\s*\\S").matcher(t);
        while (m.find()) n++;
        return n >= 2;
    }
    private static boolean demandePin(String t) {
        if (TextUtils.isEmpty(t)) return false;
        String b = t.toLowerCase(Locale.ROOT);
        if (ressembleMenu(b)) return false;
        for (String m : PIN_PROMPTS) if (b.contains(m)) return true;
        return false;
    }
    private static boolean ecranTransitoire(String t) { return contient(t, ECRAN_TRANSITOIRE); }
    private static boolean boiteParasite(String t) { return contient(t, BOITE_PARASITE); }
    private static boolean ecranDattente(String t) {
        if (TextUtils.isEmpty(t) || ecranResultat(t) || attenteClics >= ATTENTE_CLICS_MAX) return false;
        return contient(t, ATTENTE_MARKERS);
    }
    private static boolean peutCliquerAttente(String t) {
        long now = System.currentTimeMillis();
        String sig = TextUtils.isEmpty(t) ? "<vide>" : t;
        if (sig.equals(lastAttenteSignature) && now - lastAttenteAt < ATTENTE_REPEAT_MS) return false;
        lastAttenteSignature = sig; lastAttenteAt = now; attenteClics++; lastProgressAt = now;
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        AccessibilityNodeInfo root;
        try { root = racineUssd(); } catch (Exception e) { return; }
        if (root == null) return;
        try {
            if (!estBoiteUssd(root, event)) return;
            String text = collecterTexte(root);

            if (!estArme()) {
                // Boîte orpheline : la fermer si elle n'attend pas de saisie
                if (findEditable(root) == null && ecranDattente(text)) {
                    fermer(400L, true);
                }
                return;
            }

            // ===== MODE RETRAIT =====
            if (modeRetrait) { traiterRetrait(root, text); return; }

            // Menu parasite (offre / forfait) : ANNULER, lecture sans solde
            if (!lectureFaite && boiteParasite(text)) {
                texteLu = ""; lectureFaite = true; lastActionAt = System.currentTimeMillis();
                Log.d(TAG, "menu parasite -> ANNULER");
                fermer(250L, true);
                return;
            }

            // Séquence multi-étape à taper avant lecture
            if (!menuReply.isEmpty()) {
                String[] rep = menuReply.split("\\|");
                if (menuReplyIndex < rep.length) {
                    long now = System.currentTimeMillis();
                    if (now - lastActionAt < MIN_ACTION_INTERVAL_MS) return;
                    AccessibilityNodeInfo edit = findEditable(root);
                    if (edit == null) {
                        if (ecranDattente(text) && peutCliquerAttente(text)) { lastActionAt = now; fermer(250L, false); }
                        return;
                    }
                    String sig = TextUtils.isEmpty(text) ? "<vide>" : text;
                    if (sig.equals(lastHandledSignature)) return;
                    lastActionAt = now;
                    final String v = rep[menuReplyIndex], s2 = sig;
                    ecrireEtValider(v, () -> { lastProgressAt = System.currentTimeMillis(); menuReplyIndex++; lastHandledSignature = s2; });
                    return;
                }
            }

            // Lecture du solde
            if (!TextUtils.isEmpty(text) && !lectureFaite) {
                if (ecranTransitoire(text)) return;
                if (ecranDattente(text)) {
                    if (peutCliquerAttente(text)) { lastActionAt = System.currentTimeMillis(); fermer(250L, false); }
                    return;
                }
                texteLu = text; lectureFaite = true;
                Log.d(TAG, "solde lu");
                fermer(300L, true);
            }
        } catch (Exception e) { Log.e(TAG, "event: " + e.getMessage()); }
    }

    private void fermer(long delai, boolean annulerDabord) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                AccessibilityNodeInfo r = racineUssd();
                if (r == null) return;
                if (annulerDabord) { if (!clic(r, CANCEL_LABELS, "android:id/button2") && !clic(r, SEND_LABELS, "android:id/button1")) { } }
                else clic(r, SEND_LABELS, "android:id/button1");
            } catch (Exception ignore) { }
        }, delai);
    }

    // ---- utilitaires ----
    private AccessibilityNodeInfo racineUssd() {
        try { AccessibilityNodeInfo a = getRootInActiveWindow(); if (a != null && estBoiteUssd(a, null)) return a; } catch (Exception ignore) { }
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            if (ws != null) for (AccessibilityWindowInfo w : ws) {
                if (w == null) continue;
                AccessibilityNodeInfo r = null;
                try { r = w.getRoot(); } catch (Exception ignore) { }
                if (r != null && estBoiteUssd(r, null)) return r;
            }
        } catch (Exception ignore) { }
        try { return getRootInActiveWindow(); } catch (Exception e) { return null; }
    }

    private boolean estBoiteUssd(AccessibilityNodeInfo root, AccessibilityEvent event) {
        CharSequence pkgCs = root.getPackageName() != null ? root.getPackageName() : (event != null ? event.getPackageName() : null);
        String pkg = pkgCs == null ? "" : pkgCs.toString().toLowerCase(Locale.ROOT);
        if (pkg.startsWith("mg.kajypay")) return false;
        if (pkg.contains("dialer") || pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("phone")) return true;
        CharSequence cls = event != null ? event.getClassName() : null;
        if (cls != null && cls.toString().toLowerCase(Locale.ROOT).contains("alertdialog")) return findEditable(root) != null;
        return false;
    }

    private String collecterTexte(AccessibilityNodeInfo node) { StringBuilder sb = new StringBuilder(); collecter(node, sb, 0); return sb.toString().trim(); }
    private void collecter(AccessibilityNodeInfo n, StringBuilder sb, int d) {
        if (n == null || d > 25) return;
        try {
            CharSequence t = n.getText();
            if (!estEditable(n) && t != null && t.length() > 0) {
                String s = t.toString().trim();
                if (!s.isEmpty() && sb.indexOf(s) < 0) { if (sb.length() > 0) sb.append(" | "); sb.append(s); }
            }
            for (int i = 0; i < n.getChildCount(); i++) collecter(n.getChild(i), sb, d + 1);
        } catch (Exception ignore) { }
    }
    private static boolean estEditable(AccessibilityNodeInfo n) {
        if (n == null) return false;
        try {
            if (n.isEditable()) return true;
            CharSequence c = n.getClassName();
            return c != null && c.toString().toLowerCase(Locale.ROOT).contains("edittext");
        } catch (Exception e) { return false; }
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n) { return findEditableRec(n, 0); }
    private AccessibilityNodeInfo findEditableRec(AccessibilityNodeInfo n, int d) {
        if (n == null || d > 25) return null;
        try {
            if (estEditable(n) && n.isVisibleToUser()) return n;
            for (int i = 0; i < n.getChildCount(); i++) { AccessibilityNodeInfo f = findEditableRec(n.getChild(i), d + 1); if (f != null) return f; }
        } catch (Exception ignore) { }
        return null;
    }
    private void ecrireEtValider(String value, Runnable onOk) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                AccessibilityNodeInfo r = racineUssd();
                if (r == null) return;
                AccessibilityNodeInfo champ = findEditable(r);
                if (champ == null) return;
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                champ.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { AccessibilityNodeInfo r2 = racineUssd(); if (r2 != null && clic(r2, SEND_LABELS, "android:id/button1")) onOk.run(); } catch (Exception ignore) { }
                }, 350L);
            } catch (Exception ignore) { }
        }, 350L);
    }
    private boolean clic(AccessibilityNodeInfo root, String[] labels, String viewId) {
        try {
            List<AccessibilityNodeInfo> l = root.findAccessibilityNodeInfosByViewId(viewId);
            if (l != null && !l.isEmpty() && clicNoeud(l.get(0))) return true;
        } catch (Exception ignore) { }
        List<AccessibilityNodeInfo> btns = new ArrayList<>();
        collecterClic(root, btns, 0);
        boolean cancel = labels == CANCEL_LABELS;
        for (AccessibilityNodeInfo b : btns) {
            String lab = labelDe(b);
            if (lab.isEmpty()) continue;
            boolean estCancel = estLabel(lab, CANCEL_LABELS);
            if (cancel ? estCancel : !estCancel) {
                for (String s : labels) if (lab.equals(s) || lab.startsWith(s)) { if (clicNoeud(b)) return true; }
            }
        }
        return false;
    }
    private void collecterClic(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int d) {
        if (n == null || d > 25 || out.size() > 80) return;
        try {
            if (n.isVisibleToUser() && (n.isClickable() || estBouton(n))) out.add(n);
            for (int i = 0; i < n.getChildCount(); i++) collecterClic(n.getChild(i), out, d + 1);
        } catch (Exception ignore) { }
    }
    private static boolean estBouton(AccessibilityNodeInfo n) {
        try { CharSequence c = n.getClassName(); if (c == null) return false; String s = c.toString().toLowerCase(Locale.ROOT); return s.contains("button") || s.contains("textview"); } catch (Exception e) { return false; }
    }
    private static boolean estLabel(String lab, String[] cles) { for (String c : cles) if (lab.equals(c) || lab.startsWith(c)) return true; return false; }
    private static String labelDe(AccessibilityNodeInfo n) {
        try { CharSequence t = n.getText(); if (t == null || t.length() == 0) t = n.getContentDescription(); return t == null ? "" : t.toString().trim().toLowerCase(Locale.ROOT); } catch (Exception e) { return ""; }
    }
    private boolean clicNoeud(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo n = node; int g = 0;
        while (n != null && g++ < 8) {
            try { if (n.isClickable() && n.isEnabled()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK); n = n.getParent(); } catch (Exception e) { return false; }
        }
        return false;
    }
}
