package mg.kajypay.gateway;

import android.content.Context;
import android.content.SharedPreferences;

public final class Store {
    private final SharedPreferences p;
    public Store(Context c) { p = c.getApplicationContext().getSharedPreferences("kajypay", Context.MODE_PRIVATE); }
    public boolean estAppaire() { return p.getString("jeton", null) != null; }
    public String api() { return p.getString("api", Api.DEFAULT_API); }
    public String jeton() { return p.getString("jeton", null); }
    public String nom() { return p.getString("nom", "Téléphone"); }
    public String sims() { return p.getString("sims", "[]"); }
    public boolean pause() { return p.getBoolean("pause", false); }
    public boolean autoDemarrage() { return p.getBoolean("auto", true); }
    public long dernierContact() { return p.getLong("contact", 0); }
    public void enregistrer(String api, String jeton, String nom, String sims) {
        p.edit().putString("api", api).putString("jeton", jeton).putString("nom", nom).putString("sims", sims).apply();
    }
    public void majSims(String sims) { p.edit().putString("sims", sims).apply(); }
    public void setPause(boolean v) { p.edit().putBoolean("pause", v).apply(); }
    public void setDernierContact(long t) { p.edit().putLong("contact", t).apply(); }
    public String simsEnvoyees() { return p.getString("sims_env", ""); }
    public void setSimsEnvoyees(String v) { p.edit().putString("sims_env", v).apply(); }
    public void setAuto(boolean v) { p.edit().putBoolean("auto", v).apply(); }
    public boolean soldeAuto() { return p.getBoolean("solde_auto", true); }
    public void setSoldeAuto(boolean v) { p.edit().putBoolean("solde_auto", v).apply(); }
    public void effacer() { p.edit().clear().apply(); }
}
