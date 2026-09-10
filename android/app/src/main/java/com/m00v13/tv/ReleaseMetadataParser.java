package com.m00v13.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReleaseMetadataParser {
    private static final Pattern SIZE = Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB)");

    public static final class Parsed {
        public final String quality;
        public final String videoCodec;
        public final String hdr;
        public final String audioCodec;
        public final String audioLayout;
        public final List<String> audioLanguages;
        public final List<String> subtitleLanguages;

        Parsed(String quality, String videoCodec, String hdr, String audioCodec, String audioLayout,
               List<String> audioLanguages, List<String> subtitleLanguages) {
            this.quality = quality;
            this.videoCodec = videoCodec;
            this.hdr = hdr;
            this.audioCodec = audioCodec;
            this.audioLayout = audioLayout;
            this.audioLanguages = audioLanguages;
            this.subtitleLanguages = subtitleLanguages;
        }
    }

    private ReleaseMetadataParser() {}

    public static Parsed parse(String title) {
        String s = title == null ? "" : title.toUpperCase(Locale.US).replace('_', ' ').replace('.', ' ');
        String quality = containsAny(s, "2160P", "4K", "UHD") ? "2160p" :
            containsAny(s, "1080P") ? "1080p" : containsAny(s, "720P") ? "720p" :
            containsAny(s, "480P", "SD") ? "SD" : "?";

        String videoCodec = containsAny(s, "AV1") ? "AV1" :
            containsAny(s, "HEVC", "H265", "H 265", "X265") ? "HEVC" :
            containsAny(s, "H264", "H 264", "X264", "AVC") ? "AVC" : "?";

        String hdr = containsAny(s, "DOLBY VISION", "DOVI", " DV ") ? "DV" :
            containsAny(s, "HDR10+", "HDR10PLUS") ? "HDR10+" :
            containsAny(s, "HDR10", " HDR ") ? "HDR10" : "";

        String audioCodec = containsAny(s, "TRUEHD") ? "TrueHD" :
            containsAny(s, "DTS-HD", "DTS HD", "DTSHD") ? "DTS-HD" :
            containsAny(s, "DTS:X", "DTS X") ? "DTS:X" :
            containsAny(s, "DDP", "EAC3", "E-AC3", "DD+") ? "DD+" :
            containsAny(s, "AC3", "DD5", "DD 5") ? "AC3" :
            containsAny(s, "AAC") ? "AAC" : "?";

        String audioLayout = containsAny(s, "7.1", "7 1") ? "7.1" :
            containsAny(s, "5.1", "5 1") ? "5.1" :
            containsAny(s, "2.0", "2 0") ? "2.0" : "";

        List<String> audio = new ArrayList<>();
        addLanguageIfPresent(audio, s, "en", " ENG ", " ENGLISH ");
        addLanguageIfPresent(audio, s, "es", " SPA ", " SPANISH ", " ESP ");
        addLanguageIfPresent(audio, s, "fr", " FRE ", " FRENCH ", " FRA ");
        addLanguageIfPresent(audio, s, "de", " GER ", " GERMAN ", " DEU ");
        addLanguageIfPresent(audio, s, "it", " ITA ", " ITALIAN ");
        addLanguageIfPresent(audio, s, "ja", " JPN ", " JAPANESE ");
        addLanguageIfPresent(audio, s, "ko", " KOR ", " KOREAN ");
        addLanguageIfPresent(audio, s, "hi", " HINDI ");

        List<String> subs = new ArrayList<>();
        if (containsAny(s, "ENG SUB", "ENGLISH SUB")) subs.add("en");
        if (containsAny(s, "SPA SUB", "SPANISH SUB")) subs.add("es");
        if (containsAny(s, "FRE SUB", "FRENCH SUB")) subs.add("fr");
        if (containsAny(s, "JPN SUB", "JAPANESE SUB")) subs.add("ja");

        return new Parsed(quality, videoCodec, hdr, audioCodec, audioLayout,
            Collections.unmodifiableList(audio), Collections.unmodifiableList(subs));
    }

    public static long parseSizeBytes(String text) {
        if (text == null) return 0L;
        Matcher m = SIZE.matcher(text.replace(',', '.'));
        if (!m.find()) return 0L;
        double value;
        try { value = Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return 0L; }
        String unit = m.group(2).toUpperCase(Locale.US);
        double mult = "TB".equals(unit) ? 1099511627776d : "GB".equals(unit) ? 1073741824d :
            "MB".equals(unit) ? 1048576d : 1024d;
        double bytes = value * mult;
        return bytes >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) bytes);
    }

    private static void addLanguageIfPresent(List<String> out, String s, String code, String... needles) {
        if (containsAny(s, needles) && !out.contains(code)) out.add(code);
    }

    private static boolean containsAny(String s, String... needles) {
        for (String needle : needles) if (s.contains(needle)) return true;
        return false;
    }
}
