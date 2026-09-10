package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileStore {
    private static final String PREFS = "m00v13_profiles";
    private final SharedPreferences prefs;

    public ProfileStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public String activeProfile() { return prefs.getString("active_profile", "Default"); }
    public void setActiveProfile(String name) { prefs.edit().putString("active_profile", name).apply(); }
    private String root() { return "profile." + activeProfile() + "."; }

    public String preferredLanguage() { return prefs.getString(root() + "language", "en"); }
    public void setPreferredLanguage(String language) { prefs.edit().putString(root() + "language", language).apply(); }

    public void saveProgress(String mediaId, long positionMs, long durationMs) {
        String prefix = root() + "media." + mediaId;
        boolean watched = durationMs > 0 && positionMs >= (long)(durationMs * 0.90);
        SharedPreferences.Editor e = prefs.edit()
            .putLong(prefix + ".position", Math.max(0L, positionMs))
            .putLong(prefix + ".duration", Math.max(0L, durationMs))
            .putBoolean(prefix + ".watched", watched)
            .putLong(prefix + ".updated", System.currentTimeMillis());
        if (watched) {
            Set<String> ids = new HashSet<>(prefs.getStringSet(root() + "watched_ids", new HashSet<>()));
            ids.add(mediaId);
            e.putStringSet(root() + "watched_ids", ids);
        }
        e.apply();
    }

    public long progressMs(String mediaId) { return prefs.getLong(root() + "media." + mediaId + ".position", 0L); }
    public long durationMs(String mediaId) { return prefs.getLong(root() + "media." + mediaId + ".duration", 0L); }
    public long lastUpdatedMs(String mediaId) { return prefs.getLong(root() + "media." + mediaId + ".updated", 0L); }
    public boolean isWatched(String mediaId) { return prefs.getBoolean(root() + "media." + mediaId + ".watched", false); }
    public List<String> watchedMediaIds() { return new ArrayList<>(prefs.getStringSet(root() + "watched_ids", new HashSet<>())); }

    public boolean isInWatchlist(String mediaId) { return prefs.getStringSet(root() + "watchlist", new HashSet<>()).contains(mediaId); }
    public void setWatchlist(String mediaId, boolean enabled) {
        Set<String> ids = new HashSet<>(prefs.getStringSet(root() + "watchlist", new HashSet<>()));
        if (enabled) ids.add(mediaId); else ids.remove(mediaId);
        prefs.edit().putStringSet(root() + "watchlist", ids).apply();
    }

    public void recordTagAffinity(List<String> tags, int delta) {
        SharedPreferences.Editor e = prefs.edit();
        for (String tag : tags) {
            String key = root() + "tag." + tag.toLowerCase(java.util.Locale.US);
            e.putInt(key, Math.max(-20, Math.min(20, prefs.getInt(key, 0) + delta)));
        }
        e.apply();
    }

    public int tagAffinity(String tag) { return prefs.getInt(root() + "tag." + tag.toLowerCase(java.util.Locale.US), 0); }
}
