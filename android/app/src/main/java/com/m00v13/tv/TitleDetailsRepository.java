package com.m00v13.tv;

import android.content.Context;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real metadata loader for the title-details UI. No display values are fabricated. */
public final class TitleDetailsRepository {
    private static final String CINEMETA = "https://v3-cinemeta.strem.io/";
    private static final String TMDB = "https://api.themoviedb.org/3/";
    private static final String TMDB_POSTER = "https://image.tmdb.org/t/p/w500";
    private static final String TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280";
    private final Context context;

    public TitleDetailsRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    public TitleDetailsData load(MediaCard media) throws Exception {
        if (media == null || media.id == null) return TitleDetailsData.fallback(media);
        if (media.id.startsWith("cinemeta_movie_") || media.id.startsWith("cinemeta_series_")) return loadCinemeta(media);
        if (media.id.startsWith("tmdb_movie_") || media.id.startsWith("tmdb_tv_")) return loadTmdb(media);
        return TitleDetailsData.fallback(media);
    }

    public List<TitleDetailsData.Episode> loadSeason(MediaCard media, int seasonNumber) throws Exception {
        if (media == null || !media.series || seasonNumber <= 0) return Collections.emptyList();
        if (media.id.startsWith("cinemeta_series_")) {
            TitleDetailsData d = loadCinemeta(media);
            List<TitleDetailsData.Episode> episodes = d.episodesBySeason.get(seasonNumber);
            return episodes == null ? Collections.emptyList() : episodes;
        }
        if (media.id.startsWith("tmdb_tv_")) return loadTmdbSeason(media, seasonNumber);
        return Collections.emptyList();
    }

    private TitleDetailsData loadCinemeta(MediaCard media) throws Exception {
        String prefix = media.series ? "cinemeta_series_" : "cinemeta_movie_";
        String imdb = media.id.substring(prefix.length());
        JSONObject root = getJson(CINEMETA + "meta/" + (media.series ? "series" : "movie") + "/" + Uri.encode(imdb) + ".json", null);
        JSONObject m = root.optJSONObject("meta");
        if (m == null) return TitleDetailsData.fallback(media);

        ArrayList<String> genres = strings(m.optJSONArray("genres"));
        ArrayList<String> cast = stringsFlexible(m.opt("cast"));
        ArrayList<String> directors = stringsFlexible(m.opt("director"));
        ArrayList<String> writers = stringsFlexible(m.opt("writer"));
        String trailer = trailerFromCinemeta(m);
        String release = first(m.optString("releaseInfo", ""), m.optString("year", ""), media.subtitle);
        String runtime = cleanRuntime(m.opt("runtime"));
        String rating = m.optString("imdbRating", "");
        String certification = first(m.optString("contentRating", ""), m.optString("certificate", ""));
        String bg = first(m.optString("background", ""), media.artworkUrl);
        String description = first(m.optString("description", ""), m.optString("overview", ""));

        ArrayList<Integer> seasons = new ArrayList<>();
        LinkedHashMap<Integer, List<TitleDetailsData.Episode>> bySeason = new LinkedHashMap<>();
        if (media.series) {
            JSONArray videos = m.optJSONArray("videos");
            if (videos != null) {
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.optJSONObject(i); if (v == null) continue;
                    int season = v.optInt("season", 0), episode = v.optInt("episode", 0);
                    if (season <= 0 || episode <= 0) continue;
                    String epTitle = first(v.optString("title", ""), "Episode " + episode);
                    String thumb = first(v.optString("thumbnail", ""), media.artworkUrl);
                    long duration = runtimeMs(v.opt("runtime"));
                    String subtitle = "S" + season + "E" + episode + " • " + epTitle;
                    String id = "cinemeta_series_" + imdb + "_s" + season + "e" + episode;
                    MediaCard card = new MediaCard(id, media.title, subtitle, true, media.genre, media.tags,
                        thumb, null, duration, imdb, season, episode, null, 0);
                    TitleDetailsData.Episode item = new TitleDetailsData.Episode(card, epTitle,
                        first(v.optString("overview", ""), v.optString("description", "")), thumb, duration);
                    List<TitleDetailsData.Episode> list = bySeason.get(season);
                    if (list == null) { list = new ArrayList<>(); bySeason.put(season, list); seasons.add(season); }
                    list.add(item);
                }
            }
            Collections.sort(seasons);
            for (List<TitleDetailsData.Episode> list : bySeason.values()) {
                list.sort(Comparator.comparingInt(e -> e.card.episodeNumber));
            }
        }
        return new TitleDetailsData(media, bg, description, release, runtime, rating, certification,
            trailer, genres, cast, directors, writers, seasons, bySeason);
    }

    private TitleDetailsData loadTmdb(MediaCard media) throws Exception {
        String token = new MetadataStore(context).tmdbToken();
        if (token == null || token.trim().isEmpty()) return TitleDetailsData.fallback(media);
        boolean tv = media.id.startsWith("tmdb_tv_");
        int id = parseTmdbId(media.id, tv);
        if (id <= 0) return TitleDetailsData.fallback(media);
        String append = tv ? "credits,videos,content_ratings" : "credits,videos,release_dates";
        JSONObject m = getJson(TMDB + (tv ? "tv/" : "movie/") + id + "?language=en-US&append_to_response=" + append, token);

        ArrayList<String> genres = new ArrayList<>();
        JSONArray genreArray = m.optJSONArray("genres");
        if (genreArray != null) for (int i = 0; i < genreArray.length(); i++) {
            JSONObject g = genreArray.optJSONObject(i); if (g != null) addUnique(genres, g.optString("name", ""));
        }
        ArrayList<String> cast = new ArrayList<>(), directors = new ArrayList<>(), writers = new ArrayList<>();
        JSONObject credits = m.optJSONObject("credits");
        if (credits != null) {
            JSONArray ca = credits.optJSONArray("cast");
            if (ca != null) for (int i = 0; i < ca.length() && cast.size() < 8; i++) {
                JSONObject p = ca.optJSONObject(i); if (p != null) addUnique(cast, p.optString("name", ""));
            }
            JSONArray crew = credits.optJSONArray("crew");
            if (crew != null) for (int i = 0; i < crew.length(); i++) {
                JSONObject p = crew.optJSONObject(i); if (p == null) continue;
                String job = p.optString("job", ""), name = p.optString("name", "");
                if ("Director".equalsIgnoreCase(job)) addUnique(directors, name);
                if (job.toLowerCase(Locale.US).contains("writer") || "Screenplay".equalsIgnoreCase(job)) addUnique(writers, name);
            }
        }

        ArrayList<Integer> seasons = new ArrayList<>();
        if (tv) {
            JSONArray ss = m.optJSONArray("seasons");
            if (ss != null) for (int i = 0; i < ss.length(); i++) {
                JSONObject s = ss.optJSONObject(i); if (s == null) continue;
                int n = s.optInt("season_number", 0); if (n > 0 && s.optInt("episode_count", 0) > 0) seasons.add(n);
            }
        }
        String runtime = "";
        if (tv) {
            JSONArray runs = m.optJSONArray("episode_run_time");
            if (runs != null && runs.length() > 0) runtime = runs.optInt(0) > 0 ? runs.optInt(0) + " min" : "";
        } else if (m.optInt("runtime", 0) > 0) runtime = m.optInt("runtime") + " min";

        String trailer = trailerFromTmdb(m.optJSONObject("videos"));
        String certification = tv ? tvCertification(m.optJSONObject("content_ratings")) : movieCertification(m.optJSONObject("release_dates"));
        double vote = m.optDouble("vote_average", 0.0);
        String rating = vote > 0 ? String.format(Locale.US, "%.1f", vote) : "";
        String date = tv ? m.optString("first_air_date", "") : m.optString("release_date", "");
        String release = date.length() >= 4 ? date.substring(0,4) : media.subtitle;
        String bg = pathUrl(m.optString("backdrop_path", ""), TMDB_BACKDROP);
        if (bg.isEmpty()) bg = first(pathUrl(m.optString("poster_path", ""), TMDB_POSTER), media.artworkUrl);
        return new TitleDetailsData(media, bg, m.optString("overview", ""), release, runtime, rating,
            certification, trailer, genres, cast, directors, writers, seasons, Collections.emptyMap());
    }

    private List<TitleDetailsData.Episode> loadTmdbSeason(MediaCard media, int season) throws Exception {
        String token = new MetadataStore(context).tmdbToken();
        if (token == null || token.trim().isEmpty()) return Collections.emptyList();
        int id = parseTmdbId(media.id, true); if (id <= 0) return Collections.emptyList();
        JSONObject root = getJson(TMDB + "tv/" + id + "/season/" + season + "?language=en-US", token);
        JSONArray eps = root.optJSONArray("episodes"); if (eps == null) return Collections.emptyList();
        ArrayList<TitleDetailsData.Episode> out = new ArrayList<>();
        for (int i = 0; i < eps.length(); i++) {
            JSONObject e = eps.optJSONObject(i); if (e == null) continue;
            int ep = e.optInt("episode_number", 0); if (ep <= 0) continue;
            String name = first(e.optString("name", ""), "Episode " + ep);
            String thumb = pathUrl(e.optString("still_path", ""), TMDB_POSTER);
            long duration = Math.max(0, e.optLong("runtime", 0L)) * 60000L;
            String subtitle = "S" + season + "E" + ep + " • " + name;
            MediaCard card = new MediaCard("tmdb_tv_" + id + "_s" + season + "e" + ep,
                media.title, subtitle, true, media.genre, media.tags, thumb, null, duration,
                media.id, season, ep, null, 0);
            out.add(new TitleDetailsData.Episode(card, name, e.optString("overview", ""), thumb, duration));
        }
        return out;
    }

    private JSONObject getJson(String url, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(7500); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "M00V13/0.1 AndroidTV");
        if (bearer != null && !bearer.trim().isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer.trim());
        int code = c.getResponseCode(); InputStream raw = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = raw == null ? "" : read(raw, 3 * 1024 * 1024); c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("metadata HTTP " + code);
        return new JSONObject(body);
    }

    private static String read(InputStream raw, int max) throws Exception {
        try (InputStream in = new BufferedInputStream(raw); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int total = 0;
            while (total < max) { int n = in.read(b, 0, Math.min(b.length, max-total)); if (n < 0) break; out.write(b,0,n); total += n; }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String trailerFromCinemeta(JSONObject m) {
        JSONArray trailers = m.optJSONArray("trailers");
        if (trailers != null) for (int i = 0; i < trailers.length(); i++) {
            JSONObject t = trailers.optJSONObject(i); if (t == null) continue;
            String source = first(t.optString("source", ""), t.optString("ytId", ""), t.optString("url", ""));
            String u = youtube(source); if (!u.isEmpty()) return u;
        }
        JSONArray streams = m.optJSONArray("trailerStreams");
        if (streams != null) for (int i = 0; i < streams.length(); i++) {
            JSONObject t = streams.optJSONObject(i); if (t == null) continue;
            String source = first(t.optString("ytId", ""), t.optString("source", ""), t.optString("url", ""));
            String u = youtube(source); if (!u.isEmpty()) return u;
        }
        return youtube(m.optString("trailer", ""));
    }

    private static String trailerFromTmdb(JSONObject videos) {
        if (videos == null) return ""; JSONArray a = videos.optJSONArray("results"); if (a == null) return "";
        String fallback = "";
        for (int i = 0; i < a.length(); i++) {
            JSONObject v = a.optJSONObject(i); if (v == null || !"YouTube".equalsIgnoreCase(v.optString("site", ""))) continue;
            String key = v.optString("key", ""); if (key.isEmpty()) continue;
            if (fallback.isEmpty()) fallback = youtube(key);
            if ("Trailer".equalsIgnoreCase(v.optString("type", "")) && v.optBoolean("official", false)) return youtube(key);
        }
        return fallback;
    }

    private static String tvCertification(JSONObject ratings) {
        if (ratings == null) return ""; JSONArray a = ratings.optJSONArray("results"); if (a == null) return "";
        for (int i = 0; i < a.length(); i++) { JSONObject r = a.optJSONObject(i); if (r != null && "US".equals(r.optString("iso_3166_1"))) return r.optString("rating", ""); }
        return "";
    }

    private static String movieCertification(JSONObject dates) {
        if (dates == null) return ""; JSONArray results = dates.optJSONArray("results"); if (results == null) return "";
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i); if (r == null || !"US".equals(r.optString("iso_3166_1"))) continue;
            JSONArray rows = r.optJSONArray("release_dates"); if (rows == null) continue;
            for (int j = 0; j < rows.length(); j++) { JSONObject x = rows.optJSONObject(j); if (x != null && !x.optString("certification", "").isEmpty()) return x.optString("certification", ""); }
        }
        return "";
    }

    private static ArrayList<String> strings(JSONArray a) {
        ArrayList<String> out = new ArrayList<>(); if (a == null) return out;
        for (int i = 0; i < a.length(); i++) addUnique(out, a.optString(i, "")); return out;
    }

    private static ArrayList<String> stringsFlexible(Object value) {
        ArrayList<String> out = new ArrayList<>();
        if (value instanceof JSONArray) return strings((JSONArray)value);
        if (value instanceof String) {
            String s = ((String)value).trim();
            for (String p : s.split(",")) addUnique(out, p.trim());
        }
        return out;
    }

    private static void addUnique(List<String> out, String value) { if (value != null) { String v = value.trim(); if (!v.isEmpty() && !out.contains(v)) out.add(v); } }
    private static String pathUrl(String path, String base) { return path == null || path.isEmpty() ? "" : base + path; }
    private static String youtube(String raw) { if (raw == null) return ""; String s = raw.trim(); if (s.isEmpty()) return ""; if (s.startsWith("http://") || s.startsWith("https://")) return s; return "https://www.youtube.com/watch?v=" + Uri.encode(s); }
    private static String first(String... values) { for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim(); return ""; }

    private static String cleanRuntime(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return "";
        if (raw instanceof Number) { long n = ((Number)raw).longValue(); return n > 0 ? n + " min" : ""; }
        String s = String.valueOf(raw).trim(); return s;
    }

    private static long runtimeMs(Object raw) {
        if (raw == null || raw == JSONObject.NULL) return 0L;
        if (raw instanceof Number) return Math.max(0L, ((Number)raw).longValue()) * 60000L;
        String s = String.valueOf(raw).toLowerCase(Locale.US);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s);
        if (!m.find()) return 0L;
        try { return Long.parseLong(m.group(1)) * 60000L; } catch (Exception e) { return 0L; }
    }

    private static int parseTmdbId(String mediaId, boolean tv) {
        String prefix = tv ? "tmdb_tv_" : "tmdb_movie_"; if (!mediaId.startsWith(prefix)) return -1;
        String rest = mediaId.substring(prefix.length()); int end = rest.indexOf('_'); if (end >= 0) rest = rest.substring(0, end);
        try { return Integer.parseInt(rest); } catch (Exception e) { return -1; }
    }
}
