package mg.kajypay.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        Store s = new Store(c);
        if (!s.estAppaire() || s.pause()) return;
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0 || msgs[0] == null) return;
        StringBuilder sb = new StringBuilder();
        for (SmsMessage m : msgs) if (m != null && m.getMessageBody() != null) sb.append(m.getMessageBody());
        String texte = sb.toString();
        Filtre.Res r = Filtre.analyser(texte);
        if (r == null) return;
        int slot = Slots.depuisIntent(c, intent);
        new Journal(c).ajouter(slot, msgs[0].getOriginatingAddress(), texte, msgs[0].getTimestampMillis(), r.operateur, r.montant);
        if (!KajyService.demanderSync()) {
            final Context app = c.getApplicationContext();
            final PendingResult pr = goAsync();
            new Thread(() -> { try { Sync.envoyer(app); } finally { pr.finish(); } }).start();
        }
    }
}
