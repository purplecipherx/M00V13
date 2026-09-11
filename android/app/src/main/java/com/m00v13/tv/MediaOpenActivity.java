package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opens a catalog item directly: cached sources first, otherwise scrape the selected title. */
public final class MediaOpenActivity extends Activity {
    public static final String EXTRA_MEDIA_ID="media_id";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LoadingOverlay loading;
    private TextView status;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state); TvUi.disableWindowAnimations(this);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(TvUi.BG);
        status=TvUi.text(this,"Preparing title…",20,true); status.setGravity(android.view.Gravity.CENTER); root.addView(status,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        String id=getIntent().getStringExtra(EXTRA_MEDIA_ID); MediaCard card=id==null?null:new CatalogStore(this).find(id);
        if(card==null){status.setText("Title metadata is unavailable.");return;}
        open(card);
    }

    private void open(MediaCard card){
        List<SourceOption> fresh=new SourceStore(this).getFresh(card.id);
        if(!fresh.isEmpty()){showSources(card);return;}
        loading=LoadingOverlay.show(this,"Searching sources for "+card.title+"…");
        executor.submit(()->{
            try{
                String query=card.title+(card.subtitle==null||card.subtitle.isEmpty()?"":" "+card.subtitle);
                NativeScraperEngine.SearchResult result=new TieredSearchEngine(this).search(query);
                runOnUiThread(()->{
                    hideLoading(); if(dead())return;
                    if(result.sources.isEmpty()){status.setText("No usable sources found for "+card.title+".");return;}
                    new SourceStore(this).put(card.id,result.sources); showSources(card);
                });
            }catch(Exception e){DebugLog.append(this,"OPEN","Source load failed: "+msg(e));runOnUiThread(()->{hideLoading();if(!dead())status.setText("Source search failed: "+msg(e));});}
        });
    }

    private void showSources(MediaCard card){Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,card.title);startActivity(i);finish();}
    private void hideLoading(){if(loading!=null){loading.hide();loading=null;}}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    @Override protected void onDestroy(){hideLoading();executor.shutdownNow();super.onDestroy();}
}
