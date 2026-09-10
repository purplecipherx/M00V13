package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private LinearLayout results;
    private CatalogStore catalog;
    private EditText input;
    private Button onlineButton;
    private TextView onlineStatus;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        catalog = new CatalogStore(this);
        setContentView(build());
    }

    @Override protected void onDestroy() {
        searchExecutor.shutdownNow();
        super.onDestroy();
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(56), dp(36), dp(56), dp(36));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        root.addView(text("Search", 30, true));
        input = new EditText(this);
        input.setHint("Movie or show title…");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(180, 165, 195));
        input.setTextSize(19);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setFocusable(true);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62));
        ip.topMargin = dp(12); ip.bottomMargin = dp(12);
        root.addView(input, ip);

        onlineButton = new Button(this);
        onlineButton.setText("Search sources");
        onlineButton.setTextColor(Color.WHITE);
        onlineButton.setTextSize(17);
        onlineButton.setAllCaps(false);
        onlineButton.setFocusable(true);
        onlineButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        onlineButton.setOnClickListener(v -> runOnlineSearch());
        root.addView(onlineButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));

        onlineStatus = text("Local catalog results appear below. Press Search sources to scrape fresh provider results.", 15, false);
        onlineStatus.setTextColor(Color.rgb(200, 185, 215));
        onlineStatus.setPadding(0, dp(8), 0, dp(14));
        root.addView(onlineStatus);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        root.addView(results);
        render("");

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runOnlineSearch();
                return true;
            }
            return false;
        });
        return scroll;
    }

    private void runOnlineSearch() {
        final String query = input.getText() == null ? "" : input.getText().toString().trim();
        if (query.isEmpty()) {
            onlineStatus.setText("Enter a movie or show title first.");
            return;
        }
        onlineButton.setEnabled(false);
        onlineStatus.setText("Searching provider tiers…");
        searchExecutor.submit(() -> {
            NativeScraperEngine.SearchResult result = new NativeScraperEngine().search(query);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                onlineButton.setEnabled(true);
                if (result.sources.isEmpty()) {
                    if (result.providerErrors.isEmpty()) onlineStatus.setText("No usable sources found.");
                    else onlineStatus.setText("No usable sources found • " + result.providerErrors.get(0));
                    return;
                }
                String mediaId = searchMediaId(query);
                new SourceStore(this).put(mediaId, result.sources);
                onlineStatus.setText(result.sources.size() + " fresh sources found");
                Intent choose = new Intent(this, SourceSelectionActivity.class);
                choose.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID, mediaId);
                choose.putExtra(SourceSelectionActivity.EXTRA_TITLE, query);
                startActivity(choose);
            });
        });
    }

    private void render(String query) {
        results.removeAllViews();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) {
            TextView hint = text("Start typing. The Android TV keyboard can be used with the remote or voice keyboard.", 17, false);
            hint.setTextColor(Color.rgb(200, 185, 215));
            results.addView(hint);
            return;
        }

        List<MediaCard> matches = new ArrayList<>();
        for (MediaCard item : catalog.all()) {
            if (matches(item, q)) matches.add(item);
            if (matches.size() >= 30) break;
        }
        if (matches.isEmpty()) {
            results.addView(text("No local catalog matches. Search sources will query the native scraper engine.", 18, false));
            return;
        }
        for (MediaCard item : matches) results.addView(card(item));
    }

    private boolean matches(MediaCard item, String q) {
        if (item.title.toLowerCase(Locale.US).contains(q)) return true;
        if (item.subtitle != null && item.subtitle.toLowerCase(Locale.US).contains(q)) return true;
        if (item.genre.toLowerCase(Locale.US).contains(q)) return true;
        for (String tag : item.tags) if (tag.toLowerCase(Locale.US).contains(q)) return true;
        return false;
    }

    private Button card(MediaCard item) {
        Button b = new Button(this);
        b.setText(item.title + (item.subtitle == null || item.subtitle.isEmpty() ? "" : "\n" + item.subtitle));
        b.setTextColor(Color.WHITE); b.setTextSize(17); b.setAllCaps(false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(68));
        p.bottomMargin = dp(9); b.setLayoutParams(p);
        b.setOnClickListener(v -> {
            Intent i = new Intent(this, SourceSelectionActivity.class);
            i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID, item.id);
            i.putExtra(SourceSelectionActivity.EXTRA_TITLE, item.title);
            startActivity(i);
        });
        return b;
    }

    private static String searchMediaId(String query) {
        return "search_" + Integer.toHexString(query.trim().toLowerCase(Locale.US).hashCode());
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
