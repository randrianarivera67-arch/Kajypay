package mg.kajypay.gateway;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
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
    static final int ACCENT = Color.parseColor("#0E6B47"), SOFT = Color.parseColor("#E1F2E9"), DANGER = Color.parseColor("#B42318");
    private Store store;
    private Journal journal;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean accueilVisible = false;
    private int simChoisie = 1;
    private TextView etat, simLabel, montant, nombre, contact, info;
    private Button pauseBtn;
    private LinearLayout liste, alerte;
    private final List<LinearLayout> onglets = new ArrayList<>();
    private final List<Integer> slotsOnglets = new ArrayList<>();
    private final Runnable boucle = new Runnable() {
        @Override
        public void run() {
            if (accueilVisible) majAccueil();
            h.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        journal = new Journal(this);
        if (store.estAppaire()) afficherAccueil(); else afficherConnexion();
    }

    @Override
    protected void onResume() { super.onResume(); h.post(boucle); }

    @Override
    protected void onPause() { h.removeCallbacks(boucle); super.onPause(); }

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

    void page(View contenu) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(contenu);
        setContentView(sv);
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

    static String ar(long v) { return String.format(Locale.FRANCE, "%,d", v).replace('\u202f', ' ').replace('\u00a0', ' '); }

    static long debutJour() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    boolean smsAutorise() { return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED; }

    void demanderPermissions() {
        List<String> manque = new ArrayList<>();
        String[] base = Build.VERSION.SDK_INT >= 33
            ? new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS}
            : new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_PHONE_STATE};
        for (String p : base) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) manque.add(p);
        if (!manque.isEmpty()) requestPermissions(manque.toArray(new String[0]), 7);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        demarrerSiPossible();
        if (accueilVisible) majAccueil();
    }

    void demarrerSiPossible() {
        if (store.estAppaire() && !store.pause() && smsAutorise() && !KajyService.enMarche()) {
            try { KajyService.demarrer(this); } catch (Exception ignore) { }
        }
    }

    void afficherConnexion() {
        accueilVisible = false;
        LinearLayout c = colonne();
        c.setPadding(dp(20), dp(28), dp(20), dp(24));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        c.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
        c.addView(texte("Connecter ce téléphone", 24, TEXT, true), plein(20));
        TextView aide = texte("Dans votre espace KajyPay, ouvrez Appareils, créez un appareil puis saisissez le code affiché.", 15, MUTED, false);
        aide.setLineSpacing(0, 1.3f);
        c.addView(aide, plein(8));
        c.addView(texte("Code d'appairage", 14, TEXT, true), plein(28));
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
        TextView erreur = texte("", 14, DANGER, false);
        c.addView(erreur, plein(8));
        Button ok = bouton("Connecter", ACCENT, Color.WHITE, 0, 16);
        c.addView(ok, plein(12));
        LinearLayout note = colonne();
        note.setBackground(fond(SOFT, 14, 0, 0));
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView nt = texte("KajyPay ne lit que les SMS de réception d'argent de votre opérateur. Vos messages personnels restent sur ce téléphone.", 13, Color.parseColor("#2F4A3D"), false);
        nt.setLineSpacing(0, 1.3f);
        note.addView(nt);
        c.addView(note, plein(28));
        ok.setOnClickListener(v -> {
            String k = code.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (k.length() != 8) { erreur.setText("Le code contient 8 caractères."); return; }
            erreur.setText("");
            ok.setEnabled(false);
            ok.setText("Connexion…");
            new Thread(() -> {
                String msg;
                try {
                    String api = Api.DEFAULT_API;
                    JSONObject r = new JSONObject(Api.post(api + "/gateway/appairer", new JSONObject().put("code", k).toString(), null));
                    if (r.optBoolean("ok")) {
                        store.enregistrer(api, r.getString("jeton_appareil"), r.optString("nom", "Téléphone"), r.optJSONArray("sims") == null ? "[]" : r.getJSONArray("sims").toString());
                        store.setPause(false);
                        runOnUiThread(this::afficherAccueil);
                        return;
                    }
                    msg = "Code refusé : " + r.optString("erreur", "erreur inconnue");
                } catch (Exception e) {
                    msg = "Pas de connexion au serveur. Vérifiez internet.";
                }
                final String m = msg;
                runOnUiThread(() -> { erreur.setText(m); ok.setEnabled(true); ok.setText("Connecter"); });
            }).start();
        });
        page(c);
    }

    void afficherAccueil() {
        LinearLayout c = colonne();
        c.setPadding(dp(20), dp(24), dp(20), dp(24));

        LinearLayout tete = new LinearLayout(this);
        tete.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        tete.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView titre = texte("KajyPay", 20, TEXT, true);
        titre.setPadding(dp(10), 0, 0, 0);
        tete.addView(titre, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        tete.addView(texte(store.nom(), 13, MUTED, false));
        c.addView(tete);

        alerte = colonne();
        alerte.setBackground(fond(Color.parseColor("#FDECEA"), 16, 0, 0));
        alerte.setPadding(dp(14), dp(12), dp(14), dp(12));
        alerte.addView(texte("Autorisation SMS requise pour recevoir les paiements.", 14, DANGER, true));
        LinearLayout ab = new LinearLayout(this);
        Button autoriser = bouton("Autoriser", DANGER, Color.WHITE, 0, 14);
        Button reglages = bouton("Paramètres", CARD, DANGER, DANGER, 14);
        ab.addView(autoriser, poids(0));
        ab.addView(reglages, poids(8));
        alerte.addView(ab, plein(10));
        c.addView(alerte, plein(16));
        autoriser.setOnClickListener(v -> demanderPermissions());
        reglages.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))));

        LinearLayout rangee = new LinearLayout(this);
        onglets.clear();
        slotsOnglets.clear();
        try {
            JSONArray sims = new JSONArray(store.sims());
            for (int i = 0; i < sims.length(); i++) {
                JSONObject s = sims.getJSONObject(i);
                final int slot = s.optInt("slot", i + 1);
                String op = s.optString("operateur");
                LinearLayout o = new LinearLayout(this);
                o.setGravity(Gravity.CENTER_VERTICAL);
                o.setPadding(dp(10), dp(10), dp(10), dp(10));
                o.setMinimumHeight(dp(56));
                o.setClickable(true);
                o.setFocusable(true);
                View barre = new View(this);
                barre.setBackground(fond(couleurOp(op), 3, 0, 0));
                o.addView(barre, new LinearLayout.LayoutParams(dp(8), dp(28)));
                LinearLayout col = colonne();
                col.setPadding(dp(10), 0, 0, 0);
                col.addView(texte("SIM " + slot, 14, TEXT, true));
                col.addView(texte(libelle(op) + (s.optInt("actif", 1) == 1 ? "" : " (arrêtée)"), 12, MUTED, false));
                o.addView(col);
                o.setOnClickListener(v -> { simChoisie = slot; majAccueil(); });
                rangee.addView(o, poids(i == 0 ? 0 : 8));
                onglets.add(o);
                slotsOnglets.add(slot);
            }
        } catch (Exception ignore) { }
        if (!slotsOnglets.isEmpty() && !slotsOnglets.contains(simChoisie)) simChoisie = slotsOnglets.get(0);
        c.addView(rangee, plein(16));

        LinearLayout hero = colonne();
        hero.setBackground(fond(ACCENT, 22, 0, 0));
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
        c.addView(hero, plein(14));

        LinearLayout actions = new LinearLayout(this);
        pauseBtn = bouton("Pause", CARD, ACCENT, LINE, 14);
        Button synchro = bouton("Synchro", CARD, ACCENT, LINE, 14);
        Button tester = bouton("Tester", CARD, ACCENT, LINE, 14);
        actions.addView(pauseBtn, poids(0));
        actions.addView(synchro, poids(8));
        actions.addView(tester, poids(8));
        c.addView(actions, plein(14));
        info = texte("", 13, MUTED, false);
        c.addView(info, plein(8));

        c.addView(texte("Paiements reçus", 16, TEXT, true), plein(14));
        liste = colonne();
        c.addView(liste, plein(8));

        Button deco = bouton("Déconnecter cet appareil", CARD, DANGER, DANGER, 15);
        c.addView(deco, plein(24));
        c.addView(texte("KajyPay version " + BuildConfig.VERSION_NAME, 12, MUTED, false), plein(16));

        pauseBtn.setOnClickListener(v -> {
            boolean p = !store.pause();
            store.setPause(p);
            if (p) KajyService.arreter(this); else demarrerSiPossible();
            info.setText(p ? "Réception en pause. Les SMS reçus pendant la pause ne seront pas transmis." : "Réception reprise.");
            h.postDelayed(this::majAccueil, 400);
        });
        synchro.setOnClickListener(v -> {
            info.setText("Envoi en cours…");
            new Thread(() -> { String m = Sync.envoyer(this); runOnUiThread(() -> { info.setText(m); majAccueil(); }); }).start();
        });
        tester.setOnClickListener(v -> {
            info.setText("Test en cours…");
            new Thread(() -> { String m = Sync.ping(this); runOnUiThread(() -> { info.setText(m); majAccueil(); }); }).start();
        });
        deco.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Déconnecter cet appareil ?")
            .setMessage("Ce téléphone ne transmettra plus les paiements et son journal local sera effacé. Il faudra un nouveau code pour le reconnecter.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Déconnecter", (d, w) -> { KajyService.arreter(this); journal.vider(); store.effacer(); afficherConnexion(); })
            .show());

        page(c);
        accueilVisible = true;
        demanderPermissions();
        demarrerSiPossible();
        majAccueil();
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

    void majAccueil() {
        if (!accueilVisible || etat == null) return;
        boolean autorise = smsAutorise();
        alerte.setVisibility(autorise ? View.GONE : View.VISIBLE);
        etat.setText(!autorise ? "●  Autorisation SMS manquante" : store.pause() ? "●  En pause" : KajyService.enMarche() ? "●  Service en marche" : "●  Service arrêté");
        pauseBtn.setText(store.pause() ? "Reprendre" : "Pause");
        String op = "";
        try {
            JSONArray sims = new JSONArray(store.sims());
            for (int i = 0; i < sims.length(); i++) if (sims.getJSONObject(i).optInt("slot") == simChoisie) op = sims.getJSONObject(i).optString("operateur");
        } catch (Exception ignore) { }
        simLabel.setText("SIM " + simChoisie + (op.isEmpty() ? "" : ", " + libelle(op)));
        for (int i = 0; i < onglets.size(); i++) {
            boolean choisi = slotsOnglets.get(i) == simChoisie;
            onglets.get(i).setBackground(fond(CARD, 14, choisi ? ACCENT : LINE, choisi ? 2 : 1));
        }
        long[] j = journal.jour(simChoisie, debutJour());
        long att = journal.enAttenteTotal();
        montant.setText(ar(j[1]) + " Ar");
        nombre.setText(j[0] + (j[0] > 1 ? " paiements confirmés" : " paiement confirmé") + (att > 0 ? ", " + att + " en attente d'envoi" : ""));
        long dc = store.dernierContact();
        long min = dc == 0 ? -1 : (System.currentTimeMillis() - dc) / 60000;
        contact.setText(min < 0 ? "Pas encore de contact avec le serveur" : min == 0 ? "Dernier contact : à l'instant" : "Dernier contact : il y a " + min + " min");
        liste.removeAllViews();
        List<Journal.Ligne> ls = journal.derniers(simChoisie, 20);
        if (ls.isEmpty()) {
            liste.addView(texte("Aucun paiement reçu sur la SIM " + simChoisie + " pour le moment.", 14, MUTED, false));
            return;
        }
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        for (Journal.Ligne l : ls) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(14), dp(12), dp(14), dp(12));
            r.setBackground(fond(CARD, 16, LINE, 1));
            LinearLayout g = colonne();
            g.addView(texte("+" + ar(l.montant) + " Ar", 16, "reconnu".equals(l.statut) ? ACCENT : TEXT, true));
            g.addView(texte(libelle(l.operateur) + ", " + f.format(new Date(l.recuLe)), 12, MUTED, false));
            r.addView(g, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            r.addView(texte(statutLibelle(l), 12, statutCouleur(l), true));
            liste.addView(r, plein(8));
        }
    }
}
