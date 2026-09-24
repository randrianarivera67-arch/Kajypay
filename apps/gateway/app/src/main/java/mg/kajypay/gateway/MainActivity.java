package mg.kajypay.gateway;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int BG = Color.parseColor("#F3F5F4"), CARD = Color.WHITE, LINE = Color.parseColor("#E1E7E3");
    static final int TEXT = Color.parseColor("#0E1A14"), MUTED = Color.parseColor("#56655D"), WARN = Color.parseColor("#9A6700");
    static final int ACCENT = Color.parseColor("#0E6B47"), SOFT = Color.parseColor("#E1F2E9"), DANGER = Color.parseColor("#B42318"), NAV_IDLE = Color.parseColor("#5F6E66");
    static final int ACCUEIL = 0, HISTORIQUE = 1, REGLAGES = 2, JOURNAL = 3;
    private Store store;
    private Journal journal;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int onglet = -1, simChoisie = 1, histoSim = 0, histoPeriode = 0;
    private String histoQ = "";
    private ScrollView scroll;
    private LinearLayout contenu, liste, alerte, batterie, listeHisto;
    private TextView etat, simLabel, montant, nombre, contact, batterieTxt, soldeMontant, soldeMaj, info, resumeHisto, erreurConnexion;
    private Button pauseBtn, okConnexion;
    private final List<Button> navBoutons = new ArrayList<>();
    private final List<LinearLayout> onglets = new ArrayList<>();
    private final List<Integer> slotsOnglets = new ArrayList<>();
    private final List<TextView> badges = new ArrayList<>();
    private final Runnable boucle = new Runnable() {
        @Override
        public void run() {
            if (onglet == ACCUEIL) majAccueil();
            h.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installerRapportCrash();
        store = new Store(this);
        journal = new Journal(this);
        if (store.estAppaire()) demarrerApp(); else afficherConnexion();
        afficherDernierCrash();
    }

    static boolean rapportInstalle = false;

    void installerRapportCrash() {
        if (rapportInstalle) return;
        rapportInstalle = true;
        final SharedPreferences cp = getApplicationContext().getSharedPreferences("crash", MODE_PRIVATE);
        final Thread.UncaughtExceptionHandler avant = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            cp.edit().putString("trace", "KajyPay " + BuildConfig.VERSION_NAME + "\n" + sw).commit();
            if (avant != null) avant.uncaughtException(t, e);
        });
    }

    void afficherDernierCrash() {
        SharedPreferences cp = getSharedPreferences("crash", MODE_PRIVATE);
        String tr = cp.getString("trace", null);
        if (tr == null) return;
        cp.edit().remove("trace").apply();
        final String court = tr.length() > 2500 ? tr.substring(0, 2500) : tr;
        new AlertDialog.Builder(this)
            .setTitle("KajyPay s'est arrêté")
            .setMessage(court)
            .setNegativeButton("Fermer", null)
            .setPositiveButton("Copier", (d, w) -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("crash", court));
                Toast.makeText(this, "Rapport copié", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    void demarrerApp() {
        construireCadre();
        montrer(ACCUEIL);
        demanderPermissions();
        demarrerSiPossible();
        detecterSims();
    }

    @Override
    protected void onResume() { super.onResume(); h.post(boucle); }

    @Override
    protected void onPause() { h.removeCallbacks(boucle); super.onPause(); }

    @Override
    public void onBackPressed() {
        if (onglet == HISTORIQUE || onglet == REGLAGES || onglet == JOURNAL) montrer(ACCUEIL); else super.onBackPressed();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable fond(int couleur, int rayon, int bord, int epaisseur) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(couleur);
        g.setCornerRadius(dp(rayon));
        if (bord != 0) g.setStroke(dp(epaisseur), bord);
        return g;
    }

    TextView texte(String s, int sp, int couleur, boolean gras) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(couleur);
        if (gras) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Button bouton(String s, int fondCouleur, int texteCouleur, int bord, int sp) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextSize(sp);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setTextColor(texteCouleur);
        bt.setBackground(fond(fondCouleur, 16, bord, 1));
        bt.setMinHeight(dp(50));
        bt.setStateListAnimator(null);
        return bt;
    }

    LinearLayout colonne() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams plein(int haut) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(haut);
        return lp;
    }

    LinearLayout.LayoutParams poids(int gauche) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = dp(gauche);
        return lp;
    }

    static String libelle(String op) {
        if ("orange".equals(op)) return "Orange Money";
        if ("mvola".equals(op)) return "MVola";
        if ("airtel".equals(op)) return "Airtel Money";
        return op == null ? "" : op;
    }

    static int couleurOp(String op) {
        if ("orange".equals(op)) return Color.parseColor("#FF7900");
        if ("mvola".equals(op)) return Color.parseColor("#FFC20E");
        if ("airtel".equals(op)) return Color.parseColor("#E4002B");
        return MUTED;
    }

    static int nomRessourceOp(String op) {
        if ("orange".equals(op)) return R.drawable.ic_op_orange;
        if ("mvola".equals(op)) return R.drawable.ic_op_mvola;
        if ("airtel".equals(op)) return R.drawable.ic_op_airtel;
        return 0;
    }

    static String initialeOp(String op) {
        if ("orange".equals(op)) return "O";
        if ("mvola".equals(op)) return "M";
        if ("airtel".equals(op)) return "A";
        return "?";
    }

    boolean logoExiste(int res) {
        try { return res != 0 && getResources().getResourceName(res) != null && getDrawable(res) != null; }
        catch (Exception e) { return false; }
    }

    View pastilleOp(String op, int taille) {
        int res = nomRessourceOp(op);
        if (logoExiste(res)) {
            ImageView v = new ImageView(this);
            v.setImageResource(res);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(taille), dp(taille));
            v.setLayoutParams(lp);
            return v;
        }
        TextView t = texte(initialeOp(op), taille > 30 ? 15 : 12, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        int s = dp(taille);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
        t.setLayoutParams(lp);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(couleurOp(op));
        t.setBackground(g);
        return t;
    }

    String texteBatterie() {
        try {
            BatteryManager bm = getSystemService(BatteryManager.class);
            int n = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = registerReceiver(null, f);
            boolean charge = false;
            if (b != null) {
                int st = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charge = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
            }
            if (n < 0) return "";
            return "Batterie " + n + "%" + (charge ? " (en charge)" : "");
        } catch (Exception e) { return ""; }
    }

    static String ar(long v) { return String.format(Locale.FRANCE, "%,d", v).replace('\u202f', ' ').replace('\u00a0', ' '); }

    static long debutJour() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    boolean smsAutorise() { return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED; }

    String[] permissionsVoulues() {
        return Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE};
    }

    boolean autorisationsOk() {
        for (String p : permissionsVoulues()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    boolean batterieOk() {
        PowerManager pm = getSystemService(PowerManager.class);
        return pm == null || pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    void ouvrirBatterie() {
        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    void ouvrirParametresApp() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
    }

    void demanderPermissions() {
        List<String> manque = new ArrayList<>();
        for (String p : permissionsVoulues()) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) manque.add(p);
        if (!manque.isEmpty()) requestPermissions(manque.toArray(new String[0]), 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        demarrerSiPossible();
        detecterSims();
        if (onglet == ACCUEIL) majAccueil();
    }

    void demarrerSiPossible() {
        if (store.estAppaire() && !store.pause() && smsAutorise() && !KajyService.enMarche()) {
            try { KajyService.demarrer(this); } catch (Exception ignore) { }
        }
    }

    void detecterSims() {
        new Thread(() -> {
            if (Sync.synchroniserSims(this)) runOnUiThread(() -> { if (onglet == ACCUEIL || onglet == REGLAGES) montrer(onglet); });
        }).start();
    }

    JSONArray sims() {
        try { return new JSONArray(store.sims()); } catch (Exception e) { return new JSONArray(); }
    }

    List<JSONObject> simsActives() {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = sims();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("actif", 1) == 1) out.add(o);
        }
        return out;
    }

    String operateurDe(int slot) {
        for (JSONObject o : simsActives()) if (o.optInt("slot") == slot) return o.optString("operateur");
        return "";
    }

    void lancerSolde(int slot, Runnable finUi) {
        JSONArray a = sims();
        String code = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optInt("slot") == slot) code = o.optString("code_ussd_solde", "");
        }
        if (code == null || code.trim().isEmpty()) {
            Toast.makeText(this, "Aucun code USSD configuré pour la SIM " + slot + ". Réglages > Cartes SIM.", Toast.LENGTH_LONG).show();
            if (finUi != null) finUi.run();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, 8);
            Toast.makeText(this, "Autorisez « Appels téléphoniques » pour lire le solde.", Toast.LENGTH_LONG).show();
            if (finUi != null) finUi.run();
            return;
        }
        if (!UssdReader.estVivant(this)) {
            demanderAccessibilite();
            if (finUi != null) finUi.run();
            return;
        }
        final int fslot = slot;
        SoldeUssd.consulter(this, slot, code, (ok, montant, texte) -> {
            if (ok) new Thread(() -> Sync.envoyerSolde(this, fslot, montant, texte)).start();
            runOnUiThread(() -> {
                Toast.makeText(this, ok ? ("Solde SIM " + fslot + " : " + (montant != null ? ar(montant) + " Ar" : "lu")) : ("Lecture impossible : " + texte), Toast.LENGTH_LONG).show();
                if (finUi != null) finUi.run();
            });
        });
    }

    void dialogueCodeUssd(int slot, String actuel) {
        final EditText e = new EditText(this);
        e.setText(actuel);
        e.setHint("#144*5*3#  (séparez par | si plusieurs étapes)");
        e.setTextColor(TEXT);
        e.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this)
            .setTitle("Code USSD solde — SIM " + slot)
            .setMessage("Code de consultation du solde Mobile Money de cette SIM. Pour un menu à plusieurs étapes, séparez les réponses par « | » (ex. *436#|6|2).")
            .setView(e)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Enregistrer", (d, w) -> {
                String code = e.getText().toString().trim();
                new Thread(() -> {
                    boolean ok = false;
                    try {
                        org.json.JSONObject r = new org.json.JSONObject(Api.post(store.api() + "/gateway/mon-ussd", new org.json.JSONObject().put("slot", slot).put("code_ussd_solde", code).toString(), "Appareil " + store.jeton()));
                        ok = r.optBoolean("ok");
                    } catch (Exception ex) { }
                    final boolean f = ok;
                    runOnUiThread(() -> {
                        Toast.makeText(this, f ? "Code enregistré" : "Échec de l'enregistrement", Toast.LENGTH_SHORT).show();
                        if (f) { store.setSimsEnvoyees(""); new Thread(() -> { Sync.synchroniserSims(this); runOnUiThread(() -> montrer(REGLAGES)); }).start(); }
                    });
                }).start();
            })
            .show();
    }


    void demanderAccessibilite() {
        new AlertDialog.Builder(this)
            .setTitle("Activer la lecture du solde")
            .setMessage("Pour lire le solde Mobile Money, activez « KajyPay solde » dans Accessibilité. KajyPay lit alors uniquement l'écran des menus USSD, jamais vos autres applications.")
            .setNegativeButton("Plus tard", null)
            .setPositiveButton("Ouvrir les réglages", (d, w) -> {
                try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                catch (Exception e) { Toast.makeText(this, "Ouvrez Réglages > Accessibilité", Toast.LENGTH_LONG).show(); }
            })
            .show();
    }

    String texteContact() {
        long dc = store.dernierContact();
        long min = dc == 0 ? -1 : (System.currentTimeMillis() - dc) / 60000;
        return min < 0 ? "Pas encore de contact avec le serveur" : min == 0 ? "Dernier contact : à l'instant" : "Dernier contact : il y a " + min + " min";
    }

    void afficherConnexion() {
        onglet = -1;
        LinearLayout c = colonne();
        c.setPadding(dp(20), dp(28), dp(20), dp(24));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        c.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
        c.addView(texte("Connecter ce téléphone", 24, TEXT, true), plein(20));
        TextView aide = texte("Dans votre espace KajyPay, ouvrez Appareils, puis scannez le QR code affiché.", 15, MUTED, false);
        aide.setLineSpacing(0, 1.3f);
        c.addView(aide, plein(8));
        Button qr = bouton("Scanner le QR code", ACCENT, Color.WHITE, 0, 16);
        c.addView(qr, plein(24));
        TextView ou = texte("ou saisissez le code", 13, MUTED, false);
        ou.setGravity(Gravity.CENTER);
        c.addView(ou, plein(20));
        c.addView(texte("Code d'appairage", 14, TEXT, true), plein(12));
        EditText code = new EditText(this);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8), new InputFilter.AllCaps()});
        code.setTextSize(24);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setLetterSpacing(0.3f);
        code.setGravity(Gravity.CENTER);
        code.setTextColor(TEXT);
        code.setHint("XXXXXXXX");
        code.setBackground(fond(CARD, 14, Color.parseColor("#CBD6D0"), 1));
        code.setMinHeight(dp(56));
        c.addView(code, plein(8));
        erreurConnexion = texte("", 14, DANGER, false);
        c.addView(erreurConnexion, plein(8));
        okConnexion = bouton("Connecter", CARD, ACCENT, ACCENT, 16);
        c.addView(okConnexion, plein(8));
        LinearLayout note = colonne();
        note.setBackground(fond(SOFT, 14, 0, 0));
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView nt = texte("KajyPay ne lit que les SMS de réception d'argent de votre opérateur. Vos messages personnels restent sur ce téléphone.", 13, Color.parseColor("#2F4A3D"), false);
        nt.setLineSpacing(0, 1.3f);
        note.addView(nt);
        c.addView(note, plein(28));
        qr.setOnClickListener(v -> {
            try {
                GmsBarcodeScannerOptions o = new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
                GmsBarcodeScanning.getClient(this, o).startScan()
                    .addOnSuccessListener(bc -> {
                        String brut = bc.getRawValue();
                        try { brut = new JSONObject(brut).optString("code", brut); } catch (Exception ignore) { }
                        appairer(brut);
                    })
                    .addOnFailureListener(e -> erreurConnexion.setText("Scanner indisponible sur ce téléphone. Saisissez le code à 8 caractères."));
            } catch (Exception e) {
                erreurConnexion.setText("Scanner indisponible sur ce téléphone. Saisissez le code à 8 caractères.");
            }
        });
        okConnexion.setOnClickListener(v -> appairer(code.getText().toString()));
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(c);
        setContentView(sv);
    }

    void appairer(String brut) {
        String k = brut == null ? "" : brut.trim().toUpperCase(Locale.ROOT);
        if (!k.matches("[A-Z0-9]{8}")) { if (erreurConnexion != null) erreurConnexion.setText("Le code contient 8 lettres ou chiffres."); return; }
        if (erreurConnexion != null) erreurConnexion.setText("");
        if (okConnexion != null) { okConnexion.setEnabled(false); okConnexion.setText("Connexion…"); }
        new Thread(() -> {
            String msg;
            try {
                String api = Api.DEFAULT_API;
                JSONObject r = new JSONObject(Api.post(api + "/gateway/appairer", new JSONObject().put("code", k).toString(), null));
                if (r.optBoolean("ok")) {
                    store.enregistrer(api, r.getString("jeton_appareil"), r.optString("nom", "Téléphone"), r.optJSONArray("sims") == null ? "[]" : r.getJSONArray("sims").toString());
                    store.setPause(false);
                    store.setSimsEnvoyees("");
                    runOnUiThread(this::demarrerApp);
                    return;
                }
                msg = "Code refusé : " + r.optString("erreur", "erreur inconnue");
            } catch (Exception e) {
                msg = "Pas de connexion au serveur. Vérifiez internet.";
            }
            final String m = msg;
            runOnUiThread(() -> {
                if (erreurConnexion != null) erreurConnexion.setText(m);
                if (okConnexion != null) { okConnexion.setEnabled(true); okConnexion.setText("Connecter"); }
            });
        }).start();
    }

    void construireCadre() {
        LinearLayout racine = colonne();
        racine.setBackgroundColor(BG);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        contenu = colonne();
        contenu.setPadding(dp(20), dp(24), dp(20), dp(24));
        scroll.addView(contenu);
        racine.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        View sep = new View(this);
        sep.setBackgroundColor(LINE);
        racine.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(BG);
        nav.setPadding(dp(8), dp(4), dp(8), dp(8));
        navBoutons.clear();
        nav.addView(navBouton("Accueil", R.drawable.ic_home, ACCUEIL), poids(0));
        nav.addView(navBouton("Historique", R.drawable.ic_list, HISTORIQUE), poids(0));
        nav.addView(navBouton("Réglages", R.drawable.ic_settings, REGLAGES), poids(0));
        racine.addView(nav, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(racine);
    }

    Button navBouton(String s, int icone, int cible) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setStateListAnimator(null);
        b.setCompoundDrawablesWithIntrinsicBounds(null, getDrawable(icone), null, null);
        b.setCompoundDrawablePadding(dp(2));
        b.setMinHeight(dp(56));
        b.setPadding(0, dp(6), 0, dp(4));
        b.setOnClickListener(v -> montrer(cible));
        navBoutons.add(b);
        return b;
    }

    void majNav() {
        int actif = onglet == JOURNAL ? ACCUEIL : onglet;
        for (int i = 0; i < navBoutons.size(); i++) {
            int col = i == actif ? ACCENT : NAV_IDLE;
            navBoutons.get(i).setTextColor(col);
            navBoutons.get(i).setCompoundDrawableTintList(ColorStateList.valueOf(col));
        }
    }

    void montrer(int t) {
        if (contenu == null) construireCadre();
        onglet = t;
        etat = null;
        contenu.removeAllViews();
        if (t == ACCUEIL) construireAccueil();
        else if (t == HISTORIQUE) construireHistorique();
        else if (t == REGLAGES) construireReglages();
        else construireJournal();
        majNav();
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    void construireAccueil() {
        LinearLayout tete = new LinearLayout(this);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        tete.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView titre = texte("KajyPay", 20, TEXT, true);
        titre.setPadding(dp(10), 0, 0, 0);
        tete.addView(titre, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tete.addView(texte(store.nom(), 13, MUTED, false));
        contenu.addView(tete);

        alerte = colonne();
        alerte.setBackground(fond(Color.parseColor("#FDECEA"), 16, 0, 0));
        alerte.setPadding(dp(14), dp(12), dp(14), dp(12));
        alerte.addView(texte("Autorisation SMS requise pour recevoir les paiements.", 14, DANGER, true));
        LinearLayout ab = new LinearLayout(this);
        Button autoriser = bouton("Autoriser", DANGER, Color.WHITE, 0, 14);
        Button params = bouton("Paramètres", CARD, DANGER, DANGER, 14);
        ab.addView(autoriser, poids(0));
        ab.addView(params, poids(8));
        alerte.addView(ab, plein(10));
        contenu.addView(alerte, plein(16));
        autoriser.setOnClickListener(v -> demanderPermissions());
        params.setOnClickListener(v -> ouvrirParametresApp());

        batterie = colonne();
        batterie.setBackground(fond(Color.parseColor("#FFF4D6"), 16, 0, 0));
        batterie.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView bt = texte("L'économie de batterie peut couper KajyPay et faire manquer des paiements.", 14, WARN, true);
        bt.setLineSpacing(0, 1.2f);
        batterie.addView(bt);
        Button bb = bouton("Désactiver l'économie de batterie", WARN, Color.WHITE, 0, 14);
        batterie.addView(bb, plein(10));
        contenu.addView(batterie, plein(12));
        bb.setOnClickListener(v -> ouvrirBatterie());

        LinearLayout rangee = new LinearLayout(this);
        onglets.clear();
        slotsOnglets.clear();
        badges.clear();
        for (JSONObject s : simsActives()) {
            final int slot = s.optInt("slot", 1);
            String op = s.optString("operateur");
            LinearLayout o = new LinearLayout(this);
            o.setGravity(Gravity.CENTER_VERTICAL);
            o.setPadding(dp(10), dp(10), dp(10), dp(10));
            o.setMinimumHeight(dp(56));
            o.setClickable(true);
            o.addView(pastilleOp(op, 28));
            LinearLayout col = colonne();
            col.setPadding(dp(10), 0, dp(6), 0);
            col.addView(texte("SIM " + slot, 14, TEXT, true));
            col.addView(texte(libelle(op), 12, MUTED, false));
            o.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            TextView badge = texte("0", 12, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setMinWidth(dp(26));
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            o.addView(badge);
            o.setOnClickListener(v -> { simChoisie = slot; majAccueil(); });
            rangee.addView(o, poids(onglets.isEmpty() ? 0 : 8));
            onglets.add(o);
            slotsOnglets.add(slot);
            badges.add(badge);
        }
        if (onglets.isEmpty()) rangee.addView(texte("Aucune SIM Mobile Money détectée. Vérifiez les cartes SIM et l'autorisation Téléphone.", 14, MUTED, false));
        if (!slotsOnglets.isEmpty() && !slotsOnglets.contains(simChoisie)) simChoisie = slotsOnglets.get(0);
        contenu.addView(rangee, plein(16));

        LinearLayout hero = colonne();
        GradientDrawable heroFond = new GradientDrawable();
        heroFond.setColor(ACCENT);
        heroFond.setCornerRadius(dp(22));
        heroFond.setStroke(dp(3), Color.parseColor("#F2C14E"));
        hero.setBackground(heroFond);
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        etat = texte("", 14, Color.WHITE, true);
        hero.addView(etat);
        simLabel = texte("", 12, Color.parseColor("#CFE9DC"), true);
        hero.addView(simLabel, plein(4));
        hero.addView(texte("Reçu aujourd'hui", 13, Color.parseColor("#CFE9DC"), false), plein(14));
        montant = texte("", 36, Color.WHITE, true);
        hero.addView(montant);
        nombre = texte("", 13, Color.parseColor("#CFE9DC"), false);
        hero.addView(nombre);
        contact = texte("", 12, Color.parseColor("#CFE9DC"), false);
        hero.addView(contact, plein(8));
        batterieTxt = texte("", 12, Color.parseColor("#CFE9DC"), false);
        hero.addView(batterieTxt, plein(2));
        contenu.addView(hero, plein(14));

        LinearLayout soldeCarte = colonne();
        soldeCarte.setBackground(fond(CARD, 18, LINE, 1));
        soldeCarte.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout soldeHaut = new LinearLayout(this);
        soldeHaut.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout soldeG = colonne();
        soldeG.addView(texte("Solde Mobile Money", 13, MUTED, false));
        soldeMontant = texte("—", 24, TEXT, true);
        soldeG.addView(soldeMontant);
        soldeMaj = texte("", 12, MUTED, false);
        soldeG.addView(soldeMaj);
        soldeHaut.addView(soldeG, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button verifierSolde = bouton("Vérifier", ACCENT, Color.WHITE, 0, 14);
        verifierSolde.setPadding(dp(18), 0, dp(18), 0);
        soldeHaut.addView(verifierSolde);
        soldeCarte.addView(soldeHaut);
        contenu.addView(soldeCarte, plein(12));
        verifierSolde.setOnClickListener(v -> {
            verifierSolde.setEnabled(false);
            verifierSolde.setText("…");
            lancerSolde(simChoisie, () -> { verifierSolde.setEnabled(true); verifierSolde.setText("Vérifier"); majAccueil(); });
        });

        LinearLayout actions = new LinearLayout(this);
        pauseBtn = bouton("Pause", CARD, ACCENT, LINE, 13);
        Button synchro = bouton("Synchro", CARD, ACCENT, LINE, 13);
        Button tester = bouton("Tester", CARD, ACCENT, LINE, 13);
        Button jrn = bouton("Journal", CARD, ACCENT, LINE, 13);
        actions.addView(pauseBtn, poids(0));
        actions.addView(synchro, poids(6));
        actions.addView(tester, poids(6));
        actions.addView(jrn, poids(6));
        contenu.addView(actions, plein(14));
        info = texte("", 13, MUTED, false);
        contenu.addView(info, plein(8));

        LinearLayout titreListe = new LinearLayout(this);
        titreListe.setGravity(Gravity.CENTER_VERTICAL);
        titreListe.addView(texte("Derniers paiements", 16, TEXT, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button tout = bouton("Tout voir", BG, ACCENT, 0, 13);
        titreListe.addView(tout);
        contenu.addView(titreListe, plein(10));
        liste = colonne();
        contenu.addView(liste, plein(4));

        pauseBtn.setOnClickListener(v -> {
            boolean p = !store.pause();
            store.setPause(p);
            if (p) KajyService.arreter(this); else demarrerSiPossible();
            info.setText(p ? "Réception en pause. Les SMS reçus pendant la pause ne seront pas transmis." : "Réception reprise.");
            h.postDelayed(this::majAccueil, 400);
        });
        synchro.setOnClickListener(v -> {
            info.setText("Envoi en cours…");
            new Thread(() -> { String m = Sync.envoyer(this); runOnUiThread(() -> { if (info != null) info.setText(m); majAccueil(); }); }).start();
        });
        tester.setOnClickListener(v -> {
            info.setText("Test en cours…");
            new Thread(() -> { String m = Sync.ping(this); runOnUiThread(() -> { if (info != null) info.setText(m); majAccueil(); }); }).start();
        });
        jrn.setOnClickListener(v -> montrer(JOURNAL));
        tout.setOnClickListener(v -> { histoSim = simChoisie; montrer(HISTORIQUE); });
        majAccueil();
    }

    void majAccueil() {
        if (onglet != ACCUEIL || etat == null) return;
        boolean autorise = smsAutorise();
        alerte.setVisibility(autorise ? View.GONE : View.VISIBLE);
        batterie.setVisibility(batterieOk() ? View.GONE : View.VISIBLE);
        boolean srv = store.dernierContact() > 0 && System.currentTimeMillis() - store.dernierContact() < 180000;
        etat.setText(!autorise ? "●  Autorisation SMS manquante" : store.pause() ? "●  En pause" : !srv ? "●  Serveur non joignable" : KajyService.enMarche() ? "●  Serveur connecté" : "●  Service arrêté");
        pauseBtn.setText(store.pause() ? "Reprendre" : "Pause");
        String op = operateurDe(simChoisie);
        simLabel.setText("SIM " + simChoisie + (op.isEmpty() ? "" : ", " + libelle(op)));
        long debut = debutJour();
        for (int i = 0; i < onglets.size(); i++) {
            boolean choisi = slotsOnglets.get(i) == simChoisie;
            onglets.get(i).setBackground(fond(CARD, 14, choisi ? ACCENT : LINE, choisi ? 2 : 1));
            TextView bd = badges.get(i);
            bd.setText(String.valueOf(journal.totaux(slotsOnglets.get(i), debut)[0]));
            bd.setTextColor(choisi ? Color.WHITE : ACCENT);
            bd.setBackground(fond(choisi ? ACCENT : SOFT, 12, 0, 0));
        }
        long[] j = journal.totaux(simChoisie, debut);
        long att = journal.enAttenteTotal();
        montant.setText(ar(j[1]) + " Ar");
        nombre.setText(j[0] + (j[0] > 1 ? " paiements confirmés" : " paiement confirmé") + (att > 0 ? ", " + att + " en attente d'envoi" : ""));
        contact.setText(texteContact());
        if (batterieTxt != null) batterieTxt.setText(texteBatterie());
        if (soldeMontant != null) {
            JSONArray sa = sims();
            Long m = null; long maj = 0; String code = "";
            for (int i = 0; i < sa.length(); i++) {
                JSONObject o = sa.optJSONObject(i);
                if (o != null && o.optInt("slot") == simChoisie) {
                    code = o.optString("code_ussd_solde", "");
                    if (o.has("solde_operateur_ar") && !o.isNull("solde_operateur_ar")) m = o.optLong("solde_operateur_ar");
                    maj = o.optLong("solde_maj", 0);
                }
            }
            soldeMontant.setText(m != null ? ar(m) + " Ar" : (code.isEmpty() ? "Non configuré" : "—"));
            if (maj > 0) { long min = (System.currentTimeMillis() - maj) / 60000; soldeMaj.setText(min <= 0 ? "à l'instant" : "il y a " + min + " min"); }
            else soldeMaj.setText(code.isEmpty() ? "Ajoutez le code USSD dans Réglages" : "Jamais vérifié");
        }
        liste.removeAllViews();
        List<Journal.Ligne> ls = journal.rechercher(simChoisie, 0, null, true, 5);
        if (ls.isEmpty()) { liste.addView(texte("Aucun paiement reçu sur la SIM " + simChoisie + " pour le moment.", 14, MUTED, false), plein(6)); return; }
        for (Journal.Ligne l : ls) liste.addView(carteSms(l, false), plein(8));
    }

    String statutLibelle(Journal.Ligne l) {
        if ("reconnu".equals(l.statut)) return l.facture ? "Confirmé" : "Crédit épuisé";
        if ("attente".equals(l.statut)) return "En attente";
        if ("doublon".equals(l.statut)) return "Déjà reçu";
        if ("ignore".equals(l.statut)) {
            if ("sim_incoherente".equals(l.raison)) return "SIM différente";
            if ("sim_inactive".equals(l.raison)) return "SIM arrêtée";
            if ("operateur_absent".equals(l.raison)) return "Opérateur non configuré";
            if ("sim_ambigue".equals(l.raison)) return "SIM ambiguë";
            return "Ignoré";
        }
        return "Erreur";
    }

    int statutCouleur(Journal.Ligne l) {
        if ("reconnu".equals(l.statut)) return l.facture ? ACCENT : DANGER;
        if ("attente".equals(l.statut)) return WARN;
        if ("ignore".equals(l.statut)) return DANGER;
        return MUTED;
    }

    LinearLayout carteSms(Journal.Ligne l, boolean texteComplet) {
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        LinearLayout r = colonne();
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(fond(CARD, 16, LINE, 1));
        LinearLayout haut = new LinearLayout(this);
        haut.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout g = colonne();
        g.addView(texte("+" + ar(l.montant) + " Ar", 16, "reconnu".equals(l.statut) && l.facture ? ACCENT : TEXT, true));
        g.addView(texte(libelle(l.operateur) + (l.slot > 0 ? ", SIM " + l.slot : "") + ", " + f.format(new Date(l.recuLe)), 12, MUTED, false));
        haut.addView(g, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        haut.addView(texte(statutLibelle(l), 12, statutCouleur(l), true));
        r.addView(haut);
        if (texteComplet && l.texte != null) {
            TextView t = texte(l.texte, 12, MUTED, false);
            t.setLineSpacing(0, 1.2f);
            r.addView(t, plein(8));
        }
        return r;
    }

    Button puce(String s, boolean actif, View.OnClickListener cl) {
        Button b = bouton(s, actif ? ACCENT : CARD, actif ? Color.WHITE : TEXT, actif ? 0 : LINE, 13);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setOnClickListener(cl);
        return b;
    }

    LinearLayout.LayoutParams libre() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    void construireHistorique() {
        contenu.addView(texte("Historique", 24, TEXT, true));
        LinearLayout sims = new LinearLayout(this);
        sims.addView(puce("Toutes", histoSim == 0, v -> { histoSim = 0; montrer(HISTORIQUE); }), libre());
        for (JSONObject s : simsActives()) {
            final int slot = s.optInt("slot");
            sims.addView(puce("SIM " + slot, histoSim == slot, v -> { histoSim = slot; montrer(HISTORIQUE); }), libre());
        }
        contenu.addView(sims, plein(16));
        LinearLayout per = new LinearLayout(this);
        String[] noms = {"Aujourd'hui", "7 jours", "Tout"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            per.addView(puce(noms[i], histoPeriode == i, v -> { histoPeriode = k; montrer(HISTORIQUE); }), libre());
        }
        contenu.addView(per, plein(8));
        EditText rech = new EditText(this);
        rech.setHint("Rechercher un montant ou un numéro");
        rech.setSingleLine(true);
        rech.setTextSize(15);
        rech.setTextColor(TEXT);
        rech.setPadding(dp(14), 0, dp(14), 0);
        rech.setMinHeight(dp(50));
        rech.setBackground(fond(CARD, 14, LINE, 1));
        rech.setText(histoQ);
        contenu.addView(rech, plein(12));
        rech.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { histoQ = e.toString(); majHistorique(); }
        });
        resumeHisto = texte("", 14, TEXT, true);
        contenu.addView(resumeHisto, plein(14));
        listeHisto = colonne();
        contenu.addView(listeHisto, plein(4));
        majHistorique();
    }

    void majHistorique() {
        if (listeHisto == null) return;
        long depuis = histoPeriode == 0 ? debutJour() : histoPeriode == 1 ? debutJour() - 6L * 86400000L : 0;
        List<Journal.Ligne> ls = journal.rechercher(histoSim, depuis, histoQ, true, 200);
        long total = 0, n = 0;
        for (Journal.Ligne l : ls) if ("reconnu".equals(l.statut) && l.facture) { total += l.montant; n++; }
        resumeHisto.setText(n + (n > 1 ? " paiements confirmés, " : " paiement confirmé, ") + ar(total) + " Ar");
        listeHisto.removeAllViews();
        if (ls.isEmpty()) { listeHisto.addView(texte("Aucun paiement pour ce filtre.", 14, MUTED, false), plein(6)); return; }
        for (Journal.Ligne l : ls) listeHisto.addView(carteSms(l, false), plein(8));
    }

    void construireJournal() {
        Button retour = bouton("‹  Retour", BG, ACCENT, 0, 14);
        retour.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        retour.setPadding(0, 0, 0, 0);
        retour.setOnClickListener(v -> montrer(ACCUEIL));
        contenu.addView(retour);
        contenu.addView(texte("Journal", 24, TEXT, true), plein(4));
        TextView s = texte("Tous les SMS Mobile Money reçus par ce téléphone, y compris ceux qui ont été ignorés.", 14, MUTED, false);
        s.setLineSpacing(0, 1.3f);
        contenu.addView(s, plein(6));
        long att = journal.enAttenteTotal();
        if (att > 0) {
            Button env = bouton("Envoyer les " + att + " SMS en attente", ACCENT, Color.WHITE, 0, 14);
            env.setOnClickListener(v -> new Thread(() -> { String m = Sync.envoyer(this); runOnUiThread(() -> { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); montrer(JOURNAL); }); }).start());
            contenu.addView(env, plein(12));
        }
        List<Journal.Ligne> ls = journal.rechercher(0, 0, null, false, 200);
        if (ls.isEmpty()) { contenu.addView(texte("Aucun SMS Mobile Money reçu pour le moment.", 14, MUTED, false), plein(16)); return; }
        for (Journal.Ligne l : ls) contenu.addView(carteSms(l, true), plein(8));
    }

    View ligne(String titre, String sous, String valeur, View.OnClickListener cl) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setMinimumHeight(dp(56));
        LinearLayout g = colonne();
        g.addView(texte(titre, 15, TEXT, true));
        if (sous != null) g.addView(texte(sous, 12, MUTED, false));
        r.addView(g, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        String v = (valeur == null ? "" : valeur) + (cl != null ? "  ›" : "");
        if (!v.isEmpty()) r.addView(texte(v, 13, cl != null ? ACCENT : MUTED, true));
        if (cl != null) { r.setClickable(true); r.setOnClickListener(cl); }
        return r;
    }

    View ligneSwitch(String titre, String sous, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(10), dp(10), dp(10));
        r.setMinimumHeight(dp(56));
        LinearLayout g = colonne();
        g.addView(texte(titre, 15, TEXT, true));
        if (sous != null) g.addView(texte(sous, 12, MUTED, false));
        r.addView(g, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        return r;
    }

    void groupe(String titre, List<View> lignes) {
        contenu.addView(texte(titre, 12, MUTED, true), plein(20));
        LinearLayout carte = colonne();
        carte.setBackground(fond(CARD, 18, LINE, 1));
        for (int i = 0; i < lignes.size(); i++) {
            if (i > 0) {
                View sep = new View(this);
                sep.setBackgroundColor(LINE);
                carte.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
            carte.addView(lignes.get(i));
        }
        contenu.addView(carte, plein(8));
    }

    void construireReglages() {
        contenu.addView(texte("Réglages", 24, TEXT, true));

        List<View> service = new ArrayList<>();
        service.add(ligneSwitch("Réception des paiements", "Transmet les SMS de paiement", !store.pause(), (b, on) -> {
            store.setPause(!on);
            if (on) demarrerSiPossible(); else KajyService.arreter(this);
        }));
        service.add(ligneSwitch("Démarrage automatique", "Relance après redémarrage du téléphone", store.autoDemarrage(), (b, on) -> store.setAuto(on)));
        groupe("SERVICE", service);

        List<View> cartes = new ArrayList<>();
        JSONArray a = sims();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            final int slot = o.optInt("slot");
            final String code = o.optString("code_ussd_solde", "");
            cartes.add(ligne("SIM " + slot, libelle(o.optString("operateur")), o.optInt("actif", 1) == 1 ? "Détectée" : "Absente", null));
            cartes.add(ligne("Code USSD solde SIM " + slot, code.isEmpty() ? "Non configuré (ex. #144*5*3#)" : code, "Modifier", v -> dialogueCodeUssd(slot, code)));
        }
        cartes.add(ligne("Lecture du solde", UssdReader.estVivant(this) ? "Accessibilité activée" : "Accessibilité désactivée", UssdReader.estVivant(this) ? "OK" : "Activer", v -> demanderAccessibilite()));
        cartes.add(ligneSwitch("Vérifier le solde après chaque paiement", "Lit le solde peu après un paiement reçu", store.soldeAuto(), (b, on) -> store.setSoldeAuto(on)));
        cartes.add(ligne("Actualiser les SIM", "Relire les cartes SIM du téléphone", "", v -> {
            store.setSimsEnvoyees("");
            Toast.makeText(this, "Lecture des cartes SIM…", Toast.LENGTH_SHORT).show();
            new Thread(() -> { Sync.synchroniserSims(this); runOnUiThread(() -> montrer(REGLAGES)); }).start();
        }));
        groupe("CARTES SIM", cartes);

        List<View> tel = new ArrayList<>();
        tel.add(ligne("Économie de batterie", "Doit être désactivée pour ne rien manquer", batterieOk() ? "Désactivée" : "Active", v -> ouvrirBatterie()));
        tel.add(ligne("Autorisations", "SMS, Téléphone, Notifications", autorisationsOk() ? "Accordées" : "Incomplètes", v -> ouvrirParametresApp()));
        groupe("TÉLÉPHONE", tel);

        List<View> maint = new ArrayList<>();
        maint.add(ligne("SMS en attente d'envoi", "Appuyez pour envoyer maintenant", String.valueOf(journal.enAttenteTotal()), v ->
            new Thread(() -> { String m = Sync.envoyer(this); runOnUiThread(() -> { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); montrer(REGLAGES); }); }).start()));
        maint.add(ligne("Supprimer le journal local", "Efface l'historique sur ce téléphone", "", v -> new AlertDialog.Builder(this)
            .setTitle("Supprimer le journal local ?")
            .setMessage("L'historique de ce téléphone sera effacé. Les paiements restent enregistrés sur le serveur KajyPay. Les SMS en attente d'envoi sont conservés.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer", (d, w) -> { int n = journal.viderTraites(); Toast.makeText(this, n + " éléments supprimés", Toast.LENGTH_SHORT).show(); montrer(REGLAGES); })
            .show()));
        groupe("MAINTENANCE", maint);

        List<View> app = new ArrayList<>();
        app.add(ligne("Nom de l'appareil", null, store.nom(), null));
        app.add(ligne("Serveur", texteContact(), "", v -> new Thread(() -> { String m = Sync.ping(this); runOnUiThread(() -> { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); montrer(REGLAGES); }); }).start()));
        groupe("APPAREIL", app);

        Button deco = bouton("Déconnecter cet appareil", CARD, DANGER, DANGER, 15);
        contenu.addView(deco, plein(24));
        deco.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Déconnecter cet appareil ?")
            .setMessage("Ce téléphone ne transmettra plus les paiements et son journal local sera effacé. Il faudra un nouveau code pour le reconnecter.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Déconnecter", (d, w) -> { KajyService.arreter(this); journal.vider(); store.effacer(); contenu = null; afficherConnexion(); })
            .show());
        TextView ver = texte("KajyPay version " + BuildConfig.VERSION_NAME, 12, MUTED, false);
        ver.setGravity(Gravity.CENTER);
        contenu.addView(ver, plein(16));
    }
}
