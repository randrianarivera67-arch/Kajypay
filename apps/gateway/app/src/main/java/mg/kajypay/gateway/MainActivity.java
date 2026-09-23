package mg.kajypay.gateway;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int BG = Color.parseColor("#F3F5F4"), CARD = Color.WHITE, LINE = Color.parseColor("#E1E7E3");
    static final int TEXT = Color.parseColor("#0E1A14"), MUTED = Color.parseColor("#56655D");
    static final int ACCENT = Color.parseColor("#0E6B47"), SOFT = Color.parseColor("#E1F2E9"), DANGER = Color.parseColor("#B42318");
    private Store store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        if (store.estAppaire()) afficherAccueil(); else afficherConnexion();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable fond(int couleur, int rayon, int bord) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(couleur);
        g.setCornerRadius(dp(rayon));
        if (bord != 0) g.setStroke(dp(1), bord);
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

    Button bouton(String s, int fondCouleur, int texteCouleur, int bord) {
        Button bt = new Button(this);
        bt.setText(s);
        bt.setAllCaps(false);
        bt.setTextSize(16);
        bt.setTypeface(Typeface.DEFAULT_BOLD);
        bt.setTextColor(texteCouleur);
        bt.setBackground(fond(fondCouleur, 16, bord));
        bt.setMinHeight(dp(54));
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

    void page(View contenu) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(contenu);
        setContentView(sv);
    }

    String libelle(String op) {
        switch (op) {
            case "orange": return "Orange Money";
            case "mvola": return "MVola";
            case "airtel": return "Airtel Money";
            default: return op;
        }
    }

    int couleurOp(String op) {
        switch (op) {
            case "orange": return Color.parseColor("#FF7900");
            case "mvola": return Color.parseColor("#FFC20E");
            case "airtel": return Color.parseColor("#E4002B");
            default: return MUTED;
        }
    }

    void afficherConnexion() {
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
        code.setBackground(fond(CARD, 14, Color.parseColor("#CBD6D0")));
        code.setMinHeight(dp(56));
        c.addView(code, plein(8));
        TextView erreur = texte("", 14, DANGER, false);
        c.addView(erreur, plein(8));
        Button ok = bouton("Connecter", ACCENT, Color.WHITE, 0);
        c.addView(ok, plein(12));
        LinearLayout note = colonne();
        note.setBackground(fond(SOFT, 14, 0));
        note.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView nt = texte("KajyPay ne lit que les SMS de réception d'argent de votre opérateur. Vos messages personnels restent sur ce téléphone.", 13, Color.parseColor("#2F4A3D"), false);
        nt.setLineSpacing(0, 1.3f);
        note.addView(nt);
        c.addView(note, plein(28));
        ok.setOnClickListener(v -> {
            String k = code.getText().toString().trim().toUpperCase();
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
        tete.addView(titre);
        c.addView(tete);

        LinearLayout onglets = new LinearLayout(this);
        try {
            JSONArray sims = new JSONArray(store.sims());
            for (int i = 0; i < sims.length(); i++) {
                JSONObject s = sims.getJSONObject(i);
                String op = s.optString("operateur");
                LinearLayout o = new LinearLayout(this);
                o.setGravity(Gravity.CENTER_VERTICAL);
                o.setPadding(dp(10), dp(10), dp(10), dp(10));
                o.setBackground(fond(CARD, 14, i == 0 ? ACCENT : LINE));
                View barre = new View(this);
                barre.setBackground(fond(couleurOp(op), 3, 0));
                o.addView(barre, new LinearLayout.LayoutParams(dp(8), dp(28)));
                LinearLayout col = colonne();
                col.setPadding(dp(10), 0, 0, 0);
                col.addView(texte("SIM " + s.optInt("slot"), 14, TEXT, true));
                col.addView(texte(libelle(op) + (s.optInt("actif", 1) == 1 ? "" : " (arrêtée)"), 12, MUTED, false));
                o.addView(col);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                if (i > 0) lp.leftMargin = dp(8);
                onglets.addView(o, lp);
            }
        } catch (Exception ignore) { }
        c.addView(onglets, plein(18));

        LinearLayout hero = colonne();
        hero.setBackground(fond(ACCENT, 22, 0));
        hero.setPadding(dp(20), dp(18), dp(20), dp(20));
        TextView etat = texte("●  Appareil connecté", 14, Color.WHITE, true);
        hero.addView(etat);
        hero.addView(texte(store.nom(), 26, Color.WHITE, true), plein(12));
        TextView detail = texte("Réception des SMS : prochaine version", 13, Color.parseColor("#CFE9DC"), false);
        hero.addView(detail, plein(4));
        c.addView(hero, plein(14));

        Button tester = bouton("Tester la connexion", CARD, ACCENT, LINE);
        c.addView(tester, plein(18));
        Button deco = bouton("Déconnecter cet appareil", CARD, DANGER, DANGER);
        c.addView(deco, plein(12));
        c.addView(texte("KajyPay version " + BuildConfig.VERSION_NAME, 12, MUTED, false), plein(24));

        tester.setOnClickListener(v -> {
            tester.setEnabled(false);
            tester.setText("Test en cours…");
            new Thread(() -> {
                String msg;
                try {
                    JSONObject r = new JSONObject(Api.post(store.api() + "/gateway/ping", "{}", "Appareil " + store.jeton()));
                    if (r.optBoolean("ok")) {
                        if (r.optJSONArray("sims") != null) store.majSims(r.getJSONArray("sims").toString());
                        msg = "●  Connexion OK";
                    } else msg = "●  Refusé : " + r.optString("erreur");
                } catch (Exception e) {
                    msg = "●  Pas de connexion internet";
                }
                final String m = msg;
                runOnUiThread(() -> { etat.setText(m); tester.setEnabled(true); tester.setText("Tester la connexion"); });
            }).start();
        });
        deco.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Déconnecter cet appareil ?")
            .setMessage("Ce téléphone ne transmettra plus les paiements. Il faudra un nouveau code pour le reconnecter.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Déconnecter", (d, w) -> { store.effacer(); afficherConnexion(); })
            .show());
        page(c);
    }
}
