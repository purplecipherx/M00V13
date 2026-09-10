package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private static final int CARD = Color.rgb(45, 25, 67);
    private static final int WHITE = Color.WHITE;

    private ProfileStore profiles;
    private CatalogStore catalog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = new ProfileStore(this);
        catalog = new CatalogStore(this);
        getWindow().getDecorView().setBackgroundColor(BG);
        setContentView(buildHome());
    }

    @Override protected void onResume() {
        super.onResume();
        if (profiles != null && catalog != null) setContentView(buildHome());
    }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(30), dp(56), dp(38));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cow = new ImageView(this);
        cow.setImageResource(R.drawable.ic_m00v13);
        header.addView(cow, new LinearLayout.LayoutParams(dp(82), dp(82)));
        TextView title = text("M00V13", 34, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(82), 1f);
        tp.setMarginStart(dp(18));
        header.addView(title, tp);
        header.addView(text(profiles.activeProfile() + "  •  " + profiles.preferredLanguage().toUpperCase(), 18, false));
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(0, dp(14), 0, dp(8));
        nav.addView(navButton("Home", null));
        nav.addView(navButton("Movies", null));
        nav.addView(navButton("TV", null));
        nav.addView(navButton("Downloads", v -> startActivity(new Intent(this, DownloadsActivity.class))));
        nav.addView(navButton("Search", null));
        nav.addView(navButton("System", v -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        root.addView(nav);

        List<MediaCard> all = catalog.all();
        List<MediaCard> continuing = continueWatching(all);
        List<MediaCard> nextUp = nextUp(all);
        RecommendationEngine recommender = new RecommendationEngine();
        List<MediaCard> recommended = recommender.rankOverall(all, profiles, 12);

        addRow(root, "Continue Watching", continuing, "Start something and your position will appear here.");
        addRow(root, "Next Up", nextUp, "Series automation will place the next unwatched episodes here.");
        addRow(root, "Recommended for You", recommended, "Recommendations appear as your profile builds watch history.");

        MediaCard recent = mostRecentlyTouched(all);
        if (recent != null) {
            addRow(root, "Because You Watched " + recent.title,
                recommender.becauseYouWatched(recent, all, profiles, 10),
                "Related titles will appear here as the catalog fills.");
        }

        TextView storage = text(storageSummary(), 16, false);
        storage.setTextColor(Color.rgb(205, 190, 220));
        storage.setPadding(0, dp(24), 0, 0);
        root.addView(storage);
        return scroll;
    }

    private List<MediaCard> continueWatching(List<MediaCard> all) {
        ArrayList<MediaCard> out = new ArrayList<>();
        for (MediaCard c : all) {
            long p = profiles.progressMs(c.id);
            long d = profiles.durationMs(c.id);
            if (!profiles.isWatched(c.id) && p > 0 && d > 0) out.add(c);
        }
        out.sort(Comparator.comparingLong((MediaCard c) -> profiles.lastUpdatedMs(c.id)).reversed());
        return out;
    }

    private List<MediaCard> nextUp(List<MediaCard> all) {
        ArrayList<MediaCard> out = new ArrayList<>();
        for (MediaCard c : all) if (c.series && !profiles.isWatched(c.id)) out.add(c);
        out.sort(Comparator.comparingLong((MediaCard c) -> profiles.lastUpdatedMs(c.id)).reversed());
        return out.size() <= 10 ? out : new ArrayList<>(out.subList(0, 10));
    }

    private MediaCard mostRecentlyTouched(List<MediaCard> all) {
        MediaCard best = null;
        long when = 0L;
        for (MediaCard c : all) {
            long t = profiles.lastUpdatedMs(c.id);
            if (t > when) { when = t; best = c; }
        }
        return best;
    }

    private void addRow(LinearLayout root, String heading, List<MediaCard> items, String emptyMessage) {
        TextView h = text(heading, 24, true);
        h.setPadding(0, dp(24), 0, dp(8));
        root.addView(h);
        if (items == null || items.isEmpty()) {
            TextView empty = text(emptyMessage, 16, false);
            empty.setTextColor(Color.rgb(190, 175, 205));
            root.addView(empty);
            return;
        }

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (MediaCard item : items) row.addView(mediaButton(item));
        hsv.addView(row);
        root.addView(hsv);
    }

    private Button mediaButton(MediaCard item) {
        Button b = new Button(this);
        String sub = item.subtitle == null || item.subtitle.isEmpty() ? "" : "\n" + item.subtitle;
        long p = profiles.progressMs(item.id), d = profiles.durationMs(item.id);
        String progress = p > 0 && d > 0 ? "\n" + Math.min(99, (p * 100 / d)) + "% watched" : "";
        b.setText(item.title + sub + progress);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));
        LinearLayout.LayoutParams pms = new LinearLayout.LayoutParams(dp(250), dp(112));
        pms.setMarginEnd(dp(12));
        b.setLayoutParams(pms);
        b.setOnClickListener(v -> playKnown(item));
        b.setOnLongClickListener(v -> {
            profiles.setWatchlist(item.id, !profiles.isInWatchlist(item.id));
            return true;
        });
        return b;
    }

    private void playKnown(MediaCard item) {
        if (item.streamUri == null || item.streamUri.isEmpty()) return;
        Intent play = new Intent(this, PlayerActivity.class);
        play.putExtra(PlayerActivity.EXTRA_MEDIA_ID, item.id);
        play.putExtra(PlayerActivity.EXTRA_URI, item.streamUri);
        startActivity(play);
    }

    private String storageSummary() {
        long free = StoragePolicy.availableBytes(getFilesDir());
        return "Free " + (free / StoragePolicy.MIB) + " MiB  •  protected system reserve " + (StoragePolicy.SYSTEM_RESERVE_BYTES / StoragePolicy.MIB) + " MiB";
    }

    private Button navButton(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        if (click != null) b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1f);
        p.setMarginEnd(dp(9));
        b.setLayoutParams(p);
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
