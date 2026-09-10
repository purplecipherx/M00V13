package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;

public final class DebridStore {
    private static final String PREFS = "m00v13_debrid";
    private final Context context;
    private final SharedPreferences prefs;

    public DebridStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isConnected() {
        return clientId() != null && clientSecret() != null && refreshToken() != null;
    }

    public String clientId() { return emptyToNull(prefs.getString("rd.client_id", null)); }
    public String clientSecret() { return SecretStore.get(context, "rd.client_secret"); }
    public String accessToken() { return SecretStore.get(context, "rd.access_token"); }
    public String refreshToken() { return SecretStore.get(context, "rd.refresh_token"); }
    public long accessExpiresAtMs() { return prefs.getLong("rd.expires_at", 0L); }

    public void saveCredentials(String clientId, String clientSecret) throws Exception {
        prefs.edit().putString("rd.client_id", clientId).apply();
        SecretStore.put(context, "rd.client_secret", clientSecret);
    }

    public void saveTokens(String accessToken, String refreshToken, long expiresInSeconds) throws Exception {
        SecretStore.put(context, "rd.access_token", accessToken);
        SecretStore.put(context, "rd.refresh_token", refreshToken);
        long expiresAt = System.currentTimeMillis() + Math.max(60L, expiresInSeconds) * 1000L;
        prefs.edit().putLong("rd.expires_at", expiresAt).apply();
    }

    public void disconnect() {
        prefs.edit().remove("rd.client_id").remove("rd.expires_at").apply();
        SecretStore.remove(context, "rd.client_secret");
        SecretStore.remove(context, "rd.access_token");
        SecretStore.remove(context, "rd.refresh_token");
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
