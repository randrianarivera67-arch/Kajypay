package mg.kajypay.gateway;

import android.content.Context;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Sync {
    public static synchronized String envoyer(Context c) {
        Store s = new Store(c);
        if (!s.estAppaire()) return "Appareil non connecté";
        Journal j = new Journal(c);
        int total = 0;
        try {
            while (true) {
                List<Journal.Ligne> a = j.enAttente(50);
                if (a.isEmpty()) break;
                JSONArray arr = new JSONArray();
                for (Journal.Ligne l : a) {
                    JSONObject o = new JSONObject().put("expediteur", l.expediteur == null ? "" : l.expediteur).put("texte", l.texte).put("recu_le", l.recuLe);
                    if (l.slot > 0) o.put("slot", l.slot);
                    arr.put(o);
                }
                JSONObject r = new JSONObject(Api.post(s.api() + "/gateway/sms", new JSONObject().put("sms", arr).toString(), "Appareil " + s.jeton()));
                if (!r.optBoolean("ok")) return "Refusé : " + r.optString("erreur");
                JSONArray res = r.getJSONArray("resultats");
                if (res.length() < a.size()) return "Réponse incomplète du serveur";
                for (int i = 0; i < a.size(); i++) {
                    JSONObject x = res.getJSONObject(i);
                    j.maj(a.get(i).id, x.optString("statut", "erreur"), x.has("raison") ? x.optString("raison") : null, x.optInt("sim", 0), x.optBoolean("facture", false));
                }
                total += a.size();
                s.setDernierContact(System.currentTimeMillis());
            }
            return total == 0 ? "Rien à envoyer" : total + " SMS envoyés";
        } catch (Exception e) {
            return "Pas de connexion, nouvel essai automatique";
        }
    }

    public static synchronized String ping(Context c) {
        Store s = new Store(c);
        if (!s.estAppaire()) return "Appareil non connecté";
        try {
            JSONObject r = new JSONObject(Api.post(s.api() + "/gateway/ping", "{}", "Appareil " + s.jeton()));
            if (!r.optBoolean("ok")) return "Refusé : " + r.optString("erreur");
            if (r.optJSONArray("sims") != null) s.majSims(r.getJSONArray("sims").toString());
            s.setDernierContact(System.currentTimeMillis());
            return "Connexion OK";
        } catch (Exception e) {
            return "Pas de connexion internet";
        }
    }
}
