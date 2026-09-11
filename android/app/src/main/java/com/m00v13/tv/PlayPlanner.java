package com.m00v13.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Device-aware source planner for appliance-style one-click playback.
 * It never fabricates capabilities: it only filters against explicit user policy and uses
 * conservative compatibility heuristics. The source picker remains the fallback.
 */
public final class PlayPlanner {
    public static final class Plan {
        public final SourceOption best;
        public final List<SourceOption> ranked;
        public final String reason;
        Plan(SourceOption best, List<SourceOption> ranked, String reason) {
            this.best = best;
            this.ranked = ranked;
            this.reason = reason;
        }
        public boolean playable() { return best != null && best.uri != null && !best.uri.trim().isEmpty(); }
    }

    private final AppSettingsStore settings;
    private final boolean debridConnected;

    public PlayPlanner(android.content.Context context) {
        settings = new AppSettingsStore(context);
        debridConnected = new DebridStore(context).isConnected();
    }

    public Plan plan(List<SourceOption> sources) {
        ArrayList<SourceOption> acceptable = new ArrayList<>();
        if (sources != null) {
            for (SourceOption source : sources) {
                if (source == null || source.uri == null || source.uri.trim().isEmpty()) continue;
                if (settings.exclude3d() && looks3d(source)) continue;
                if (qualityRank(source.quality) > qualityRank(settings.maxQuality())) continue;
                if (source.uri.startsWith("magnet:") && !debridConnected) continue;
                acceptable.add(source);
            }
        }
        acceptable.sort(Comparator.comparingInt(this::score).reversed());
        SourceOption best = acceptable.isEmpty() ? null : acceptable.get(0);
        String why;
        if (best == null) why = "No source passed playback policy";
        else if (Boolean.TRUE.equals(best.cached)) why = "RD-cached source ranked highest";
        else if (!best.uri.startsWith("magnet:")) why = "Direct playable source ranked highest";
        else why = "Best compatible source";
        return new Plan(best, acceptable, why);
    }

    private int score(SourceOption s) {
        int n = s.score;
        if (Boolean.TRUE.equals(s.cached)) n += 5000;
        else if (Boolean.FALSE.equals(s.cached)) n -= 700;
        if (!s.uri.startsWith("magnet:")) n += 900;
        n += qualityValue(s.quality) * 30;
        String codec = lower(s.videoCodec);
        if (codec.contains("hevc") || codec.contains("h265") || codec.contains("265")) n += 90;
        else if (codec.contains("h264") || codec.contains("avc") || codec.contains("264")) n += 70;
        else if (codec.contains("av1")) n += 40; // conservative on inexpensive TV hardware
        String hdr = lower(s.hdr);
        if (hdr.contains("hdr10")) n += 25;
        if (hdr.contains("dolby") || hdr.contains("dv")) n += 10;
        if (s.seeders > 0) n += Math.min(250, s.seeders);
        if (s.sizeBytes > 0) {
            long gib = s.sizeBytes / (1024L * 1024L * 1024L);
            if (gib > 80) n -= 180;
            else if (gib > 40) n -= 80;
        }
        return n;
    }

    private static boolean looks3d(SourceOption s) {
        String x = (lower(s.provider) + " " + lower(s.quality) + " " + lower(s.videoCodec) + " " + lower(s.hdr));
        return x.contains(" 3d") || x.contains("3d ") || x.contains("sbs") || x.contains("tab") || x.contains("half-sbs");
    }

    private static int qualityRank(String q) {
        String x = lower(q);
        if (x.contains("2160") || x.contains("4k")) return 4;
        if (x.contains("1080")) return 3;
        if (x.contains("720")) return 2;
        return 1;
    }

    private static int qualityValue(String q) { return qualityRank(q); }
    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.US); }
}
