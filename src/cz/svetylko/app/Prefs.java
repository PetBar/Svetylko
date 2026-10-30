package cz.svetylko.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Uložené nastavení aplikace. */
final class Prefs {
    private Prefs() {}

    static final int STYLE_RING = 0, STYLE_DOT = 1, STYLE_BOTH = 2;
    static final int ANIM_PULSE = 0, ANIM_BLINK = 1, ANIM_STEADY = 2;
    static final int POS_LEFT = 0, POS_CENTER = 1, POS_RIGHT = 2;

    /** Hodnota "nesvítit" pro aplikaci (skutečné barvy mají alfa 0xFF, takže 1 nemůže nastat). */
    static final int COLOR_OFF = 1;
    /** Možnosti maximální doby svícení v minutách; 0 = neomezeně. */
    static final int[] TIMEOUTS = {5, 15, 30, 60, 120, 0};

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("svetylko", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }
    static int style(Context c) { return sp(c).getInt("style", STYLE_RING); }
    static int anim(Context c) { return sp(c).getInt("anim", ANIM_PULSE); }
    static int dotPos(Context c) { return sp(c).getInt("dotPos", POS_RIGHT); }
    static int brightness(Context c) { return sp(c).getInt("brightness", 15); }   // 1..100
    static int size(Context c) { return sp(c).getInt("size", 5); }                // 1..10
    static int timeoutIndex(Context c) { return sp(c).getInt("timeoutIdx", 2); }  // 30 min
    static int timeoutMinutes(Context c) {
        int i = timeoutIndex(c);
        if (i < 0 || i >= TIMEOUTS.length) i = 2;
        return TIMEOUTS[i];
    }
    static boolean respectDnd(Context c) { return sp(c).getBoolean("respectDnd", true); }

    static boolean chargeLight(Context c) { return sp(c).getBoolean("chargeLight", true); }
    static boolean chargeFull(Context c) { return sp(c).getBoolean("chargeFull", true); }
    static int chargePos(Context c) { return sp(c).getInt("chargePos", POS_LEFT); }
    static int chargeSize(Context c) { return sp(c).getInt("chargeSize", 5); }               // 1..10
    static int chargeBrightness(Context c) { return sp(c).getInt("chargeBrightness", 10); }  // 1..100
    static int chargeAnim(Context c) { return sp(c).getInt("chargeAnim", ANIM_PULSE); }
    static boolean chargePercent(Context c) { return sp(c).getBoolean("chargePercent", false); }

    /** Od kolika procent svítí nabíjení modře. */
    static final int CHARGE_HIGH_LEVEL = 80;

    static final int DND_NOTHING = 0, DND_CHARGE_ONLY = 1, DND_ALL = 2;
    /** Co svítí v režimu Nerušit. */
    static int dndMode(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains("dndMode")) return p.getInt("dndMode", DND_NOTHING);
        return respectDnd(c) ? DND_NOTHING : DND_ALL; // převzetí z verze 1.1
    }

    /** Svítí aplikace? Výchozí hodnota pro neoznačené aplikace je "defOn". */
    static boolean appEnabled(Context c, String pkg) {
        SharedPreferences p = sp(c);
        if (p.getInt("c_" + pkg, 0) == COLOR_OFF) return false; // starší verze
        return p.getBoolean("e_" + pkg, p.getBoolean("defOn", true));
    }

    static void setAppEnabled(Context c, String pkg, boolean on) {
        SharedPreferences.Editor e = sp(c).edit().putBoolean("e_" + pkg, on);
        if (sp(c).getInt("c_" + pkg, 0) == COLOR_OFF) e.remove("c_" + pkg);
        e.apply();
    }

    /** Označí nebo odznačí všechny aplikace najednou. */
    static void setAllApps(Context c, boolean on) {
        SharedPreferences p = sp(c);
        SharedPreferences.Editor e = p.edit().putBoolean("defOn", on);
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("e_")) e.remove(k);
            else if (k.startsWith("c_") && p.getInt(k, 0) == COLOR_OFF) e.remove(k);
        }
        e.apply();
    }

    /** 0 = automaticky, jinak ARGB barva. */
    static int appColor(Context c, String pkg) { return sp(c).getInt("c_" + pkg, 0); }

    static void setAppColor(Context c, String pkg, int color) {
        SharedPreferences.Editor e = sp(c).edit();
        if (color == 0) e.remove("c_" + pkg); else e.putInt("c_" + pkg, color);
        e.apply();
    }

    static void putInt(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    static void putBool(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
}
