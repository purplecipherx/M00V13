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
import java.util.List;

public final class SourceSelectionActivity extends Activity {
    public static final String EXTRA_MEDIA_ID = "media_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_LABELS = "labels";

    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        ArrayList<String> uris = getIntent().getStringArrayListExtra(EXTRA_URIS);
        ArrayList<String> labels = getIntent().getStringArrayListExtra(EXTRA_LABELS);

        if ((uris == null || uris.isEmpty()) && mediaId != null) {
            List<SourceOption> cached = new SourceStore(this).getFresh(mediaId);
            uris = new ArrayList<>();
            labels = new ArrayList<>();
            for (SourceOption source : cached) {
                if (source.uri == null || source.uri.trim().isEmpty()) continue;
                uris.add(source.uri);
                labels.add(source.compactLabel() + "\n" + source.provider);
            }
        }

        final ArrayList<String> finalUris = uris == null ? new ArrayList<>() : uris;
        final ArrayList<String> finalLabels = labels == null ? new ArrayList<>() : labels;

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(38), dp(56), dp(38));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        TextView heading = text(title == null ? "Choose source" : title, 30, true);
        root.addView(heading);
        TextView hint = text("Best source is first. Audio language is shown when known; actual media tracks are verified at playback.", 16, false);
        hint.setTextColor(Color.rgb(205, 190, 220));
        hint.setPadding(0, dp(8), 0, dp(22));
        root.addView(hint);

        if (finalUris.isEmpty()) {
            root.addView(text("No fresh playable sources are cached. A scrape will populate this screen.", 20, false));
        } else {
            for (int i = 0; i < finalUris.size(); i++) {
                final int index = i;
                String label = i < finalLabels.size() ? finalLabels.get(i) : "Source " + (i + 1);
                Button b = new Button(this);
                b.setText(label);
                b.setTextColor(Color.WHITE);
                b.setTextSize(16);
                b.setAllCaps(false);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                b.setFocusable(true);
                b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(76));
                p.bottomMargin = dp(10);
                b.setLayoutParams(p);
                b.setOnClickListener(v -> {
                    Intent play = new Intent(this, PlayerActivity.class);
                    play.putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId);
                    play.putExtra(PlayerActivity.EXTRA_URI, finalUris.get(index));
                    play.putStringArrayListExtra(PlayerActivity.EXTRA_FALLBACK_URIS, orderedFallbacks(finalUris, index));
                    startActivity(play);
                });
                root.addView(b);
            }
        }
        setContentView(scroll);
    }

    private ArrayList<String> orderedFallbacks(ArrayList<String> all, int selected) {
        ArrayList<String> out = new ArrayList<>();
        for (int i = selected + 1; i < all.size(); i++) out.add(all.get(i));
        for (int i = 0; i < selected; i++) out.add(all.get(i));
        return out;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
