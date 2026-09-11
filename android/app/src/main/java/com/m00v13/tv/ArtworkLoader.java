package com.m00v13.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide artwork loader optimized for fast TV poster fill. */
public final class ArtworkLoader {
    private static final int MEMORY_KIB = 16 * 1024;
    private static final ExecutorService SHARED_IO = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "m00v13-artwork");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(MEMORY_KIB) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getAllocationByteCount() / 1024);
        }
    };
    private static final Object INFLIGHT_LOCK = new Object();
    private static final Map<String, List<WeakReference<ImageView>>> INFLIGHT = new HashMap<>();

    private final ArtworkCache disk;

    public ArtworkLoader(Context context) {
        disk = new ArtworkCache(context.getApplicationContext());
    }

    /** The executor parameter is retained for API compatibility; artwork work uses one shared pool. */
    public void load(ImageView view, String url, int targetWidthPx, Executor executor) {
        if (view == null) return;
        final int target = bucket(targetWidthPx);
        final String key = key(url, target);
        view.setTag(key);
        if (url == null || url.isEmpty()) {
            view.setImageDrawable(null);
            return;
        }

        Bitmap hit;
        synchronized (MEMORY) { hit = MEMORY.get(key); }
        if (hit != null && !hit.isRecycled()) {
            view.setImageBitmap(hit);
            return;
        }

        view.setImageDrawable(null);
        synchronized (INFLIGHT_LOCK) {
            List<WeakReference<ImageView>> waiters = INFLIGHT.get(key);
            if (waiters != null) {
                waiters.add(new WeakReference<>(view));
                return;
            }
            waiters = new ArrayList<>(4);
            waiters.add(new WeakReference<>(view));
            INFLIGHT.put(key, waiters);
        }

        SHARED_IO.execute(() -> {
            Bitmap bitmap = null;
            try {
                File file = disk.fetch(url, 90, target);
                if (file != null) bitmap = decodeForTarget(file, target);
                if (bitmap != null) {
                    synchronized (MEMORY) { MEMORY.put(key, bitmap); }
                }
            } catch (Throwable ignored) {}

            final Bitmap ready = bitmap;
            final List<WeakReference<ImageView>> waiters;
            synchronized (INFLIGHT_LOCK) { waiters = INFLIGHT.remove(key); }
            if (waiters == null || ready == null || ready.isRecycled()) return;
            for (WeakReference<ImageView> ref : waiters) {
                ImageView targetView = ref.get();
                if (targetView == null) continue;
                targetView.post(() -> {
                    Object tag = targetView.getTag();
                    if (key.equals(tag) && !ready.isRecycled()) targetView.setImageBitmap(ready);
                });
            }
        });
    }

    private static Bitmap decodeForTarget(File file, int target) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = false;
        options.inSampleSize = sampleSize(bounds.outWidth, target);
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (decoded == null || decoded.getWidth() <= target) return decoded;
        int h = Math.max(1, Math.round(decoded.getHeight() * (target / (float) decoded.getWidth())));
        Bitmap scaled = Bitmap.createScaledBitmap(decoded, target, h, false);
        if (scaled != decoded) decoded.recycle();
        return scaled;
    }

    private static int sampleSize(int width, int target) {
        if (width <= 0 || target <= 0) return 1;
        int sample = 1;
        while (width / (sample * 2) >= target) sample *= 2;
        return Math.max(1, sample);
    }

    public static void clearMemory() {
        synchronized (MEMORY) { MEMORY.evictAll(); }
        synchronized (INFLIGHT_LOCK) { INFLIGHT.clear(); }
    }

    private static int bucket(int px) {
        if (px <= 360) return 320;
        if (px <= 500) return 420;
        if (px <= 650) return 560;
        return 700;
    }

    private static String key(String url, int width) {
        return (url == null ? "" : url) + "#" + width;
    }
}
