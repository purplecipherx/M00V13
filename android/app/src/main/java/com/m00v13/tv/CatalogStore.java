package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class CatalogStore {
    private static final String PREFS = "m00v13_catalog";
    private static final String KEY = "items_v1";
    private final SharedPreferences prefs;

    public CatalogStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public synchronized List<MediaCard> all() {
        ArrayList<MediaCard> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new MediaCard(
                    o.optString("id"), o.optString("title"), o.optString("subtitle"),
                    o.optBoolean("series"), o.optString("genre"), jsonStrings(o.optJSONArray("tags")),
                    emptyToNull(o.optString("artworkUrl")), emptyToNull(o.optString("streamUri")),
                    o.optLong("durationMs")));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public synchronized void upsert(MediaCard card) {
        List<MediaCard> items = new ArrayList<>(all());
        for (Iterator<MediaCard> it = items.iterator(); it.hasNext();) {
            if (it.next().id.equals(card.id)) it.remove();
        }
        items.add(card);
        save(items);
    }

    public MediaCard find(String id) {
        for (MediaCard card : all()) if (card.id.equals(id)) return card;
        return null;
    }

    private void save(List<MediaCard> items) {
        JSONArray a = new JSONArray();
        for (MediaCard c : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("title", c.title); o.put("subtitle", c.subtitle);
                o.put("series", c.series); o.put("genre", c.genre); o.put("tags", new JSONArray(c.tags));
                o.put("artworkUrl", c.artworkUrl == null ? "" : c.artworkUrl);
                o.put("streamUri", c.streamUri == null ? "" : c.streamUri);
                o.put("durationMs", c.durationMs);
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
