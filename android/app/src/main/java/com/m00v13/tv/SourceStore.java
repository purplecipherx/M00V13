package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SourceStore {
    private static final String PREFS = "m00v13_sources";
    private static final long DEFAULT_TTL_MS = 30L * 60L * 1000L;
    private final SharedPreferences prefs;

    public SourceStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public void put(String mediaId, List<SourceOption> sources) {
        JSONArray a = new JSONArray();
        for (SourceOption s : sources) {
            try {
                JSONObject o = new JSONObject();
                o.put("provider", s.provider); o.put("uri", s.uri); o.put("quality", s.quality);
                o.put("videoCodec", s.videoCodec); o.put("hdr", s.hdr); o.put("audioCodec", s.audioCodec);
                o.put("audioLayout", s.audioLayout); o.put("audioLanguages", new JSONArray(s.audioLanguages));
                o.put("subtitleLanguages", new JSONArray(s.subtitleLanguages)); o.put("sizeBytes", s.sizeBytes);
                o.put("seeders", s.seeders); if (s.cached != null) o.put("cached", s.cached.booleanValue());
                o.put("score", s.score); a.put(o);
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString("sources." + mediaId, a.toString())
            .putLong("updated." + mediaId, System.currentTimeMillis()).apply();
    }

    public List<SourceOption> getFresh(String mediaId) { return getFresh(mediaId, DEFAULT_TTL_MS); }

    public List<SourceOption> getFresh(String mediaId, long ttlMs) {
        long age = System.currentTimeMillis() - prefs.getLong("updated." + mediaId, 0L);
        if (age < 0 || age > ttlMs) return Collections.emptyList();
        ArrayList<SourceOption> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("sources." + mediaId, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Boolean cached = o.has("cached") ? Boolean.valueOf(o.optBoolean("cached")) : null;
                out.add(new SourceOption(o.optString("provider"), o.optString("uri"), o.optString("quality"),
                    o.optString("videoCodec"), o.optString("hdr"), o.optString("audioCodec"), o.optString("audioLayout"),
                    strings(o.optJSONArray("audioLanguages")), strings(o.optJSONArray("subtitleLanguages")),
                    o.optLong("sizeBytes"), o.optInt("seeders", -1), cached, o.optInt("score")));
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public void clear(String mediaId) {
        prefs.edit().remove("sources." + mediaId).remove("updated." + mediaId).apply();
    }

    private static List<String> strings(JSONArray a) {
        if (a == null) return Collections.emptyList();
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, ""); if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
