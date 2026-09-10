package com.m00v13.tv;

public final class PredictiveCachePolicy {
    public static final long ARTWORK_SOFT_BYTES = 192L * StoragePolicy.MIB;
    public static final long ARTWORK_HARD_BYTES = 320L * StoragePolicy.MIB;
    public static final long METADATA_SOFT_BYTES = 32L * StoragePolicy.MIB;
    public static final long METADATA_HARD_BYTES = 64L * StoragePolicy.MIB;

    private PredictiveCachePolicy() {}

    public static int priority(
            boolean visibleNow,
            boolean nextScreen,
            boolean continueWatching,
            boolean nextUp,
            boolean activeDownload,
            boolean watchlist,
            boolean recommended,
            long lastViewedAgeHours) {
        int score = 0;
        if (visibleNow) score += 1000;
        if (nextScreen) score += 700;
        if (continueWatching) score += 650;
        if (nextUp) score += 600;
        if (activeDownload) score += 550;
        if (watchlist) score += 400;
        if (recommended) score += 250;
        if (lastViewedAgeHours <= 24) score += 300;
        else if (lastViewedAgeHours <= 168) score += 120;
        return score;
    }

    public static boolean shouldEvict(int priority, long cacheBytes, boolean storageCritical) {
        if (storageCritical) return priority < 700;
        if (cacheBytes > ARTWORK_HARD_BYTES) return priority < 650;
        if (cacheBytes > ARTWORK_SOFT_BYTES) return priority < 400;
        return false;
    }
}
