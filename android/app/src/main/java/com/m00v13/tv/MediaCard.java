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

    public MediaCard(String id, String title, String subtitle, boolean series, String genre,
                     List<String> tags, String artworkUrl, String streamUri, long durationMs) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.series = series;
        this.genre = genre == null ? "" : genre;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(tags);
        this.artworkUrl = artworkUrl;
        this.streamUri = streamUri;
        this.durationMs = durationMs;
    }
}
