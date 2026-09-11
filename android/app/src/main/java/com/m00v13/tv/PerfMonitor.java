package com.m00v13.tv;

import android.app.Activity;
import android.os.Build;
import android.view.FrameMetrics;
import android.view.Window;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Lightweight production-safe frame monitor. Logs only meaningful jank, not every frame. */
public final class PerfMonitor {
    private static final WeakHashMap<Activity, Window.OnFrameMetricsAvailableListener> LISTENERS = new WeakHashMap<>();
    private PerfMonitor() {}

    public static void attach(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 24) return;
        synchronized (LISTENERS) {
            if (LISTENERS.containsKey(activity)) return;
            WeakReference<Activity> ref = new WeakReference<>(activity);
            Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
                long totalNs = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
                if (totalNs < 40_000_000L && dropped <= 0) return;
                Activity a = ref.get();
                if (a == null || a.isFinishing()) return;
                long inputNs = metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION);
                long layoutNs = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION);
                long drawNs = metrics.getMetric(FrameMetrics.DRAW_DURATION);
                DebugLog.append(a, "PERF", "frame=" + (totalNs / 1_000_000L) + "ms input=" +
                    (inputNs / 1_000_000L) + "ms layout=" + (layoutNs / 1_000_000L) + "ms draw=" +
                    (drawNs / 1_000_000L) + "ms dropped=" + dropped + " activity=" + a.getClass().getSimpleName());
            };
            activity.getWindow().addOnFrameMetricsAvailableListener(listener, null);
            LISTENERS.put(activity, listener);
        }
    }

    public static void detach(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < 24) return;
        synchronized (LISTENERS) {
            Window.OnFrameMetricsAvailableListener listener = LISTENERS.remove(activity);
            if (listener != null) {
                try { activity.getWindow().removeOnFrameMetricsAvailableListener(listener); } catch (Throwable ignored) {}
            }
        }
    }
}
