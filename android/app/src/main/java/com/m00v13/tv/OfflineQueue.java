package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OfflineQueue {
    private static final String PREFS = "m00v13_offline";
    private static final String KEY_QUEUE = "queue_v1";
    private final SharedPreferences prefs;

    public static final class Entry {
        public final String mediaId;
        public final String title;
        public final String uri;
        public final long estimatedBytes;
        public final boolean automatic;
        public final boolean pinned;
        public final long queuedAtMs;

        public Entry(String mediaId, String title, String uri, long estimatedBytes,
                     boolean automatic, boolean pinned, long queuedAtMs) {
            this.mediaId = mediaId;
            this.title = title;
            this.uri = uri;
            this.estimatedBytes = estimatedBytes;
            this.automatic = automatic;
            this.pinned = pinned;
            this.queuedAtMs = queuedAtMs;
        }
    }

    public OfflineQueue(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public synchronized List<Entry> list() {
        ArrayList<Entry> out = new ArrayList<>();
        String raw = prefs.getString(KEY_QUEUE, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(
                    o.optString("mediaId"), o.optString("title"), o.optString("uri"),
                    o.optLong("estimatedBytes"), o.optBoolean("automatic"),
                    o.optBoolean("pinned"), o.optLong("queuedAtMs")));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public synchronized boolean enqueue(Entry entry) {
        List<Entry> entries = list();
        for (Entry e : entries) if (e.mediaId.equals(entry.mediaId)) return false;
        entries.add(entry);
        save(entries);
        return true;
    }

    public synchronized void remove(String mediaId) {
        List<Entry> entries = list();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext();) {
            if (it.next().mediaId.equals(mediaId)) it.remove();
        }
        save(entries);
    }

    public int keepNextEpisodes() { return prefs.getInt("keep_next_episodes", 3); }
    public void setKeepNextEpisodes(int count) { prefs.edit().putInt("keep_next_episodes", Math.max(0, Math.min(10, count))).apply(); }
    public boolean autoDeleteWatched() { return prefs.getBoolean("auto_delete_watched", true); }
    public void setAutoDeleteWatched(boolean value) { prefs.edit().putBoolean("auto_delete_watched", value).apply(); }
    public long maxDownloadBytes() { return prefs.getLong("max_download_bytes", 0L); }
    public void setMaxDownloadBytes(long bytes) { prefs.edit().putLong("max_download_bytes", Math.max(0L, bytes)).apply(); }
    public long extraReserveBytes() { return prefs.getLong("extra_reserve_bytes", 0L); }
    public void setExtraReserveBytes(long bytes) { prefs.edit().putLong("extra_reserve_bytes", Math.max(0L, bytes)).apply(); }

    private void save(List<Entry> entries) {
        JSONArray a = new JSONArray();
        for (Entry e : entries) {
            JSONObject o = new JSONObject();
            try {
                o.put("mediaId", e.mediaId);
                o.put("title", e.title);
                o.put("uri", e.uri);
                o.put("estimatedBytes", e.estimatedBytes);
                o.put("automatic", e.automatic);
                o.put("pinned", e.pinned);
                o.put("queuedAtMs", e.queuedAtMs);
                a.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_QUEUE, a.toString()).apply();
    }
}
