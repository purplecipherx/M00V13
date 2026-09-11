package com.m00v13.tv;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * Bounded search concurrency based on current network and device capacity.
 * We intentionally keep a floor of 10 for responsive provider fan-out and a
 * hard ceiling of 100 to prevent runaway thread creation on large devices.
 */
public final class SearchConcurrency {
    public static final int MIN_THREADS = 10;
    public static final int MAX_THREADS = 100;

    private SearchConcurrency() {}

    public static int recommended(Context context) {
        try {
            int override = new AppSettingsStore(context).searchWorkerOverride();
            if (override > 0) {
                int fixed = clamp(override);
                DebugLog.append(context, "SEARCH", "Fixed concurrency=" + fixed);
                return fixed;
            }
        } catch (Exception ignored) {}

        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int memoryMb = 256;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) memoryMb = Math.max(128, am.getMemoryClass());
        } catch (Exception ignored) {}

        // Network I/O threads spend most of their time waiting, so more than one
        // thread per CPU core is useful. Memory remains the stronger hardware cap.
        int cpuBudget = cores * 8;
        int memoryBudget = Math.max(MIN_THREADS, memoryMb / 12);
        int hardwareBudget = clamp(Math.min(cpuBudget, memoryBudget));

        int networkBudget = MIN_THREADS;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            NetworkCapabilities caps = active == null || cm == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null) {
                int kbps = caps.getLinkDownstreamBandwidthKbps();
                if (kbps > 0) {
                    // Roughly one concurrent request per ~4 Mbps, with the mandatory 10-thread floor.
                    networkBudget = clamp(Math.max(MIN_THREADS, kbps / 4000));
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) networkBudget = 40;
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) networkBudget = 24;
                else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) networkBudget = 14;
            }
        } catch (Exception ignored) {}

        int recommended = clamp(Math.min(hardwareBudget, networkBudget));
        DebugLog.append(context, "SEARCH", "Adaptive concurrency=" + recommended + " cores=" + cores + " memoryClassMiB=" + memoryMb + " networkBudget=" + networkBudget);
        return recommended;
    }

    public static int forJobs(Context context, int jobs) {
        if (jobs <= 0) return 1;
        return Math.max(1, Math.min(jobs, recommended(context)));
    }

    private static int clamp(int value) { return Math.max(MIN_THREADS, Math.min(MAX_THREADS, value)); }
}
