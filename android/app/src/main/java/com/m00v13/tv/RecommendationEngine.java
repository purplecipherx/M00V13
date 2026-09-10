package com.m00v13.tv;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationEngine {
    public List<MediaCard> rankOverall(List<MediaCard> catalog, ProfileStore profile, int limit) {
        Map<String, Integer> genreWeights = new HashMap<>();
        Set<String> watched = new HashSet<>(profile.watchedMediaIds());
        for (MediaCard item : catalog) {
            if (watched.contains(item.id)) genreWeights.merge(item.genre, 4, Integer::sum);
        }

        List<MediaCard> candidates = new ArrayList<>();
        for (MediaCard item : catalog) if (!watched.contains(item.id)) candidates.add(item);
        candidates.sort(Comparator.comparingInt((MediaCard item) -> overallScore(item, profile, genreWeights)).reversed());
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    public List<MediaCard> becauseYouWatched(MediaCard seed, List<MediaCard> catalog, ProfileStore profile, int limit) {
        List<MediaCard> candidates = new ArrayList<>();
        for (MediaCard item : catalog) {
            if (item.id.equals(seed.id) || profile.isWatched(item.id)) continue;
            candidates.add(item);
        }
        candidates.sort(Comparator.comparingInt((MediaCard item) -> similarity(seed, item)).reversed());
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    private int overallScore(MediaCard item, ProfileStore profile, Map<String, Integer> genreWeights) {
        int score = genreWeights.getOrDefault(item.genre, 0);
        if (profile.isInWatchlist(item.id)) score += 30;
        if (item.series) score += 2;
        for (String tag : item.tags) score += profile.tagAffinity(tag) * 3;
        return score;
    }

    private int similarity(MediaCard a, MediaCard b) {
        int score = 0;
        if (!a.genre.isEmpty() && a.genre.equalsIgnoreCase(b.genre)) score += 20;
        for (String tag : a.tags) if (b.tags.contains(tag)) score += 7;
        if (a.series == b.series) score += 2;
        return score;
    }
}
