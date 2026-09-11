package com.m00v13.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.util.LruCache;
import java.io.File;
import java.util.concurrent.Executor;

/** Small process-wide artwork memory cache layered on top of ArtworkCache. */
public final class ArtworkLoader {
    private static final int MEMORY_KIB = 8 * 1024;
    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(MEMORY_KIB) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getAllocationByteCount() / 1024);
        }
    };

    private final ArtworkCache disk;

    public ArtworkLoader(Context context) {
        disk = new ArtworkCache(context.getApplicationContext());
    }

    public void load(ImageView view, String url, int targetWidthPx, Executor executor) {
        if (view == null) return;
        final String key = key(url, targetWidthPx);
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
        executor.execute(() -> {
            try {
                File file = disk.fetch(url, 90, targetWidthPx);
                if (file == null) return;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                if (bitmap == null) return;
                synchronized (MEMORY) { MEMORY.put(key, bitmap); }
                view.post(() -> {
                    Object tag = view.getTag();
                    if (key.equals(tag) && !bitmap.isRecycled()) view.setImageBitmap(bitmap);
                });
            } catch (Throwable ignored) {}
        });
    }

    public static void clearMemory() {
        synchronized (MEMORY) { MEMORY.evictAll(); }
    }

    private static String key(String url, int width) {
        return (url == null ? "" : url) + "#" + Math.max(1, width);
    }
}
