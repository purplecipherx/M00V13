package com.m00v13.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Tier 1 -> Tier 2 -> Tier 3 waterfall optimized for fast first usable result. */
public final class TieredSearchEngine {
    private static final int MIN_USABLE = 5;
    private static final int MIN_WITHOUT_DEBRID = 8;
    private static final int MAX_CACHE_PROBES = 1;
    private static final int FAST_CACHE_PROBE_BUDGET_MS = 800;
    private static final int MAX_RETURNED = 40;
    private final Context context;

    public TieredSearchEngine(Context context) { this.context = context.getApplicationContext(); }

    public NativeScraperEngine.SearchResult search(String query) {
        ArrayList<SourceOption> collected = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        boolean debridConnected = new DebridStore(context).isConnected();
        AppSettingsStore settings = new AppSettingsStore(context);

        for (int tier = 1; tier <= 3; tier++) {
            if (!tierEnabled(settings, tier)) continue;
            long started = System.currentTimeMillis();
            NativeScraperEngine.SearchResult pass = new NativeScraperEngine(context, tier).search(query);
            DebugLog.append(context, "SEARCH", "tier=" + tier + " query='" + query + "' sources=" + pass.sources.size() + " ms=" + (System.currentTimeMillis()-started));
            collected.addAll(pass.sources);
            errors.addAll(pass.providerErrors);
            collected = rank(filterBySettings(dedupe(collected), settings));

            int needed = debridConnected ? MIN_USABLE : MIN_WITHOUT_DEBRID;
            if (collected.size() >= needed) break;
        }

        collected = rank(filterBySettings(dedupe(collected), settings));

        if (debridConnected && settings.verifyDebridCache() && !collected.isEmpty()) {
            // Cache probing is useful for smart one-click, but it must never hold the source
            // picker hostage behind a slow Real-Debrid request. Give the best-candidate probe
            // a very small latency budget; on timeout we return the ranked sources immediately.
            final ArrayList<SourceOption> probeInput = new ArrayList<>(collected);
            ExecutorService probeExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "m00v13-rd-fast-probe");
                t.setDaemon(true);
                return t;
            });
            Future<List<SourceOption>> probe = probeExecutor.submit(() -> new RealDebridClient(context).probeCache(probeInput, MAX_CACHE_PROBES));
            try {
                collected = new ArrayList<>(probe.get(FAST_CACHE_PROBE_BUDGET_MS, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                probe.cancel(true);
                DebugLog.append(context, "SEARCH", "debrid cache probe cutoff after " + FAST_CACHE_PROBE_BUDGET_MS + "ms");
            } catch (Exception e) {
                errors.add("debrid cache probe: " + shortMessage(e));
            } finally {
                probeExecutor.shutdownNow();
            }
        }

        collected = rank(filterBySettings(dedupe(collected), settings));
        if (collected.size() > MAX_RETURNED) collected = new ArrayList<>(collected.subList(0, MAX_RETURNED));
        return new NativeScraperEngine.SearchResult(Collections.unmodifiableList(collected), Collections.unmodifiableList(errors));
    }

    private static boolean tierEnabled(AppSettingsStore s, int tier) {
        return tier == 1 ? s.providerTier1() : tier == 2 ? s.providerTier2() : s.providerTier3();
    }

    private static ArrayList<SourceOption> filterBySettings(List<SourceOption> input, AppSettingsStore settings) {
        ArrayList<SourceOption> out = new ArrayList<>();
        int max = qualityRank(settings.maxQuality());
        for (SourceOption s : input) {
            int q = qualityRank(s.quality);
            if (q > max && q > 0) continue;
            out.add(s);
        }
        return out;
    }

    private static int qualityRank(String q) {
        if (q == null) return 0;
        String v = q.toLowerCase(Locale.US);
        if (v.contains("2160") || v.contains("4k")) return 4;
        if (v.contains("1080")) return 3;
        if (v.contains("720")) return 2;
        if (v.contains("480") || v.contains("sd")) return 1;
        return 0;
    }

    private static ArrayList<SourceOption> dedupe(List<SourceOption> input) {
        Map<String, SourceOption> unique = new LinkedHashMap<>();
        for (SourceOption source : input) {
            if (source == null || source.uri == null || source.uri.trim().isEmpty()) continue;
            String key = dedupeKey(source.uri);
            SourceOption current = unique.get(key);
            if (current == null || source.score > current.score) unique.put(key, source);
        }
        return new ArrayList<>(unique.values());
    }

    private static ArrayList<SourceOption> rank(List<SourceOption> input) {
        ArrayList<SourceOption> out = new ArrayList<>(input);
        out.sort(Comparator.comparing((SourceOption s) -> Boolean.TRUE.equals(s.cached)).reversed()
            .thenComparing(Comparator.comparingInt((SourceOption s) -> s.score).reversed())
            .thenComparing(Comparator.comparingInt((SourceOption s) -> s.seeders).reversed()));
        return out;
    }

    private static String dedupeKey(String uri) {
        String lower = uri.toLowerCase(Locale.US);
        int start = lower.indexOf("btih:");
        if (start >= 0) {
            int end = lower.indexOf('&', start);
            return end < 0 ? lower.substring(start) : lower.substring(start, end);
        }
        return lower;
    }

    private static String shortMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        String m = x.getMessage();
        return m == null || m.trim().isEmpty() ? x.getClass().getSimpleName() : m;
    }
}
