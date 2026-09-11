package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** See-all grid with the same poster geometry and preview model as MediaHubActivity. */
public final class CatalogGridActivity extends Activity {
    public static final String EXTRA_TITLE="title",EXTRA_IDS="ids",EXTRA_FOCUS_KEY="focus_key";
    private static final int BG=Color.rgb(4,3,12),PANEL=Color.rgb(13,8,27),BLUE=Color.rgb(44,157,255),PURPLE=Color.rgb(180,78,255),WHITE=Color.rgb(247,245,250),MUTED=Color.rgb(190,181,202);
    private final ExecutorService pool=Executors.newFixedThreadPool(3);private CatalogStore catalog;private ImageView previewArt;private TextView previewTitle,previewMeta,previewDesc;private String focusKey;private int token;
    @Override protected void onCreate(Bundle b){super.onCreate(b);TvUi.disableWindowAnimations(this);catalog=new CatalogStore(this);focusKey=getIntent().getStringExtra(EXTRA_FOCUS_KEY);render();}
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
    private void render(){int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);int side=w<1000?0:(int)(w*.21f);if(side>0){View p=preview(side,h);root.addView(p,new FrameLayout.LayoutParams(side,h));}
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(18),dp(18),dp(32));scroll.addView(content);TextView title=text(getIntent().getStringExtra(EXTRA_TITLE),28,true);content.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
        GridLayout grid=new GridLayout(this);int cols=w<700?2:w<1000?4:5;grid.setColumnCount(cols);content.addView(grid);ArrayList<String> ids=getIntent().getStringArrayListExtra(EXTRA_IDS);if(ids==null)ids=new ArrayList<>();int avail=w-side-dp(36),cw=Math.max(dp(120),(avail-dp(12)*(cols-1))/cols),ch=(int)(cw*1.49f);
        for(String id:ids){MediaCard m=catalog.find(id);if(m==null)continue;View c=card(m,cw,ch);GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=cw;gp.height=ch;gp.setMargins(0,0,dp(12),dp(12));grid.addView(c,gp);}FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(w-side,h);sp.leftMargin=side;root.addView(scroll,sp);setContentView(root);restore(root);}
    private View preview(int w,int h){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(14),dp(18),dp(14),dp(18));p.setBackgroundColor(Color.rgb(3,3,11));previewArt=new ImageView(this);previewArt.setScaleType(ImageView.ScaleType.CENTER_CROP);previewArt.setBackgroundColor(PANEL);p.addView(previewArt,new LinearLayout.LayoutParams(-1,Math.min((int)(h*.58f),(int)(w*1.48f))));previewTitle=text("Select a title",21,true);previewTitle.setPadding(0,dp(12),0,0);p.addView(previewTitle);previewMeta=text("",13,false);previewMeta.setTextColor(PURPLE);p.addView(previewMeta);previewDesc=text("Hover a title to preview it.",14,false);previewDesc.setTextColor(MUTED);previewDesc.setMaxLines(7);previewDesc.setEllipsize(TextUtils.TruncateAt.END);previewDesc.setPadding(0,dp(8),0,0);p.addView(previewDesc);return p;}
    private View card(MediaCard m,int w,int h){FrameLayout c=new FrameLayout(this);c.setFocusable(true);c.setClickable(true);c.setPadding(dp(3),dp(3),dp(3),dp(3));c.setBackground(outline(false));c.setTag("grid:"+m.id);ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackgroundColor(PANEL);c.addView(im,new FrameLayout.LayoutParams(-1,-1));load(im,m.artworkUrl,w*2);TextView n=text(m.title,12,true);n.setGravity(Gravity.BOTTOM);n.setMaxLines(2);n.setEllipsize(TextUtils.TruncateAt.END);n.setPadding(dp(7),0,dp(5),dp(7));n.setBackgroundColor(Color.argb(95,0,0,0));c.addView(n,new FrameLayout.LayoutParams(-1,(int)(h*.25f),Gravity.BOTTOM));c.setOnFocusChangeListener((v,f)->{c.setBackground(outline(f));n.setTextColor(f?BLUE:WHITE);if(f){remember(m.id);preview(m);if(new AppSettingsStore(this).clickSounds())c.playSoundEffect(SoundEffectConstants.CLICK);}});c.setOnClickListener(v->{Intent i=new Intent(this,MediaOpenActivity.class);i.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID,m.id);startActivity(i);overridePendingTransition(0,0);});return c;}
    private void preview(MediaCard m){if(previewTitle==null)return;previewTitle.setText(m.title);previewMeta.setText((m.series?"TV Series":"Movie")+(m.subtitle==null||m.subtitle.isEmpty()?"":" • "+m.subtitle)+(m.genre.isEmpty()?"":" • "+m.genre));previewDesc.setText("Loading description…");previewArt.setImageDrawable(null);load(previewArt,m.artworkUrl,700);int t=++token;pool.submit(()->{String d;try{d=new CinemetaClient().description(m);}catch(Exception e){d="";}if(d==null||d.isEmpty())d=previewMeta.getText().toString();final String x=d;runOnUiThread(()->{if(!isFinishing()&&t==token)previewDesc.setText(x);});});}
    private void remember(String id){getSharedPreferences("m00v13_grid_focus",MODE_PRIVATE).edit().putString(focusKey==null?"grid":focusKey,id).apply();}
    private void restore(View root){String id=getSharedPreferences("m00v13_grid_focus",MODE_PRIVATE).getString(focusKey==null?"grid":focusKey,null);if(id==null)return;root.post(()->{View v=find(root,"grid:"+id);if(v!=null)v.requestFocus();});}
    private View find(View v,String tag){if(tag.equals(v.getTag()))return v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View x=find(g.getChildAt(i),tag);if(x!=null)return x;}}return null;}
    private void load(ImageView v,String u,int target){if(u==null||u.isEmpty())return;pool.submit(()->{try{File f=new ArtworkCache(this).fetch(u,86,Math.min(target,getResources().getDisplayMetrics().widthPixels));if(f==null)return;Bitmap b=BitmapFactory.decodeFile(f.getAbsolutePath());runOnUiThread(()->{if(!isFinishing()&&b!=null)v.setImageBitmap(b);});}catch(Exception ignored){}});}
    private GradientDrawable outline(boolean f){GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(6));g.setStroke(dp(f?3:1),f?BLUE:Color.rgb(42,34,53));return g;}private TextView text(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextColor(WHITE);v.setTextSize(z);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}private int dp(int x){return TvUi.dp(this,x);}
}
