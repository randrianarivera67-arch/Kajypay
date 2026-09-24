package mg.kajypay.gateway;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.FileProvider;
import java.io.File;
import org.json.JSONObject;

/**
 * Mise a jour automatique : interroge le serveur, propose la version, telecharge
 * l'APK puis ouvre l'installateur. La signature etant identique, l'installation
 * se fait par-dessus : aucune desinstallation, aucune perte de donnees.
 */
public final class MiseAJour {
    private static final String TAG = "MiseAJour";
    private static boolean dejaVerifie = false;

    public static void verifier(Activity act, String api, boolean forcer) {
        if (dejaVerifie && !forcer) return;
        dejaVerifie = true;
        new Thread(() -> {
            try {
                String rep = Api.get(api + "/maj/gateway", null);
                JSONObject r = new JSONObject(rep);
                if (!r.optBoolean("ok") || !r.optBoolean("disponible")) return;
                int vc = r.optInt("version_code");
                if (vc <= BuildConfig.VERSION_CODE) return;
                final String nom = r.optString("version_nom");
                final String url = r.optString("url");
                final String notes = r.optString("notes", "");
                final boolean oblig = r.optBoolean("obligatoire");
                if (url.isEmpty()) return;
                new Handler(Looper.getMainLooper()).post(() -> proposer(act, nom, url, notes, oblig));
            } catch (Exception e) { Log.e(TAG, "verifier: " + e.getMessage()); }
        }).start();
    }

    private static void proposer(Activity act, String nom, String url, String notes, boolean oblig) {
        if (act.isFinishing()) return;
        String msg = "Version " + nom + " disponible." + (notes.isEmpty() ? "" : "\n\n" + notes)
            + "\n\nLa mise a jour s'installe par-dessus : vos donnees sont conservees.";
        AlertDialog.Builder b = new AlertDialog.Builder(act)
            .setTitle("Mise a jour disponible")
            .setMessage(msg)
            .setPositiveButton("Mettre a jour", (d, w) -> telecharger(act, nom, url));
        if (!oblig) b.setNegativeButton("Plus tard", null);
        b.setCancelable(!oblig);
        b.show();
    }

    private static void telecharger(Activity act, String nom, String url) {
        try {
            File dossier = new File(act.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "maj");
            if (!dossier.exists()) dossier.mkdirs();
            final File cible = new File(dossier, "kajypay-gateway-" + nom + ".apk");
            if (cible.exists()) cible.delete();

            DownloadManager dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("KajyPay " + nom);
            req.setDescription("Telechargement de la mise a jour");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(act, Environment.DIRECTORY_DOWNLOADS, "maj/" + cible.getName());
            req.setMimeType("application/vnd.android.package-archive");
            final long id = dm.enqueue(req);

            BroadcastReceiver rec = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    long fini = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (fini != id) return;
                    try { c.unregisterReceiver(this); } catch (Exception ignore) { }
                    DownloadManager d2 = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
                    Cursor cur = d2.query(new DownloadManager.Query().setFilterById(id));
                    boolean ok = false;
                    if (cur != null && cur.moveToFirst()) {
                        int st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        ok = st == DownloadManager.STATUS_SUCCESSFUL;
                        cur.close();
                    }
                    if (ok && cible.exists()) installer(act, cible);
                    else new AlertDialog.Builder(act).setTitle("Telechargement echoue")
                        .setMessage("Reessayez plus tard ou verifiez votre connexion.")
                        .setPositiveButton("Fermer", null).show();
                }
            };
            IntentFilter fl = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (android.os.Build.VERSION.SDK_INT >= 33) act.registerReceiver(rec, fl, Context.RECEIVER_EXPORTED);
            else act.registerReceiver(rec, fl);
        } catch (Exception e) {
            Log.e(TAG, "telecharger: " + e.getMessage());
            try { act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignore) { }
        }
    }

    private static void installer(Activity act, File apk) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(act)
                    .setTitle("Autoriser l'installation")
                    .setMessage("Autorisez KajyPay a installer les mises a jour, puis relancez la mise a jour.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Ouvrir les reglages", (d, w) -> {
                        try {
                            act.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + act.getPackageName())));
                        } catch (Exception ignore) { }
                    }).show();
                return;
            }
            Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".fichiers", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Exception e) { Log.e(TAG, "installer: " + e.getMessage()); }
    }
}
