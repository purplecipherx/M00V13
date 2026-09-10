package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MetadataActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(40), dp(56), dp(40));
        root.setBackgroundColor(BG);

        TextView title = text("Metadata", 30, true); root.addView(title);
        TextView note = text("TMDB powers title matching, posters, years, seasons and episode identity. Paste your TMDB API Read Access Token; it is stored with Android Keystore encryption.", 17, false);
        note.setPadding(0, dp(10), 0, dp(18)); root.addView(note);

        MetadataStore store = new MetadataStore(this);
        EditText token = new EditText(this);
        token.setHint(store.isConfigured() ? "TMDB token configured — paste a new token to replace it" : "TMDB API Read Access Token");
        token.setSingleLine(true); token.setTextColor(Color.WHITE); token.setHintTextColor(Color.LTGRAY);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        root.addView(token, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));

        Button save = button("Save TMDB token");
        save.setOnClickListener(v -> {
            try { store.saveTmdbToken(token.getText().toString()); token.setText(""); status.setText("TMDB configured ✓"); }
            catch (Exception e) { status.setText("Could not save token: " + message(e)); }
        });
        root.addView(save, params());

        Button clear = button("Disconnect TMDB");
        clear.setOnClickListener(v -> { store.clear(); status.setText("TMDB disconnected"); });
        root.addView(clear, params());

        status = text(store.isConfigured() ? "TMDB configured ✓" : "TMDB not configured", 17, false);
        status.setPadding(0, dp(16), 0, 0); root.addView(status);
        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setAllCaps(false); b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE)); return b;
    }
    private LinearLayout.LayoutParams params() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)); p.topMargin=dp(10); return p; }
    private TextView text(String s, int sp, boolean bold) { TextView v=new TextView(this); v.setText(s); v.setTextColor(Color.WHITE); v.setTextSize(sp); if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v; }
    private static String message(Throwable t){ return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage(); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
