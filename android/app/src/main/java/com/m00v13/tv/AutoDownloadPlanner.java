package com.m00v13.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AutoDownloadPlanner {
    public List<MediaCard> nextEpisodes(String seriesKey, List<MediaCard> catalog,
                                        ProfileStore profile, OfflineQueue queue) {
        if (seriesKey == null || seriesKey.isEmpty()) return Collections.emptyList();
        ArrayList<MediaCard> episodes = new ArrayList<>();
        Set<String> alreadyQueued = queuedIds(queue.list());
        for (MediaCard item : catalog) {
            if (!item.isEpisode() || !seriesKey.equals(item.seriesKey)) continue;
            if (profile.isWatched(item.id) || alreadyQueued.contains(item.id)) continue;
            episodes.add(item);
        }
        Collections.sort(episodes, new Comparator<MediaCard>() {
            @Override public int compare(MediaCard a, MediaCard b) {
                if (a.seasonNumber != b.seasonNumber) return a.seasonNumber - b.seasonNumber;
                return a.episodeNumber - b.episodeNumber;
            }
        });
        int limit = Math.min(queue.keepNextEpisodes(), episodes.size());
        return new ArrayList<>(episodes.subList(0, limit));
    }

    public List<MediaCard> remainingCollection(String collectionKey, List<MediaCard> catalog,
                                                ProfileStore profile, OfflineQueue queue) {
        if (collectionKey == null || collectionKey.isEmpty()) return Collections.emptyList();
        ArrayList<MediaCard> items = new ArrayList<>();
        Set<String> alreadyQueued = queuedIds(queue.list());
        for (MediaCard item : catalog) {
            if (!item.isCollectionMember() || !collectionKey.equals(item.collectionKey)) continue;
            if (profile.isWatched(item.id) || alreadyQueued.contains(item.id)) continue;
            items.add(item);
        }
        Collections.sort(items, new Comparator<MediaCard>() {
            @Override public int compare(MediaCard a, MediaCard b) { return a.collectionOrder - b.collectionOrder; }
        });
        return items;
    }

    public int enqueueResolved(List<MediaCard> planned, SourceStore sources, OfflineQueue queue) {
        int added = 0;
        for (MediaCard item : planned) {
            List<SourceOption> options = sources.getFresh(item.id);
            if (options.isEmpty()) continue;
            SourceOption best = options.get(0);
            if (best.uri == null || best.uri.trim().isEmpty()) continue;
            if (queue.enqueue(new OfflineQueue.Entry(item.id, item.title, best.uri,
                    best.sizeBytes, true, false, System.currentTimeMillis()))) added++;
        }
        return added;
    }

    private Set<String> queuedIds(List<OfflineQueue.Entry> entries) {
        HashSet<String> ids = new HashSet<>();
        for (OfflineQueue.Entry e : entries) ids.add(e.mediaId);
        return ids;
    }
}
