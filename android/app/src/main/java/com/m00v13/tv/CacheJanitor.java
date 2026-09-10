package com.m00v13.tv;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CacheJanitor {
    public static final class Entry {
        public final File file;
        public final int priority;
        public final boolean inUse;
        public final long lastAccessMs;

        public Entry(File file, int priority, boolean inUse, long lastAccessMs) {
            this.file = file;
            this.priority = priority;
            this.inUse = inUse;
            this.lastAccessMs = lastAccessMs;
        }
    }

    public long trimArtwork(List<Entry> entries, boolean storageCritical) {
        long total = 0L;
        for (Entry e : entries) if (e != null && e.file != null && e.file.isFile()) total += e.file.length();
        long target = storageCritical ? PredictiveCachePolicy.ARTWORK_SOFT_BYTES / 2 : PredictiveCachePolicy.ARTWORK_SOFT_BYTES;
        if (!storageCritical && total <= PredictiveCachePolicy.ARTWORK_SOFT_BYTES) return 0L;

        List<Entry> candidates = new ArrayList<>();
        for (Entry e : entries) {
            if (e == null || e.file == null || e.inUse || !e.file.isFile()) continue;
            if (PredictiveCachePolicy.shouldEvict(e.priority, total, storageCritical)) candidates.add(e);
        }

        candidates.sort(Comparator
            .comparingInt((Entry e) -> e.priority)
            .thenComparingLong(e -> e.lastAccessMs));

        long reclaimed = 0L;
        for (Entry e : candidates) {
            long length = e.file.length();
            if (e.file.delete()) {
                reclaimed += length;
                total -= length;
            }
            if (total <= target) break;
        }
        return reclaimed;
    }
}
