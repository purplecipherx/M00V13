package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BrowseActivity extends Activity {
    public static final String EXTRA_KIND = "kind";
    public static final String KIND_MOVIES = "movies";
    public static final String KIND_TV = "tv";
    private static final int BG = Color.rgb(9, 5, 15);
    private final ExecutorService artExecutor=Executors.newFixedThreadPool(2);
    private ScreenProfile screen;
    private ImageView previewArt;
    private TextView previewTitle,previewMeta;
    private int previewToken=0;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        screen=ScreenProfile.detect(this);
        String kind = getIntent().getStringExtra(EXTRA_KIND);
        setContentView(build(KIND_TV.equals(kind)));
    }
    @Override protected void onDestroy(){artExecutor.shutdownNow();super.onDestroy();}

    private View build(boolean tv) {
        List<MediaCard> items = new ArrayList<>();
        for (MediaCard item : new CatalogStore(this).all()) if (tv == item.series) items.add(item);
        Collections.sort(items, Comparator.comparing(a -> a.title.toLowerCase()));

        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(screen.sidePaddingDp),dp(24),dp(screen.sidePaddingDp),dp(24));page.setBackgroundColor(BG);
        page.addView(TvUi.text(this,tv?"TV Shows":"Movies",screen.mobile()?27:31,true));
        if(items.isEmpty()){
            TextView empty=TvUi.text(this,"Your local catalog is empty. Search or select something from Home to populate it.",18,false);empty.setTextColor(TvUi.MUTED);empty.setPadding(0,dp(18),0,0);page.addView(empty);return page;
        }

        if(screen.mobile()){
            ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
            for(MediaCard item:items)list.addView(row(item));page.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));return page;
        }

        LinearLayout split=new LinearLayout(this);split.setOrientation(LinearLayout.HORIZONTAL);split.setPadding(0,dp(18),0,0);
        ScrollView leftScroll=new ScrollView(this);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);leftScroll.addView(left);
        for(MediaCard item:items)left.addView(row(item));
        split.addView(leftScroll,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,0.54f));

        LinearLayout preview=new LinearLayout(this);preview.setOrientation(LinearLayout.VERTICAL);preview.setGravity(Gravity.CENTER_HORIZONTAL);preview.setPadding(dp(28),0,0,0);
        previewArt=new ImageView(this);previewArt.setScaleType(ImageView.ScaleType.CENTER_CROP);previewArt.setBackgroundColor(TvUi.CARD);
        int pw=Math.min(dp(360),Math.max(dp(240),screen.widthPx/4));int ph=(int)(pw*1.5f);preview.addView(previewArt,new LinearLayout.LayoutParams(pw,ph));
        previewTitle=TvUi.text(this,"",25,true);previewTitle.setPadding(0,dp(14),0,dp(4));preview.addView(previewTitle,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)));
        previewMeta=TvUi.text(this,"",16,false);previewMeta.setTextColor(TvUi.MUTED);previewMeta.setGravity(Gravity.TOP);preview.addView(previewMeta,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        split.addView(preview,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,0.46f));
        page.addView(split,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
        showPreview(items.get(0));
        return page;
    }

    private View row(MediaCard item){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);box.setFocusable(true);box.setClickable(true);box.setPadding(dp(10),dp(6),dp(10),dp(6));box.setBackground(TvUi.focusBackground(this,false,dp(8)));
        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setBackgroundColor(TvUi.CARD);box.addView(thumb,new LinearLayout.LayoutParams(dp(68),dp(96)));
        String detail=item.subtitle==null||item.subtitle.isEmpty()?"":item.subtitle;if(item.isEpisode())detail+=(detail.isEmpty()?"":" • ")+"S"+item.seasonNumber+"E"+item.episodeNumber;
        TextView t=TvUi.text(this,item.title+(detail.isEmpty()?"":"\n"+detail),17,true);t.setMaxLines(2);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(96),1f);tp.setMarginStart(dp(14));box.addView(t,tp);
        loadArtwork(thumb,item.artworkUrl,220,0);
        box.setOnFocusChangeListener((v,f)->{box.setBackground(TvUi.focusBackground(this,f,dp(8)));t.setTextColor(f?TvUi.BLUE:TvUi.WHITE);if(f){showPreview(item);if(new AppSettingsStore(this).clickSounds())box.playSoundEffect(android.view.SoundEffectConstants.CLICK);}});
        box.setOnClickListener(v->open(item));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(110));p.bottomMargin=dp(7);box.setLayoutParams(p);return box;
    }

    private void showPreview(MediaCard item){
        if(previewTitle==null)return;previewTitle.setText(item.title);String type=item.series?"TV":"Movie";String meta=type+(item.subtitle==null||item.subtitle.isEmpty()?"":" • "+item.subtitle)+(item.genre==null||item.genre.isEmpty()?"":"\n"+item.genre);previewMeta.setText(meta);previewArt.setImageDrawable(null);int token=++previewToken;loadArtwork(previewArt,item.artworkUrl,720,token);
    }

    private void loadArtwork(ImageView target,String url,int width,int token){if(url==null||url.isEmpty())return;artExecutor.submit(()->{try{File f=new ArtworkCache(this).fetch(url,82,width);if(f==null)return;android.graphics.Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&b!=null&&(token==0||token==previewToken))target.setImageBitmap(b);});}catch(Exception e){DebugLog.append(this,"ART","Browse art failed: "+e.getMessage());}});}

    private void open(MediaCard item) {
        Intent i = new Intent(this, SourceSelectionActivity.class);
        i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID, item.id);
        i.putExtra(SourceSelectionActivity.EXTRA_TITLE, item.title);
        startActivity(i);
    }
    private int dp(int value) { return TvUi.dp(this,value); }
}
