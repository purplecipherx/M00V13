package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private ProfileStore profiles;
    private CatalogStore catalog;
    private ScreenProfile screen;
    private final ExecutorService artworkExecutor = Executors.newFixedThreadPool(2);
    private TextView heroTitle;
    private TextView heroMeta;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profiles = new ProfileStore(this);
        catalog = new CatalogStore(this);
        screen = ScreenProfile.detect(this);
        DebugLog.boot(this);
        DebugLog.append(this,"UI","Screen profile "+screen.kind+" "+screen.widthPx+"x"+screen.heightPx);
        getWindow().getDecorView().setBackgroundColor(TvUi.BG);
        setContentView(buildHome());
    }

    @Override protected void onResume() {
        super.onResume();
        if (profiles != null && catalog != null) { screen=ScreenProfile.detect(this); setContentView(buildHome()); }
    }

    @Override protected void onDestroy() { artworkExecutor.shutdownNow(); super.onDestroy(); }

    private View buildHome() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(screen.sidePaddingDp),dp(screen.mobile()?16:24),dp(screen.sidePaddingDp),dp(40)); root.setBackgroundColor(TvUi.BG); scroll.addView(root);

        LinearLayout top = new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_m00v13); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); top.addView(logo,new LinearLayout.LayoutParams(dp(screen.mobile()?42:58),dp(screen.mobile()?42:58)));
        TextView brand = TvUi.text(this,"M00V13",screen.mobile()?22:27,true); brand.setTextColor(TvUi.PURPLE);
        LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(0,dp(screen.mobile()?46:58),1f); brandP.setMarginStart(dp(12)); top.addView(brand,brandP);
        addNav(top,"Home",null); addNav(top,"Movies",v->openBrowse(BrowseActivity.KIND_MOVIES)); addNav(top,"TV",v->openBrowse(BrowseActivity.KIND_TV)); addNav(top,"Search",v->startActivity(new Intent(this,SearchActivity.class)));
        if(!screen.mobile()) addNav(top,"Downloads",v->startActivity(new Intent(this,DownloadsActivity.class)));
        addNav(top,"⚙",v->startActivity(new Intent(this,SettingsActivity.class))); root.addView(top);

        List<MediaCard> all=catalog.all(); List<MediaCard> continuing=continueWatching(all); RecommendationEngine recommender=new RecommendationEngine(); List<MediaCard> recommended=recommender.rankOverall(all,profiles,16);
        MediaCard hero=!continuing.isEmpty()?continuing.get(0):(!recommended.isEmpty()?recommended.get(0):mostRecentlyTouched(all));
        root.addView(buildHero(hero),new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(screen.heroHeightDp)));

        addRow(root,"Continue Watching",continuing,"Nothing in progress yet");
        addRow(root,"Popular Movies",filter(all,false),"Popular movies will fill as metadata is cached");
        addRow(root,"Popular TV",filter(all,true),"Popular shows will fill as metadata is cached");
        addRow(root,"Recommended Movies",filterRecommended(recommended,false),"Movie recommendations build from this profile");
        addRow(root,"Recommended TV",filterRecommended(recommended,true),"TV recommendations build from this profile");
        addRow(root,"Blockbusters • Movies",filter(all,false),"Blockbuster movie rail will use cached discovery metadata");
        addRow(root,"Blockbusters • TV",filter(all,true),"Blockbuster TV rail will use cached discovery metadata");

        LinearLayout footer=new LinearLayout(this); footer.setOrientation(LinearLayout.HORIZONTAL); footer.setGravity(Gravity.CENTER_VERTICAL); footer.setPadding(0,dp(20),0,0);
        TextView status=TvUi.text(this,storageSummary(),screen.mobile()?12:14,false); status.setTextColor(TvUi.MUTED); footer.addView(status,new LinearLayout.LayoutParams(0,dp(48),1f));
        if(!screen.mobile()){ addFooterButton(footer,"Debrid",DebridActivity.class); addFooterButton(footer,"Support 💜",DonateActivity.class); }
        addFooterButton(footer,profiles.activeProfile(),ProfileActivity.class); root.addView(footer); return scroll;
    }

    private View buildHero(MediaCard hero) {
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.HORIZONTAL); panel.setGravity(Gravity.CENTER_VERTICAL); panel.setPadding(dp(screen.mobile()?18:30),dp(16),dp(screen.mobile()?18:30),dp(16)); panel.setBackgroundColor(TvUi.PANEL);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(screen.heroHeightDp)); pp.topMargin=dp(18); pp.bottomMargin=dp(12); panel.setLayoutParams(pp);
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setGravity(Gravity.CENTER_VERTICAL); panel.addView(info,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1f));
        heroTitle=TvUi.text(this,hero==null?"What do you want to watch?":hero.title,screen.mobile()?26:34,true); info.addView(heroTitle);
        heroMeta=TvUi.text(this,hero==null?"Search • stream • download":heroDetail(hero),screen.mobile()?14:17,false); heroMeta.setTextColor(TvUi.MUTED); heroMeta.setPadding(0,dp(8),0,dp(16)); info.addView(heroMeta);
        View action=TvUi.button(this,hero==null?"Search":"PLAY"); action.setOnClickListener(v->{if(hero==null)startActivity(new Intent(this,SearchActivity.class));else openMedia(hero);}); info.addView(action,new LinearLayout.LayoutParams(dp(screen.mobile()?140:180),dp(54))); return panel;
    }

    private void addNav(LinearLayout row,String label,View.OnClickListener click){ View b=TvUi.button(this,label); if(click!=null)b.setOnClickListener(click); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(screen.mobile()?44:48)); p.setMarginStart(dp(screen.mobile()?4:8)); row.addView(b,p); }
    private void addFooterButton(LinearLayout row,String label,Class<?> activity){ View b=TvUi.button(this,label); b.setOnClickListener(v->startActivity(new Intent(this,activity))); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(46)); p.setMarginStart(dp(8)); row.addView(b,p); }

    private void addRow(LinearLayout root,String heading,List<MediaCard> items,String emptyMessage){
        TextView h=TvUi.text(this,heading,screen.mobile()?18:22,true); h.setPadding(0,dp(16),0,dp(8)); root.addView(h);
        if(items==null||items.isEmpty()){ TextView empty=TvUi.text(this,emptyMessage,screen.mobile()?13:15,false); empty.setTextColor(TvUi.MUTED); empty.setPadding(dp(2),dp(4),0,dp(12)); root.addView(empty); return; }
        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); hsv.setClipChildren(false); hsv.setClipToPadding(false); LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setClipChildren(false);
        int limit=Math.min(items.size(),screen.mobile()?10:14); for(int i=0;i<limit;i++)row.addView(poster(items.get(i))); hsv.addView(row); root.addView(hsv,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(screen.railHeightDp)));
    }

    private View poster(MediaCard item){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setFocusable(true); card.setClickable(true); card.setPadding(dp(4),dp(4),dp(4),dp(4)); card.setBackgroundColor(Color.TRANSPARENT);
        ImageView art=new ImageView(this); art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setBackgroundColor(TvUi.CARD); card.addView(art,new LinearLayout.LayoutParams(dp(screen.posterWidthDp),dp(screen.posterHeightDp)));
        TextView title=TvUi.text(this,item.title,screen.mobile()?12:14,true); title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); card.addView(title,new LinearLayout.LayoutParams(dp(screen.posterWidthDp),dp(32)));
        card.setOnFocusChangeListener((v,focused)->{ title.setTextColor(focused?TvUi.BLUE:TvUi.WHITE); card.animate().scaleX(focused?1.07f:1f).scaleY(focused?1.07f:1f).setDuration(100).start(); card.setElevation(dp(focused?12:0)); if(focused)showHero(item); });
        card.setOnClickListener(v->openMedia(item)); card.setOnLongClickListener(v->{profiles.setWatchlist(item.id,!profiles.isInWatchlist(item.id));return true;});
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(screen.posterWidthDp+14),dp(screen.railHeightDp-4)); p.setMarginEnd(dp(10)); card.setLayoutParams(p); loadArtwork(art,item.artworkUrl); return card;
    }

    private void loadArtwork(ImageView image,String url){ if(url==null||url.trim().isEmpty())return; artworkExecutor.submit(()->{ try{ File f=new ArtworkCache(this).fetch(url,80); if(f==null)return; android.graphics.Bitmap bitmap=BitmapFactory.decodeFile(f.getAbsolutePath()); runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()&&bitmap!=null)image.setImageBitmap(bitmap);}); }catch(Exception e){DebugLog.append(this,"ART","Artwork failed: "+e.getMessage());} }); }
    private void showHero(MediaCard item){if(heroTitle!=null)heroTitle.setText(item.title);if(heroMeta!=null)heroMeta.setText(heroDetail(item));}
    private String heroDetail(MediaCard item){String type=item.series?"TV":"Movie";String sub=item.subtitle==null||item.subtitle.isEmpty()?"":" • "+item.subtitle;long p=profiles.progressMs(item.id),d=profiles.durationMs(item.id);String progress=p>0&&d>0?" • "+Math.min(99,p*100/d)+"% watched":"";return type+sub+progress;}
    private void openBrowse(String kind){Intent i=new Intent(this,BrowseActivity.class);i.putExtra(BrowseActivity.EXTRA_KIND,kind);startActivity(i);}
    private List<MediaCard> filter(List<MediaCard> all,boolean series){ArrayList<MediaCard> out=new ArrayList<>();for(MediaCard c:all)if(c.series==series)out.add(c);out.sort(Comparator.comparing((MediaCard c)->c.title.toLowerCase(java.util.Locale.US)));if(out.size()>18)return new ArrayList<>(out.subList(0,18));return out;}
    private List<MediaCard> filterRecommended(List<MediaCard> in,boolean series){ArrayList<MediaCard> out=new ArrayList<>();for(MediaCard c:in)if(c.series==series)out.add(c);return out;}
    private List<MediaCard> continueWatching(List<MediaCard> all){ArrayList<MediaCard> out=new ArrayList<>();for(MediaCard c:all){long p=profiles.progressMs(c.id),d=profiles.durationMs(c.id);if(!profiles.isWatched(c.id)&&p>0&&d>0)out.add(c);}out.sort(Comparator.comparingLong((MediaCard c)->profiles.lastUpdatedMs(c.id)).reversed());return out;}
    private MediaCard mostRecentlyTouched(List<MediaCard> all){MediaCard best=null;long when=0;for(MediaCard c:all){long t=profiles.lastUpdatedMs(c.id);if(t>when){when=t;best=c;}}return best;}
    private void openMedia(MediaCard item){List<SourceOption> sources=new SourceStore(this).getFresh(item.id);if(!sources.isEmpty()){Intent choose=new Intent(this,SourceSelectionActivity.class);choose.putExtra(SourceSelectionActivity.EXTRA_MEDIA_ID,item.id);choose.putExtra(SourceSelectionActivity.EXTRA_TITLE,item.title);startActivity(choose);return;}if(item.streamUri==null||item.streamUri.isEmpty()){startActivity(new Intent(this,SearchActivity.class));return;}Intent play=new Intent(this,PlayerActivity.class);play.putExtra(PlayerActivity.EXTRA_MEDIA_ID,item.id);play.putExtra(PlayerActivity.EXTRA_URI,item.streamUri);startActivity(play);}
    private String storageSummary(){long free=StoragePolicy.availableBytes(getFilesDir());String debrid=new DebridStore(this).isConnected()?"RD ✓":"RD off";String metadata=new MetadataStore(this).isConfigured()?"Metadata ✓":"Metadata off";return debrid+" • "+metadata+" • Free "+(free/StoragePolicy.MIB)+" MiB";}
    private int dp(int value){return TvUi.dp(this,value);}
}
