package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private static final int CACHE_PROBE_LIMIT = 8;
    private LinearLayout results;
    private EditText input;
    private Button searchButton;
    private TextView status;
    private CatalogStore catalog;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state) { super.onCreate(state); catalog = new CatalogStore(this); setContentView(build()); }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this); LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(56), dp(36), dp(56), dp(36)); root.setBackgroundColor(BG); scroll.addView(root);
        root.addView(text("Search", 30, true));
        input = new EditText(this); input.setHint("Movie or show title…"); input.setSingleLine(true); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.rgb(180,165,195)); input.setTextSize(19); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH); input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)); ip.topMargin=dp(12); ip.bottomMargin=dp(12); root.addView(input, ip);
        searchButton = button(new MetadataStore(this).isConfigured() ? "Find movie / show" : "Search sources without metadata"); searchButton.setOnClickListener(v -> beginSearch()); root.addView(searchButton, full(58));
        status = text(new MetadataStore(this).isConfigured() ? "TMDB will identify the exact title first, then M00V13 will scrape sources." : "TMDB is not configured. Raw source search is still available.", 15, false);
        status.setTextColor(Color.rgb(200,185,215)); status.setPadding(0,dp(8),0,dp(14)); root.addView(status);
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        input.setOnEditorActionListener((v, actionId, event) -> { if(actionId==EditorInfo.IME_ACTION_SEARCH){ beginSearch(); return true;} return false; });
        return scroll;
    }

    private void beginSearch() {
        String q = input.getText()==null?"":input.getText().toString().trim(); if(q.isEmpty()){ status.setText("Enter a title first."); return; }
        MetadataStore ms = new MetadataStore(this); if(!ms.isConfigured()){ scrapeRaw(q); return; }
        searchButton.setEnabled(false); status.setText("Finding exact title…"); results.removeAllViews();
        executor.submit(() -> {
            try { List<TmdbClient.Result> found = new TmdbClient(ms.tmdbToken()).searchMulti(q); runOnUiThread(() -> { if(dead())return; searchButton.setEnabled(true); showMetadataResults(found); }); }
            catch(Exception e){ runOnUiThread(() -> { if(dead())return; searchButton.setEnabled(true); status.setText("Metadata search failed: "+msg(e)); }); }
        });
    }

    private void showMetadataResults(List<TmdbClient.Result> found) {
        results.removeAllViews(); if(found.isEmpty()){ status.setText("No matching movie or show found."); return; } status.setText("Choose the exact title:");
        for(TmdbClient.Result r: found){ String type = r.series ? "TV" : "Movie"; Button b = button(r.title + (r.year.isEmpty()?"":" ("+r.year+")") + "  •  " + type); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); b.setOnClickListener(v -> scrapeMetadataResult(r,b)); LinearLayout.LayoutParams p=full(68); p.bottomMargin=dp(9); results.addView(b,p); }
    }

    private List<SourceOption> rankByCacheIfConnected(List<SourceOption> sources) {
        if (!new DebridStore(this).isConnected()) return sources;
        try { return new RealDebridClient(this).probeCache(sources, CACHE_PROBE_LIMIT); }
        catch (Exception ignored) { return sources; }
    }

    private void scrapeMetadataResult(TmdbClient.Result r, Button selected) {
        selected.setEnabled(false); status.setText("Scraping sources for " + r.title + "…"); final LoadingOverlay loading = LoadingOverlay.show(this);
        executor.submit(() -> {
            try {
                MediaCard card = new TmdbClient(new MetadataStore(this).tmdbToken()).toCard(r);
                NativeScraperEngine.SearchResult scrape = new NativeScraperEngine(this).search(r.sourceQuery());
                List<SourceOption> ranked = rankByCacheIfConnected(scrape.sources);
                runOnUiThread(() -> {
                    loading.hide(); if(dead())return; selected.setEnabled(true); catalog.upsert(card);
                    if(ranked.isEmpty()){ status.setText("Title saved, but no usable sources were found."); return; }
                    new SourceStore(this).put(card.id, ranked);
                    int cached = 0; for (SourceOption s : ranked) if (Boolean.TRUE.equals(s.cached)) cached++;
                    status.setText(ranked.size()+" sources found" + (cached>0 ? " • "+cached+" cached" : ""));
                    Intent i=new Intent(this,SourceSelectionActivity.class); i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id); i.putExtra(SourceSelectionActivity.EXTRA_TITLE,r.title); startActivity(i);
                });
            } catch(Exception e){ runOnUiThread(() -> { loading.hide(); if(dead())return; selected.setEnabled(true); status.setText("Search failed: "+msg(e)); }); }
        });
    }

    private void scrapeRaw(String query) {
        searchButton.setEnabled(false); status.setText("Searching sources…"); final LoadingOverlay loading=LoadingOverlay.show(this);
        executor.submit(() -> {
            NativeScraperEngine.SearchResult r = new NativeScraperEngine(this).search(query); List<SourceOption> ranked = rankByCacheIfConnected(r.sources);
            runOnUiThread(() -> { loading.hide(); if(dead())return; searchButton.setEnabled(true); if(ranked.isEmpty()){ status.setText("No usable sources found."); return; }
                String id="search_"+Integer.toHexString(query.toLowerCase(java.util.Locale.US).hashCode()); new SourceStore(this).put(id,ranked);
                Intent i=new Intent(this,SourceSelectionActivity.class); i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,id); i.putExtra(SourceSelectionActivity.EXTRA_TITLE,query); startActivity(i); });
        });
    }

    private Button button(String s){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setAllCaps(false); b.setFocusable(true); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE)); return b; }
    private LinearLayout.LayoutParams full(int h){ return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(h)); }
    private TextView text(String s,int sp,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(sp);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v; }
    private boolean dead(){ return isFinishing() || isDestroyed(); }
    private static String msg(Throwable t){ Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage(); }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
