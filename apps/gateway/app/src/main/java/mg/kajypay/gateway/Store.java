package mg.kajypay.gateway;

import android.content.Context;
import android.content.SharedPreferences;

public final class Store {
    private final SharedPreferences p;
    public Store(Context c) { p = c.getSharedPreferences("kajypay", Context.MODE_PRIVATE); }
    public boolean estAppaire() { return p.getString("jeton", null) != null; }
    public String api() { return p.getString("api", Api.DEFAULT_API); }
    public String jeton() { return p.getString("jeton", null); }
    public String nom() { return p.getString("nom", "Téléphone"); }
    public String sims() { return p.getString("sims", "[]"); }
    public void enregistrer(String api, String jeton, String nom, String sims) {
        p.edit().putString("api", api).putString("jeton", jeton).putString("nom", nom).putString("sims", sims).apply();
    }
    public void majSims(String sims) { p.edit().putString("sims", sims).apply(); }
    public void effacer() { p.edit().clear().apply(); }
}
