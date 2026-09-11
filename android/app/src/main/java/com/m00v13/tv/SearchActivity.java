package com.m00v13.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SearchActivity extends Activity {
    private LinearLayout results;
    private LinearLayout recent;
    private EditText input;
    private Button searchButton;
    private TextView status;
    private CatalogStore catalog;
    private SearchHistoryStore history;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean searchRunning = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        catalog = new CatalogStore(this);
        history = new SearchHistoryStore(this);
        setContentView(build());
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }

    private ScrollView build() {
        ScreenProfile screen=ScreenProfile.detect(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(screen.sidePaddingDp), dp(screen.mobile()?20:36), dp(screen.sidePaddingDp), dp(36));
        root.setBackgroundColor(TvUi.BG);
        scroll.addView(root);

        root.addView(TvUi.text(this, "Search", screen.mobile()?26:30, true));
        input = new EditText(this);
        input.setHint("Movie or show title…"); input.setSingleLine(true); input.setTextColor(TvUi.WHITE); input.setHintTextColor(TvUi.MUTED); input.setTextSize(screen.mobile()?17:19); input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(TvUi.PURPLE));
        input.setOnFocusChangeListener((v, focused) -> input.setTextColor(focused ? TvUi.BLUE : TvUi.WHITE));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)); ip.topMargin = dp(12); ip.bottomMargin = dp(12); root.addView(input, ip);

        searchButton = TvUi.button(this, "Find movie / show"); searchButton.setOnClickListener(v -> beginSearch()); root.addView(searchButton, full(58));
        status = TvUi.text(this, new MetadataStore(this).isConfigured()
            ? "TMDB metadata enabled • exact-title search first, then provider waterfall."
            : "Keyless metadata enabled • posters and movie/TV matches do not require a TMDB key.", 15, false);
        status.setTextColor(TvUi.MUTED); status.setPadding(0, dp(8), 0, dp(10)); root.addView(status);

        recent = new LinearLayout(this); recent.setOrientation(LinearLayout.VERTICAL); root.addView(recent);
        renderRecent();
        results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); root.addView(results);
        input.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_SEARCH) { beginSearch(); return true; } return false; });
        return scroll;
    }

    private void renderRecent(){
        if(recent==null)return;
        recent.removeAllViews();
        List<String> items=history.recent();
        if(items.isEmpty())return;
        TextView h=TvUi.text(this,"Recent searches — hold to delete",17,true);h.setTextColor(TvUi.MUTED);h.setPadding(0,dp(6),0,dp(6));recent.addView(h);
        int limit=Math.min(8,items.size());
        for(int i=0;i<limit;i++){
            String q=items.get(i);
            Button b=TvUi.button(this,q); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v->{input.setText(q);input.setSelection(q.length());beginSearch();});
            b.setOnLongClickListener(v->{history.remove(q);renderRecent();status.setText("Removed from recent searches.");return true;});
            LinearLayout.LayoutParams p=full(50);p.bottomMargin=dp(5);recent.addView(b,p);
        }
    }

    private void beginSearch() {
        if (searchRunning) return;
        String q = input.getText() == null ? "" : input.getText().toString().trim();
        if (q.isEmpty()) { status.setText("Enter a title first."); return; }
        history.add(q); renderRecent();
        hideKeyboard(); input.clearFocus(); searchButton.requestFocus(); searchRunning = true; searchButton.setEnabled(false); results.removeAllViews();
        DebugLog.append(this,"SEARCH","Metadata lookup query='"+q+"'");

        MetadataStore ms = new MetadataStore(this);
        status.setText("Finding title…");
        executor.submit(() -> {
            try {
                if (ms.isConfigured()) {
                    List<TmdbClient.Result> found = new TmdbClient(ms.tmdbToken()).searchMulti(q);
                    runOnUiThread(() -> { if (dead()) return; finishBusy(); showTmdbResults(found); });
                } else {
                    CinemetaClient cm=new CinemetaClient();
                    ArrayList<MediaCard> found=new ArrayList<>();
                    found.addAll(cm.searchMovies(q)); found.addAll(cm.searchSeries(q));
                    runOnUiThread(() -> { if (dead()) return; finishBusy(); showCinemetaResults(found); });
                }
            } catch (Exception e) {
                DebugLog.append(this,"SEARCH","Metadata lookup failed: "+msg(e));
                runOnUiThread(() -> { if (dead()) return; finishBusy(); status.setText("Metadata lookup failed. " + msg(e)); });
            }
        });
    }

    private void showTmdbResults(List<TmdbClient.Result> found) {
        results.removeAllViews(); if (found.isEmpty()) { status.setText("No exact match. Try a broader title or alternate spelling."); return; } status.setText("Choose a title:");
        for (TmdbClient.Result r : found) {
            String type = r.series ? "TV" : "Movie";
            Button b = TvUi.button(this, r.title + (r.year.isEmpty() ? "" : " (" + r.year + ")") + "  •  " + type);
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setOnClickListener(v -> scrapeTmdbResult(r, b)); LinearLayout.LayoutParams p = full(68); p.bottomMargin = dp(9); results.addView(b, p);
        }
    }

    private void showCinemetaResults(List<MediaCard> found) {
        results.removeAllViews(); if(found.isEmpty()){status.setText("No exact match. Try a broader title or alternate spelling.");return;} status.setText("Choose a title:");
        int limit=Math.min(20,found.size());
        for(int i=0;i<limit;i++){
            MediaCard card=found.get(i); String type=card.series?"TV":"Movie"; String year=card.subtitle==null||card.subtitle.isEmpty()?"":" ("+card.subtitle+")";
            Button b=TvUi.button(this,card.title+year+"  •  "+type); b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); b.setOnClickListener(v->scrapeCard(card,b)); LinearLayout.LayoutParams p=full(68);p.bottomMargin=dp(9);results.addView(b,p);
        }
    }

    private void scrapeTmdbResult(TmdbClient.Result r, Button selected) {
        MediaCard card=new TmdbClient(new MetadataStore(this).tmdbToken()).toCard(r);
        scrapeCard(card,selected);
    }

    private void scrapeCard(MediaCard card, Button selected) {
        if (searchRunning) return;
        List<SourceOption> cached=new SourceStore(this).getFresh(card.id);
        if(!cached.isEmpty()){
            catalog.upsert(card);
            status.setText(cached.size()+" cached sources — opening immediately");
            Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,card.title);startActivity(i);return;
        }
        searchRunning=true; selected.setEnabled(false); status.setText("Searching sources for " + card.title + "…"); final LoadingOverlay loading=LoadingOverlay.show(this);
        executor.submit(() -> {
            try {
                String query=card.title+(card.subtitle==null||card.subtitle.isEmpty()?"":" "+card.subtitle);
                NativeScraperEngine.SearchResult scrape=new TieredSearchEngine(this).search(query); List<SourceOption> ranked=scrape.sources;
                runOnUiThread(() -> {
                    loading.hide(); if(dead())return; searchRunning=false; selected.setEnabled(true); catalog.upsert(card);
                    if(ranked.isEmpty()){status.setText("Title saved, but no usable sources were found.");return;}
                    new SourceStore(this).put(card.id,ranked); int cachedCount=0;for(SourceOption s:ranked)if(Boolean.TRUE.equals(s.cached))cachedCount++;
                    status.setText(ranked.size()+" sources found"+(cachedCount>0?" • "+cachedCount+" debrid-cached":""));
                    Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,card.title);startActivity(i);
                });
            }catch(Exception e){DebugLog.append(this,"SEARCH","Source search failed: "+msg(e));runOnUiThread(()->{loading.hide();if(dead())return;searchRunning=false;selected.setEnabled(true);status.setText("Search failed: "+msg(e));});}
        });
    }

    private void finishBusy(){searchRunning=false;searchButton.setEnabled(true);searchButton.requestFocus();}
    private void hideKeyboard(){InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.hideSoftInputFromWindow(input.getWindowToken(),0);}
    private LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(h));}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private int dp(int v){return TvUi.dp(this,v);}
}
