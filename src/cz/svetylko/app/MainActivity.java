package cz.svetylko.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Nastavení aplikace. */
public class MainActivity extends Activity {

    private LinearLayout content;
    private LinearLayout permBox;
    private LinearLayout appsBox;
    private TextView countLabel;
    private EditText search;
    private final List<AppRow> rows = new ArrayList<>();

    private static class AppRow {
        AppItem item; View view; CheckBox check;
    }
    private float dp;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dp = getResources().getDisplayMetrics().density;
        TypedValue tv = new TypedValue();
        accent = getTheme().resolveAttribute(android.R.attr.colorAccent, tv, true) ? tv.data : 0xFF2979FF;

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        setContentView(scroll);

        final int pad = px(16);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets ins) {
                Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                content.setPadding(pad + b.left, pad + b.top, pad + b.right, pad + b.bottom);
                return WindowInsets.CONSUMED;
            }
        });

        NotifService.ensureChannel(this);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissions();
    }

    // ------------------------------------------------------------------ stavba UI

    private void build() {
        TextView title = text("Notifikační světýlko", 24, true);
        content.addView(title);
        TextView sub = text("Když přijde oznámení a displej je zhasnutý, rozsvítí se černá obrazovka "
                + "se světýlkem v barvě aplikace. Klepnutím ji zhasneš, odemčením zmizí.", 14, false);
        sub.setAlpha(0.75f);
        content.addView(sub, lp(0, px(4), 0, px(8)));

        header("Oprávnění");
        permBox = new LinearLayout(this);
        permBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(permBox);

        header("Zapnutí");
        final Switch enabled = new Switch(this);
        enabled.setText("Světýlko zapnuto");
        enabled.setTextSize(16);
        enabled.setChecked(Prefs.enabled(this));
        enabled.setOnCheckedChangeListener((b, on) -> {
            Prefs.putBool(this, "enabled", on);
            if (!on) {
                NotifService.pending.clear();
                LightActivity.closeIfOpen(false);
            }
        });
        content.addView(enabled, lp(0, px(4), 0, px(4)));

        radio("V režimu Nerušit", "dndMode", Prefs.dndMode(this),
                new String[]{"Nesvítí nic", "Svítí jen nabíjení", "Svítí vše"}, true);

        header("Světýlko oznámení");
        radio("Styl", "style", Prefs.style(this),
                new String[]{"Kroužek kolem kamery", "LED tečka", "Obojí"}, true);
        radio("Poloha tečky", "dotPos", Prefs.dotPos(this),
                new String[]{"Vlevo", "Uprostřed", "Vpravo"}, false);
        radio("Animace", "anim", Prefs.anim(this),
                new String[]{"Pulzování", "Bliknutí", "Stálé"}, false);

        slider("Jas", "brightness", 1, 100, Prefs.brightness(this), " %");
        slider("Velikost", "size", 1, 10, Prefs.size(this), "");

        final String[] tLabels = {"5 min", "15 min", "30 min", "1 hod", "2 hod", "neomezeně"};
        final TextView tLabel = text("", 15, false);
        content.addView(tLabel, lp(0, px(10), 0, 0));
        SeekBar tBar = new SeekBar(this);
        tBar.setMax(tLabels.length - 1);
        tBar.setProgress(Prefs.timeoutIndex(this));
        tLabel.setText("Svítit nejdéle: " + tLabels[tBar.getProgress()]);
        tBar.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                tLabel.setText("Svítit nejdéle: " + tLabels[v]);
                Prefs.putInt(MainActivity.this, "timeoutIdx", v);
            }
        });
        content.addView(tBar);

        Button test = new Button(this);
        test.setText("Vyzkoušet světýlko");
        test.setOnClickListener(v -> NotifService.launch(this, LightActivity.PREVIEW_ALL));
        content.addView(test, lp(0, px(12), 0, px(4)));
        TextView testHint = text("Ukázka na 15 s. Opravdové světýlko se rozsvítí jen při zhasnutém "
                + "displeji.", 13, false);
        testHint.setAlpha(0.7f);
        content.addView(testHint);

        header("Tečka nabíjení");
        final Switch charge = new Switch(this);
        charge.setText("Signalizovat nabíjení");
        charge.setTextSize(16);
        charge.setChecked(Prefs.chargeLight(this));
        charge.setOnCheckedChangeListener((b, on) -> {
            Prefs.putBool(this, "chargeLight", on);
            LightActivity.refresh();
        });
        content.addView(charge, lp(0, px(4), 0, px(4)));

        content.addView(legendRow(NotifService.COLOR_CHARGING, "nabíjí se, pod 80 %"), lp(0, px(4), 0, 0));
        content.addView(legendRow(NotifService.COLOR_HIGH, "80–99 %"), lp(0, px(4), 0, 0));
        content.addView(legendRow(NotifService.COLOR_FULL, "nabito 100 % (nebo ochrana baterie)"), lp(0, px(4), 0, px(4)));

        final Switch full = new Switch(this);
        full.setText("Po nabití dál svítit zeleně");
        full.setTextSize(16);
        full.setChecked(Prefs.chargeFull(this));
        full.setOnCheckedChangeListener((b, on) -> {
            Prefs.putBool(this, "chargeFull", on);
            LightActivity.refresh();
        });
        content.addView(full, lp(0, px(4), 0, px(4)));

        final Switch pct = new Switch(this);
        pct.setText("Zobrazit procenta vedle tečky");
        pct.setTextSize(16);
        pct.setChecked(Prefs.chargePercent(this));
        pct.setOnCheckedChangeListener((b, on) -> Prefs.putBool(this, "chargePercent", on));
        content.addView(pct, lp(0, px(4), 0, px(4)));

        radioValues("Poloha", "chargePos", Prefs.chargePos(this),
                new String[]{"Vlevo", "Vpravo"}, new int[]{Prefs.POS_LEFT, Prefs.POS_RIGHT}, false);
        radio("Animace", "chargeAnim", Prefs.chargeAnim(this),
                new String[]{"Dýchání", "Bliknutí", "Stálé"}, false);
        slider("Jas", "chargeBrightness", 1, 100, Prefs.chargeBrightness(this), " %");
        slider("Velikost", "chargeSize", 1, 10, Prefs.chargeSize(this), "");

        Button testC = new Button(this);
        testC.setText("Vyzkoušet tečku nabíjení");
        testC.setOnClickListener(v -> NotifService.launch(this, LightActivity.PREVIEW_CHARGE));
        content.addView(testC, lp(0, px(12), 0, px(4)));
        TextView chargeHint = text("Tečka svítí při zhasnutém displeji, dokud je telefon na nabíječce, "
                + "a to i spolu se světýlkem oznámení. Klepnutím zhasneš. Znovu se rozsvítí po nabití "
                + "nebo po novém připojení nabíječky.", 13, false);
        chargeHint.setAlpha(0.7f);
        content.addView(chargeHint);

        header("Aplikace a barvy");
        TextView appsHint = text("Zaškrtnuté aplikace svítí. „Auto“ bere barvu z oznámení nebo "
                + "z ikony. Klepnutím na název aplikace nastavíš vlastní barvu.", 13, false);
        appsHint.setAlpha(0.7f);
        content.addView(appsHint);

        LinearLayout bulk = new LinearLayout(this);
        bulk.setOrientation(LinearLayout.HORIZONTAL);
        Button all = new Button(this);
        all.setText("Označit vše");
        all.setOnClickListener(v -> setAll(true));
        Button none = new Button(this);
        none.setText("Odznačit vše");
        none.setOnClickListener(v -> setAll(false));
        bulk.addView(all, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bulk.addView(none, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(bulk, lp(0, px(8), 0, 0));

        countLabel = text("", 14, true);
        content.addView(countLabel, lp(0, px(6), 0, 0));

        search = new EditText(this);
        search.setHint("Hledat aplikaci");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) {}
            @Override public void afterTextChanged(Editable e) { filterApps(); }
        });
        content.addView(search, lp(0, px(4), 0, 0));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(appsBox, lp(0, px(8), 0, 0));
        appsBox.addView(text("Načítám aplikace…", 14, false));
        loadApps();
    }

    private void refreshPermissions() {
        permBox.removeAllViews();
        final String pkg = getPackageName();
        NotificationManager nm = getSystemService(NotificationManager.class);
        PowerManager pm = getSystemService(PowerManager.class);

        boolean listener = nm != null && nm.isNotificationListenerAccessGranted(
                new ComponentName(this, NotifService.class));
        permRow("Přístup k oznámením", listener, v -> {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            new ComponentName(this, NotifService.class).flattenToString());
            if (!open(i)) open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        boolean notif = nm != null && nm.areNotificationsEnabled();
        permRow("Oznámení této aplikace", notif, v -> {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            } else {
                open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg));
            }
        });

        if (Build.VERSION.SDK_INT >= 34) {
            boolean fsi = nm != null && nm.canUseFullScreenIntent();
            permRow("Celoobrazovková oznámení", fsi, v -> {
                if (!open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:" + pkg)))) openAppInfo();
            });
        }

        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(pkg);
        permRow("Baterie bez omezení", battery, v -> {
            if (!open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + pkg))))
                open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        });

        if (isXiaomi()) {
            permRow("Xiaomi: Zobrazit na zamčené obrazovce + Autostart", null, v -> {
                Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                                "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", pkg);
                if (!open(i)) openAppInfo();
            });
        }

        TextView hint = text("Když je přepínač šedý a telefon hlásí „omezené nastavení“: "
                + "Informace o aplikaci → ⋮ (vpravo nahoře) → Povolit omezená nastavení. "
                + "Pak to zkus znovu.", 13, false);
        hint.setAlpha(0.7f);
        permBox.addView(hint, lp(0, px(6), 0, 0));
        Button info = new Button(this);
        info.setText("Informace o aplikaci");
        info.setOnClickListener(v -> openAppInfo());
        permBox.addView(info, wrap());
    }

    /** ok == null znamená „nejde ověřit“. */
    private void permRow(String label, Boolean ok, View.OnClickListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text((ok == null ? "•  " : ok ? "✓  " : "✗  ") + label, 15, false);
        if (ok != null) t.setTextColor(ok ? 0xFF2E9E57 : 0xFFE53935);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (ok == null || !ok) {
            Button b = new Button(this);
            b.setText(ok == null ? "Otevřít" : "Povolit");
            b.setOnClickListener(action);
            row.addView(b, wrap());
        }
        permBox.addView(row, lp(0, px(2), 0, px(2)));
    }

    private void radio(String label, String key, int value, String[] options, boolean vertical) {
        int[] values = new int[options.length];
        for (int i = 0; i < values.length; i++) values[i] = i;
        radioValues(label, key, value, options, values, vertical);
    }

    private void radioValues(String label, final String key, int value, String[] options,
                             final int[] values, boolean vertical) {
        content.addView(text(label, 15, false), lp(0, px(10), 0, 0));
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(vertical ? RadioGroup.VERTICAL : RadioGroup.HORIZONTAL);
        int checked = 1000;
        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(options[i]);
            rb.setTextSize(15);
            g.addView(rb);
            if (values[i] == value) checked = 1000 + i;
        }
        g.check(checked);
        g.setOnCheckedChangeListener((gr, id) -> {
            Prefs.putInt(this, key, values[id - 1000]);
            LightActivity.refresh();
        });
        content.addView(g);
    }

    private LinearLayout legendRow(int color, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        dot.setBackground(circle(color, false));
        row.addView(dot, new LinearLayout.LayoutParams(px(16), px(16)));
        TextView t = text(label, 14, false);
        LinearLayout.LayoutParams tl = wrap();
        tl.leftMargin = px(10);
        row.addView(t, tl);
        return row;
    }

    private void slider(final String label, final String key, final int min, int max, int value,
                        final String unit) {
        final TextView t = text(label + ": " + value + unit, 15, false);
        content.addView(t, lp(0, px(10), 0, 0));
        SeekBar s = new SeekBar(this);
        s.setMin(min);
        s.setMax(max);
        s.setProgress(value);
        s.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar sb, int v, boolean user) {
                t.setText(label + ": " + v + unit);
                Prefs.putInt(MainActivity.this, key, v);
            }
        });
        content.addView(s);
    }

    // ------------------------------------------------------------------ seznam aplikací

    private static class AppItem {
        String pkg; String label; Drawable icon; int autoColor;
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            final List<AppItem> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : list) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                AppItem it = new AppItem();
                it.pkg = p;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.icon = ri.loadIcon(pm);
                it.autoColor = ColorUtil.iconColor(this, p);
                items.add(it);
            }
            final Collator col = Collator.getInstance(new Locale("cs", "CZ"));
            Collections.sort(items, new Comparator<AppItem>() {
                @Override public int compare(AppItem a, AppItem b) { return col.compare(a.label, b.label); }
            });
            runOnUiThread(() -> showApps(items));
        }).start();
    }

    private void showApps(List<AppItem> items) {
        appsBox.removeAllViews();
        rows.clear();
        for (final AppItem it : items) {
            final AppRow ar = new AppRow();
            ar.item = it;
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, px(4), 0, px(4));

            final CheckBox cb = new CheckBox(this);
            cb.setChecked(Prefs.appEnabled(this, it.pkg));
            cb.setOnCheckedChangeListener((b, on) -> {
                Prefs.setAppEnabled(this, it.pkg, on);
                updateCount();
            });
            row.addView(cb, wrap());

            LinearLayout clickable = new LinearLayout(this);
            clickable.setOrientation(LinearLayout.HORIZONTAL);
            clickable.setGravity(Gravity.CENTER_VERTICAL);
            clickable.setPadding(0, px(4), 0, px(4));
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
                clickable.setBackgroundResource(tv.resourceId);
            }

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(it.icon);
            clickable.addView(icon, new LinearLayout.LayoutParams(px(34), px(34)));

            TextView name = text(it.label, 15, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nlp.leftMargin = px(10);
            clickable.addView(name, nlp);

            final TextView state = text("", 13, false);
            state.setAlpha(0.7f);
            clickable.addView(state, wrap());

            final View swatch = new View(this);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(px(24), px(24));
            slp.leftMargin = px(8);
            slp.rightMargin = px(4);
            clickable.addView(swatch, slp);

            final Runnable update = () -> {
                int c = Prefs.appColor(this, it.pkg);
                if (c == 0 || c == Prefs.COLOR_OFF) {
                    state.setText("Auto");
                    swatch.setBackground(circle(it.autoColor, false));
                } else {
                    state.setText("");
                    swatch.setBackground(circle(c, false));
                }
            };
            update.run();
            clickable.setOnClickListener(v -> pickColor(it, update));
            row.addView(clickable, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ar.view = row;
            ar.check = cb;
            rows.add(ar);
            appsBox.addView(row);
        }
        filterApps();
        updateCount();
    }

    private void setAll(boolean on) {
        Prefs.setAllApps(this, on);
        for (AppRow r : rows) r.check.setChecked(on); // zapíše stejnou hodnotu, nevadí
        Prefs.setAllApps(this, on);                    // a uklidí jednotlivé záznamy
        updateCount();
    }

    private void updateCount() {
        if (countLabel == null) return;
        int n = 0;
        for (AppRow r : rows) if (r.check.isChecked()) n++;
        countLabel.setText("Svítí " + n + " z " + rows.size() + " aplikací");
    }

    private void filterApps() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for (AppRow r : rows) {
            boolean show = q.isEmpty() || r.item.label.toLowerCase(Locale.ROOT).contains(q);
            r.view.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void pickColor(final AppItem it, final Runnable onDone) {
        final AlertDialog[] dlg = new AlertDialog[1];
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(px(16), px(12), px(16), px(4));
        for (final int c : ColorUtil.PALETTE) {
            View sw = new View(this);
            sw.setBackground(circle(c, false));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = px(44);
            glp.height = px(44);
            glp.setMargins(px(8), px(8), px(8), px(8));
            sw.setOnClickListener(v -> {
                Prefs.setAppColor(this, it.pkg, c);
                onDone.run();
                dlg[0].dismiss();
            });
            grid.addView(sw, glp);
        }
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle(it.label)
                .setView(grid)
                .setPositiveButton("Automaticky", (d, w) -> {
                    Prefs.setAppColor(this, it.pkg, 0);
                    onDone.run();
                })
                .setNegativeButton("Zpět", null)
                .create();
        dlg[0].show();
    }

    // ------------------------------------------------------------------ pomocné

    private GradientDrawable circle(int color, boolean hollow) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        if (hollow) {
            g.setColor(Color.TRANSPARENT);
            g.setStroke(px(2), color);
        } else {
            g.setColor(color);
            g.setStroke(px(1), 0x40808080);
        }
        return g;
    }

    private void header(String s) {
        TextView t = text(s, 13, true);
        t.setTextColor(accent);
        t.setAllCaps(true);
        t.setLetterSpacing(0.06f);
        content.addView(t, lp(0, px(22), 0, px(4)));
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(l, t, r, b);
        return p;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int px(float v) { return Math.round(v * dp); }

    private boolean open(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openAppInfo() {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private static boolean isXiaomi() {
        String m = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
