package mg.kajypay.gateway;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SimsDetect {
    public static JSONArray detecter(Context c) {
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return null;
        JSONArray out = new JSONArray();
        try {
            List<SubscriptionInfo> l = c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if (l == null) return out;
            for (SubscriptionInfo i : l) {
                int slot = i.getSimSlotIndex() + 1;
                if (slot < 1 || slot > 2) continue;
                String op = operateur(i);
                if (op != null) out.put(new JSONObject().put("slot", slot).put("operateur", op));
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    static String operateur(SubscriptionInfo i) {
        if (i.getMcc() == 646) {
            if (i.getMnc() == 2) return "orange";
            if (i.getMnc() == 4) return "mvola";
            if (i.getMnc() == 1) return "airtel";
        }
        String n = (i.getCarrierName() + " " + i.getDisplayName()).toLowerCase(Locale.ROOT);
        if (n.contains("orange")) return "orange";
        if (n.contains("telma") || n.contains("yas") || n.contains("mvola")) return "mvola";
        if (n.contains("airtel")) return "airtel";
        return null;
    }
}
