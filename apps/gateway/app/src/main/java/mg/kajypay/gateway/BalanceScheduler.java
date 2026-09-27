package mg.kajypay.gateway;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Consultation de solde APRES un mouvement (depot recu ou retrait envoye).
 * Le solde ne change que lors d'une transaction : c'est le bon moment pour le
 * relire, plutot que d'interroger l'operateur en boucle. La lecture passe par
 * UssdQueue : elle ne peut jamais tomber au milieu d'un retrait. Le resultat
 * est renvoye au serveur via Sync.envoyerSolde.
 *
 * Airtel : aucune consultation. Son menu se renumerote quand une offre s'y
 * glisse, et la sequence finit dans le menu d'offres. Ses SMS annoncent deja
 * le solde a chaque mouvement — le serveur le reprend de la.
 */
public final class BalanceScheduler {
    private static final String TAG = "BalanceScheduler";
    private static final long DELAI_MS = 20_000L; // laisser l'operateur enregistrer l'operation

    public static void apresMouvement(Context context, int slot) {
        if (context == null || slot < 1) return;
        final Context c = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Store s = new Store(c);
                if (!s.estAppaire() || s.pause() || !s.soldeAuto()) return;
                if (!UssdReader.isEnabled(c)) return;

                String operateur = "", code = "";
                JSONArray a = new JSONArray(s.sims());
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.optJSONObject(i);
                    if (o != null && o.optInt("slot") == slot) {
                        operateur = o.optString("operateur", "");
                        code = o.optString("code_ussd_solde", "");
                    }
                }
                if ("airtel".equalsIgnoreCase(operateur)) {
                    Log.d(TAG, "airtel : pas de consultation, le SMS fait foi");
                    return;
                }
                if (code == null || code.trim().isEmpty()) {
                    Log.d(TAG, "slot " + slot + " : aucun code USSD solde");
                    return;
                }
                Log.d(TAG, "controle solde apres mouvement, slot " + slot + " (" + operateur + ")");
                final int fslot = slot;
                UssdQueue.enqueueLectureSolde(c, operateur.isEmpty() ? ("slot" + slot) : operateur, code,
                    (ref, success, response) -> {
                        if (!success) { Log.e(TAG, "echec solde slot " + fslot + ": " + response); return; }
                        Long m = SoldeUssd.montant(response);
                        Sync.envoyerSolde(c, fslot, m, response);
                        Log.d(TAG, "solde slot " + fslot + " envoye: " + (m == null ? "?" : m));
                    });
            } catch (Exception e) {
                Log.e(TAG, "apresMouvement: " + e.getMessage());
            }
        }, DELAI_MS);
    }
}
