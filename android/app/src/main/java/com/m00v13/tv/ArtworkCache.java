package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fast raw artwork disk cache. Decode/resize happens once in ArtworkLoader memory, not during download. */
public final class ArtworkCache {
    private static final String PREFS = "m00v13_artwork";
    private static final long TRIM_INTERVAL_MS = 60_000L;
    private static final long TOUCH_INTERVAL_MS = 10L * 60L * 1000L;
    private static final long MAX_IMAGE_BYTES = 6L * StoragePolicy.MIB;
    private static final Map<String,Object> KEY_LOCKS = new ConcurrentHashMap<>();
    private static final Map<String,Long> LAST_TOUCH = new ConcurrentHashMap<>();
    private static final AtomicLong LAST_TRIM = new AtomicLong(0L);
    private final File dir;
    private final SharedPreferences prefs;

    public ArtworkCache(Context context) {
        dir = new File(context.getCacheDir(), "artwork");
        if (!dir.exists()) dir.mkdirs();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public File cached(String url) { return cached(url, 620); }
    public File cached(String url, int targetWidthPx) {
        if (url == null || url.isEmpty()) return null;
        String cacheKey = cacheKey(url);
        File file = new File(dir, cacheKey + ".img");
        if (!file.isFile() || file.length() <= 0) return null;
        touchThrottled(cacheKey);
        return file;
    }

    public File fetch(String url, int likelihoodScore) throws IOException { return fetch(url, likelihoodScore, 620); }

    public File fetch(String url, int likelihoodScore, int targetWidthPx) throws IOException {
        if (url == null || url.isEmpty()) return null;
        String cacheKey = cacheKey(url);
        File existing = cached(url, targetWidthPx);
        if (existing != null) { setScore(cacheKey, likelihoodScore); return existing; }

        Object lock = KEY_LOCKS.computeIfAbsent(cacheKey, k -> new Object());
        try {
            synchronized (lock) {
                existing = cached(url, targetWidthPx);
                if (existing != null) { setScore(cacheKey, likelihoodScore); return existing; }
                if (StoragePolicy.availableBytes(dir) <= StoragePolicy.SYSTEM_RESERVE_BYTES + 32L * StoragePolicy.MIB) return null;

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(5000);
                conn.setInstanceFollowRedirects(true);
                conn.setUseCaches(true);
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8,*/*;q=0.2");
                conn.setRequestProperty("User-Agent", "M00V13/0.1 AndroidTV");
                conn.connect();
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) { conn.disconnect(); return null; }
                long declared = conn.getContentLengthLong();
                if (declared > MAX_IMAGE_BYTES) { conn.disconnect(); return null; }

                File part = new File(dir, cacheKey + ".part");
                long total = 0L;
                byte[] buffer = new byte[64 * 1024];
                try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), buffer.length);
                     FileOutputStream out = new FileOutputStream(part, false)) {
                    int n;
                    while ((n = in.read(buffer)) >= 0) {
                        if (n == 0) continue;
                        total += n;
                        if (total > MAX_IMAGE_BYTES) { part.delete(); return null; }
                        out.write(buffer, 0, n);
                    }
                } finally { conn.disconnect(); }
                if (total <= 0) { part.delete(); return null; }

                File dst = new File(dir, cacheKey + ".img");
                if (dst.exists()) dst.delete();
                if (!part.renameTo(dst)) {
                    try (BufferedInputStream in = new BufferedInputStream(new java.io.FileInputStream(part));
                         FileOutputStream out = new FileOutputStream(dst, false)) {
                        int n; while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
                    }
                    part.delete();
                }

                long now = System.currentTimeMillis();
                LAST_TOUCH.put(cacheKey, now);
                prefs.edit().putString("url." + cacheKey, url).putLong("last." + cacheKey, now)
                    .putInt("score." + cacheKey, likelihoodScore).apply();
                trimOccasionally(now);
                return dst;
            }
        } finally {
            KEY_LOCKS.remove(cacheKey, lock);
        }
    }

    public void trim() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".img"));
        if (files == null) return;
        long total = 0L; for (File f : files) total += f.length();
        if (total <= PredictiveCachePolicy.ARTWORK_SOFT_BYTES) return;
        List<File> ordered = new ArrayList<>(); Collections.addAll(ordered, files);
        Collections.sort(ordered, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                String ak = strip(a.getName()), bk = strip(b.getName());
                int as = prefs.getInt("score." + ak, 0), bs = prefs.getInt("score." + bk, 0);
                if (as != bs) return as - bs;
                long al = prefs.getLong("last." + ak, 0L), bl = prefs.getLong("last." + bk, 0L);
                return Long.compare(al, bl);
            }
        });
        SharedPreferences.Editor editor = prefs.edit();
        for (File f : ordered) {
            if (total <= PredictiveCachePolicy.ARTWORK_SOFT_BYTES) break;
            long bytes = f.length(); String k = strip(f.getName());
            if (f.delete()) {
                total -= bytes;
                LAST_TOUCH.remove(k);
                editor.remove("url."+k).remove("last."+k).remove("score."+k);
            }
        }
        editor.apply();
    }

    private void trimOccasionally(long now) {
        long previous = LAST_TRIM.get();
        if (now - previous < TRIM_INTERVAL_MS) return;
        if (LAST_TRIM.compareAndSet(previous, now)) trim();
    }

    private void touchThrottled(String key) {
        long now = System.currentTimeMillis();
        Long prior = LAST_TOUCH.get(key);
        if (prior != null && now - prior < TOUCH_INTERVAL_MS) return;
        LAST_TOUCH.put(key, now);
        prefs.edit().putLong("last." + key, now).apply();
    }

    private void setScore(String key, int score) {
        if (prefs.getInt("score." + key, Integer.MIN_VALUE) != score)
            prefs.edit().putInt("score." + key, score).apply();
    }

    private static String strip(String name) { return name.endsWith(".img") ? name.substring(0, name.length() - 4) : name; }
    private static String cacheKey(String url) { return key(url); }

    private static String key(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes("UTF-8"));
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) out.append(String.format(java.util.Locale.US, "%02x", bytes[i] & 0xff));
            return out.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
