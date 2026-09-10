package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private static final int WHITE = Color.WHITE;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(BG);
        setContentView(buildHome());
    }

    private View buildHome() {
        ProfileStore profiles = new ProfileStore(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(34), dp(56), dp(34));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView cow = new ImageView(this);
        cow.setImageResource(com.m00v13.tv.R.drawable.ic_m00v13);
        header.addView(cow, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView title = text("M00V13", 34, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(92), 1f);
        titleParams.setMarginStart(dp(20));
        header.addView(title, titleParams);

        TextView profile = text(profiles.activeProfile() + "  •  " + profiles.preferredLanguage().toUpperCase(), 18, false);
        header.addView(profile);
        root.addView(header);

        TextView status = text("🐄  Popcorn ready. Pick something to watch.", 18, false);
        status.setPadding(0, dp(20), 0, dp(24));
        root.addView(status);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.addView(button("Home", null));
        nav.addView(button("Movies", null));
        nav.addView(button("TV", null));
        nav.addView(button("Downloads", null));
        nav.addView(button("Search", null));
        nav.addView(button("Settings", v -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        root.addView(nav);

        addSection(root, "Continue Watching", "Resume state is profile-local and crash-safe by design.");
        addSection(root, "Next Up", "Upcoming episodes will be pre-resolved and optionally kept downloaded.");
        addSection(root, "Recommended for You", "Profile scoring will rank likely watches without a local AI model.");
        addSection(root, "Because You Watched…", "Title-specific recommendations stay separate from overall profile recommendations.");
        addSection(root, "Downloads", storageSummary());
        return scroll;
    }

    private void addSection(LinearLayout root, String heading, String body) {
        TextView h = text(heading, 24, true);
        h.setPadding(0, dp(28), 0, dp(6));
        root.addView(h);
        TextView b = text(body, 17, false);
        b.setTextColor(Color.rgb(205, 190, 220));
        root.addView(b);
    }

    private String storageSummary() {
        long free = StoragePolicy.availableBytes(getFilesDir());
        return "Free " + (free / StoragePolicy.MIB) + " MiB  •  system reserve 1536 MiB";
    }

    private Button button(String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        if (click != null) b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(58), 1f);
        p.setMarginEnd(dp(10));
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
