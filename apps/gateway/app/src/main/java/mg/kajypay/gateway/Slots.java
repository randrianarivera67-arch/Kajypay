package mg.kajypay.gateway;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

public final class Slots {
    private static final String[] CLES_SLOT = {"android.telephony.extra.SLOT_INDEX", "slot", "slot_id", "slotId", "simSlot", "simId", "sim_id", "phone"};

    public static int depuisIntent(Context c, Intent intent) {
        Bundle b = intent.getExtras();
        if (b == null) return 0;
        int idx = -1;
        for (String k : CLES_SLOT) {
            Object o = b.get(k);
            if (o instanceof Integer) { idx = (Integer) o; break; }
        }
        if (idx < 0) {
            int subId = b.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", b.getInt("subscription", -1));
            if (subId >= 0 && c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                try {
                    SubscriptionInfo info = c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfo(subId);
                    if (info != null) idx = info.getSimSlotIndex();
                } catch (Exception ignore) { }
            }
        }
        return idx == 0 ? 1 : idx == 1 ? 2 : 0;
    }
}
