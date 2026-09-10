package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class BrowseActivity extends Activity {
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_MOVIES = "movies";
    public static final String KIND_TV = "tv";
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String kind = getIntent().getStringExtra(EXTRA_KIND);
        boolean tv = KIND_TV.equals(kind);
        setContentView(build(tv));
    }

    private ScrollView build(boolean tv) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(36), dp(56), dp(36));
        root.setBackgroundColor(BG);
        scroll.addView(root);
        root.addView(text(tv ? "TV Shows" : "Movies", 30, true));

        List<MediaCard> items = new ArrayList<>();
        for (MediaCard item : new CatalogStore(this).all()) {
            if (tv == item.series) items.add(item);
        }
        Collections.sort(items, new Comparator<MediaCard>() {
            @Override public int compare(MediaCard a, MediaCard b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });

        if (items.isEmpty()) {
            TextView empty = text("Nothing is in the local catalog yet. Search/metadata ingestion will populate this screen.", 18, false);
            empty.setTextColor(Color.rgb(200, 185, 215));
            empty.setPadding(0, dp(18), 0, 0);
            root.addView(empty);
            return scroll;
        }

        for (MediaCard item : items) root.addView(card(item));
        return scroll;
    }

    private Button card(MediaCard item) {
        Button b = new Button(this);
        String detail = item.subtitle == null || item.subtitle.isEmpty() ? "" : "\n" + item.subtitle;
        if (item.isEpisode()) detail += "  •  S" + item.seasonNumber + "E" + item.episodeNumber;
        b.setText(item.title + detail);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70));
        p.topMargin = dp(10);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> open(item));
        return b;
    }

    private void open(MediaCard item) {
        Intent i = new Intent(this, SourceSelectionActivity.class);
        i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID, item.id);
        i.putExtra(SourceSelectionActivity.EXTRA_TITLE, item.title);
        startActivity(i);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
