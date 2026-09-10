package com.m00v13.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TmdbClient {
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMAGE = "https://image.tmdb.org/t/p/w500";
    private final String token;

    public static final class Result {
        public final int tmdbId;
        public final boolean series;
        public final String title;
        public final String year;
        public final String overview;
        public final String posterUrl;
        public final String originalLanguage;
        Result(int id, boolean series, String title, String year, String overview, String posterUrl, String language) {
            this.tmdbId = id; this.series = series; this.title = title; this.year = year;
            this.overview = overview; this.posterUrl = posterUrl; this.originalLanguage = language;
        }
        public String mediaId() { return (series ? "tmdb_tv_" : "tmdb_movie_") + tmdbId; }
        public String sourceQuery() { return year.isEmpty() ? title : title + " " + year; }
    }

    public TmdbClient(String token) { this.token = token == null ? "" : token.trim(); }

    public List<Result> searchMulti(String query) throws Exception {
        String url = API + "search/multi?include_adult=false&language=en-US&query=" + Uri.encode(query);
        JSONObject root = get(url);
        JSONArray results = root.optJSONArray("results");
        if (results == null) return Collections.emptyList();
        ArrayList<Result> out = new ArrayList<>();
        for (int i = 0; i < results.length() && out.size() < 20; i++) {
            JSONObject o = results.optJSONObject(i); if (o == null) continue;
            String type = o.optString("media_type");
            boolean series = "tv".equals(type);
            if (!series && !"movie".equals(type)) continue;
            String title = series ? o.optString("name") : o.optString("title");
            String date = series ? o.optString("first_air_date") : o.optString("release_date");
            String year = date.length() >= 4 ? date.substring(0, 4) : "";
            String poster = o.optString("poster_path");
            out.add(new Result(o.optInt("id"), series, title, year, o.optString("overview"),
                poster.isEmpty() ? null : IMAGE + poster, o.optString("original_language")));
        }
        return out;
    }

    public List<MediaCard> loadTvSeason(int seriesId, int seasonNumber, String seriesTitle) throws Exception {
        JSONObject root = get(API + "tv/" + seriesId + "/season/" + seasonNumber + "?language=en-US");
        JSONArray episodes = root.optJSONArray("episodes");
        if (episodes == null) return Collections.emptyList();
        ArrayList<MediaCard> out = new ArrayList<>();
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject e = episodes.optJSONObject(i); if (e == null) continue;
            int ep = e.optInt("episode_number");
            String still = e.optString("still_path");
            out.add(new MediaCard("tmdb_tv_" + seriesId + "_s" + seasonNumber + "e" + ep,
                seriesTitle, "S" + seasonNumber + "E" + ep + " • " + e.optString("name"), true, "",
                Collections.emptyList(), still.isEmpty() ? null : IMAGE + still, null,
                Math.max(0L, e.optLong("runtime")) * 60000L, "tmdb_tv_" + seriesId,
                seasonNumber, ep, null, 0));
        }
        return out;
    }

    public MediaCard toCard(Result r) {
        return new MediaCard(r.mediaId(), r.title, r.year, r.series, "",
            r.originalLanguage.isEmpty() ? Collections.emptyList() : Collections.singletonList(r.originalLanguage),
            r.posterUrl, null, 0L, r.series ? r.mediaId() : null, 0, 0, null, 0);
    }

    private JSONObject get(String url) throws Exception {
        if (token.isEmpty()) throw new IllegalStateException("TMDB token not configured");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(7000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        InputStream raw = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = raw == null ? "" : read(raw, 2 * 1024 * 1024);
        c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("TMDB HTTP " + code);
        return new JSONObject(body);
    }

    private static String read(InputStream raw, int limit) throws Exception {
        try (InputStream in = new BufferedInputStream(raw); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int total = 0;
            while (total < limit) { int n = in.read(b, 0, Math.min(b.length, limit - total)); if (n < 0) break; out.write(b,0,n); total += n; }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
