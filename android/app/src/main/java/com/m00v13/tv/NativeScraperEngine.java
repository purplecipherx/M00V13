package com.m00v13.tv;

import android.net.Uri;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NativeScraperEngine {
    private static final int CONNECT_TIMEOUT_MS = 4500;
    private static final int READ_TIMEOUT_MS = 5500;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TOTAL_RESULTS = 30;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 Chrome/126 Safari/537.36 M00V13/0.1";

    public static final class SearchResult {
        public final List<SourceOption> sources;
        public final List<String> providerErrors;

        SearchResult(List<SourceOption> sources, List<String> providerErrors) {
            this.sources = sources;
            this.providerErrors = providerErrors;
        }
    }

    private static final class Candidate {
        final NativeProviderDefinition provider;
        final String baseUrl;
        final String title;
        final String detailsUrl;
        final int seeders;
        final long sizeBytes;

        Candidate(NativeProviderDefinition provider, String baseUrl, String title, String detailsUrl,
                  int seeders, long sizeBytes) {
            this.provider = provider;
            this.baseUrl = baseUrl;
            this.title = title;
            this.detailsUrl = detailsUrl;
            this.seeders = seeders;
            this.sizeBytes = sizeBytes;
        }
    }

    public SearchResult search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) return new SearchResult(Collections.emptyList(), Collections.emptyList());

        List<NativeProviderDefinition> providers = NativeProviderDefinition.builtIns();
        ExecutorService providerPool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, providers.size())));
        ArrayList<Future<List<Candidate>>> futures = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        for (NativeProviderDefinition provider : providers) {
            futures.add(providerPool.submit(new Callable<List<Candidate>>() {
                @Override public List<Candidate> call() throws Exception { return searchProvider(provider, query); }
            }));
        }

        ArrayList<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try { candidates.addAll(futures.get(i).get()); }
            catch (Exception e) { errors.add(providers.get(i).name + ": " + shortMessage(e)); }
        }
        providerPool.shutdownNow();

        candidates.sort(Comparator.comparingInt((Candidate c) -> c.seeders).reversed());
        if (candidates.size() > MAX_TOTAL_RESULTS) candidates = new ArrayList<>(candidates.subList(0, MAX_TOTAL_RESULTS));
        if (candidates.isEmpty()) return new SearchResult(Collections.emptyList(), Collections.unmodifiableList(errors));

        ExecutorService detailPool = Executors.newFixedThreadPool(Math.max(1, Math.min(5, candidates.size())));
        ArrayList<Future<SourceOption>> detailFutures = new ArrayList<>();
        for (Candidate candidate : candidates) {
            detailFutures.add(detailPool.submit(new Callable<SourceOption>() {
                @Override public SourceOption call() throws Exception { return resolveCandidate(candidate); }
            }));
        }

        ArrayList<SourceOption> sources = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < detailFutures.size(); i++) {
            try {
                SourceOption source = detailFutures.get(i).get();
                if (source != null && source.uri != null && seen.add(dedupeKey(source.uri))) sources.add(source);
            } catch (Exception e) {
                Candidate c = candidates.get(i);
                errors.add(c.provider.name + " detail: " + shortMessage(e));
            }
        }
        detailPool.shutdownNow();

        sources.sort(Comparator.comparingInt((SourceOption s) -> s.score).reversed()
            .thenComparing(Comparator.comparingInt((SourceOption s) -> s.seeders).reversed()));
        return new SearchResult(Collections.unmodifiableList(sources), Collections.unmodifiableList(errors));
    }

    private List<Candidate> searchProvider(NativeProviderDefinition provider, String query) throws Exception {
        Exception last = null;
        for (String mirror : provider.mirrors) {
            try {
                String base = ensureTrailingSlash(mirror);
                String path = provider.searchPath.replace("{query}", Uri.encode(query));
                String url = new URL(new URL(base), path).toString();
                String html = fetch(url);
                Document doc = Jsoup.parse(html, base);
                Elements rows = doc.select(provider.rowSelector);
                ArrayList<Candidate> out = new ArrayList<>();
                for (Element row : rows) {
                    Element titleElement = row.selectFirst(provider.titleSelector);
                    if (titleElement == null) continue;
                    String title = titleElement.text().trim();
                    String href = titleElement.attr(provider.detailsAttribute);
                    if (title.isEmpty() || href.isEmpty()) continue;
                    String detailsUrl = new URL(new URL(base), href).toString();
                    int seeders = parseInt(textOf(row, provider.seedersSelector), -1);
                    long sizeBytes = ReleaseMetadataParser.parseSizeBytes(textOf(row, provider.sizeSelector));
                    out.add(new Candidate(provider, base, title, detailsUrl, seeders, sizeBytes));
                    if (out.size() >= provider.maxResults) break;
                }
                if (!out.isEmpty()) return out;
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        return Collections.emptyList();
    }

    private SourceOption resolveCandidate(Candidate c) throws Exception {
        String html = fetch(c.detailsUrl);
        Document doc = Jsoup.parse(html, c.baseUrl);
        Element magnet = doc.selectFirst(c.provider.magnetSelector);
        if (magnet == null) return null;
        String uri = magnet.attr("href");
        if (uri == null || !uri.startsWith("magnet:")) return null;

        ReleaseMetadataParser.Parsed meta = ReleaseMetadataParser.parse(c.title);
        int score = score(meta, c.seeders, c.sizeBytes);
        return new SourceOption(c.provider.name, uri, meta.quality, meta.videoCodec, meta.hdr,
            meta.audioCodec, meta.audioLayout, meta.audioLanguages, meta.subtitleLanguages,
            c.sizeBytes, c.seeders, null, score);
    }

    private String fetch(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
        connection.setRequestProperty("Cache-Control", "no-cache");
        int code = connection.getResponseCode();
        InputStream raw = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = raw == null ? "" : readLimited(raw, MAX_BODY_BYTES);
        connection.disconnect();

        String lower = body.toLowerCase(Locale.US);
        if (code == 403 || code == 503 || lower.contains("cf-chl-") || lower.contains("cloudflare ray id") || lower.contains("just a moment...")) {
            throw new IOException("challenge page detected; provider skipped");
        }
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        return body;
    }

    private static String readLimited(InputStream raw, int maxBytes) throws IOException {
        try (InputStream in = new BufferedInputStream(raw); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            while (total < maxBytes) {
                int read = in.read(buffer, 0, Math.min(buffer.length, maxBytes - total));
                if (read < 0) break;
                out.write(buffer, 0, read);
                total += read;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String textOf(Element row, String selector) {
        if (selector == null || selector.isEmpty()) return "";
        Element e = row.selectFirst(selector);
        return e == null ? "" : e.text();
    }

    private static int parseInt(String text, int fallback) {
        if (text == null) return fallback;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return fallback;
        try { return Integer.parseInt(digits); } catch (NumberFormatException e) { return fallback; }
    }

    private static int score(ReleaseMetadataParser.Parsed meta, int seeders, long sizeBytes) {
        int score = 0;
        if ("2160p".equals(meta.quality)) score += 500;
        else if ("1080p".equals(meta.quality)) score += 400;
        else if ("720p".equals(meta.quality)) score += 300;
        else if ("SD".equals(meta.quality)) score += 150;
        if ("HEVC".equals(meta.videoCodec) || "AV1".equals(meta.videoCodec)) score += 35;
        if (!meta.hdr.isEmpty()) score += 20;
        if (!meta.audioLanguages.isEmpty()) score += 20;
        score += Math.max(0, Math.min(250, seeders));
        if (sizeBytes > 0) score += 5;
        return score;
    }

    private static String ensureTrailingSlash(String value) { return value.endsWith("/") ? value : value + "/"; }

    private static String shortMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        String m = x.getMessage();
        return m == null || m.trim().isEmpty() ? x.getClass().getSimpleName() : m;
    }

    private static String dedupeKey(String uri) {
        String lower = uri.toLowerCase(Locale.US);
        int btih = lower.indexOf("btih:");
        if (btih >= 0) {
            int end = lower.indexOf('&', btih);
            return end < 0 ? lower.substring(btih) : lower.substring(btih, end);
        }
        return lower;
    }
}
