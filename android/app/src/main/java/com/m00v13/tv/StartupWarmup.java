package com.m00v13.tv;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process warmup that runs behind the five-second splash. */
public final class StartupWarmup {
    private static final int PREFETCH_POSTERS = 18;
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "m00v13-startup-warmup");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private StartupWarmup() {}

    public static void start(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        WORKER.execute(() -> warm(app));
    }

    private static void warm(Context context) {
        long started = System.currentTimeMillis();
        try {
            CatalogStore catalog = new CatalogStore(context);
            DiscoveryStore discovery = new DiscoveryStore(context);
            ArtworkLoader artwork = new ArtworkLoader(context);

            // Parse persistent stores now instead of on the first Home frame.
            List<MediaCard> all = catalog.all();
            List<MediaCard> movies = discovery.get(DiscoveryStore.POPULAR_MOVIES);
            List<MediaCard> tv = discovery.get(DiscoveryStore.POPULAR_TV);

            // Prime the first visible Home rails from cached data immediately.
            prefetch(artwork, movies, tv, all);

            // If discovery is stale, use the splash window for network refresh too.
            if (discovery.stale()) {
                try {
                    discovery.refresh();
                    movies = discovery.get(DiscoveryStore.POPULAR_MOVIES);
                    tv = discovery.get(DiscoveryStore.POPULAR_TV);
                    prefetch(artwork, movies, tv, catalog.all());
                } catch (Throwable t) {
                    DebugLog.append(context, "STARTUP", "Discovery warmup " + shortMessage(t));
                }
            }

            DebugLog.append(context, "STARTUP", "Warmup queued in " + (System.currentTimeMillis() - started) + "ms");
        } catch (Throwable t) {
            DebugLog.append(context, "STARTUP", "Warmup failed " + shortMessage(t));
        }
    }

    private static void prefetch(ArtworkLoader artwork, List<MediaCard> movies, List<MediaCard> tv, List<MediaCard> fallback) {
        ArrayList<MediaCard> ordered = new ArrayList<>();
        if (movies != null) ordered.addAll(movies);
        if (tv != null) ordered.addAll(tv);
        if (ordered.isEmpty() && fallback != null) ordered.addAll(fallback);

        Set<String> seen = new HashSet<>();
        int queued = 0;
        for (MediaCard card : ordered) {
            if (card == null || card.artworkUrl == null || card.artworkUrl.isEmpty()) continue;
            if (!seen.add(card.artworkUrl)) continue;
            artwork.prefetch(card.artworkUrl, 320);
            if (++queued >= PREFETCH_POSTERS) break;
        }
    }

    private static String shortMessage(Throwable throwable) {
        Throwable x = throwable;
        while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }
}
