package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class DownloadsActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(build());
    }

    private ScrollView build() {
        OfflineQueue queue = new OfflineQueue(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(38), dp(56), dp(38));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        root.addView(text("Downloads", 30, true));
        root.addView(text(storageSummary(queue), 17, false));
        root.addView(text("Automatic series window: next " + queue.keepNextEpisodes() + " unwatched episodes", 17, false));
        root.addView(text("Watched downloads auto-delete: " + (queue.autoDeleteWatched() ? "ON" : "OFF"), 17, false));

        TextView heading = text("Queue", 24, true);
        heading.setPadding(0, dp(28), 0, dp(10));
        root.addView(heading);

        List<OfflineQueue.Entry> entries = queue.list();
        if (entries.isEmpty()) {
            root.addView(text("Nothing queued. Downloaded and queued titles will appear here.", 18, false));
        } else {
            for (OfflineQueue.Entry e : entries) {
                Button b = new Button(this);
                String kind = e.automatic ? "AUTO" : "MANUAL";
                String pin = e.pinned ? " • PINNED" : "";
                b.setText(e.title + "\n" + kind + pin + sizeLabel(e.estimatedBytes));
                b.setTextColor(Color.WHITE);
                b.setTextSize(16);
                b.setAllCaps(false);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setFocusable(true);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
                p.bottomMargin = dp(10);
                b.setLayoutParams(p);
                b.setOnLongClickListener(v -> { queue.remove(e.mediaId); recreate(); return true; });
                root.addView(b);
            }
        }
        return scroll;
    }

    private String storageSummary(OfflineQueue q) {
        long free = StoragePolicy.availableBytes(getFilesDir());
        long reserve = Math.max(StoragePolicy.SYSTEM_RESERVE_BYTES, q.extraReserveBytes());
        long allowed = StoragePolicy.allowedWriteBytes(getFilesDir(), downloadBytes(q.list()), q.maxDownloadBytes(), q.extraReserveBytes());
        return "Free " + mib(free) + " MiB • protected " + mib(reserve) + " MiB • writable budget " + mib(allowed) + " MiB";
    }

    private long downloadBytes(List<OfflineQueue.Entry> entries) {
        long total = 0L;
        for (OfflineQueue.Entry e : entries) total += Math.max(0L, e.estimatedBytes);
        return total;
    }

    private String sizeLabel(long bytes) { return bytes <= 0 ? "" : " • " + mib(bytes) + " MiB"; }
    private long mib(long bytes) { return bytes / StoragePolicy.MIB; }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        v.setPadding(0, dp(5), 0, dp(5));
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
