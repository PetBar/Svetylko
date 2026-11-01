package cz.svetylko.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Jednoduchý diagnostický záznam do souboru v telefonu (jen pro tuto aplikaci).
 * Ukládá čas, název balíčku aplikace a co Světýlko udělalo – nikdy obsah oznámení.
 */
final class DebugLog {
    private DebugLog() {}

    private static final String FILE = "svetylko-log.txt";
    private static final long MAX_BYTES = 150 * 1024;
    private static final int KEEP_LINES = 700;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("dd.MM. HH:mm:ss.SSS", Locale.ROOT);

    static synchronized void add(Context c, String msg) {
        try {
            File f = new File(c.getApplicationContext().getFilesDir(), FILE);
            String line = FMT.format(new Date()) + "  " + msg + "\n";
            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
                w.write(line);
            }
            if (f.length() > MAX_BYTES) trim(f);
        } catch (Exception ignored) {
        }
    }

    private static void trim(File f) throws IOException {
        ArrayDeque<String> lines = new ArrayDeque<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) {
                lines.addLast(s);
                if (lines.size() > KEEP_LINES) lines.removeFirst();
            }
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8)) {
            for (String s : lines) { w.write(s); w.write("\n"); }
        }
    }

    static synchronized String read(Context c) {
        File f = new File(c.getApplicationContext().getFilesDir(), FILE);
        if (!f.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) sb.append(s).append('\n');
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    static synchronized void clear(Context c) {
        new File(c.getApplicationContext().getFilesDir(), FILE).delete();
    }
}
