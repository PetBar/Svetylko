package cz.svetylko.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sleduje příchozí oznámení. Když je obrazovka zhasnutá a čeká nepřečtené oznámení,
 * rozsvítí černou obrazovku se světýlkem (LightActivity).
 */
public class NotifService extends NotificationListenerService {

    static final String CHANNEL = "trigger2";
    static final int TRIGGER_ID = 4242;

    /** Čekající oznámení (klíč → barva), od posledního odemčení. Přístup jen z hlavního vlákna. */
    static final Map<String, Integer> pending = new LinkedHashMap<>();
    /** Uživatel světýlko zhasl – znovu se rozsvítí až u nového oznámení. */
    static boolean snoozed = false;

    /** Nenabíjí se / nabíjí pod 80 % / nabíjí 80–99 % / nabito. */
    static final int CHARGE_NONE = 0, CHARGING = 1, CHARGE_HIGH = 2, CHARGE_FULL = 3;
    static final int COLOR_CHARGING = 0xFFFF1744; // červená
    static final int COLOR_HIGH = 0xFF2979FF;     // modrá
    static final int COLOR_FULL = 0xFF00E676;     // zelená
    /** Stav nabíjení. */
    static int chargeState = CHARGE_NONE;
    /** Nabití baterie v procentech. */
    static int batteryLevel = -1;
    /** Uživatel zhasl světýlko nabíjení – znovu až při změně stavu nabíjení nebo po odemčení. */
    static boolean chargeSnoozed = false;

    /** Čeká nepřečtené oznámení, které má svítit. */
    static boolean notifActive(Context c) {
        return Prefs.enabled(c) && !pending.isEmpty() && !snoozed
                && !(dndOn(c) && Prefs.dndMode(c) != Prefs.DND_ALL);
    }

    /** Má svítit tečka nabíjení? */
    static boolean chargeActive(Context c) {
        if (!Prefs.enabled(c) || !Prefs.chargeLight(c) || chargeSnoozed) return false;
        if (dndOn(c) && Prefs.dndMode(c) == Prefs.DND_NOTHING) return false;
        if (chargeState == CHARGING || chargeState == CHARGE_HIGH) return true;
        return chargeState == CHARGE_FULL && Prefs.chargeFull(c);
    }

    static int chargeColor() {
        switch (chargeState) {
            case CHARGE_FULL: return COLOR_FULL;
            case CHARGE_HIGH: return COLOR_HIGH;
            default: return COLOR_CHARGING;
        }
    }

    /** Je zapnutý režim Nerušit? */
    static boolean dndOn(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    static void snoozeAll() {
        snoozed = true;
        chargeSnoozed = true;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean receiverRegistered = false;

    private final Runnable showRunnable = new Runnable() {
        @Override public void run() { tryShow(); }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                onScreenOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                handler.removeCallbacks(showRunnable);
            } else if (Intent.ACTION_USER_PRESENT.equals(a)) {
                // Odemčeno – uživatel telefon vidí, světýlko se ruší.
                pending.clear();
                snoozed = false;
                chargeSnoozed = false;
                LightActivity.closeIfOpen(false);
            }
        }
    };

    private boolean batteryRegistered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            updateCharge(intent);
        }
    };

    private static int levelOf(Intent b) {
        if (b == null) return -1;
        int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return -1;
        return Math.round(level * 100f / scale);
    }

    private static int chargeStateOf(Intent b) {
        if (b == null) return CHARGE_NONE;
        if (b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) return CHARGE_NONE;
        int status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int level = levelOf(b);
        // NOT_CHARGING při zapojení = ochrana baterie (nabíjení zastaveno na 80/85 %) → bráno jako nabito
        if (status == BatteryManager.BATTERY_STATUS_FULL
                || status == BatteryManager.BATTERY_STATUS_NOT_CHARGING
                || level >= 100) return CHARGE_FULL;
        return level >= Prefs.CHARGE_HIGH_LEVEL ? CHARGE_HIGH : CHARGING;
    }

    private void updateCharge(Intent b) {
        int lvl = levelOf(b);
        int st = chargeStateOf(b);
        boolean levelChanged = lvl != batteryLevel;
        batteryLevel = lvl;
        if (st == chargeState) {
            if (levelChanged) LightActivity.refresh(); // procenta
            return;
        }
        int old = chargeState;
        chargeState = st;
        // Znovu rozsvítit po zapojení nabíječky a po nabití (přechod na modrou ne – to by rušilo).
        boolean wake = (old == CHARGE_NONE && st != CHARGE_NONE) || st == CHARGE_FULL;
        if (wake || st == CHARGE_NONE) chargeSnoozed = false;
        LightActivity.refresh();
        if (wake && !isInteractive() && !LightActivity.isOpen()) {
            handler.removeCallbacks(showRunnable);
            handler.postDelayed(showRunnable, 1500);
        }
    }

    @Override
    public void onListenerConnected() {
        if (!batteryRegistered) {
            Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batteryRegistered = true;
            chargeState = chargeStateOf(sticky);
            batteryLevel = levelOf(sticky);
        }
        if (!receiverRegistered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_USER_PRESENT);
            registerReceiver(screenReceiver, f);
            receiverRegistered = true;
        }
        ensureChannel(this);
    }

    @Override
    public void onListenerDisconnected() {
        unregister();
    }

    @Override
    public void onDestroy() {
        unregister();
        super.onDestroy();
    }

    private void unregister() {
        if (receiverRegistered) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        if (batteryRegistered) {
            try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
            batteryRegistered = false;
        }
        handler.removeCallbacks(showRunnable);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (!qualifies(sbn, rankingMap)) return;
        if (isInteractive() && !isKeyguardLocked()) return; // telefon právě používáš – oznámení vidíš
        int color = ColorUtil.colorFor(this, sbn.getPackageName(), sbn.getNotification());
        if (color == Prefs.COLOR_OFF) return;

        boolean isNew = !pending.containsKey(sbn.getKey());
        boolean alerts = isNew || (sbn.getNotification().flags & Notification.FLAG_ONLY_ALERT_ONCE) == 0;
        pending.put(sbn.getKey(), color);
        LightActivity.refresh();

        if (alerts) {
            snoozed = false;
            if (!isInteractive()) {
                handler.removeCallbacks(showRunnable);
                handler.postDelayed(showRunnable, 800);
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        onNotificationPosted(sbn, null);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (pending.remove(sbn.getKey()) != null) LightActivity.refresh();
    }

    private boolean qualifies(StatusBarNotification sbn, RankingMap rankingMap) {
        if (!Prefs.enabled(this)) return false;
        if (getPackageName().equals(sbn.getPackageName())) return false;
        if (!sbn.isClearable()) return false;
        Notification n = sbn.getNotification();
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE
                | Notification.FLAG_GROUP_SUMMARY)) != 0) return false;
        if (rankingMap != null) {
            Ranking r = new Ranking();
            if (rankingMap.getRanking(sbn.getKey(), r)
                    && r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT) {
                return false; // tichá oznámení nesvítí
            }
        }
        return true;
    }

    private void onScreenOff() {
        handler.removeCallbacks(showRunnable);
        if (LightActivity.isStarting()) return;
        if (LightActivity.isOpen()) {
            // Uživatel zhasl displej tlačítkem, zatímco světýlko svítilo.
            snoozeAll();
            LightActivity.closeIfOpen(true);
            return;
        }
        if (notifActive(this) || chargeActive(this)) {
            handler.postDelayed(showRunnable, 1500);
        }
    }

    private boolean isKeyguardLocked() {
        android.app.KeyguardManager km = getSystemService(android.app.KeyguardManager.class);
        return km != null && km.isKeyguardLocked();
    }

    private boolean isInteractive() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private void tryShow() {
        if (LightActivity.isStale()) LightActivity.closeIfOpen(false);
        if (isInteractive() || LightActivity.isOpen()) return;
        if (!notifActive(this) && !chargeActive(this)) return;
        launch(this, LightActivity.PREVIEW_NONE);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Rozsvícení světýlka",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Pomocné oznámení, které rozsvítí obrazovku se světýlkem. Hned zmizí.");
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.enableLights(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        nm.createNotificationChannel(ch);
    }

    /** Spustí světelnou obrazovku. Při zhasnutém displeji přes celoobrazovkové oznámení. */
    static void launch(Context c, int preview) {
        Intent i = new Intent(c, LightActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(LightActivity.EXTRA_PREVIEW, preview);
        if (preview != LightActivity.PREVIEW_NONE) {
            c.startActivity(i);
            return;
        }
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean fsiAllowed = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
        if (nm != null && fsiAllowed && nm.areNotificationsEnabled()) {
            PendingIntent pi = PendingIntent.getActivity(c, 1, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notif)
                    .setContentTitle("Světýlko")
                    .setContentText("Nové oznámení")
                    .setCategory(Notification.CATEGORY_ALARM)
                    .setVisibility(Notification.VISIBILITY_SECRET)
                    .setFullScreenIntent(pi, true)
                    .setLocalOnly(true)
                    .setTimeoutAfter(20000)
                    .build();
            nm.notify(TRIGGER_ID, n);
        } else {
            try { c.startActivity(i); } catch (Exception ignored) {}
        }
    }
}
