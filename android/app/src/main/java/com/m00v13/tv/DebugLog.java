package com.m00v13.tv;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-blocking debug logger. File I/O never runs on the caller/UI thread. */
public final class DebugLog {
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1024L * 1024L;
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "m00v13-debug-writer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private DebugLog() {}

    public static File file(Context c) { return new File(c.getFilesDir(), "m00v13-debug.log"); }

    public static void append(Context c, String tag, String message) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        String safeTag = tag == null ? "LOG" : tag;
        String safeMessage = message == null ? "" : message;
        long now = System.currentTimeMillis();
        WRITER.execute(() -> write(app, safeTag, safeMessage, now));
    }

    private static void write(Context c, String tag, String message, long now) {
        synchronized (LOCK) {
            try {
                File f = file(c);
                if (f.length() > MAX_BYTES) {
                    File old = new File(c.getFilesDir(), "m00v13-debug-prev.log");
                    if (old.exists()) old.delete();
                    f.renameTo(old);
                }
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(now));
                String line = ts + " [" + tag + "] " + message + "\n";
                try (FileOutputStream out = new FileOutputStream(file(c), true)) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
        }
    }

    public static void boot(Context c) {
        append(c, "BOOT", "M00V13 start • " + Build.MANUFACTURER + " " + Build.MODEL +
            " • Android " + Build.VERSION.RELEASE + " • SDK " + Build.VERSION.SDK_INT);
    }
}
