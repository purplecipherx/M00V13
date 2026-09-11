package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Opens a catalog item: cached sources first, then smart one-click playback or source picker. */
public final class MediaOpenActivity extends Activity {
    public static final String EXTRA_MEDIA_ID="media_id";
    public static final String EXTRA_FORCE_PICKER="force_picker";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LoadingOverlay loading;
    private TextView status;
    private MediaCard card;
    private boolean forcePicker;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state); TvUi.disableWindowAnimations(this);
        forcePicker=getIntent().getBooleanExtra(EXTRA_FORCE_PICKER,false);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(TvUi.BG);
        status=TvUi.text(this,"Preparing title…",20,true); status.setGravity(android.view.Gravity.CENTER); root.addView(status,new FrameLayout.LayoutParams(-1,-1)); setContentView(root);
        String id=getIntent().getStringExtra(EXTRA_MEDIA_ID); card=id==null?null:new CatalogStore(this).find(id);
        if(card==null){status.setText("Title metadata is unavailable.");return;}
        open(card);
    }

    private void open(MediaCard media){
        List<SourceOption> fresh=new SourceStore(this).getFresh(media.id);
        if(!fresh.isEmpty()){route(media,fresh);return;}
        loading=LoadingOverlay.show(this,"Searching sources for "+media.title+"…");
        executor.submit(()->{
            try{
                String query=media.title+(media.subtitle==null||media.subtitle.isEmpty()?"":" "+media.subtitle);
                NativeScraperEngine.SearchResult result=new TieredSearchEngine(this).search(query);
                runOnUiThread(()->{
                    hideLoading(); if(dead())return;
                    if(result.sources.isEmpty()){status.setText("No usable sources found for "+media.title+".");return;}
                    new SourceStore(this).put(media.id,result.sources);
                    route(media,result.sources);
                });
            }catch(Exception e){DebugLog.append(this,"OPEN","Source load failed: "+msg(e));runOnUiThread(()->{hideLoading();if(!dead())status.setText("Source search failed: "+msg(e));});}
        });
    }

    private void route(MediaCard media,List<SourceOption> sources){
        if(forcePicker){showSources(media);return;}
        AppSettingsStore settings=new AppSettingsStore(this);
        if(!settings.smartOneClickPlayback()){showSources(media);return;}
        PlayPlanner.Plan plan=new PlayPlanner(this).plan(sources);
        if(!plan.playable()){showSources(media);return;}
        SourceOption best=plan.best;
        status.setText(plan.reason+" — starting…");
        DebugLog.append(this,"PLAYPLAN",media.title+" -> "+best.compactLabel()+" • "+plan.reason);
        if(!best.uri.startsWith("magnet:")){
            startPlayer(media.id,best.uri,directFallbacks(plan.ranked,best.uri));
            return;
        }
        if(!Boolean.TRUE.equals(best.cached)){
            showSources(media);
            return;
        }
        loading=LoadingOverlay.show(this,"Resolving best cached source…");
        executor.submit(()->{
            try{
                String resolved=new RealDebridClient(this).resolveMagnet(best.uri);
                runOnUiThread(()->{hideLoading();if(!dead())startPlayer(media.id,resolved,new ArrayList<>());});
            }catch(Exception e){
                DebugLog.append(this,"PLAYPLAN","Auto resolve failed, opening picker: "+msg(e));
                runOnUiThread(()->{hideLoading();if(!dead())showSources(media);});
            }
        });
    }

    private ArrayList<String> directFallbacks(List<SourceOption> ranked,String primary){
        ArrayList<String> out=new ArrayList<>();
        for(SourceOption s:ranked){
            if(s==null||s.uri==null||s.uri.equals(primary)||s.uri.startsWith("magnet:"))continue;
            out.add(s.uri);
            if(out.size()>=4)break;
        }
        return out;
    }

    private void startPlayer(String mediaId,String uri,ArrayList<String> fallbacks){
        Intent p=new Intent(this,PlayerActivity.class);
        p.putExtra(PlayerActivity.EXTRA_MEDIA_ID,mediaId);
        p.putExtra(PlayerActivity.EXTRA_URI,uri);
        p.putStringArrayListExtra(PlayerActivity.EXTRA_FALLBACK_URIS,fallbacks);
        startActivity(p); finish(); overridePendingTransition(0,0);
    }

    private void showSources(MediaCard media){Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,media.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,media.title);startActivity(i);finish();overridePendingTransition(0,0);}
    private void hideLoading(){if(loading!=null){loading.hide();loading=null;}}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    @Override protected void onDestroy(){hideLoading();executor.shutdownNow();super.onDestroy();}
}
