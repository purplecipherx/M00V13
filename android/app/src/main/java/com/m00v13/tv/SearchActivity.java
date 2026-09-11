package com.m00v13.tv;

import android.app.Activity;
import android.content.Context;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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

/** Poster-first search surface optimized for TV focus and low-RAM devices. */
public final class SearchActivity extends Activity {
    private static final int PANEL=Color.rgb(13,8,27),BLUE=Color.rgb(44,157,255),PURPLE=Color.rgb(180,78,255),WHITE=Color.rgb(247,245,250),MUTED=Color.rgb(190,181,202);

    private LinearLayout recent;
    private EditText input;
    private Button searchButton;
    private TextView status,previewTitle,previewMeta,previewDesc;
    private ImageView previewArt;
    private RecyclerView results;
    private CatalogStore catalog;
    private SearchHistoryStore history;
    private ArtworkLoader artwork;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ExecutorService artPool=Executors.newFixedThreadPool(2);
    private final Map<String,String> descriptions=new HashMap<>();
    private volatile boolean searchRunning=false;
    private int previewToken;
    private boolean clickSounds;

    @Override protected void onCreate(Bundle b){super.onCreate(b);TvUi.disableWindowAnimations(this);catalog=new CatalogStore(this);history=new SearchHistoryStore(this);artwork=new ArtworkLoader(this);clickSounds=new AppSettingsStore(this).clickSounds();setContentView(build());}
    @Override protected void onDestroy(){executor.shutdownNow();artPool.shutdownNow();super.onDestroy();}

    private View build(){
        int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(TvUi.BG);
        int side=w<1000?0:Math.max(dp(245),(int)(w*.20f));if(side>0)root.addView(previewPane(side,h),new FrameLayout.LayoutParams(side,h));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(22),dp(24),dp(20));body.setBackgroundColor(TvUi.BG);
        body.addView(TvUi.text(this,"Search M00V13",30,true));TextView sub=TvUi.text(this,"Movies and TV • typo-tolerant matching • cached searches open instantly",14,false);sub.setTextColor(MUTED);sub.setPadding(0,dp(3),0,dp(10));body.addView(sub);

        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);input=new EditText(this);input.setHint("Movie or show title…");input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setTextSize(18);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));bar.addView(input,new LinearLayout.LayoutParams(0,dp(58),1f));searchButton=TvUi.button(this,"Search");searchButton.setOnClickListener(v->beginSearch());LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(130),dp(54));bp.leftMargin=dp(10);bar.addView(searchButton,bp);body.addView(bar);

        status=TvUi.text(this,new MetadataStore(this).isConfigured()?"TMDB metadata enabled":"Keyless Cinemeta metadata enabled",14,false);status.setTextColor(MUTED);status.setPadding(0,dp(7),0,dp(9));body.addView(status);
        recent=new LinearLayout(this);recent.setOrientation(LinearLayout.VERTICAL);body.addView(recent);renderRecent();

        results=new RecyclerView(this);results.setHasFixedSize(true);results.setItemAnimator(null);results.setOverScrollMode(View.OVER_SCROLL_NEVER);results.setVerticalScrollBarEnabled(false);body.addView(results,new LinearLayout.LayoutParams(-1,0,1f));

        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w-side,h);p.leftMargin=side;root.addView(body,p);
        input.setOnEditorActionListener((v,a,e)->{if(a==EditorInfo.IME_ACTION_SEARCH){beginSearch();return true;}return false;});return root;
    }

    private View previewPane(int w,int h){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(14),dp(20),dp(14),dp(18));p.setBackgroundColor(Color.rgb(3,3,11));previewArt=new ImageView(this);previewArt.setScaleType(ImageView.ScaleType.CENTER_CROP);previewArt.setBackgroundColor(PANEL);p.addView(previewArt,new LinearLayout.LayoutParams(-1,Math.min((int)(h*.55f),(int)(w*1.48f))));previewTitle=text("Search results",22,true);previewTitle.setPadding(0,dp(12),0,0);p.addView(previewTitle);previewMeta=text("",13,false);previewMeta.setTextColor(PURPLE);p.addView(previewMeta);previewDesc=text("Hover over a result to see its details.",14,false);previewDesc.setTextColor(MUTED);previewDesc.setMaxLines(7);previewDesc.setEllipsize(TextUtils.TruncateAt.END);previewDesc.setPadding(0,dp(8),0,0);p.addView(previewDesc);return p;}

    private void renderRecent(){if(recent==null)return;recent.removeAllViews();List<String> items=history.recent();if(items.isEmpty())return;TextView h=text("Recent searches — hold to delete",17,true);h.setTextColor(MUTED);recent.addView(h);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);int limit=Math.min(6,items.size());for(int i=0;i<limit;i++){String q=items.get(i);Button b=TvUi.button(this,q);b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.END);b.setOnClickListener(v->{input.setText(q);input.setSelection(q.length());beginSearch();});b.setOnLongClickListener(v->{history.remove(q);renderRecent();status.setText("Removed from recent searches.");return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1f);lp.setMarginEnd(dp(6));row.addView(b,lp);}recent.addView(row);View gap=new View(this);recent.addView(gap,new LinearLayout.LayoutParams(1,dp(10)));}

    private void beginSearch(){if(searchRunning)return;String q=input.getText()==null?"":input.getText().toString().trim();if(q.isEmpty()){status.setText("Enter a title first.");return;}history.add(q);renderRecent();hideKeyboard();input.clearFocus();searchButton.requestFocus();searchRunning=true;searchButton.setEnabled(false);results.setAdapter(null);status.setText("Matching titles…");DebugLog.append(this,"SEARCH","Metadata query='"+q+"'");MetadataStore ms=new MetadataStore(this);executor.submit(()->{try{ArrayList<MediaCard> cards=new ArrayList<>();if(ms.isConfigured()){TmdbClient tmdb=new TmdbClient(ms.tmdbToken());for(TmdbClient.Result r:tmdb.searchMulti(q))cards.add(tmdb.toCard(r));}else{CinemetaClient cm=new CinemetaClient();cards.addAll(cm.searchMovies(q));cards.addAll(cm.searchSeries(q));}runOnUiThread(()->{if(dead())return;finishBusy();showCards(cards);});}catch(Exception e){runOnUiThread(()->{if(dead())return;finishBusy();status.setText("Search failed: "+msg(e));});}});}

    private void showCards(List<MediaCard> found){if(found.isEmpty()){results.setAdapter(null);status.setText("No strong match. Try fewer words or another spelling.");return;}status.setText("Choose a title:");int width=getResources().getDisplayMetrics().widthPixels;int cols=width<700?2:width<1000?4:5;GridLayoutManager manager=new GridLayoutManager(this,cols);manager.setInitialPrefetchItemCount(cols);results.setLayoutManager(manager);results.setItemViewCacheSize(Math.max(6,cols*2));int available=(width<1000?width:(int)(width*.8f))-dp(48),cw=Math.max(dp(120),(available-dp(10)*(cols-1))/cols),ch=(int)(cw*1.49f);int limit=Math.min(40,found.size());ArrayList<MediaCard> cards=new ArrayList<>(found.subList(0,limit));catalog.upsertAll(cards);results.setAdapter(new SearchAdapter(cards,cw,ch));}

    private void showPreview(MediaCard c){if(previewTitle==null)return;previewTitle.setText(c.title);String meta=(c.series?"TV Series":"Movie")+(c.subtitle==null||c.subtitle.isEmpty()?"":" • "+c.subtitle);previewMeta.setText(meta);previewDesc.setText(c.genre==null||c.genre.isEmpty()?"Select to find playable sources.":c.genre+" • Select to find playable sources.");artwork.load(previewArt,c.artworkUrl,700,artPool);int t=++previewToken;String cached=descriptions.get(c.id);if(cached!=null){previewDesc.setText(cached);return;}previewDesc.postDelayed(()->{if(dead()||t!=previewToken)return;executor.submit(()->{try{String d=new CinemetaClient().description(c);if(d==null||d.isEmpty())return;descriptions.put(c.id,d);runOnUiThread(()->{if(!dead()&&t==previewToken)previewDesc.setText(d);});}catch(Exception ignored){}});},300);}

    private void scrapeCard(MediaCard card,View selected){if(searchRunning)return;List<SourceOption> cached=new SourceStore(this).getFresh(card.id);if(!cached.isEmpty()){catalog.upsert(card);Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,card.title);startActivity(i);overridePendingTransition(0,0);return;}searchRunning=true;selected.setEnabled(false);status.setText("Searching sources for "+card.title+"…");LoadingOverlay loading=LoadingOverlay.show(this);executor.submit(()->{try{String q=card.title+(card.subtitle==null||card.subtitle.isEmpty()?"":" "+card.subtitle);List<SourceOption> ranked=new TieredSearchEngine(this).search(q).sources;runOnUiThread(()->{loading.hide();if(dead())return;searchRunning=false;selected.setEnabled(true);catalog.upsert(card);if(ranked.isEmpty()){status.setText("No usable sources found.");return;}new SourceStore(this).put(card.id,ranked);Intent i=new Intent(this,SourceSelectionActivity.class);i.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,card.id);i.putExtra(SourceSelectionActivity.EXTRA_TITLE,card.title);startActivity(i);overridePendingTransition(0,0);});}catch(Exception e){runOnUiThread(()->{loading.hide();if(dead())return;searchRunning=false;selected.setEnabled(true);status.setText("Source search failed: "+msg(e));});}});}

    private final class SearchAdapter extends RecyclerView.Adapter<SearchHolder>{
        private final List<MediaCard> cards;private final int cw,ch;
        SearchAdapter(List<MediaCard> cards,int cw,int ch){this.cards=cards;this.cw=cw;this.ch=ch;setHasStableIds(true);}
        @Override public long getItemId(int position){return cards.get(position).id.hashCode();}
        @Override public SearchHolder onCreateViewHolder(ViewGroup parent,int viewType){FrameLayout c=new FrameLayout(SearchActivity.this);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(cw,ch);lp.rightMargin=dp(10);lp.bottomMargin=dp(10);c.setLayoutParams(lp);c.setFocusable(true);c.setClickable(true);c.setStateListAnimator(null);c.setPadding(dp(3),dp(3),dp(3),dp(3));c.setBackground(box(false));ImageView im=new ImageView(SearchActivity.this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);im.setBackgroundColor(PANEL);c.addView(im,new FrameLayout.LayoutParams(-1,-1));TextView n=text("",12,true);n.setGravity(Gravity.BOTTOM);n.setMaxLines(2);n.setEllipsize(TextUtils.TruncateAt.END);n.setPadding(dp(7),0,dp(5),dp(7));n.setBackgroundColor(Color.argb(100,0,0,0));c.addView(n,new FrameLayout.LayoutParams(-1,(int)(ch*.25f),Gravity.BOTTOM));return new SearchHolder(c,im,n);}
        @Override public void onBindViewHolder(SearchHolder h,int position){MediaCard card=cards.get(position);h.name.setText(card.title);h.name.setTextColor(WHITE);h.card.setBackground(box(false));artwork.load(h.image,card.artworkUrl,Math.min(cw*2,getResources().getDisplayMetrics().widthPixels),artPool);h.card.setOnFocusChangeListener((v,f)->{h.card.setBackground(box(f));h.name.setTextColor(f?BLUE:WHITE);if(f){showPreview(card);if(clickSounds)h.card.playSoundEffect(SoundEffectConstants.CLICK);}});h.card.setOnClickListener(v->scrapeCard(card,h.card));}
        @Override public int getItemCount(){return cards.size();}
    }

    private static final class SearchHolder extends RecyclerView.ViewHolder{final FrameLayout card;final ImageView image;final TextView name;SearchHolder(FrameLayout c,ImageView i,TextView n){super(c);card=c;image=i;name=n;}}
    private GradientDrawable box(boolean f){GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(6));g.setStroke(dp(f?3:1),f?BLUE:Color.rgb(42,34,53));return g;}
    private void finishBusy(){searchRunning=false;searchButton.setEnabled(true);searchButton.requestFocus();}
    private void hideKeyboard(){InputMethodManager i=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);if(i!=null)i.hideSoftInputFromWindow(input.getWindowToken(),0);}
    private TextView text(String s,int z,boolean b){TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(z);if(b)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private int dp(int x){return TvUi.dp(this,x);}
}
