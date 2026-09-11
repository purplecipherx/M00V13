package com.m00v13.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata payload used by the cinematic movie/show details screen. */
public final class TitleDetailsData {
    public final MediaCard media;
    public final String backgroundUrl;
    public final String description;
    public final String releaseInfo;
    public final String runtime;
    public final String rating;
    public final String certification;
    public final String trailerUrl;
    public final List<String> genres;
    public final List<String> cast;
    public final List<String> directors;
    public final List<String> writers;
    public final List<Integer> seasons;
    public final Map<Integer, List<Episode>> episodesBySeason;

    public TitleDetailsData(MediaCard media, String backgroundUrl, String description,
                            String releaseInfo, String runtime, String rating,
                            String certification, String trailerUrl, List<String> genres,
                            List<String> cast, List<String> directors, List<String> writers,
                            List<Integer> seasons, Map<Integer, List<Episode>> episodesBySeason) {
        this.media = media;
        this.backgroundUrl = clean(backgroundUrl);
        this.description = clean(description);
        this.releaseInfo = clean(releaseInfo);
        this.runtime = clean(runtime);
        this.rating = clean(rating);
        this.certification = clean(certification);
        this.trailerUrl = clean(trailerUrl);
        this.genres = immutableStrings(genres);
        this.cast = immutableStrings(cast);
        this.directors = immutableStrings(directors);
        this.writers = immutableStrings(writers);
        this.seasons = immutableInts(seasons);
        LinkedHashMap<Integer, List<Episode>> copy = new LinkedHashMap<>();
        if (episodesBySeason != null) {
            for (Map.Entry<Integer, List<Episode>> e : episodesBySeason.entrySet()) {
                copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            }
        }
        this.episodesBySeason = Collections.unmodifiableMap(copy);
    }

    public static TitleDetailsData fallback(MediaCard media) {
        ArrayList<String> genres = new ArrayList<>();
        if (media != null) {
            if (media.genre != null && !media.genre.isEmpty()) genres.add(media.genre);
            for (String tag : media.tags) if (!genres.contains(tag)) genres.add(tag);
        }
        return new TitleDetailsData(media, media == null ? null : media.artworkUrl, "",
            media == null ? "" : media.subtitle, "", "", "", "", genres,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyMap());
    }

    public static final class Episode {
        public final MediaCard card;
        public final String title;
        public final String overview;
        public final String artworkUrl;
        public final long durationMs;

        public Episode(MediaCard card, String title, String overview, String artworkUrl, long durationMs) {
            this.card = card;
            this.title = clean(title);
            this.overview = clean(overview);
            this.artworkUrl = clean(artworkUrl);
            this.durationMs = Math.max(0L, durationMs);
        }
    }

    private static List<String> immutableStrings(List<String> input) {
        ArrayList<String> out = new ArrayList<>();
        if (input != null) for (String value : input) {
            String clean = clean(value);
            if (!clean.isEmpty() && !out.contains(clean)) out.add(clean);
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Integer> immutableInts(List<Integer> input) {
        ArrayList<Integer> out = new ArrayList<>();
        if (input != null) for (Integer value : input) {
            if (value != null && value > 0 && !out.contains(value)) out.add(value);
        }
        Collections.sort(out);
        return Collections.unmodifiableList(out);
    }

    static String clean(String value) { return value == null ? "" : value.trim(); }
}
