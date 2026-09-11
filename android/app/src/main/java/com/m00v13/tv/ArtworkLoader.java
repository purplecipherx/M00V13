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

/**
 * Process-wide artwork loader layered over ArtworkCache.
 *
 * Identical in-flight image requests collapse into one disk/network/decode operation. This matters
 * on TV because the same poster can be visible in multiple rails and in the hover-preview pane.
 */
public final class ArtworkLoader {
    private static final int MEMORY_KIB = 12 * 1024;
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
            waiters = new ArrayList<>(3);
            waiters.add(new WeakReference<>(view));
            INFLIGHT.put(key, waiters);
        }

        executor.execute(() -> {
            Bitmap bitmap = null;
            try {
                File file = disk.fetch(url, 90, target);
                if (file != null) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    options.inDither = false;
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (bitmap != null) {
                        synchronized (MEMORY) { MEMORY.put(key, bitmap); }
                    }
                }
            } catch (Throwable ignored) {
            }

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

    public static void clearMemory() {
        synchronized (MEMORY) { MEMORY.evictAll(); }
        synchronized (INFLIGHT_LOCK) { INFLIGHT.clear(); }
    }

    private static int bucket(int px) {
        if (px <= 400) return 360;
        if (px <= 540) return 500;
        if (px <= 700) return 620;
        return 780;
    }

    private static String key(String url, int width) {
        return (url == null ? "" : url) + "#" + width;
    }
}
