package com.m00v13.tv;

import android.content.Context;

public final class MetadataStore {
    private final Context context;
    public MetadataStore(Context context) { this.context = context.getApplicationContext(); }
    public String tmdbToken() { return SecretStore.get(context, "tmdb.read_token"); }
    public boolean isConfigured() { String t = tmdbToken(); return t != null && !t.trim().isEmpty(); }
    public void saveTmdbToken(String token) throws Exception {
        String clean = token == null ? "" : token.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("TMDB read token is empty");
        SecretStore.put(context, "tmdb.read_token", clean);
    }
    public void clear() { SecretStore.remove(context, "tmdb.read_token"); }
}
