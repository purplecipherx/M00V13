package com.m00v13.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;

/** Chooses provider/detail search concurrency from device/network headroom. */
public final class SearchConcurrencyPolicy {
    public static final int MIN_THREADS = 10;
    public static final int MAX_THREADS = 100;

    private SearchConcurrencyPolicy() {}

    public static int choose(Context context, int taskCount) {
        if (taskCount <= 0) return 1;
        int cpus = Math.max(1, Runtime.getRuntime().availableProcessors());
        long maxMemMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        int hardwareCap = cpus >= 8 ? 48 : cpus >= 6 ? 36 : cpus >= 4 ? 24 : 14;
        if (maxMemMb >= 384) hardwareCap += 8;
        if (maxMemMb >= 768) hardwareCap += 12;

        int networkCap = networkCap(context);
        int target = Math.min(MAX_THREADS, Math.min(hardwareCap, networkCap));
        // HTTP scraping is I/O-bound, so 10 concurrent tasks is safe as the normal floor
        // when there are at least 10 providers/details to run.
        if (taskCount >= MIN_THREADS) target = Math.max(MIN_THREADS, target);
        return Math.max(1, Math.min(taskCount, target));
    }

    private static int networkCap(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c == null) return 10;
            int downKbps = c.getLinkDownstreamBandwidthKbps();
            if (downKbps >= 500_000) return 100;
            if (downKbps >= 200_000) return 72;
            if (downKbps >= 100_000) return 48;
            if (downKbps >= 50_000) return 32;
            if (downKbps >= 20_000) return 20;
            return 10;
        } catch (Exception ignored) {
            return 10;
        }
    }
}
