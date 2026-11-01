package cz.svetylko.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.LinkedHashSet;
import java.util.Set;

/** Černá obrazovka se světýlkem, která se ukáže přes zamčenou obrazovku. */
public class LightActivity extends Activity {

    static final String EXTRA_PREVIEW = "previewMode";
    static final int PREVIEW_NONE = 0, PREVIEW_ALL = 1, PREVIEW_CHARGE = 2, PREVIEW_TEST = 3;
    private static long lastCreated = 0;

    /** Byla světelná obrazovka vytvořena po čase t? */
    static boolean createdSince(long t) { return lastCreated >= t; }

    private static final int[] PREVIEW_COLORS = {0xFF00E676, 0xFF2979FF, 0xFFFF1744};
    private static final int[] PREVIEW_CHARGE_COLORS = {
            NotifService.COLOR_CHARGING, NotifService.COLOR_HIGH, NotifService.COLOR_FULL};
    private static final int[] PREVIEW_PERCENT = {45, 85, 100};

    private static LightActivity current;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private LightView view;
    private int preview;
    private PowerManager.WakeLock proximityLock;
    private float lastBrightness = -1;

    private long createdAt;
    private boolean resumed;

    static boolean isOpen() { return current != null; }

    /** Otevřeno, ale nikdy se pořádně nezobrazilo (např. displej se nerozsvítil). */
    static boolean isStale() {
        return current != null && !current.resumed
                && System.currentTimeMillis() - current.createdAt > 5000;
    }

    /** Čerstvě spuštěno – během rozsvěcování se ignorují zhasnutí displeje. */
    static boolean isStarting() {
        return current != null && System.currentTimeMillis() - current.createdAt < 2500;
    }

    static void closeIfOpen(boolean snooze) {
        if (current != null) current.dismiss(snooze);
    }

    static void refresh() {
        if (current != null) current.refreshState();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = this;
        createdAt = System.currentTimeMillis();
        lastCreated = createdAt;
        preview = getIntent().getIntExtra(EXTRA_PREVIEW, PREVIEW_NONE);
        if (preview == PREVIEW_NONE || preview == PREVIEW_TEST) {
            PowerManager pm0 = getSystemService(PowerManager.class);
            DebugLog.add(this, "Světelná obrazovka VYTVOŘENA" + (preview == PREVIEW_TEST ? " (zkouška)" : "")
                    + ", displej " + (pm0 != null && pm0.isInteractive() ? "svítí" : "zhasnutý"));
        }

        setShowWhenLocked(true);
        setTurnScreenOn(true);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        w.setAttributes(lp);
        w.setDecorFitsSystemWindows(false);

        view = new LightView(this);
        setContentView(view);
        view.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(true); }
        });

        WindowInsetsController ic = w.getInsetsController();
        if (ic != null) {
            ic.hide(WindowInsets.Type.systemBars());
            ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NotifService.TRIGGER_ID);

        // Limit doby svícení platí pro oznámení; nabíjení svítí, dokud je telefon na nabíječce.
        long timeoutMs = preview != PREVIEW_NONE ? 15000L : Prefs.timeoutMinutes(this) * 60000L;
        if (timeoutMs > 0) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (preview != PREVIEW_NONE) { dismiss(true); return; }
                    NotifService.snoozed = true;
                    refreshState();
                }
            }, timeoutMs);
        }
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (preview == PREVIEW_NONE || preview == PREVIEW_TEST) DebugLog.add(this, "Světelná obrazovka ZOBRAZENA");
        view.start();
        if (preview == PREVIEW_NONE) acquireProximity();
    }

    @Override
    protected void onPause() {
        resumed = false;
        view.stop();
        releaseProximity();
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isFinishing() && System.currentTimeMillis() - createdAt > 2500) {
            PowerManager pm = getSystemService(PowerManager.class);
            if (preview == PREVIEW_NONE && pm != null && !pm.isInteractive()) NotifService.snoozeAll();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseProximity();
        if (current == this) current = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        dismiss(true);
    }

    void dismiss(boolean snooze) {
        if ((preview == PREVIEW_NONE || preview == PREVIEW_TEST) && !isFinishing()) {
            DebugLog.add(this, "Světelná obrazovka zavřena" + (snooze ? " (klepnutí / zhasnutí / čas)" : " (není co ukázat)"));
        }
        if (preview == PREVIEW_NONE && snooze) NotifService.snoozeAll();
        if (current == this) current = null;
        if (!isFinishing()) finish();
    }

    /** Zjistí, co má svítit, a nastaví jas okna i jednotlivých světel. */
    void refreshState() {
        int[] notif = null;
        int[] charge = null;

        if (preview != PREVIEW_NONE) {
            if (preview == PREVIEW_ALL || preview == PREVIEW_TEST) notif = PREVIEW_COLORS;
            if (preview == PREVIEW_CHARGE || Prefs.chargeLight(this)) charge = PREVIEW_CHARGE_COLORS;
        } else {
            if (NotifService.notifActive(this)) {
                Set<Integer> distinct = new LinkedHashSet<>(NotifService.pending.values());
                notif = new int[distinct.size()];
                int k = 0;
                for (Integer c : distinct) notif[k++] = c;
            }
            if (NotifService.chargeActive(this)) charge = new int[]{NotifService.chargeColor()};
        }

        if (notif == null && charge == null) {
            dismiss(false);
            return;
        }

        // Jas okna je jeden, takže se nastaví podle jasnějšího světla
        // a slabší světlo se ztlumí průhledností.
        int bn = notif != null ? Prefs.brightness(this) : 0;
        int bc = charge != null ? Prefs.chargeBrightness(this) : 0;
        int bw = Math.max(1, Math.max(bn, bc));
        float win = Math.max(0.01f, bw / 100f);
        if (win != lastBrightness) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.screenBrightness = win;
            getWindow().setAttributes(lp);
            lastBrightness = win;
        }
        int[] percents = preview != PREVIEW_NONE ? PREVIEW_PERCENT : new int[]{NotifService.batteryLevel};
        view.setState(notif, bn / (float) bw, charge, bc / (float) bw, percents);
    }

    /** V kapse nebo displejem dolů zhasne displej pomocí senzoru přiblížení. */
    private void acquireProximity() {
        try {
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                if (proximityLock == null) {
                    proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                            "svetylko:proximity");
                    proximityLock.setReferenceCounted(false);
                }
                proximityLock.acquire(6 * 60 * 60 * 1000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseProximity() {
        try {
            if (proximityLock != null && proximityLock.isHeld()) proximityLock.release();
        } catch (Exception ignored) {
        }
    }
}
