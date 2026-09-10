package com.m00v13.tv;

import java.util.Collections;
import java.util.List;

public final class MediaCard {
    public final String id;
    public final String title;
    public final String subtitle;
    public final boolean series;
    public final String genre;
    public final List<String> tags;
    public final String artworkUrl;
    public final String streamUri;
    public final long durationMs;
    public final String seriesKey;
    public final int seasonNumber;
    public final int episodeNumber;
    public final String collectionKey;
    public final int collectionOrder;

    public MediaCard(String id, String title, String subtitle, boolean series, String genre,
                     List<String> tags, String artworkUrl, String streamUri, long durationMs) {
        this(id, title, subtitle, series, genre, tags, artworkUrl, streamUri, durationMs,
            null, 0, 0, null, 0);
    }

    public MediaCard(String id, String title, String subtitle, boolean series, String genre,
                     List<String> tags, String artworkUrl, String streamUri, long durationMs,
                     String seriesKey, int seasonNumber, int episodeNumber,
                     String collectionKey, int collectionOrder) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.series = series;
        this.genre = genre == null ? "" : genre;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(tags);
        this.artworkUrl = artworkUrl;
        this.streamUri = streamUri;
        this.durationMs = durationMs;
        this.seriesKey = seriesKey;
        this.seasonNumber = Math.max(0, seasonNumber);
        this.episodeNumber = Math.max(0, episodeNumber);
        this.collectionKey = collectionKey;
        this.collectionOrder = Math.max(0, collectionOrder);
    }

    public boolean isEpisode() { return series && seriesKey != null && !seriesKey.isEmpty() && episodeNumber > 0; }
    public boolean isCollectionMember() { return collectionKey != null && !collectionKey.isEmpty() && collectionOrder > 0; }
}
