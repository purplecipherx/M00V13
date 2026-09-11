package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SourceSelectionActivity extends Activity {
    public static final String EXTRA_MEDIA_ID="media_id", EXTRA_TITLE="title", EXTRA_URIS="uris", EXTRA_LABELS="labels";
    private final ExecutorService resolverExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService artExecutor=Executors.newFixedThreadPool(2);
    private TextView status;
    private String mediaId;
    private ArrayList<String> uris;
    private ArrayList<String> labels;
    private MediaCard media;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state); TvUi.disableWindowAnimations(this);
        String title=getIntent().getStringExtra(EXTRA_TITLE); mediaId=getIntent().getStringExtra(EXTRA_MEDIA_ID); media=mediaId==null?null:new CatalogStore(this).find(mediaId);
        uris=getIntent().getStringArrayListExtra(EXTRA_URIS); labels=getIntent().getStringArrayListExtra(EXTRA_LABELS);
        if((uris==null||uris.isEmpty())&&mediaId!=null){List<SourceOption> cached=new SourceStore(this).getFresh(mediaId);uris=new ArrayList<>();labels=new ArrayList<>();for(SourceOption s:cached){if(s.uri==null||s.uri.trim().isEmpty())continue;uris.add(s.uri);labels.add(s.compactLabel()+"\n"+s.provider);}}
        if(uris==null)uris=new ArrayList<>(); if(labels==null)labels=new ArrayList<>(); setContentView(build(title));
    }

    private View build(String title){
        ScreenProfile sp=ScreenProfile.detect(this); ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,28),TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,30));root.setBackgroundColor(TvUi.BG);scroll.addView(root);
        root.addView(TvUi.text(this,title==null?"Choose source":title,sp.mobile()?26:30,true));TextView hint=TvUi.text(this,"Best source first • RD-cached sources rank highest • bad/infringing sources are removed",15,false);hint.setTextColor(TvUi.MUTED);hint.setPadding(0,dp(6),0,dp(8));root.addView(hint);
        status=TvUi.text(this,new DebridStore(this).isConnected()?"Real-Debrid connected":"Real-Debrid not connected",15,false);status.setTextColor(TvUi.MUTED);status.setPadding(0,0,0,dp(14));root.addView(status);
        if(uris.isEmpty()){root.addView(TvUi.text(this,"No fresh playable sources are available.",20,false));return scroll;}
        for(int i=0;i<uris.size();i++)root.addView(sourceRow(i));return scroll;
    }

    private View sourceRow(int index){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(6),dp(5),dp(6),dp(5));row.setBackground(TvUi.focusBackground(this,false,dp(8)));row.setFocusable(true);row.setClickable(true);row.setStateListAnimator(null);
        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackgroundColor(TvUi.CARD);row.addView(thumb,new LinearLayout.LayoutParams(dp(96),dp(64)));loadArtwork(thumb);
        String label=index<labels.size()?labels.get(index):"Source "+(index+1);TextView text=TvUi.text(this,label,15,true);text.setMaxLines(3);text.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(68),1f);tp.setMarginStart(dp(14));row.addView(text,tp);
        row.setOnFocusChangeListener((v,f)->{row.animate().cancel();row.setScaleX(1f);row.setScaleY(1f);row.setBackground(TvUi.focusBackground(this,f,dp(8)));text.setTextColor(f?TvUi.BLUE:TvUi.WHITE);if(f&&new AppSettingsStore(this).clickSounds())row.playSoundEffect(android.view.SoundEffectConstants.CLICK);});
        row.setOnClickListener(v->openSource(row,index));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(82));p.bottomMargin=dp(8);row.setLayoutParams(p);return row;
    }

    private void loadArtwork(ImageView v){if(media==null||media.artworkUrl==null||media.artworkUrl.isEmpty())return;artExecutor.submit(()->{try{File f=new ArtworkCache(this).fetch(media.artworkUrl,78,240);if(f==null)return;android.graphics.Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&b!=null)v.setImageBitmap(b);});}catch(Exception ignored){}});}

    private void openSource(View row,int selected){
        if(selected<0||selected>=uris.size())return;String uri=uris.get(selected);
        if(!uri.startsWith("magnet:")){startPlayer(uri,directFallbacks(selected));return;}
        if(!new DebridStore(this).isConnected()){status.setText("Connect Real-Debrid first.");startActivity(new Intent(this,DebridActivity.class));return;}
        row.setEnabled(false);status.setText("Resolving selected source through Real-Debrid…");LoadingOverlay loading=LoadingOverlay.show(this,"Resolving source…");
        resolverExecutor.submit(()->{try{String resolved=new RealDebridClient(this).resolveMagnet(uri);runOnUiThread(()->{loading.hide();if(dead())return;status.setText("Resolved — starting playback");startPlayer(resolved,new ArrayList<>());});}
        catch(Exception e){String m=msg(e);boolean bad=e instanceof RealDebridClient.InfringingSourceException||m.toLowerCase().contains("infring")||m.toLowerCase().contains("not instantly available");DebugLog.append(this,"SOURCE","Resolve failed: "+m);runOnUiThread(()->{loading.hide();if(dead())return;row.setEnabled(true);if(bad){new SourceStore(this).removeUri(mediaId,uri);status.setText("Source rejected by Real-Debrid and removed. Choose another source.");row.setVisibility(View.GONE);}else status.setText("Resolve failed: "+m);});}});
    }

    private void startPlayer(String uri,ArrayList<String> fallbackUris){Intent p=new Intent(this,PlayerActivity.class);p.putExtra(PlayerActivity.EXTRA_MEDIA_ID,mediaId);p.putExtra(PlayerActivity.EXTRA_URI,uri);p.putStringArrayListExtra(PlayerActivity.EXTRA_FALLBACK_URIS,fallbackUris);startActivity(p);}
    private ArrayList<String> directFallbacks(int selected){ArrayList<String> out=new ArrayList<>();for(int i=selected+1;i<uris.size();i++)if(!uris.get(i).startsWith("magnet:"))out.add(uris.get(i));for(int i=0;i<selected;i++)if(!uris.get(i).startsWith("magnet:"))out.add(uris.get(i));return out;}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private int dp(int v){return TvUi.dp(this,v);}
    @Override protected void onDestroy(){resolverExecutor.shutdownNow();artExecutor.shutdownNow();super.onDestroy();}
}
