package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny persistent metadata cache for hover descriptions. */
public final class DescriptionCacheStore {
    private static final String PREFS = "m00v13_descriptions";
    private static final long TTL_MS = 30L * 24L * 60L * 60L * 1000L;
    private final SharedPreferences prefs;

    public DescriptionCacheStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String get(String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) return null;
        long saved = prefs.getLong("t." + mediaId, 0L);
        if (saved == 0L || System.currentTimeMillis() - saved > TTL_MS) return null;
        String value = prefs.getString("d." + mediaId, null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public void put(String mediaId, String description) {
        if (mediaId == null || mediaId.isEmpty() || description == null || description.trim().isEmpty()) return;
        prefs.edit().putString("d." + mediaId, description).putLong("t." + mediaId, System.currentTimeMillis()).apply();
    }
}
