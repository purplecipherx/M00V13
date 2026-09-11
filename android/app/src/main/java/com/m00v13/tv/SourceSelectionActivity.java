package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cinematic TV source resolver based on the Stitch M00V13 stream-results design. */
public final class SourceSelectionActivity extends Activity {
    public static final String EXTRA_MEDIA_ID="media_id", EXTRA_TITLE="title", EXTRA_URIS="uris", EXTRA_LABELS="labels";

    private static final int BG=Color.rgb(7,9,14);
    private static final int PANEL=Color.rgb(15,20,29);
    private static final int PANEL_2=Color.rgb(20,26,38);
    private static final int WHITE=Color.rgb(236,241,248);
    private static final int MUTED=Color.rgb(161,174,190);
    private static final int CYAN=Color.rgb(0,240,255);
    private static final int GREEN=Color.rgb(74,225,118);
    private static final int VIOLET=Color.rgb(168,85,247);
    private static final int GOLD=Color.rgb(245,158,11);

    private final ExecutorService resolverExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService artExecutor=Executors.newFixedThreadPool(2);
    private final Map<String,TextView> filterViews=new HashMap<>();
    private final ArrayList<SourceOption> allSources=new ArrayList<>();
    private final ArrayList<SourceOption> visibleSources=new ArrayList<>();

    private String mediaId;
    private MediaCard media;
    private boolean clickSounds;
    private ArtworkLoader artwork;
    private String activeFilter="all";
    private RecyclerView sourceList;
    private SourceAdapter adapter;
    private TextView status;
    private TextView countLine;
    private TextView inspectorName;
    private TextView inspectorCache;
    private TextView inspectorVideo;
    private TextView inspectorAudio;
    private TextView inspectorSubs;
    private TextView inspectorProvider;
    private TextView playButton;
    private ImageView poster;
    private SourceOption selectedSource;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        clickSounds=new AppSettingsStore(this).clickSounds();
        artwork=new ArtworkLoader(this);
        mediaId=getIntent().getStringExtra(EXTRA_MEDIA_ID);
        media=mediaId==null?null:new CatalogStore(this).find(mediaId);
        loadSourcesFromIntentOrCache();
        visibleSources.addAll(allSources);
        setContentView(build());
        if(!visibleSources.isEmpty()) selectSource(visibleSources.get(0));
    }

    private void loadSourcesFromIntentOrCache(){
        if(mediaId!=null) allSources.addAll(new SourceStore(this).getFresh(mediaId));
        if(!allSources.isEmpty()) return;
        ArrayList<String> uris=getIntent().getStringArrayListExtra(EXTRA_URIS);
        ArrayList<String> labels=getIntent().getStringArrayListExtra(EXTRA_LABELS);
        if(uris==null) return;
        for(int i=0;i<uris.size();i++){
            String uri=uris.get(i);
            if(uri==null||uri.trim().isEmpty())continue;
            String label=labels!=null&&i<labels.size()?labels.get(i):"Source "+(i+1);
            allSources.add(new SourceOption("legacy",label,uri,"?","?","","?","",
                Collections.emptyList(),Collections.emptyList(),-1L,-1,null,0));
        }
    }

    private View build(){
        int w=getResources().getDisplayMetrics().widthPixels;
        int h=getResources().getDisplayMetrics().heightPixels;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);

        View glow=new View(this);
        GradientDrawable glowBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.argb(90,0,128,140),Color.argb(20,5,8,15),Color.argb(50,73,23,112)});
        glow.setBackground(glowBg);root.addView(glow,new FrameLayout.LayoutParams(-1,-1));

        root.addView(buildTopBar(),frame(-1,dp(84),Gravity.TOP|Gravity.START,0,0));
        root.addView(buildNavRail(),frame(dp(76),h-dp(105),Gravity.TOP|Gravity.START,dp(20),dp(50)));

        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),0,dp(26),dp(20));
        FrameLayout.LayoutParams bp=frame(w-dp(118),h-dp(84),Gravity.TOP|Gravity.START,dp(110),dp(84));root.addView(body,bp);

        body.addView(buildBreadcrumb(),new LinearLayout.LayoutParams(-1,dp(36)));
        body.addView(buildMediaHeader(),new LinearLayout.LayoutParams(-1,dp(190)));
        body.addView(buildFilters(),new LinearLayout.LayoutParams(-1,dp(62)));

        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.HORIZONTAL);
        sourceList=new RecyclerView(this);sourceList.setHasFixedSize(true);sourceList.setItemAnimator(null);sourceList.setOverScrollMode(View.OVER_SCROLL_NEVER);sourceList.setVerticalScrollBarEnabled(false);sourceList.setItemViewCacheSize(8);sourceList.setLayoutManager(new LinearLayoutManager(this));adapter=new SourceAdapter();sourceList.setAdapter(adapter);
        content.addView(sourceList,new LinearLayout.LayoutParams(0,-1,1f));
        View gap=new View(this);content.addView(gap,new LinearLayout.LayoutParams(dp(20),1));
        int inspectorW=Math.max(dp(310),Math.min(dp(390),(int)(w*.27f)));content.addView(buildInspector(),new LinearLayout.LayoutParams(inspectorW,-1));
        body.addView(content,new LinearLayout.LayoutParams(-1,0,1f));
        return root;
    }

    private View buildTopBar(){
        FrameLayout top=new FrameLayout(this);top.setBackgroundColor(Color.rgb(9,12,18));
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("M 0 0 V 1 3",23,true);logo.setLetterSpacing(.17f);brand.addView(logo);
        TextView sub=text("STORIES WITHOUT LIMITS",9,false);sub.setTextColor(MUTED);sub.setLetterSpacing(.20f);brand.addView(sub);
        top.addView(brand,frame(dp(260),dp(62),Gravity.START|Gravity.CENTER_VERTICAL,dp(135),0));

        TextView search=text("⌕   What do you want to watch or scrape?",15,false);search.setTextColor(Color.rgb(205,213,224));search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(22),0,dp(18),0);search.setFocusable(true);search.setClickable(true);search.setBackground(pill(false));search.setOnFocusChangeListener((v,f)->search.setBackground(pill(f)));search.setOnClickListener(v->openHub(MediaHubActivity.MODE_SEARCH));
        top.addView(search,frame(Math.min(dp(560),(int)(getResources().getDisplayMetrics().widthPixels*.40f)),dp(52),Gravity.CENTER,0,0));

        TextView profile=text("◉",18,true);profile.setGravity(Gravity.CENTER);profile.setFocusable(true);profile.setClickable(true);profile.setBackground(circle(false));profile.setOnFocusChangeListener((v,f)->profile.setBackground(circle(f)));profile.setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
        top.addView(profile,frame(dp(46),dp(46),Gravity.END|Gravity.CENTER_VERTICAL,dp(28),0));
        return top;
    }

    private View buildNavRail(){
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(7),dp(10),dp(7),dp(10));rail.setBackground(round(PANEL,Color.argb(35,255,255,255),1,28));
        addNav(rail,"⌂",()->openHub(MediaHubActivity.MODE_HOME),false);
        addNav(rail,"▣",()->openHub(MediaHubActivity.MODE_MOVIES),false);
        addNav(rail,"▤",()->openHub(MediaHubActivity.MODE_TV),false);
        addNav(rail,"⌕",()->openHub(MediaHubActivity.MODE_SEARCH),false);
        addNav(rail,"♡",()->openHub(MediaHubActivity.MODE_LIBRARY),false);
        addNav(rail,"↗",()->startActivity(new Intent(this,DebridActivity.class)),false);
        addNav(rail,"▱",()->{},true);
        View spacer=new View(this);rail.addView(spacer,new LinearLayout.LayoutParams(1,0,1f));
        addNav(rail,"⚙",()->startActivity(new Intent(this,SettingsActivity.class)),false);
        return rail;
    }

    private void addNav(LinearLayout rail,String symbol,Runnable action,boolean active){
        TextView v=text(symbol,22,true);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setClickable(true);v.setTextColor(active?Color.rgb(5,42,47):Color.rgb(205,215,226));v.setBackground(navBg(active,active));v.setOnFocusChangeListener((x,f)->{v.setTextColor((f||active)?Color.rgb(5,42,47):Color.rgb(205,215,226));v.setBackground(navBg(f,active));});v.setOnClickListener(x->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(50),dp(50));p.bottomMargin=dp(10);rail.addView(v,p);
    }

    private View buildBreadcrumb(){
        TextView t=text("⌕ Search Hub   ›   "+title()+"   ›   Select Source Stream",13,true);t.setTextColor(Color.rgb(199,211,224));t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }

    private View buildMediaHeader(){
        LinearLayout header=new LinearLayout(this);header.setOrientation(LinearLayout.HORIZONTAL);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(20),dp(16),dp(20),dp(16));header.setBackground(round(Color.argb(220,18,23,31),Color.argb(28,255,255,255),1,18));
        poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(PANEL_2);header.addView(poster,new LinearLayout.LayoutParams(dp(72),dp(112)));if(media!=null&&media.artworkUrl!=null)artwork.load(poster,media.artworkUrl,240,artExecutor);

        LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);details.setPadding(dp(20),0,0,0);
        TextView eyebrow=text((media!=null&&media.series?"SERIES":"MOVIE")+" STREAM RESOLVER",12,true);eyebrow.setTextColor(Color.rgb(180,255,255));details.addView(eyebrow);
        TextView title=text(title(),28,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);details.addView(title);
        String meta=media==null?"":((media.subtitle==null?"":media.subtitle)+(media.genre==null||media.genre.isEmpty()?"":"   •   "+media.genre));TextView mt=text(meta,14,false);mt.setTextColor(MUTED);details.addView(mt);

        countLine=text(summaryLine(),14,true);countLine.setTextColor(Color.rgb(214,224,235));countLine.setPadding(0,dp(13),0,0);details.addView(countLine);
        status=text(new DebridStore(this).isConnected()?"● Real-Debrid connected":"○ Real-Debrid not connected",13,true);status.setTextColor(new DebridStore(this).isConnected()?GREEN:GOLD);details.addView(status);
        header.addView(details,new LinearLayout.LayoutParams(0,-1,1f));return header;
    }

    private View buildFilters(){
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);
        addFilter(bar,"all","All Sources");addFilter(bar,"4k","4K UHD");addFilter(bar,"1080","1080p");addFilter(bar,"cached","Cached");addFilter(bar,"hdr","HDR / DV");addFilter(bar,"atmos","Atmos");addFilter(bar,"hevc","HEVC / x265");return bar;
    }

    private void addFilter(LinearLayout bar,String key,String label){
        TextView v=text(label,12,true);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setClickable(true);filterViews.put(key,v);v.setBackground(filterBg("all".equals(key),false));v.setTextColor("all".equals(key)?Color.rgb(3,36,41):Color.rgb(220,226,234));v.setOnFocusChangeListener((x,f)->updateFilterVisual(key,f));v.setOnClickListener(x->{activeFilter=key;applyFilter();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(36),1f);p.setMarginEnd(dp(8));bar.addView(v,p);
    }

    private void updateFilterVisual(String key,boolean focused){
        TextView v=filterViews.get(key);if(v==null)return;boolean active=key.equals(activeFilter);v.setBackground(filterBg(active,focused));v.setTextColor((active||focused)?Color.rgb(3,36,41):Color.rgb(220,226,234));
    }

    private void applyFilter(){
        visibleSources.clear();for(SourceOption s:allSources)if(matches(s,activeFilter))visibleSources.add(s);for(String k:filterViews.keySet())updateFilterVisual(k,false);if(adapter!=null)adapter.notifyDataSetChanged();if(visibleSources.isEmpty()){selectSource(null);status.setText("No sources match this filter.");}else{selectSource(visibleSources.get(0));status.setText(new DebridStore(this).isConnected()?"● Real-Debrid connected":"○ Real-Debrid not connected");}
    }

    private boolean matches(SourceOption s,String filter){
        if("all".equals(filter))return true;String q=lower(s.quality),v=lower(s.videoCodec),h=lower(s.hdr),a=lower(s.audioCodec)+" "+lower(s.audioLayout);
        if("4k".equals(filter))return q.contains("2160")||q.contains("4k");
        if("1080".equals(filter))return q.contains("1080");
        if("cached".equals(filter))return Boolean.TRUE.equals(s.cached);
        if("hdr".equals(filter))return !h.isEmpty()&&(h.contains("hdr")||h.contains("dv")||h.contains("vision"));
        if("atmos".equals(filter))return a.contains("atmos")||a.contains("truehd");
        if("hevc".equals(filter))return v.contains("hevc")||v.contains("265");
        return true;
    }

    private View buildInspector(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(round(Color.argb(235,17,22,31),Color.argb(30,255,255,255),1,18));
        TextView label=text("ACTIVE STREAM INSPECTOR",11,true);label.setTextColor(CYAN);label.setLetterSpacing(.08f);card.addView(label);
        inspectorName=text("Select a source",22,true);inspectorName.setMaxLines(2);inspectorName.setEllipsize(TextUtils.TruncateAt.END);inspectorName.setPadding(0,dp(5),0,dp(14));card.addView(inspectorName);
        inspectorCache=inspectorBlock(card);inspectorVideo=inspectorBlock(card);inspectorAudio=inspectorBlock(card);inspectorSubs=inspectorBlock(card);inspectorProvider=inspectorBlock(card);
        View spacer=new View(this);card.addView(spacer,new LinearLayout.LayoutParams(1,0,1f));
        playButton=text("▷  Start Playback",19,true);playButton.setGravity(Gravity.CENTER);playButton.setFocusable(true);playButton.setClickable(true);playButton.setTextColor(Color.rgb(3,43,48));playButton.setBackground(playBg(false));playButton.setOnFocusChangeListener((v,f)->playButton.setBackground(playBg(f)));playButton.setOnClickListener(v->{if(selectedSource!=null)openSource(playButton,selectedSource);});card.addView(playButton,new LinearLayout.LayoutParams(-1,dp(58)));return card;
    }

    private TextView inspectorBlock(LinearLayout parent){TextView t=text("",13,false);t.setTextColor(Color.rgb(218,225,234));t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setBackground(round(Color.rgb(10,14,21),Color.argb(25,255,255,255),1,10));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);parent.addView(t,p);return t;}

    private void selectSource(SourceOption s){
        selectedSource=s;if(inspectorName==null)return;if(s==null){inspectorName.setText("No matching source");inspectorCache.setText("No source selected");inspectorVideo.setText("");inspectorAudio.setText("");inspectorSubs.setText("");inspectorProvider.setText("");playButton.setEnabled(false);return;}playButton.setEnabled(true);inspectorName.setText(sourceName(s));
        String cache=Boolean.TRUE.equals(s.cached)?"● INSTANT CACHED":Boolean.FALSE.equals(s.cached)?"○ NOT CACHED":"◌ CACHE STATUS UNKNOWN";inspectorCache.setText(cache+(s.sizeBytes>0?"\nSize  "+gb(s.sizeBytes):""));inspectorCache.setTextColor(Boolean.TRUE.equals(s.cached)?GREEN:Color.rgb(218,225,234));
        inspectorVideo.setText("VIDEO\n"+safe(s.quality)+"   •   "+safe(s.videoCodec)+(s.hdr.isEmpty()?"":"   •   "+s.hdr));
        inspectorAudio.setText("AUDIO\n"+safe(s.audioCodec)+(s.audioLayout.isEmpty()?"":" "+s.audioLayout)+(s.audioLanguages.isEmpty()?"":"   •   "+join(s.audioLanguages)));
        inspectorSubs.setText("SUBTITLES\n"+(s.subtitleLanguages.isEmpty()?"Not reported":join(s.subtitleLanguages)));
        inspectorProvider.setText("SOURCE\n"+s.provider+(s.seeders>=0?"   •   "+s.seeders+" seeds":"")+"   •   score "+s.score);
    }

    private final class SourceAdapter extends RecyclerView.Adapter<SourceHolder>{
        @Override public long getItemId(int position){SourceOption s=visibleSources.get(position);return (s.uri==null?position:s.uri.hashCode());}
        SourceAdapter(){setHasStableIds(true);}
        @Override public SourceHolder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout row=new LinearLayout(SourceSelectionActivity.this);row.setOrientation(LinearLayout.HORIZONTAL);row.setFocusable(true);row.setClickable(true);row.setStateListAnimator(null);row.setPadding(0,0,0,0);row.setBackground(sourceBg(false));RecyclerView.LayoutParams rp=new RecyclerView.LayoutParams(-1,dp(132));rp.bottomMargin=dp(10);row.setLayoutParams(rp);
            View accent=new View(SourceSelectionActivity.this);row.addView(accent,new LinearLayout.LayoutParams(dp(5),-1));
            LinearLayout body=new LinearLayout(SourceSelectionActivity.this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(12),dp(18),dp(10));
            LinearLayout tags=new LinearLayout(SourceSelectionActivity.this);tags.setOrientation(LinearLayout.HORIZONTAL);body.addView(tags,new LinearLayout.LayoutParams(-1,dp(28)));
            TextView name=text("",16,false);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.MARQUEE);name.setMarqueeRepeatLimit(-1);body.addView(name,new LinearLayout.LayoutParams(-1,dp(34)));
            TextView metrics=text("",13,false);metrics.setTextColor(MUTED);body.addView(metrics,new LinearLayout.LayoutParams(-1,dp(28)));row.addView(body,new LinearLayout.LayoutParams(0,-1,1f));return new SourceHolder(row,accent,tags,name,metrics);
        }
        @Override public void onBindViewHolder(SourceHolder h,int position){
            SourceOption s=visibleSources.get(position);h.tags.removeAllViews();h.tags.addView(tag(cacheTag(s),Boolean.TRUE.equals(s.cached)?GREEN:Color.rgb(145,157,170)));h.tags.addView(tag(safe(s.quality),Color.rgb(215,225,235)));if(!s.hdr.isEmpty())h.tags.addView(tag(s.hdr,GOLD));if(!"?".equals(s.videoCodec))h.tags.addView(tag(s.videoCodec,CYAN));if(!"?".equals(s.audioCodec))h.tags.addView(tag(s.audioCodec+(!s.audioLayout.isEmpty()?" "+s.audioLayout:""),VIOLET));
            h.name.setText(sourceName(s));h.metrics.setText(metrics(s));h.accent.setBackgroundColor(Boolean.TRUE.equals(s.cached)?GREEN:cacheUnknown(s)?Color.rgb(111,124,137):VIOLET);h.row.setBackground(sourceBg(false));h.name.setTextColor(WHITE);
            h.row.setOnFocusChangeListener((v,f)->{h.row.setBackground(sourceBg(f));h.name.setTextColor(f?Color.WHITE:WHITE);h.name.setSelected(f);if(f){selectSource(s);if(clickSounds)h.row.playSoundEffect(SoundEffectConstants.CLICK);}});h.row.setOnClickListener(v->openSource(h.row,s));
        }
        @Override public int getItemCount(){return visibleSources.size();}
    }

    private static final class SourceHolder extends RecyclerView.ViewHolder{final LinearLayout row,tags;final View accent;final TextView name,metrics;SourceHolder(LinearLayout r,View a,LinearLayout t,TextView n,TextView m){super(r);row=r;accent=a;tags=t;name=n;metrics=m;}}

    private TextView tag(String value,int color){TextView t=text(value,11,true);t.setTextColor(color);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);t.setBackground(round(Color.argb(35,255,255,255),Color.argb(45,255,255,255),1,5));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(23));p.setMarginEnd(dp(7));t.setLayoutParams(p);return t;}

    private void openSource(View row,SourceOption source){
        if(source==null||source.uri==null)return;String uri=source.uri;if(!uri.startsWith("magnet:")){startPlayer(uri,directFallbacks(source));return;}
        if(!new DebridStore(this).isConnected()){status.setText("Connect Real-Debrid first.");startActivity(new Intent(this,DebridActivity.class));return;}
        row.setEnabled(false);playButton.setEnabled(false);status.setText("Resolving selected source through Real-Debrid…");LoadingOverlay loading=LoadingOverlay.show(this,"Resolving source…");
        resolverExecutor.submit(()->{try{String resolved=new RealDebridClient(this).resolveMagnet(uri);runOnUiThread(()->{loading.hide();if(dead())return;row.setEnabled(true);playButton.setEnabled(true);status.setText("Resolved — starting playback");startPlayer(resolved,new ArrayList<>());});}
        catch(Exception e){String m=msg(e);boolean bad=e instanceof RealDebridClient.InfringingSourceException||m.toLowerCase(Locale.US).contains("infring")||m.toLowerCase(Locale.US).contains("not instantly available");DebugLog.append(this,"SOURCE","Resolve failed: "+m);runOnUiThread(()->{loading.hide();if(dead())return;row.setEnabled(true);playButton.setEnabled(true);if(bad){new SourceStore(this).removeUri(mediaId,uri);allSources.remove(source);visibleSources.remove(source);adapter.notifyDataSetChanged();status.setText("Source rejected and removed. Choose another source.");selectSource(visibleSources.isEmpty()?null:visibleSources.get(0));}else status.setText("Resolve failed: "+m);});}});
    }

    private void startPlayer(String uri,ArrayList<String> fallbackUris){Intent p=new Intent(this,PlayerActivity.class);p.putExtra(PlayerActivity.EXTRA_MEDIA_ID,mediaId);p.putExtra(PlayerActivity.EXTRA_URI,uri);p.putStringArrayListExtra(PlayerActivity.EXTRA_FALLBACK_URIS,fallbackUris);startActivity(p);overridePendingTransition(0,0);}
    private ArrayList<String> directFallbacks(SourceOption selected){ArrayList<String> out=new ArrayList<>();for(SourceOption s:visibleSources)if(s!=selected&&s.uri!=null&&!s.uri.startsWith("magnet:"))out.add(s.uri);return out;}

    private String title(){String explicit=getIntent().getStringExtra(EXTRA_TITLE);if(explicit!=null&&!explicit.trim().isEmpty())return explicit.trim();return media==null?"Choose Source":media.title;}
    private String summaryLine(){int cached=0,k4=0,unknown=0;for(SourceOption s:allSources){if(Boolean.TRUE.equals(s.cached))cached++;else if(s.cached==null)unknown++;String q=lower(s.quality);if(q.contains("2160")||q.contains("4k"))k4++;}return allSources.size()+" sources   •   "+cached+" cached   •   "+unknown+" cache unknown   •   "+k4+" in 4K";}
    private String sourceName(SourceOption s){if(s.releaseName!=null&&!s.releaseName.isEmpty())return s.releaseName;try{if(s.uri!=null&&s.uri.startsWith("magnet:")){String dn=Uri.parse(s.uri).getQueryParameter("dn");if(dn!=null&&!dn.trim().isEmpty())return dn.trim();}}catch(Exception ignored){}return s.provider+" • "+s.compactLabel();}
    private String metrics(SourceOption s){StringBuilder b=new StringBuilder();if(s.sizeBytes>0)b.append("▣ ").append(gb(s.sizeBytes));if(s.seeders>=0){if(b.length()>0)b.append("     ");b.append("♟ ").append(s.seeders).append(" seeds");}if(s.provider!=null&&!s.provider.isEmpty()){if(b.length()>0)b.append("     ");b.append("Indexer: ").append(s.provider);}return b.toString();}
    private static String cacheTag(SourceOption s){return Boolean.TRUE.equals(s.cached)?"RD+ CACHED":Boolean.FALSE.equals(s.cached)?"UNCACHED":"CACHE ?";}
    private static boolean cacheUnknown(SourceOption s){return s.cached==null;}
    private static String safe(String s){return s==null||s.trim().isEmpty()?"?":s;}
    private static String lower(String s){return s==null?"":s.toLowerCase(Locale.US);}
    private static String gb(long bytes){return String.format(Locale.US,"%.1f GB",bytes/1073741824.0);}
    private static String join(List<String> v){StringBuilder b=new StringBuilder();for(int i=0;i<v.size();i++){if(i>0)b.append(" / ");b.append(v.get(i).toUpperCase(Locale.US));}return b.toString();}

    private void openHub(String mode){Intent i=new Intent(this,MediaHubActivity.class);i.putExtra(MediaHubActivity.EXTRA_MODE,mode);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(i);overridePendingTransition(0,0);}
    private GradientDrawable sourceBg(boolean focus){return round(focus?Color.rgb(20,32,39):Color.rgb(20,24,31),focus?CYAN:Color.argb(20,255,255,255),focus?2:1,14);}
    private GradientDrawable playBg(boolean focus){GradientDrawable g=round(CYAN,focus?Color.WHITE:CYAN,focus?2:1,12);return g;}
    private GradientDrawable filterBg(boolean active,boolean focus){return round((active||focus)?CYAN:Color.rgb(19,24,31),(active||focus)?CYAN:Color.argb(35,255,255,255),1,18);}
    private GradientDrawable pill(boolean focus){return round(Color.rgb(22,26,33),focus?CYAN:Color.argb(30,255,255,255),focus?2:1,24);}
    private GradientDrawable circle(boolean focus){return round(focus?Color.rgb(206,252,255):Color.rgb(18,25,33),focus?CYAN:Color.argb(35,255,255,255),focus?2:1,40);}
    private GradientDrawable navBg(boolean focused,boolean active){return round((focused||active)?CYAN:Color.TRANSPARENT,(focused||active)?CYAN:Color.TRANSPARENT,1,12);}
    private GradientDrawable round(int fill,int stroke,int strokeDp,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(Color.alpha(stroke)>0)g.setStroke(dp(strokeDp),stroke);return g;}
    private TextView text(String s,int sp,boolean bold){TextView v=new TextView(this);v.setText(s==null?"":s);v.setTextColor(WHITE);v.setTextSize(sp);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private FrameLayout.LayoutParams frame(int w,int h,int gravity,int marginX,int marginY){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);if((gravity&Gravity.RIGHT)==Gravity.RIGHT||(gravity&Gravity.END)==Gravity.END)p.rightMargin=marginX;else p.leftMargin=marginX;if((gravity&Gravity.BOTTOM)==Gravity.BOTTOM)p.bottomMargin=marginY;else p.topMargin=marginY;return p;}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
    private int dp(int v){return TvUi.dp(this,v);}
    @Override protected void onDestroy(){resolverExecutor.shutdownNow();artExecutor.shutdownNow();super.onDestroy();}
}
