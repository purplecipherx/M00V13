package com.m00v13.tv;

import android.content.Context;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tier 1 -> Tier 2 -> Tier 3 waterfall. Providers run concurrently inside each tier.
 * We stop early only when the accumulated result set is actually useful, and when
 * Real-Debrid is connected we preferentially continue until cached sources are found.
 */
public final class TieredSearchEngine {
    private static final int MIN_USABLE = 5;
    private static final int MIN_CACHED = 2;
    private static final int MIN_WITHOUT_DEBRID = 8;
    private static final int MAX_CACHE_PROBES_PER_PASS = 8;
    private static final int MAX_RETURNED = 40;

    private final Context context;

    public TieredSearchEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public NativeScraperEngine.SearchResult search(String query) {
        ArrayList<SourceOption> collected = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        boolean debridConnected = new DebridStore(context).isConnected();

        for (int tier = 1; tier <= 3; tier++) {
            NativeScraperEngine.SearchResult pass = new NativeScraperEngine(context, tier).search(query);
            collected.addAll(pass.sources);
            errors.addAll(pass.providerErrors);
            collected = dedupe(collected);
            collected = rank(collected);

            if (debridConnected && !collected.isEmpty()) {
                try {
                    collected = new ArrayList<>(new RealDebridClient(context).probeCache(collected, MAX_CACHE_PROBES_PER_PASS));
                } catch (Exception e) {
                    errors.add("debrid cache probe: " + shortMessage(e));
                }
            }

            if (satisfied(collected, debridConnected)) break;
        }

        collected = rank(dedupe(collected));
        if (collected.size() > MAX_RETURNED) collected = new ArrayList<>(collected.subList(0, MAX_RETURNED));
        return new NativeScraperEngine.SearchResult(Collections.unmodifiableList(collected), Collections.unmodifiableList(errors));
    }

    private static boolean satisfied(List<SourceOption> sources, boolean debridConnected) {
        if (sources.size() < MIN_USABLE) return false;
        if (!debridConnected) return sources.size() >= MIN_WITHOUT_DEBRID;
        int cached = 0;
        for (SourceOption source : sources) if (Boolean.TRUE.equals(source.cached)) cached++;
        return cached >= MIN_CACHED;
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
        out.sort(Comparator
            .comparing((SourceOption s) -> Boolean.TRUE.equals(s.cached)).reversed()
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
