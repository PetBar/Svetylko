package cz.svetylko.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;

import java.util.List;
import java.util.Random;

/**
 * Kreslí na černém pozadí dvě nezávislá světla:
 * světýlko oznámení (kroužek kolem kamery a/nebo tečka) a tečku nabíjení.
 */
class LightView extends View {

    private static final long PULSE_MS = 2600, BREATH_MS = 3600;
    private static final long BLINK_MS = 3000, BLINK_ON_MS = 450, STEADY_MS = 3000;
    private static final long SHIFT_MS = 60000; // posun proti vypálení displeje

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final Random random = new Random();

    // světýlko oznámení
    private final int style, notifAnim, dotPos, size;
    private int[] notifColors;
    private float notifScale = 1f;

    // tečka nabíjení
    private final int chargePos, chargeSize, chargeAnim;
    private final boolean showPercent;
    private int[] chargeColors;
    private int[] percents = {-1};
    private float chargeScale = 1f;

    private boolean running;
    private final long t0 = SystemClock.uptimeMillis();
    private long lastShift = 0;
    private float shiftX, shiftY;

    private float camX, camY, camR;
    private boolean hasCutout;
    private final int[] loc = new int[2];

    LightView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        style = Prefs.style(c);
        notifAnim = Prefs.anim(c);
        dotPos = Prefs.dotPos(c);
        size = clamp(Prefs.size(c));
        int cp = Prefs.chargePos(c);
        chargePos = cp == Prefs.POS_RIGHT ? Prefs.POS_RIGHT : Prefs.POS_LEFT;
        chargeSize = clamp(Prefs.chargeSize(c));
        chargeAnim = Prefs.chargeAnim(c);
        showPercent = Prefs.chargePercent(c);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        setBackgroundColor(Color.BLACK);
        setKeepScreenOn(true);
    }

    private static int clamp(int v) { return Math.max(1, Math.min(10, v)); }

    /**
     * @param notif   barvy oznámení (null = nesvítí)
     * @param charge  barvy nabíjení (null = nesvítí); víc barev = ukázka, střídají se
     * @param pcts    procenta baterie ke každé barvě nabíjení (-1 = neznámé)
     */
    void setState(int[] notif, float nScale, int[] charge, float cScale, int[] pcts) {
        notifColors = (notif != null && notif.length > 0) ? notif : null;
        chargeColors = (charge != null && charge.length > 0) ? charge : null;
        notifScale = nScale;
        chargeScale = cScale;
        percents = (pcts != null && pcts.length > 0) ? pcts : new int[]{-1};
        invalidate();
    }

    void start() {
        running = true;
        invalidate();
    }

    void stop() {
        running = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        findCutout();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        findCutout();
        return super.onApplyWindowInsets(insets);
    }

    /** Najde průstřel kamery v horní části displeje. */
    private void findCutout() {
        DisplayCutout dc = null;
        WindowInsets wi = getRootWindowInsets();
        if (wi != null) dc = wi.getDisplayCutout();
        if (dc == null) {
            Display d = getDisplay();
            if (d != null) dc = d.getCutout();
        }
        hasCutout = false;
        if (dc != null) {
            List<Rect> rects = dc.getBoundingRects();
            Rect best = null;
            for (Rect r : rects) {
                if (r.isEmpty()) continue;
                if (best == null || r.top < best.top) best = r;
            }
            if (best != null) {
                getLocationOnScreen(loc);
                camX = best.exactCenterX() - loc[0];
                camY = best.exactCenterY() - loc[1];
                camR = Math.min(best.width(), best.height()) / 2f;
                hasCutout = camR > 2 * dp;
            }
        }
        if (!hasCutout) {
            camX = getWidth() / 2f;
            camY = 22 * dp;
            camR = 6 * dp;
        }
        invalidate();
    }

    // ------------------------------------------------------------------ animace

    private static long period(int anim, long pulse) {
        switch (anim) {
            case Prefs.ANIM_BLINK: return BLINK_MS;
            case Prefs.ANIM_STEADY: return STEADY_MS;
            default: return pulse;
        }
    }

    /** Jas 0..1 v čase t. */
    private static float intensity(int anim, long pulse, long t) {
        switch (anim) {
            case Prefs.ANIM_BLINK:
                return (t % BLINK_MS) < BLINK_ON_MS ? 1f : 0f;
            case Prefs.ANIM_STEADY:
                return 1f;
            default:
                double phase = (t % pulse) / (double) pulse;
                return (float) (0.12 + 0.88 * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase)));
        }
    }

    /** Za jak dlouho je potřeba překreslit. */
    private static long nextDelay(int anim, long pulse, long t, boolean multiColor) {
        switch (anim) {
            case Prefs.ANIM_BLINK: {
                long p = t % BLINK_MS;
                return p < BLINK_ON_MS ? BLINK_ON_MS - p : BLINK_MS - p;
            }
            case Prefs.ANIM_STEADY:
                return multiColor ? STEADY_MS - (t % STEADY_MS) : SHIFT_MS;
            default:
                return 40; // ~25 snímků/s stačí na plynulý pulz
        }
    }

    // ------------------------------------------------------------------ kreslení

    private float dotRadius(int s) { return (2.5f + s * 0.8f) * dp; }

    private float sideX(int pos) {
        return pos == Prefs.POS_LEFT ? 30 * dp : getWidth() - 30 * dp;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long t = SystemClock.uptimeMillis() - t0;

        if (t - lastShift > SHIFT_MS || lastShift == 0) {
            lastShift = t == 0 ? 1 : t;
            shiftX = (random.nextFloat() * 6f - 3f) * dp;
            shiftY = (random.nextFloat() * 4f - 2f) * dp;
        }

        boolean notifDot = false;
        if (notifColors != null) {
            notifDot = style == Prefs.STYLE_DOT || style == Prefs.STYLE_BOTH;
            drawNotif(canvas, t);
        }
        if (chargeColors != null) {
            // Když je tečka oznámení na stejné straně, tečka nabíjení jde pod ni.
            boolean below = notifDot && dotPos == chargePos;
            drawCharge(canvas, t, below);
        }

        if (running) {
            long d = Long.MAX_VALUE;
            if (notifColors != null) d = Math.min(d, nextDelay(notifAnim, PULSE_MS, t, notifColors.length > 1));
            if (chargeColors != null) d = Math.min(d, nextDelay(chargeAnim, BREATH_MS, t, chargeColors.length > 1));
            postInvalidateDelayed(Math.max(16, Math.min(d, SHIFT_MS) + 2));
        }
    }

    private void drawNotif(Canvas canvas, long t) {
        int color = notifColors[(int) ((t / period(notifAnim, PULSE_MS)) % notifColors.length)];
        float a = intensity(notifAnim, PULSE_MS, t) * notifScale;
        if (a <= 0.001f) return;

        boolean ring = style == Prefs.STYLE_RING || style == Prefs.STYLE_BOTH;
        boolean dot = style == Prefs.STYLE_DOT || style == Prefs.STYLE_BOTH;
        float stroke = (1.5f + size * 0.35f) * dp;
        float ringR = camR + 2.5f * dp + stroke / 2f;

        if (ring) {
            float rx = camX + shiftX / 3f, ry = camY + shiftY / 3f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(color);
            paint.setAlpha((int) (70 * a));                // jemná záře
            paint.setStrokeWidth(stroke * 2.6f);
            canvas.drawCircle(rx, ry, ringR + stroke * 0.3f, paint);
            paint.setAlpha((int) (255 * a));               // samotný kroužek
            paint.setStrokeWidth(stroke);
            canvas.drawCircle(rx, ry, ringR, paint);
        }
        if (dot) {
            float r = dotRadius(size);
            float x, y = camY;
            if (dotPos == Prefs.POS_CENTER) {
                x = camX;
                if (ring || hasCutout) y = camY + ringR + stroke + r + 6 * dp;
            } else {
                x = sideX(dotPos);
            }
            drawDot(canvas, x + shiftX, y + shiftY, r, color, a);
        }
    }

    private void drawCharge(Canvas canvas, long t, boolean below) {
        int idx = (int) ((t / period(chargeAnim, BREATH_MS)) % chargeColors.length);
        int color = chargeColors[idx];
        float a = intensity(chargeAnim, BREATH_MS, t) * chargeScale;

        float r = dotRadius(chargeSize);
        float x = sideX(chargePos) + shiftX;
        float y = camY + shiftY;
        if (below) y += dotRadius(size) + r + 12 * dp;

        if (a > 0.001f) drawDot(canvas, x, y, r, color, a);

        if (showPercent) {
            int pct = percents[Math.min(idx, percents.length - 1)];
            if (pct >= 0) {
                // Procenta svítí stále (ne podle animace), ať jdou přečíst.
                textPaint.setTextSize(Math.max(11 * dp, r * 1.5f));
                textPaint.setColor(color);
                textPaint.setAlpha((int) (200 * chargeScale));
                float gap = r * 1.6f + 4 * dp;
                float ty = y - (textPaint.ascent() + textPaint.descent()) / 2f;
                String s = pct + " %";
                if (chargePos == Prefs.POS_LEFT) {
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(s, x + gap, ty, textPaint);
                } else {
                    textPaint.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(s, x - gap, ty, textPaint);
                }
            }
        }
    }

    private void drawDot(Canvas canvas, float x, float y, float r, int color, float a) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha((int) (60 * a));
        canvas.drawCircle(x, y, r * 1.8f, paint);
        paint.setAlpha((int) (255 * a));
        canvas.drawCircle(x, y, r, paint);
    }
}
