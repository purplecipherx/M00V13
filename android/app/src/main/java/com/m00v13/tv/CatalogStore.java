package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Process-wide catalog cache.
 *
 * The old implementation reparsed and rewrote the entire JSON catalog for every single upsert.
 * Discovery/genre refreshes can insert dozens of cards, so that turned one refresh into dozens of
 * O(n) parses + O(n) serializations. Keep a process cache and coalesce persistence instead.
 */
public final class CatalogStore {
    private static final String PREFS = "m00v13_catalog";
    private static final String KEY = "items_v1";
    private static final Object LOCK = new Object();
    private static final Map<String, MediaCard> CACHE = new LinkedHashMap<>();
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "m00v13-catalog-writer");
        t.setDaemon(true);
        return t;
    });
    private static SharedPreferences sharedPrefs;
    private static boolean loaded;
    private static long generation;
    private static ScheduledFuture<?> pendingWrite;

    public CatalogStore(Context context) {
        synchronized (LOCK) {
            if (sharedPrefs == null) {
                sharedPrefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            }
            ensureLoadedLocked();
        }
    }

    public List<MediaCard> all() {
        synchronized (LOCK) {
            ensureLoadedLocked();
            return new ArrayList<>(CACHE.values());
        }
    }

    public void upsert(MediaCard card) {
        if (card == null || card.id == null || card.id.isEmpty()) return;
        synchronized (LOCK) {
            ensureLoadedLocked();
            CACHE.put(card.id, card);
            schedulePersistLocked();
        }
    }

    public void upsertAll(Collection<MediaCard> cards) {
        if (cards == null || cards.isEmpty()) return;
        synchronized (LOCK) {
            ensureLoadedLocked();
            boolean changed = false;
            for (MediaCard card : cards) {
                if (card == null || card.id == null || card.id.isEmpty()) continue;
                CACHE.put(card.id, card);
                changed = true;
            }
            if (changed) schedulePersistLocked();
        }
    }

    public MediaCard find(String id) {
        if (id == null) return null;
        synchronized (LOCK) {
            ensureLoadedLocked();
            return CACHE.get(id);
        }
    }

    /** Force a durable snapshot when a caller explicitly needs it. */
    public void flush() {
        final List<MediaCard> snapshot;
        synchronized (LOCK) {
            ensureLoadedLocked();
            generation++;
            if (pendingWrite != null) pendingWrite.cancel(false);
            pendingWrite = null;
            snapshot = new ArrayList<>(CACHE.values());
        }
        persist(snapshot);
    }

    private static void ensureLoadedLocked() {
        if (loaded || sharedPrefs == null) return;
        loaded = true;
        CACHE.clear();
        try {
            JSONArray a = new JSONArray(sharedPrefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                MediaCard card = new MediaCard(
                    o.optString("id"), o.optString("title"), o.optString("subtitle"),
                    o.optBoolean("series"), o.optString("genre"), jsonStrings(o.optJSONArray("tags")),
                    emptyToNull(o.optString("artworkUrl")), emptyToNull(o.optString("streamUri")),
                    o.optLong("durationMs"), emptyToNull(o.optString("seriesKey")),
                    o.optInt("seasonNumber"), o.optInt("episodeNumber"),
                    emptyToNull(o.optString("collectionKey")), o.optInt("collectionOrder"));
                if (card.id != null && !card.id.isEmpty()) CACHE.put(card.id, card);
            }
        } catch (JSONException ignored) {}
    }

    private static void schedulePersistLocked() {
        final long myGeneration = ++generation;
        if (pendingWrite != null) pendingWrite.cancel(false);
        pendingWrite = WRITER.schedule(() -> {
            final List<MediaCard> snapshot;
            synchronized (LOCK) {
                if (myGeneration != generation) return;
                snapshot = new ArrayList<>(CACHE.values());
                pendingWrite = null;
            }
            persist(snapshot);
        }, 250, TimeUnit.MILLISECONDS);
    }

    private static void persist(List<MediaCard> items) {
        SharedPreferences prefs;
        synchronized (LOCK) { prefs = sharedPrefs; }
        if (prefs == null) return;
        JSONArray a = new JSONArray();
        for (MediaCard c : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("title", c.title); o.put("subtitle", c.subtitle);
                o.put("series", c.series); o.put("genre", c.genre); o.put("tags", new JSONArray(c.tags));
                o.put("artworkUrl", c.artworkUrl == null ? "" : c.artworkUrl);
                o.put("streamUri", c.streamUri == null ? "" : c.streamUri);
                o.put("durationMs", c.durationMs);
                o.put("seriesKey", c.seriesKey == null ? "" : c.seriesKey);
                o.put("seasonNumber", c.seasonNumber); o.put("episodeNumber", c.episodeNumber);
                o.put("collectionKey", c.collectionKey == null ? "" : c.collectionKey);
                o.put("collectionOrder", c.collectionOrder);
                a.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY, a.toString()).apply();
    }

    private static List<String> jsonStrings(JSONArray a) {
        if (a == null) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String value = a.optString(i, "");
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
