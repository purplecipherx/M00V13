package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProfileStore {
    private static final String PREFS = "m00v13_profiles";
    private final SharedPreferences prefs;

    public ProfileStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String activeProfile() { return prefs.getString("active_profile", "Default"); }
    public void setActiveProfile(String name) { prefs.edit().putString("active_profile", name).apply(); }

    public String preferredLanguage() {
        return prefs.getString("profile." + activeProfile() + ".language", "en");
    }

    public void setPreferredLanguage(String language) {
        prefs.edit().putString("profile." + activeProfile() + ".language", language).apply();
    }

    public void saveProgress(String mediaId, long positionMs, long durationMs) {
        String prefix = "profile." + activeProfile() + ".media." + mediaId;
        boolean watched = durationMs > 0 && positionMs >= (long)(durationMs * 0.90);
        prefs.edit()
            .putLong(prefix + ".position", positionMs)
            .putLong(prefix + ".duration", durationMs)
            .putBoolean(prefix + ".watched", watched)
            .putLong(prefix + ".updated", System.currentTimeMillis())
            .apply();
    }

    public boolean isWatched(String mediaId) {
        return prefs.getBoolean("profile." + activeProfile() + ".media." + mediaId + ".watched", false);
    }
}
