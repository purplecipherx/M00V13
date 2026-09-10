package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProfileStore {
    private static final String PREFS = "m00v13_profiles";
    private static final String DEFAULT_PROFILE = "Default";
    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureProfile(DEFAULT_PROFILE);
    }

    public String activeProfile() { return prefs.getString("active_profile", DEFAULT_PROFILE); }

    public void setActiveProfile(String name) {
        String clean = cleanName(name);
        if (clean.isEmpty()) return;
        ensureProfile(clean);
        prefs.edit().putString("active_profile", clean).apply();
    }

    public List<String> profiles() {
        Set<String> names = new HashSet<>(prefs.getStringSet("profile_names", new HashSet<>()));
        names.add(DEFAULT_PROFILE);
        ArrayList<String> out = new ArrayList<>(names);
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        if (out.remove(DEFAULT_PROFILE)) out.add(0, DEFAULT_PROFILE);
        return out;
    }

    public boolean createProfile(String name) {
        String clean = cleanName(name);
        if (clean.isEmpty()) return false;
        Set<String> names = new HashSet<>(prefs.getStringSet("profile_names", new HashSet<>()));
        boolean added = names.add(clean);
        if (added) prefs.edit().putStringSet("profile_names", names).apply();
        return added;
    }

    public void ensureProfile(String name) {
        String clean = cleanName(name);
        if (clean.isEmpty()) return;
        Set<String> names = new HashSet<>(prefs.getStringSet("profile_names", new HashSet<>()));
        if (names.add(clean)) prefs.edit().putStringSet("profile_names", names).apply();
    }

    private String root() { return "profile." + activeProfile() + "."; }

    public String preferredLanguage() { return prefs.getString(root() + "language", "en"); }
    public void setPreferredLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return;
        prefs.edit().putString(root() + "language", language.trim().toLowerCase(java.util.Locale.US)).apply();
    }

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

    private static String cleanName(String raw) {
        if (raw == null) return "";
        String clean = raw.trim().replaceAll("[\\r\\n\\t]", " ");
        if (clean.length() > 24) clean = clean.substring(0, 24).trim();
        return clean;
    }
}
