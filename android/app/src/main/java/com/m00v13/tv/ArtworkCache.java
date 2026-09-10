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

public final class ArtworkCache {
    private static final String PREFS = "m00v13_artwork";
    private final File dir;
    private final SharedPreferences prefs;

    public ArtworkCache(Context context) {
        dir = new File(context.getCacheDir(), "artwork");
        if (!dir.exists()) dir.mkdirs();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public File cached(String url) {
        if (url == null || url.isEmpty()) return null;
        File file = new File(dir, key(url) + ".img");
        if (!file.isFile()) return null;
        touch(url);
        return file;
    }

    public synchronized File fetch(String url, int likelihoodScore) throws IOException {
        File existing = cached(url);
        if (existing != null) {
            setScore(url, likelihoodScore);
            return existing;
        }
        if (StoragePolicy.availableBytes(dir) <= StoragePolicy.SYSTEM_RESERVE_BYTES + 32L * StoragePolicy.MIB) {
            return null;
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) { conn.disconnect(); return null; }
        long declared = conn.getContentLengthLong();
        if (declared > 8L * StoragePolicy.MIB) { conn.disconnect(); return null; }

        File tmp = new File(dir, key(url) + ".part");
        File dst = new File(dir, key(url) + ".img");
        long total = 0L;
        byte[] buffer = new byte[32 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream(), buffer.length);
             FileOutputStream out = new FileOutputStream(tmp, false)) {
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                total += n;
                if (total > 8L * StoragePolicy.MIB) { tmp.delete(); return null; }
                out.write(buffer, 0, n);
            }
        } finally { conn.disconnect(); }
        if (dst.exists()) dst.delete();
        if (!tmp.renameTo(dst)) { tmp.delete(); throw new IOException("Cannot finalize artwork cache file"); }
        prefs.edit().putString("url." + key(url), url).putLong("last." + key(url), System.currentTimeMillis())
            .putInt("score." + key(url), likelihoodScore).apply();
        trim();
        return dst;
    }

    public synchronized void trim() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".img"));
        if (files == null) return;
        long total = 0L;
        for (File f : files) total += f.length();
        if (total <= PredictiveCachePolicy.ARTWORK_SOFT_BYTES) return;

        List<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, files);
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
            long bytes = f.length();
            String k = strip(f.getName());
            if (f.delete()) {
                total -= bytes;
                prefs.edit().remove("url." + k).remove("last." + k).remove("score." + k).apply();
            }
        }
    }

    private void touch(String url) { prefs.edit().putLong("last." + key(url), System.currentTimeMillis()).apply(); }
    private void setScore(String url, int score) { prefs.edit().putInt("score." + key(url), score).apply(); }
    private static String strip(String name) { return name.endsWith(".img") ? name.substring(0, name.length() - 4) : name; }

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
