package mg.kajypay.gateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Store s = new Store(c);
        if (s.estAppaire() && s.autoDemarrage() && !s.pause()) {
            try { KajyService.demarrer(c); } catch (Exception ignore) { }
        }
    }
}
