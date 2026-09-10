package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

public final class ArtworkCache {
    private static final String PREFS = "m00v13_artwork";
    private static final int JPEG_QUALITY = 90;
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
        String cacheKey = cacheKey(url, targetWidthPx);
        File file = new File(dir, cacheKey + ".img");
        if (!file.isFile()) return null;
        touch(cacheKey);
        return file;
    }

    public File fetch(String url, int likelihoodScore) throws IOException { return fetch(url, likelihoodScore, 620); }
    public synchronized File fetch(String url, int likelihoodScore, int targetWidthPx) throws IOException {
        int target = bucket(targetWidthPx);
        String cacheKey = cacheKey(url, target);
        File existing = cached(url, target);
        if (existing != null) { setScore(cacheKey, likelihoodScore); return existing; }
        if (StoragePolicy.availableBytes(dir) <= StoragePolicy.SYSTEM_RESERVE_BYTES + 32L * StoragePolicy.MIB) return null;

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000); conn.setReadTimeout(12000); conn.setInstanceFollowRedirects(true); conn.connect();
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); return null; }
        long declared = conn.getContentLengthLong();
        if (declared > 10L * StoragePolicy.MIB) { conn.disconnect(); return null; }

        File raw = new File(dir, cacheKey + ".part");
        long total = 0L; byte[] buffer = new byte[32 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), buffer.length); FileOutputStream out = new FileOutputStream(raw, false)) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue; total += n;
                if (total > 10L * StoragePolicy.MIB) { raw.delete(); return null; }
                out.write(buffer, 0, n);
            }
        } finally { conn.disconnect(); }

        File dst = new File(dir, cacheKey + ".img");
        Bitmap decoded = BitmapFactory.decodeFile(raw.getAbsolutePath());
        if (decoded == null) { raw.delete(); return null; }
        Bitmap finalBitmap = decoded;
        if (decoded.getWidth() > target) {
            int h = Math.max(1, Math.round(decoded.getHeight() * (target / (float) decoded.getWidth())));
            finalBitmap = Bitmap.createScaledBitmap(decoded, target, h, true);
        }
        try (FileOutputStream out = new FileOutputStream(dst, false)) {
            if (!finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw new IOException("Artwork compression failed");
        } finally {
            if (finalBitmap != decoded) finalBitmap.recycle();
            decoded.recycle();
            raw.delete();
        }

        prefs.edit().putString("url." + cacheKey, url).putLong("last." + cacheKey, System.currentTimeMillis())
            .putInt("score." + cacheKey, likelihoodScore).apply();
        trim();
        return dst;
    }

    public synchronized void trim() {
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
                return al < bl ? -1 : (al == bl ? 0 : 1);
            }
        });
        for (File f : ordered) {
            if (total <= PredictiveCachePolicy.ARTWORK_SOFT_BYTES) break;
            long bytes=f.length(); String k=strip(f.getName());
            if (f.delete()) { total -= bytes; prefs.edit().remove("url."+k).remove("last."+k).remove("score."+k).apply(); }
        }
    }

    private void touch(String key) { prefs.edit().putLong("last." + key, System.currentTimeMillis()).apply(); }
    private void setScore(String key, int score) { prefs.edit().putInt("score." + key, score).apply(); }
    private static String strip(String name) { return name.endsWith(".img") ? name.substring(0, name.length() - 4) : name; }
    private static int bucket(int px) { if (px <= 400) return 360; if (px <= 540) return 500; if (px <= 700) return 620; return 780; }
    private static String cacheKey(String url, int width) { return key(url) + "_" + bucket(width); }

    private static String key(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = digest.digest(value.getBytes("UTF-8")); StringBuilder out = new StringBuilder();
            for (int i = 0; i < 12; i++) out.append(String.format(java.util.Locale.US, "%02x", bytes[i] & 0xff)); return out.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }
}
