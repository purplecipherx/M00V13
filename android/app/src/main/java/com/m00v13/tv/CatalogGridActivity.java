package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** See-all grid using recycled poster views and lazy artwork. */
public final class CatalogGridActivity extends Activity {
    public static final String EXTRA_TITLE="title",EXTRA_IDS="ids",EXTRA_FOCUS_KEY="focus_key";
    private static final int BG=Color.rgb(4,3,12),PANEL=Color.rgb(13,8,27),BLUE=Color.rgb(44,157,255),PURPLE=Color.rgb(180,78,255),WHITE=Color.rgb(247,245,250),MUTED=Color.rgb(190,181,202);

    private final ExecutorService artPool=Executors.newFixedThreadPool(2);
    private final ExecutorService dataPool=Executors.newSingleThreadExecutor();
    private final Map<String,String> descriptions=new HashMap<>();
    private CatalogStore catalog;
    private ArtworkLoader artwork;
    private ImageView previewArt;
    private TextView previewTitle,previewMeta,previewDesc;
    private String focusKey,focusId;
    private int token;
    private boolean clickSounds;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        TvUi.disableWindowAnimations(this);
        catalog=new CatalogStore(this);
        artwork=new ArtworkLoader(this);
        clickSounds=new AppSettingsStore(this).clickSounds();
        focusKey=getIntent().getStringExtra(EXTRA_FOCUS_KEY);
        String key=focusKey==null?"grid":focusKey;
        focusId=getSharedPreferences("m00v13_grid_focus",MODE_PRIVATE).getString(key,null);
        render();
    }

    @Override protected void onPause(){
        if(focusId!=null){String key=focusKey==null?"grid":focusKey;getSharedPreferences("m00v13_grid_focus",MODE_PRIVATE).edit().putString(key,focusId).apply();}
        super.onPause();
    }

    @Override protected void onDestroy(){artPool.shutdownNow();dataPool.shutdownNow();super.onDestroy();}

    private void render(){
        int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        int side=w<1000?0:(int)(w*.21f);
        if(side>0)root.addView(preview(side,h),new FrameLayout.LayoutParams(side,h));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(18),dp(18),dp(18));body.setBackgroundColor(BG);
        TextView title=text(getIntent().getStringExtra(EXTRA_TITLE),28,true);body.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));

        ArrayList<String> ids=getIntent().getStringArrayListExtra(EXTRA_IDS);if(ids==null)ids=new ArrayList<>();
        ArrayList<MediaCard> cards=new ArrayList<>(ids.size());for(String id:ids){MediaCard m=catalog.find(id);if(m!=null)cards.add(m);}

        int cols=w<700?2:w<1000?4:5;
        RecyclerView grid=new RecyclerView(this);grid.setHasFixedSize(true);grid.setItemAnimator(null);grid.setOverScrollMode(View.OVER_SCROLL_NEVER);grid.setVerticalScrollBarEnabled(false);grid.setItemViewCacheSize(Math.max(6,cols*2));
        GridLayoutManager manager=new GridLayoutManager(this,cols);manager.setInitialPrefetchItemCount(cols);grid.setLayoutManager(manager);
        int avail=w-side-dp(36),cw=Math.max(dp(120),(avail-dp(12)*(cols-1))/cols),ch=(int)(cw*1.49f);
        GridAdapter adapter=new GridAdapter(cards,cw,ch,grid);grid.setAdapter(adapter);
        body.addView(grid,new LinearLayout.LayoutParams(-1,0,1f));

        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(w-side,h);bp.leftMargin=side;root.addView(body,bp);setContentView(root);
        restore(grid,adapter);
    }

    private View preview(int w,int h){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(14),dp(18),dp(14),dp(18));p.setBackgroundColor(Color.rgb(3,3,11));previewArt=new ImageView(this);previewArt.setScaleType(ImageView.ScaleType.CENTER_CROP);previewArt.setBackgroundColor(PANEL);p.addView(previewArt,new LinearLayout.LayoutParams(-1,Math.min((int)(h*.58f),(int)(w*1.48f))));previewTitle=text("Select a title",21,true);previewTitle.setPadding(0,dp(12),0,0);p.addView(previewTitle);previewMeta=text("",13,false);previewMeta.setTextColor(PURPLE);p.addView(previewMeta);previewDesc=text("Hover a title to preview it.",14,false);previewDesc.setTextColor(MUTED);previewDesc.setMaxLines(7);previewDesc.setEllipsize(TextUtils.TruncateAt.END);previewDesc.setPadding(0,dp(8),0,0);p.addView(previewDesc);return p;}

    private void showPreview(MediaCard m){
        if(previewTitle==null)return;
        previewTitle.setText(m.title);String meta=(m.series?"TV Series":"Movie")+(m.subtitle==null||m.subtitle.isEmpty()?"":" • "+m.subtitle)+(m.genre.isEmpty()?"":" • "+m.genre);previewMeta.setText(meta);previewDesc.setText(meta);
        artwork.load(previewArt,m.artworkUrl,700,artPool);
        int t=++token;String cached=descriptions.get(m.id);if(cached!=null){previewDesc.setText(cached);return;}
        previewDesc.postDelayed(()->{if(isFinishing()||isDestroyed()||t!=token)return;dataPool.submit(()->{String d;try{d=new CinemetaClient().description(m);}catch(Exception e){d="";}if(d==null||d.isEmpty())d=meta;descriptions.put(m.id,d);final String x=d;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&t==token)previewDesc.setText(x);});});},300);
    }

    private void restore(RecyclerView grid,GridAdapter adapter){if(focusId==null)return;int pos=adapter.positionOf(focusId);if(pos<0)return;grid.scrollToPosition(pos);grid.post(()->{RecyclerView.ViewHolder h=grid.findViewHolderForAdapterPosition(pos);if(h!=null)h.itemView.requestFocus();});}

    private final class GridAdapter extends RecyclerView.Adapter<Holder>{
        private final List<MediaCard> cards;private final int cw,ch;private final RecyclerView grid;
        GridAdapter(List<MediaCard> cards,int cw,int ch,RecyclerView grid){this.cards=cards;this.cw=cw;this.ch=ch;this.grid=grid;setHasStableIds(true);}
        @Override public long getItemId(int position){return cards.get(position).id.hashCode();}
        int positionOf(String id){for(int i=0;i<cards.size();i++)if(cards.get(i).id.equals(id))return i;return -1;}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){FrameLayout c=new FrameLayout(CatalogGridActivity.this);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(cw,ch);lp.rightMargin=dp(12);lp.bottomMargin=dp(12);c.setLayoutParams(lp);c.setFocusable(true);c.setClickable(true);c.setStateListAnimator(null);c.setPadding(dp(3),dp(3),dp(3),dp(3));c.setBackground(outline(false));ImageView im=new ImageView(CatalogGridActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackgroundColor(PANEL);c.addView(im,new FrameLayout.LayoutParams(-1,-1));TextView n=text("",12,true);n.setGravity(Gravity.BOTTOM);n.setMaxLines(2);n.setEllipsize(TextUtils.TruncateAt.END);n.setPadding(dp(7),0,dp(5),dp(7));n.setBackgroundColor(Color.argb(95,0,0,0));c.addView(n,new FrameLayout.LayoutParams(-1,(int)(ch*.25f),Gravity.BOTTOM));return new Holder(c,im,n);}
        @Override public void onBindViewHolder(Holder h,int position){MediaCard m=cards.get(position);h.name.setText(m.title);h.name.setTextColor(WHITE);h.card.setBackground(outline(false));artwork.load(h.image,m.artworkUrl,Math.min(cw*2,getResources().getDisplayMetrics().widthPixels),artPool);h.card.setOnFocusChangeListener((v,f)->{h.card.setBackground(outline(f));h.name.setTextColor(f?BLUE:WHITE);if(f){focusId=m.id;showPreview(m);if(clickSounds)h.card.playSoundEffect(SoundEffectConstants.CLICK);}});h.card.setOnClickListener(v->{Intent i=new Intent(CatalogGridActivity.this,MediaOpenActivity.class);i.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID,m.id);startActivity(i);overridePendingTransition(0,0);});}
        @Override public int getItemCount(){return cards.size();}
    }

    private static final class Holder extends RecyclerView.ViewHolder{final FrameLayout card;final ImageView image;final TextView name;Holder(FrameLayout c,ImageView i,TextView n){super(c);card=c;image=i;name=n;}}
    private GradientDrawable outline(boolean f){GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(6));g.setStroke(dp(f?3:1),f?BLUE:Color.rgb(42,34,53));return g;}
    private TextView text(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextColor(WHITE);v.setTextSize(z);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private int dp(int x){return TvUi.dp(this,x);}
}
