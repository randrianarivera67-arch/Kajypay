package mg.kajypay.gateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KajyService extends Service {
    private static final String CANAL = "kajypay_service";
    private static volatile KajyService instance;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ex.submit(() -> { Sync.synchroniserSims(KajyService.this); Sync.ping(KajyService.this); Sync.envoyer(KajyService.this); RetraitUssd.traiterUn(KajyService.this); });
            h.postDelayed(this, 60000);
        }
    };

    public static boolean enMarche() { return instance != null; }

    public static void demarrer(Context c) {
        Intent i = new Intent(c, KajyService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void arreter(Context c) { c.stopService(new Intent(c, KajyService.class)); }

    public static boolean demanderSync() {
        KajyService s = instance;
        if (s == null) return false;
        s.ex.submit(() -> Sync.envoyer(s));
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Service KajyPay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CANAL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_notif).setContentTitle("KajyPay en service").setContentText("Réception des paiements active").setContentIntent(pi).setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC); else startForeground(1, n);
        instance = this;
        h.post(tick);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    @Override
    public void onDestroy() {
        instance = null;
        h.removeCallbacks(tick);
        ex.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
