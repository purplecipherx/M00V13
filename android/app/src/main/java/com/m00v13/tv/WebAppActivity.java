package com.m00v13.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the supplied Stitch UI as a packaged local web application.
 * HTML/CSS/JS live in android_asset; only metadata/artwork/network data and native services leave it.
 */
public final class WebAppActivity extends Activity {
    private static final String START = "file:///android_asset/web/index.html";
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "m00v13-web-native");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> sourceSearchInFlight = new HashSet<>();
    private WebView web;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) {}
        try { getWindow().setStatusBarColor(Color.BLACK); } catch (Throwable ignored) {}
        try { getWindow().setNavigationBarColor(Color.BLACK); } catch (Throwable ignored) {}

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "M00V13Native");
        setContentView(web);
        web.loadUrl(START);
        web.requestFocus();
    }

    private MediaCard findByTitle(String title) {
        String wanted = normalize(title);
        if (wanted.isEmpty()) return null;
        MediaCard partial = null;
        for (MediaCard card : new CatalogStore(this).all()) {
            String got = normalize(card.title);
            if (got.equals(wanted)) return card;
            if (partial == null && (got.contains(wanted) || wanted.contains(got))) partial = card;
        }
        return partial;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private void startMedia(MediaCard card, boolean picker) {
        if (card == null) return;
        Intent i = new Intent(this, MediaOpenActivity.class);
        i.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID, card.id);
        if (picker) i.putExtra(MediaOpenActivity.EXTRA_FORCE_PICKER, true);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    private JSONArray sourcesJson(List<SourceOption> sources) {
        JSONArray out = new JSONArray();
        for (SourceOption s : sources) {
            try {
                JSONObject o = new JSONObject();
                o.put("provider", s.provider == null ? "" : s.provider);
                o.put("releaseName", s.releaseName == null ? "" : s.releaseName);
                o.put("uri", s.uri == null ? "" : s.uri);
                o.put("quality", s.quality == null ? "" : s.quality);
                o.put("videoCodec", s.videoCodec == null ? "" : s.videoCodec);
                o.put("audioCodec", s.audioCodec == null ? "" : s.audioCodec);
                o.put("audioLayout", s.audioLayout == null ? "" : s.audioLayout);
                o.put("seeders", s.seeders);
                o.put("size", s.sizeBytes > 0 ? String.format(Locale.US, "%.1f GB", s.sizeBytes / 1073741824.0) : "—");
                if (s.cached != null) o.put("cached", s.cached.booleanValue());
                out.put(o);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private final class Bridge {
        @JavascriptInterface public String getHomeCatalog() {
            try {
                ArrayList<MediaCard> cards = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                DiscoveryStore d = new DiscoveryStore(WebAppActivity.this);
                addUnique(cards, seen, d.get(DiscoveryStore.POPULAR_MOVIES), 6);
                addUnique(cards, seen, d.get(DiscoveryStore.POPULAR_TV), 6);
                if (cards.size() < 6) addUnique(cards, seen, new CatalogStore(WebAppActivity.this).all(), 6);
                JSONArray out = new JSONArray();
                for (MediaCard c : cards) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.id);
                    o.put("title", c.title);
                    o.put("subtitle", c.subtitle == null ? "" : c.subtitle);
                    o.put("series", c.series);
                    o.put("genre", c.genre == null ? "" : c.genre);
                    o.put("artworkUrl", c.artworkUrl == null ? "" : c.artworkUrl);
                    out.put(o);
                }
                return out.toString();
            } catch (Exception e) {
                DebugLog.append(WebAppActivity.this, "WEB", "catalog " + e.getClass().getSimpleName());
                return "[]";
            }
        }

        @JavascriptInterface public String getAppState() {
            JSONObject o = new JSONObject();
            try {
                ProfileStore p = new ProfileStore(WebAppActivity.this);
                AppSettingsStore a = new AppSettingsStore(WebAppActivity.this);
                o.put("profile", p.activeProfile());
                o.put("watchlist", p.watchlistMediaIds().size());
                o.put("downloads", new OfflineQueue(WebAppActivity.this).list().size());
                o.put("debrid", new DebridStore(WebAppActivity.this).isConnected());
                o.put("maxQuality", a.maxQuality());
                o.put("workers", SearchConcurrency.recommended(WebAppActivity.this));
                o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
                o.put("android", "API " + Build.VERSION.SDK_INT);
                o.put("metadata", new MetadataStore(WebAppActivity.this).tmdbToken().isEmpty() ? "CINEMETA" : "TMDB");
            } catch (Exception ignored) {}
            return o.toString();
        }

        @JavascriptInterface public String getProviders() {
            JSONArray out = new JSONArray();
            try {
                AppSettingsStore a = new AppSettingsStore(WebAppActivity.this);
                for (NativeProviderDefinition p : NativeProviderDefinition.loadAll(WebAppActivity.this)) {
                    JSONObject o = new JSONObject();
                    o.put("id", p.id); o.put("name", p.name); o.put("tier", p.tier);
                    o.put("enabled", a.providerEnabled(p.id, p.tier));
                    o.put("mirrors", p.mirrors.size()); o.put("maxResults", p.maxResults);
                    out.put(o);
                }
            } catch (Exception ignored) {}
            return out.toString();
        }

        @JavascriptInterface public String getSources(String title) {
            MediaCard card = findByTitle(title);
            if (card == null) return "[]";
            List<SourceOption> fresh = new SourceStore(WebAppActivity.this).getFresh(card.id);
            if (fresh.isEmpty()) warmSources(card);
            return sourcesJson(fresh).toString();
        }

        @JavascriptInterface public void playMedia(String mediaId, String title) {
            runOnUiThread(() -> {
                MediaCard card = mediaId == null || mediaId.isEmpty() ? null : new CatalogStore(WebAppActivity.this).find(mediaId);
                if (card == null) card = findByTitle(title);
                startMedia(card, false);
            });
        }

        @JavascriptInterface public void playSource(String uri, String title) {
            if (uri == null || uri.trim().isEmpty()) return;
            MediaCard card = findByTitle(title);
            if (!uri.startsWith("magnet:")) {
                runOnUiThread(() -> startPlayer(card, uri));
                return;
            }
            if (!new DebridStore(WebAppActivity.this).isConnected()) return;
            worker.submit(() -> {
                try {
                    String resolved = new RealDebridClient(WebAppActivity.this).resolveMagnet(uri);
                    runOnUiThread(() -> startPlayer(card, resolved));
                } catch (Exception e) {
                    DebugLog.append(WebAppActivity.this, "WEB", "resolve " + e.getClass().getSimpleName());
                }
            });
        }

        @JavascriptInterface public void toggleWatchlist(String mediaId) {
            if (mediaId == null || mediaId.isEmpty()) return;
            ProfileStore p = new ProfileStore(WebAppActivity.this);
            p.setWatchlist(mediaId, !p.isInWatchlist(mediaId));
        }

        @JavascriptInterface public void copyText(String text) {
            if (text == null) return;
            runOnUiThread(() -> {
                ClipboardManager c = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (c != null) c.setPrimaryClip(ClipData.newPlainText("M00V13", text));
            });
        }

        @JavascriptInterface public void openNative(String page) {
            runOnUiThread(() -> {
                Class<?> cls = "debrid".equals(page) ? DebridActivity.class : "profile".equals(page) ? ProfileActivity.class : "diagnostics".equals(page) ? DiagnosticsActivity.class : null;
                if (cls != null) {
                    startActivity(new Intent(WebAppActivity.this, cls));
                    overridePendingTransition(0, 0);
                }
            });
        }

        @JavascriptInterface public void finishOrBack() {
            runOnUiThread(WebAppActivity.this::finish);
        }
    }

    private void warmSources(MediaCard card) {
        synchronized (sourceSearchInFlight) {
            if (!sourceSearchInFlight.add(card.id)) return;
        }
        worker.submit(() -> {
            try {
                String q = card.title + (card.subtitle == null || card.subtitle.isEmpty() ? "" : " " + card.subtitle);
                NativeScraperEngine.SearchResult r = new TieredSearchEngine(WebAppActivity.this).search(q);
                if (!r.sources.isEmpty()) new SourceStore(WebAppActivity.this).put(card.id, r.sources);
            } catch (Exception e) {
                DebugLog.append(WebAppActivity.this, "WEB", "source search " + e.getClass().getSimpleName());
            } finally {
                synchronized (sourceSearchInFlight) { sourceSearchInFlight.remove(card.id); }
            }
        });
    }

    private void startPlayer(MediaCard card, String uri) {
        Intent p = new Intent(this, PlayerActivity.class);
        if (card != null) p.putExtra(PlayerActivity.EXTRA_MEDIA_ID, card.id);
        p.putExtra(PlayerActivity.EXTRA_URI, uri);
        p.putStringArrayListExtra(PlayerActivity.EXTRA_FALLBACK_URIS, new ArrayList<>());
        startActivity(p);
        overridePendingTransition(0, 0);
    }

    private static void addUnique(List<MediaCard> out, Set<String> seen, List<MediaCard> input, int limit) {
        if (input == null) return;
        for (MediaCard c : input) {
            if (c == null || c.id == null || !seen.add(c.id)) continue;
            out.add(c);
            if (out.size() >= limit) return;
        }
    }

    @Override public void onBackPressed() {
        if (web != null) web.evaluateJavascript("if(window.M00&&location.hash&&location.hash!=='#home'){M00.route('home')}else{M00V13Native.finishOrBack()}", null);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        if (web != null) {
            web.removeJavascriptInterface("M00V13Native");
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}