package com.m00v13.tv;

import java.util.Collections;
import java.util.List;

public final class SourceOption {
    public final String provider;
    public final String uri;
    public final String quality;
    public final String videoCodec;
    public final String hdr;
    public final String audioCodec;
    public final String audioLayout;
    public final List<String> audioLanguages;
    public final List<String> subtitleLanguages;
    public final long sizeBytes;
    public final int seeders;
    public final Boolean cached;
    public final int score;

    public SourceOption(String provider, String uri, String quality, String videoCodec, String hdr,
                        String audioCodec, String audioLayout, List<String> audioLanguages,
                        List<String> subtitleLanguages, long sizeBytes, int seeders,
                        Boolean cached, int score) {
        this.provider = provider == null ? "unknown" : provider;
        this.uri = uri;
        this.quality = quality == null ? "?" : quality;
        this.videoCodec = videoCodec == null ? "?" : videoCodec;
        this.hdr = hdr == null ? "" : hdr;
        this.audioCodec = audioCodec == null ? "?" : audioCodec;
        this.audioLayout = audioLayout == null ? "" : audioLayout;
        this.audioLanguages = audioLanguages == null ? Collections.emptyList() : Collections.unmodifiableList(audioLanguages);
        this.subtitleLanguages = subtitleLanguages == null ? Collections.emptyList() : Collections.unmodifiableList(subtitleLanguages);
        this.sizeBytes = sizeBytes;
        this.seeders = seeders;
        this.cached = cached;
        this.score = score;
    }

    public String compactLabel() {
        StringBuilder out = new StringBuilder();
        out.append(quality).append(" • ").append(videoCodec);
        if (!hdr.isEmpty()) out.append(" • ").append(hdr);
        out.append(" • ").append(audioCodec);
        if (!audioLayout.isEmpty()) out.append(" ").append(audioLayout);
        if (!audioLanguages.isEmpty()) out.append(" • ").append(String.join("/", audioLanguages).toUpperCase());
        if (!subtitleLanguages.isEmpty()) out.append(" + ").append(String.join("/", subtitleLanguages).toUpperCase()).append(" SUBS");
        if (Boolean.TRUE.equals(cached)) out.append(" • Cached");
        else if (Boolean.FALSE.equals(cached)) out.append(" • Uncached");
        if (sizeBytes > 0) out.append(" • ").append(String.format(java.util.Locale.US, "%.1f GB", sizeBytes / 1073741824.0));
        if (seeders >= 0) out.append(" • ").append(seeders).append(" seeds");
        return out.toString();
    }
}
