package mg.kajypay.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

/**
 * Reception SMS robuste : lecture par PDUs (compatible tous constructeurs), avec
 * repli sur getMessagesFromIntent. Seuls les SMS d'argent reconnus par le filtre
 * sont retenus ; le reste ne quitte jamais le telephone. Apres un depot reconnu,
 * declenche une relecture de solde (BalanceScheduler).
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        Store s = new Store(c);
        if (!s.estAppaire() || s.pause()) return;

        String texte = "", expediteur = "";
        long dateMs = System.currentTimeMillis();
        try {
            Bundle b = intent.getExtras();
            Object[] pdus = b == null ? null : (Object[]) b.get("pdus");
            String format = b == null ? null : b.getString("format");
            if (pdus != null && pdus.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (Object pdu : pdus) {
                    SmsMessage m = format != null ? SmsMessage.createFromPdu((byte[]) pdu, format)
                                                  : SmsMessage.createFromPdu((byte[]) pdu);
                    if (m != null) { sb.append(m.getMessageBody()); expediteur = m.getOriginatingAddress(); dateMs = m.getTimestampMillis(); }
                }
                texte = sb.toString();
            }
        } catch (Exception e) { Log.e(TAG, "pdus: " + e.getMessage()); }

        if (texte.isEmpty()) {
            // repli
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null || msgs.length == 0 || msgs[0] == null) return;
            StringBuilder sb = new StringBuilder();
            for (SmsMessage m : msgs) if (m != null && m.getMessageBody() != null) sb.append(m.getMessageBody());
            texte = sb.toString();
            expediteur = msgs[0].getOriginatingAddress();
            dateMs = msgs[0].getTimestampMillis();
        }
        if (texte.isEmpty()) return;

        Filtre.Res r = Filtre.analyser(texte);
        if (r == null) return;

        int slot = Slots.depuisIntent(c, intent);
        new Journal(c).ajouter(slot, expediteur, texte, dateMs, r.operateur, r.montant);

        final Context app = c.getApplicationContext();
        final int fslot = slot;
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                if (!KajyService.demanderSync()) Sync.envoyer(app);
                if (fslot > 0) BalanceScheduler.apresMouvement(app, fslot);
            } finally { pr.finish(); }
        }).start();
    }
}
