package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;

public final class ProfileActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private ProfileStore profiles;
    private LinearLayout profileList;
    private TextView active;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = new ProfileStore(this);
        setContentView(build());
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(36), dp(56), dp(36));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        root.addView(text("Profiles", 30, true));
        active = text("", 18, false);
        active.setTextColor(Color.rgb(205, 190, 220));
        active.setPadding(0, dp(7), 0, dp(18));
        root.addView(active);

        profileList = new LinearLayout(this);
        profileList.setOrientation(LinearLayout.VERTICAL);
        root.addView(profileList);
        renderProfiles();

        TextView createTitle = text("Create profile", 22, true);
        createTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(createTitle);
        EditText name = input("Profile name");
        root.addView(name, rowParams());
        Button create = button("Create and switch");
        create.setOnClickListener(v -> {
            if (profiles.createProfile(name.getText().toString())) {
                profiles.setActiveProfile(name.getText().toString());
                name.setText("");
                renderProfiles();
            }
        });
        root.addView(create, rowParams());

        TextView langTitle = text("Preferred playback language", 22, true);
        langTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(langTitle);
        LinearLayout langs = new LinearLayout(this);
        langs.setOrientation(LinearLayout.HORIZONTAL);
        for (String code : Arrays.asList("en", "es", "fr", "de", "it", "pt", "ja")) {
            Button b = button(code.toUpperCase());
            b.setOnClickListener(v -> { profiles.setPreferredLanguage(code); renderProfiles(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
            p.setMarginEnd(dp(7));
            langs.addView(b, p);
        }
        root.addView(langs);
        return scroll;
    }

    private void renderProfiles() {
        if (profileList == null) return;
        profileList.removeAllViews();
        active.setText("Active: " + profiles.activeProfile() + "  •  language " + profiles.preferredLanguage().toUpperCase());
        List<String> names = profiles.profiles();
        for (String name : names) {
            Button b = button((name.equals(profiles.activeProfile()) ? "✓  " : "") + name);
            b.setOnClickListener(v -> { profiles.setActiveProfile(name); renderProfiles(); });
            profileList.addView(b, rowParams());
        }
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setSingleLine(true); e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.rgb(180, 165, 195)); e.setTextSize(18);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE); e.setFocusable(true);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(16); b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        return b;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        p.bottomMargin = dp(8); return p;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
