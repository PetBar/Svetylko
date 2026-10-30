package cz.svetylko.app;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/** Určení barvy světýlka pro aplikaci. */
final class ColorUtil {
    private ColorUtil() {}

    static final int[] PALETTE = {
            0xFF00E676, 0xFF1DE9B6, 0xFF00E5FF, 0xFF2979FF,
            0xFF651FFF, 0xFFD500F9, 0xFFFF4081, 0xFFFF1744,
            0xFFFF9100, 0xFFFFEA00, 0xFFC6FF00, 0xFFFFFFFF
    };

    private static final Map<String, Integer> iconCache = new HashMap<>();

    /** Vrátí barvu pro oznámení, nebo Prefs.COLOR_OFF, když má aplikace nesvítit. */
    static int colorFor(Context c, String pkg, Notification n) {
        if (!Prefs.appEnabled(c, pkg)) return Prefs.COLOR_OFF;
        int user = Prefs.appColor(c, pkg);
        if (user != 0 && user != Prefs.COLOR_OFF) return user;
        if (n != null && n.color != Notification.COLOR_DEFAULT) {
            int v = vivid(n.color);
            if (v != 0) return v;
        }
        return iconColor(c, pkg);
    }

    /** Zesvětlí a nasytí barvu, aby byla na černé dobře vidět. Šedé/černé barvy vrací jako 0. */
    static int vivid(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color | 0xFF000000, hsv);
        if (hsv[1] < 0.25f || hsv[2] < 0.2f) return 0;
        hsv[1] = Math.max(hsv[1], 0.7f);
        hsv[2] = 1f;
        return Color.HSVToColor(hsv);
    }

    /** Převládající sytá barva ikony aplikace (bílá, když ikona žádnou nemá). */
    static int iconColor(Context c, String pkg) {
        synchronized (iconCache) {
            Integer cached = iconCache.get(pkg);
            if (cached != null) return cached;
        }
        int result = 0xFFFFFFFF;
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            final int s = 40;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.draw(cv);
            float[] weight = new float[36];
            float[] hueSum = new float[36];
            float[] hsv = new float[3];
            for (int y = 0; y < s; y++) {
                for (int x = 0; x < s; x++) {
                    int px = b.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    Color.colorToHSV(px, hsv);
                    if (hsv[1] < 0.3f || hsv[2] < 0.3f) continue;
                    int bucket = Math.min(35, (int) (hsv[0] / 10f));
                    float w = hsv[1] * hsv[2];
                    weight[bucket] += w;
                    hueSum[bucket] += hsv[0] * w;
                }
            }
            b.recycle();
            int best = -1;
            float bestW = 0;
            for (int i = 0; i < 36; i++) {
                if (weight[i] > bestW) { bestW = weight[i]; best = i; }
            }
            if (best >= 0 && bestW > 3f) {
                result = Color.HSVToColor(new float[]{hueSum[best] / weight[best], 0.85f, 1f});
            }
        } catch (Exception ignored) {
        }
        synchronized (iconCache) { iconCache.put(pkg, result); }
        return result;
    }
}
