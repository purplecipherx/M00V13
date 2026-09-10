package com.m00v13.tv;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DownloadJanitor {
    public static final class Item {
        public final File file;
        public final boolean watched;
        public final boolean pinned;
        public final boolean currentlyPlaying;
        public final long lastAccessMs;

        public Item(File file, boolean watched, boolean pinned, boolean currentlyPlaying, long lastAccessMs) {
            this.file = file;
            this.watched = watched;
            this.pinned = pinned;
            this.currentlyPlaying = currentlyPlaying;
            this.lastAccessMs = lastAccessMs;
        }
    }

    public long reclaimIfCritical(File volume, List<Item> items) {
        if (!StoragePolicy.isCritical(volume)) return 0L;
        long needed = StoragePolicy.bytesNeededForEmergencyTarget(volume);
        if (needed <= 0L) return 0L;

        List<Item> eligible = new ArrayList<>();
        for (Item item : items) {
            if (item == null || item.file == null || item.pinned || item.currentlyPlaying) continue;
            if (!item.file.isFile()) continue;
            eligible.add(item);
        }

        eligible.sort(Comparator
            .comparing((Item i) -> !i.watched)
            .thenComparingLong(i -> i.lastAccessMs));

        long reclaimed = 0L;
        for (Item item : eligible) {
            long length = item.file.length();
            if (item.file.delete()) reclaimed += length;
            if (reclaimed >= needed || StoragePolicy.availableBytes(volume) >= StoragePolicy.EMERGENCY_TARGET_BYTES) break;
        }
        return reclaimed;
    }
}
