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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Personal library / vault dashboard matching the cinematic 10-foot design. */
public final class LibraryActivity extends Activity {
    private static final int BG = Color.rgb(7, 9, 14);
    private static final int RAIL = Color.rgb(10, 14, 21);
    private static final int PANEL = Color.rgb(24, 28, 36);
    private static final int PANEL_2 = Color.rgb(30, 34, 43);
    private static final int WHITE = Color.rgb(239, 241, 246);
    private static final int MUTED = Color.rgb(170, 179, 191);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int GREEN = Color.rgb(96, 245, 135);
    private static final int PURPLE = Color.rgb(221, 183, 255);
    private static final int ERROR = Color.rgb(255, 180, 171);

    private final ExecutorService artPool = Executors.newFixedThreadPool(3);
    private ProfileStore profiles;
    private CatalogStore catalog;
    private ArtworkLoader artwork;
    private SourceStore sourceStore;
    private OfflineQueue offline;
    private List<MediaCard> all = Collections.emptyList();
    private List<MediaCard> resume = Collections.emptyList();
    private List<MediaCard> watchlist = Collections.emptyList();
    private int watchFilter = 0;
    private boolean clickSounds;
    private int screenWidth;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        profiles = new ProfileStore(this);
        catalog = new CatalogStore(this);
        artwork = new ArtworkLoader(this);
        sourceStore = new SourceStore(this);
        offline = new OfflineQueue(this);
        clickSounds = new AppSettingsStore(this).clickSounds();
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        refreshData();
        setContentView(buildShell());
    }

    @Override protected void onResume() {
        super.onResume();
        clickSounds = new AppSettingsStore(this).clickSounds();
        refreshData();
        if (getWindow() != null && getWindow().getDecorView() != null) setContentView(buildShell());
    }

    @Override protected void onDestroy() {
        artPool.shutdownNow();
        super.onDestroy();
    }

    private void refreshData() {
        all = catalog.all();
        resume = profiles.continueWatching(all);
        Set<String> ids = new HashSet<>(profiles.watchlistMediaIds());
        ArrayList<MediaCard> items = new ArrayList<>();
        for (MediaCard card : all) if (ids.contains(card.id)) items.add(card);
        watchlist = items;
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        View haze = new View(this);
        haze.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.argb(30,0,240,255), Color.argb(0,0,0,0), Color.argb(28,124,58,237), Color.argb(0,0,0,0)}));
        root.addView(haze, new FrameLayout.LayoutParams(-1,-1));

        root.addView(buildNavRail(), frame(dp(78), -1, Gravity.START|Gravity.TOP, 0, 0));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(30), dp(10), dp(28), dp(22));
        root.addView(page, frame(screenWidth-dp(78), -1, Gravity.START|Gravity.TOP, dp(78),0));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(74)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), 0, dp(8), dp(20));
        scroll.addView(body);
        page.addView(scroll, new LinearLayout.LayoutParams(-1,0,1f));

        body.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(130)));
        body.addView(buildTabs(), new LinearLayout.LayoutParams(-1, dp(58)));
        body.addView(sectionHeading("Resume Playback", "SPATIAL D-PAD TARGET 01", "Row 1"), new LinearLayout.LayoutParams(-1,dp(52)));
        body.addView(buildResumeRow(), new LinearLayout.LayoutParams(-1,dp(210)));
        body.addView(buildWatchlistHeading(), new LinearLayout.LayoutParams(-1,dp(62)));
        body.addView(buildWatchlistRow(), new LinearLayout.LayoutParams(-1,dp(304)));
        body.addView(buildBento(), new LinearLayout.LayoutParams(-1,dp(340)));
        body.addView(buildFooter(), new LinearLayout.LayoutParams(-1,dp(72)));
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("M 0 0 V 1 3",22,true);logo.setLetterSpacing(.24f);
        TextView sub=text("STORIES WITHOUT LIMITS",9,true);sub.setTextColor(MUTED);sub.setLetterSpacing(.18f);
        brand.addView(logo);brand.addView(sub);
        top.addView(brand,frame(dp(310),dp(60),Gravity.START|Gravity.TOP,dp(8),dp(4)));

        TextView search=text("⌕   What do you want to watch or scrape?",14,false);
        search.setTextColor(Color.rgb(205,214,226));search.setGravity(Gravity.CENTER_VERTICAL);search.setPadding(dp(22),0,dp(20),0);
        search.setFocusable(true);search.setClickable(true);search.setBackground(pill(false,false));
        search.setOnFocusChangeListener((v,f)->search.setBackground(pill(f,false)));
        search.setOnClickListener(v->openHub(MediaHubActivity.MODE_SEARCH));
        top.addView(search,frame(Math.min(dp(560),(int)(screenWidth*.40f)),dp(48),Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(7)));

        LinearLayout acct=new LinearLayout(this);acct.setOrientation(LinearLayout.HORIZONTAL);acct.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        TextView clock=text(new SimpleDateFormat("h:mm a",Locale.US).format(new java.util.Date()),13,true);clock.setPadding(0,0,dp(15),0);acct.addView(clock,new LinearLayout.LayoutParams(-2,dp(42)));
        TextView profile=text(profileInitial(),14,true);profile.setGravity(Gravity.CENTER);profile.setFocusable(true);profile.setClickable(true);profile.setBackground(circle(false));
        profile.setOnFocusChangeListener((v,f)->profile.setBackground(circle(f)));profile.setOnClickListener(v->open(ProfileActivity.class));acct.addView(profile,new LinearLayout.LayoutParams(dp(40),dp(40)));
        top.addView(acct,frame(dp(185),dp(52),Gravity.END|Gravity.TOP,dp(5),dp(5)));
        return top;
    }

    private View buildHeader() {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(8));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setGravity(Gravity.CENTER_VERTICAL);
        TextView eye=text("●  PERSONAL VAULT  •  LOCAL PROFILE ACTIVE",11,true);eye.setTextColor(CYAN);eye.setLetterSpacing(.12f);left.addView(eye);
        TextView title=text("LIBRARY",38,true);left.addView(title);
        TextView desc=text("YOUR WATCHLIST, CONTINUED EXPERIENCES, AND SMART COLLECTIONS",13,false);desc.setTextColor(MUTED);left.addView(desc);
        row.addView(left,new LinearLayout.LayoutParams(0,-1,1f));

        LinearLayout status=new LinearLayout(this);status.setOrientation(LinearLayout.HORIZONTAL);status.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(round(PANEL,Color.argb(25,255,255,255),1,14));
        status.addView(statusChip("✓  Local Profile",profiles.activeProfile(),GREEN));
        boolean rd=new DebridStore(this).isConnected();status.addView(statusChip("☁  Real-Debrid",rd?"Connected":"Not connected",rd?CYAN:ERROR));
        List<OfflineQueue.Entry> q=offline.list();status.addView(statusChip("⇩  Offline Queue",q.size()+" item"+(q.size()==1?"":"s"),PURPLE));
        row.addView(status,new LinearLayout.LayoutParams(dp(480),dp(88)));
        return row;
    }

    private View statusChip(String top,String bottom,int color){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(10),0,dp(10),0);TextView a=text(top,11,true);a.setTextColor(color);TextView b=text(bottom,10,false);b.setTextColor(MUTED);c.addView(a);c.addView(b);return c;}

    private View buildTabs(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tab("▷  CONTINUE WATCHING  ("+resume.size()+")",true,()->{}),tabParams(190));
        row.addView(tab("▱  WATCHLIST  ("+watchlist.size()+")",false,()->focusWatchlist()),tabParams(170));
        row.addView(tab("◫  SMART COLLECTIONS",false,()->{}),tabParams(190));
        row.addView(tab("⇩  DOWNLOADS & CACHE",false,()->open(DownloadsActivity.class)),tabParams(190));
        return row;
    }

    private View sectionHeading(String title,String sub,String right){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.BOTTOM);TextView a=text(title,26,true);row.addView(a);TextView b=text("   "+sub,11,true);b.setTextColor(MUTED);row.addView(b,new LinearLayout.LayoutParams(0,-1,1f));TextView c=text(right,11,false);c.setTextColor(MUTED);c.setGravity(Gravity.END|Gravity.BOTTOM);row.addView(c,new LinearLayout.LayoutParams(dp(100),-1));return row;}

    private View buildResumeRow(){
        FrameLayout box=new FrameLayout(this);
        RecyclerView rail=new RecyclerView(this);rail.setOverScrollMode(View.OVER_SCROLL_NEVER);rail.setItemAnimator(null);rail.setHorizontalScrollBarEnabled(false);rail.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));rail.setItemViewCacheSize(8);
        rail.setAdapter(new ResumeAdapter(new ArrayList<>(resume.subList(0,Math.min(10,resume.size())))));
        box.addView(rail,new FrameLayout.LayoutParams(-1,-1));
        if(resume.isEmpty()){TextView e=text("Nothing in progress yet. Start a movie or episode and it will appear here.",15,false);e.setTextColor(MUTED);e.setGravity(Gravity.CENTER);box.addView(e,new FrameLayout.LayoutParams(-1,-1));}
        return box;
    }

    private View buildWatchlistHeading(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("My Watchlist & Priorities",26,true);row.addView(title,new LinearLayout.LayoutParams(0,-1,1f));
        int cached=countInstantDebrid(watchlist);
        addFilter(row,"All ("+watchlist.size()+")",0);
        addFilter(row,"Movies ("+countType(false)+")",1);
        addFilter(row,"TV Shows ("+countType(true)+")",2);
        addFilter(row,"⚡ Instant Debrid ("+cached+")",3);
        return row;
    }

    private void addFilter(LinearLayout row,String label,int filter){TextView b=tab(label,watchFilter==filter,()->{watchFilter=filter;setContentView(buildShell());});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(36));p.leftMargin=dp(8);row.addView(b,p);}

    private View buildWatchlistRow(){
        FrameLayout box=new FrameLayout(this);
        RecyclerView rail=new RecyclerView(this);rail.setId(View.generateViewId());rail.setOverScrollMode(View.OVER_SCROLL_NEVER);rail.setItemAnimator(null);rail.setHorizontalScrollBarEnabled(false);rail.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));rail.setItemViewCacheSize(10);
        List<MediaCard> filtered=filteredWatchlist();rail.setAdapter(new PosterAdapter(new ArrayList<>(filtered.subList(0,Math.min(18,filtered.size())))));
        box.addView(rail,new FrameLayout.LayoutParams(-1,-1));
        if(filtered.isEmpty()){TextView e=text(watchlist.isEmpty()?"Your watchlist is empty. Add titles with + from details.":"No titles match this filter.",15,false);e.setTextColor(MUTED);e.setGravity(Gravity.CENTER);box.addView(e,new FrameLayout.LayoutParams(-1,-1));}
        return box;
    }

    private View buildBento(){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setPadding(0,dp(8),0,dp(12));
        LinearLayout collections=new LinearLayout(this);collections.setOrientation(LinearLayout.VERTICAL);collections.setPadding(0,0,dp(20),0);
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);TextView title=text("Smart Collections",26,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(45),1f));TextView allBtn=text("LOCAL PROFILE",10,true);allBtn.setTextColor(CYAN);allBtn.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);head.addView(allBtn,new LinearLayout.LayoutParams(dp(130),dp(45)));collections.addView(head);
        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(collectionCard("Movies in Watchlist",countType(false)+" TITLES","Your saved movie queue.",CYAN,()->{watchFilter=1;setContentView(buildShell());}),collectionParams());
        cards.addView(collectionCard("TV in Watchlist",countType(true)+" TITLES","Series saved to your profile.",PURPLE,()->{watchFilter=2;setContentView(buildShell());}),collectionParams());
        cards.addView(collectionCard("Watched History",profiles.watchedMediaIds().size()+" TITLES","Completed titles recorded locally.",GREEN,()->{}),collectionParams());
        collections.addView(cards,new LinearLayout.LayoutParams(-1,0,1f));
        row.addView(collections,new LinearLayout.LayoutParams(0,-1,.60f));

        row.addView(vaultCard(),new LinearLayout.LayoutParams(0,-1,.40f));
        return row;
    }

    private View collectionCard(String title,String count,String body,int accent,Runnable action){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setFocusable(true);c.setClickable(true);c.setBackground(round(PANEL,Color.argb(28,255,255,255),1,14));c.setOnFocusChangeListener((v,f)->c.setBackground(round(f?Color.rgb(35,42,52):PANEL,f?CYAN:Color.argb(28,255,255,255),f?2:1,14)));c.setOnClickListener(v->action.run());
        View art=new View(this);art.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.argb(160,accent>>16&255,accent>>8&255,accent&255),Color.rgb(15,18,24)}));c.addView(art,new LinearLayout.LayoutParams(-1,dp(86)));
        TextView num=text(count,11,true);num.setTextColor(accent);num.setPadding(0,dp(8),0,dp(4));c.addView(num);
        TextView t=text(title,15,true);c.addView(t);
        TextView d=text(body,11,false);d.setTextColor(MUTED);d.setMaxLines(3);c.addView(d);
        return c;
    }
    private LinearLayout.LayoutParams collectionParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);p.rightMargin=dp(10);return p;}

    private View vaultCard(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(round(Color.argb(235,24,28,36),Color.argb(25,255,255,255),1,16));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);TextView t=text("Profile & Debrid Vault",25,true);head.addView(t,new LinearLayout.LayoutParams(0,dp(42),1f));TextView on=text("● LOCAL",10,true);on.setTextColor(GREEN);on.setGravity(Gravity.CENTER);head.addView(on,new LinearLayout.LayoutParams(dp(72),dp(30)));card.addView(head);
        TextView profile=text("◉  "+profiles.activeProfile(),16,true);profile.setTextColor(PURPLE);card.addView(profile,new LinearLayout.LayoutParams(-1,dp(38)));

        LinearLayout stats=new LinearLayout(this);stats.setOrientation(LinearLayout.HORIZONTAL);stats.setPadding(0,dp(8),0,dp(8));stats.setBackground(round(Color.rgb(17,20,27),Color.TRANSPARENT,0,10));
        stats.addView(stat("WATCHED TITLES",String.valueOf(profiles.watchedMediaIds().size()),"local history"),new LinearLayout.LayoutParams(0,dp(82),1f));
        stats.addView(stat("IN PROGRESS",String.valueOf(resume.size()),resumeMinutes()+" min remaining"),new LinearLayout.LayoutParams(0,dp(82),1f));card.addView(stats);

        boolean rd=new DebridStore(this).isConnected();
        LinearLayout rdRow=new LinearLayout(this);rdRow.setOrientation(LinearLayout.HORIZONTAL);rdRow.setGravity(Gravity.CENTER_VERTICAL);rdRow.setPadding(dp(10),dp(8),dp(10),dp(8));rdRow.setBackground(round(PANEL_2,Color.TRANSPARENT,0,10));
        TextView left=text("☁  Real-Debrid\n"+(rd?"Account connected":"Connect in Debrid settings"),11,true);left.setTextColor(rd?CYAN:MUTED);rdRow.addView(left,new LinearLayout.LayoutParams(0,dp(52),1f));
        TextView right=text(rd?"READY":"OFFLINE",11,true);right.setTextColor(rd?GREEN:ERROR);right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);rdRow.addView(right,new LinearLayout.LayoutParams(dp(85),dp(52)));card.addView(rdRow,new LinearLayout.LayoutParams(-1,dp(62)));

        List<OfflineQueue.Entry> q=offline.list();long bytes=0;for(OfflineQueue.Entry e:q)bytes+=Math.max(0,e.estimatedBytes);
        TextView queue=text("⇩  Downloads & Cache   "+q.size()+" queued  •  "+size(bytes),11,true);queue.setFocusable(true);queue.setClickable(true);queue.setPadding(dp(10),0,dp(10),0);queue.setBackground(pill(false,false));queue.setOnFocusChangeListener((v,f)->queue.setBackground(pill(f,false)));queue.setOnClickListener(v->open(DownloadsActivity.class));card.addView(queue,new LinearLayout.LayoutParams(-1,dp(46)));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(10),0,0);
        TextView debrid=tab("MANAGE DEBRID",true,()->open(DebridActivity.class));actions.addView(debrid,new LinearLayout.LayoutParams(0,dp(42),1f));
        TextView manage=tab("PROFILE",false,()->open(ProfileActivity.class));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(110),dp(42));mp.leftMargin=dp(8);actions.addView(manage,mp);card.addView(actions);
        return card;
    }

    private View stat(String label,String value,String sub){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(14),dp(8),dp(10),dp(8));TextView l=text(label,10,false);l.setTextColor(MUTED);TextView v=text(value,25,true);TextView s=text(sub,10,false);s.setTextColor(GREEN);c.addView(l);c.addView(v);c.addView(s);return c;}

    private View buildFooter(){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),dp(8),dp(14),dp(8));row.setBackground(round(Color.argb(220,10,14,21),Color.argb(25,255,255,255),1,12));TextView left=text("▲/▼ Navigate Rows    ◀/▶ Browse Items    ENTER Play / Details",11,false);left.setTextColor(MUTED);row.addView(left,new LinearLayout.LayoutParams(0,-1,1f));TextView right=text("LOCAL PROFILE  •  RD STATUS  •  DOWNLOAD QUEUE",11,true);right.setTextColor(CYAN);right.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);row.addView(right,new LinearLayout.LayoutParams(-2,-1));return row;}

    private View buildNavRail(){
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(9),dp(46),dp(9),dp(16));rail.setBackgroundColor(RAIL);
        addNav(rail,"▣",false,()->openHub(MediaHubActivity.MODE_HOME));
        addNav(rail,"⌂",false,()->openHub(MediaHubActivity.MODE_HOME));
        addNav(rail,"▤",false,()->openHub(MediaHubActivity.MODE_MOVIES));
        addNav(rail,"▥",false,()->openHub(MediaHubActivity.MODE_TV));
        addNav(rail,"⌕",false,()->openHub(MediaHubActivity.MODE_SEARCH));
        addNav(rail,"↗",false,()->open(DebridActivity.class));
        addNav(rail,"▱",false,()->open(ProviderSettingsActivity.class));
        addNav(rail,"♡",true,()->{});
        View gap=new View(this);rail.addView(gap,new LinearLayout.LayoutParams(1,0,1f));
        addNav(rail,"⚙",false,()->open(SettingsActivity.class));return rail;
    }

    private void addNav(LinearLayout rail,String glyph,boolean active,Runnable action){TextView b=text(glyph,20,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setTextColor(active?Color.rgb(0,55,60):Color.rgb(198,210,220));b.setBackground(navBackground(active,false));b.setOnFocusChangeListener((v,f)->b.setBackground(navBackground(active,f)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48));p.bottomMargin=dp(10);rail.addView(b,p);}

    private final class ResumeAdapter extends RecyclerView.Adapter<ResumeHolder>{
        private final List<MediaCard> items;ResumeAdapter(List<MediaCard> items){this.items=items;setHasStableIds(true);}
        @Override public long getItemId(int p){return items.get(p).id.hashCode();}
        @Override public ResumeHolder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout card=new LinearLayout(LibraryActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setFocusable(true);card.setClickable(true);card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(cardBg(false));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(dp(248),dp(188));lp.rightMargin=dp(12);lp.topMargin=dp(6);card.setLayoutParams(lp);
            FrameLayout imageWrap=new FrameLayout(LibraryActivity.this);ImageView image=new ImageView(LibraryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(PANEL_2);imageWrap.addView(image,new FrameLayout.LayoutParams(-1,-1));TextView badge=text("",10,true);badge.setPadding(dp(7),dp(2),dp(7),dp(2));badge.setBackground(round(Color.argb(210,15,19,26),Color.TRANSPARENT,0,5));imageWrap.addView(badge,new FrameLayout.LayoutParams(-2,dp(25),Gravity.TOP|Gravity.START));card.addView(imageWrap,new LinearLayout.LayoutParams(-1,dp(112)));
            TextView title=text("",14,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title,new LinearLayout.LayoutParams(-1,dp(28)));TextView progress=text("",11,false);progress.setTextColor(MUTED);card.addView(progress,new LinearLayout.LayoutParams(-1,dp(26)));return new ResumeHolder(card,image,badge,title,progress);
        }
        @Override public void onBindViewHolder(ResumeHolder h,int p){MediaCard m=items.get(p);long pos=profiles.progressMs(m.id),dur=profiles.durationMs(m.id);int pct=dur>0?(int)Math.min(100,(pos*100)/dur):0;long left=Math.max(0,dur-pos)/60000L;h.title.setText(m.title);h.badge.setText((m.series?episodeTag(m):"MOVIE")+"   "+pct+"%");h.progress.setText(left>0?left+"m left":"Resume available");artwork.load(h.image,m.artworkUrl,480,artPool);bindCardFocus(h.card,()->openMedia(m),()->openDetails(m));}
        @Override public int getItemCount(){return items.size();}
    }
    private static final class ResumeHolder extends RecyclerView.ViewHolder{final LinearLayout card;final ImageView image;final TextView badge,title,progress;ResumeHolder(LinearLayout c,ImageView i,TextView b,TextView t,TextView p){super(c);card=c;image=i;badge=b;title=t;progress=p;}}

    private final class PosterAdapter extends RecyclerView.Adapter<PosterHolder>{
        private final List<MediaCard> items;PosterAdapter(List<MediaCard> items){this.items=items;setHasStableIds(true);}
        @Override public long getItemId(int p){return items.get(p).id.hashCode();}
        @Override public PosterHolder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout card=new LinearLayout(LibraryActivity.this);card.setOrientation(LinearLayout.VERTICAL);card.setFocusable(true);card.setClickable(true);card.setPadding(dp(7),dp(7),dp(7),dp(7));card.setBackground(cardBg(false));RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(dp(178),dp(286));lp.rightMargin=dp(12);lp.topMargin=dp(4);card.setLayoutParams(lp);
            FrameLayout wrap=new FrameLayout(LibraryActivity.this);ImageView image=new ImageView(LibraryActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(PANEL_2);wrap.addView(image,new FrameLayout.LayoutParams(-1,-1));TextView badge=text("",9,true);badge.setPadding(dp(6),dp(2),dp(6),dp(2));badge.setBackground(round(Color.argb(215,12,16,22),Color.TRANSPARENT,0,5));wrap.addView(badge,new FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP|Gravity.START));card.addView(wrap,new LinearLayout.LayoutParams(-1,dp(210)));
            TextView title=text("",13,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title,new LinearLayout.LayoutParams(-1,dp(27)));TextView meta=text("",10,false);meta.setTextColor(MUTED);meta.setMaxLines(2);card.addView(meta,new LinearLayout.LayoutParams(-1,dp(37)));return new PosterHolder(card,image,badge,title,meta);
        }
        @Override public void onBindViewHolder(PosterHolder h,int p){MediaCard m=items.get(p);boolean cached=hasInstantDebrid(m);h.title.setText(m.title);h.badge.setText((cached?"RD READY  •  ":"")+(m.series?"TV":"MOVIE"));h.badge.setTextColor(cached?GREEN:WHITE);h.meta.setText((m.subtitle==null?"":m.subtitle)+(m.genre==null||m.genre.isEmpty()?"":"  •  "+m.genre));artwork.load(h.image,m.artworkUrl,356,artPool);bindCardFocus(h.card,()->openDetails(m),()->removeWatchlist(m));}
        @Override public int getItemCount(){return items.size();}
    }
    private static final class PosterHolder extends RecyclerView.ViewHolder{final LinearLayout card;final ImageView image;final TextView badge,title,meta;PosterHolder(LinearLayout c,ImageView i,TextView b,TextView t,TextView m){super(c);card=c;image=i;badge=b;title=t;meta=m;}}

    private void bindCardFocus(View card,Runnable click,Runnable longClick){card.setOnFocusChangeListener((v,f)->{v.setBackground(cardBg(f));v.setScaleX(f?1.04f:1f);v.setScaleY(f?1.04f:1f);v.setTranslationZ(f?dp(10):0);if(f&&clickSounds)v.playSoundEffect(SoundEffectConstants.CLICK);});card.setOnClickListener(v->click.run());card.setOnLongClickListener(v->{longClick.run();return true;});}

    private void openMedia(MediaCard m){Intent i=new Intent(this,MediaOpenActivity.class);i.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID,m.id);startActivity(i);overridePendingTransition(0,0);}
    private void openDetails(MediaCard m){Intent i=new Intent(this,TitleDetailsActivity.class);i.putExtra(TitleDetailsActivity.EXTRA_MEDIA_ID,m.id);startActivity(i);overridePendingTransition(0,0);}
    private void removeWatchlist(MediaCard m){profiles.setWatchlist(m.id,false);refreshData();setContentView(buildShell());}

    private boolean hasInstantDebrid(MediaCard m){for(SourceOption s:sourceStore.getFresh(m.id))if(Boolean.TRUE.equals(s.cached))return true;return false;}
    private int countInstantDebrid(List<MediaCard> items){int n=0;for(MediaCard m:items)if(hasInstantDebrid(m))n++;return n;}
    private int countType(boolean series){int n=0;for(MediaCard m:watchlist)if(m.series==series)n++;return n;}
    private List<MediaCard> filteredWatchlist(){ArrayList<MediaCard> out=new ArrayList<>();for(MediaCard m:watchlist){if(watchFilter==1&&m.series)continue;if(watchFilter==2&&!m.series)continue;if(watchFilter==3&&!hasInstantDebrid(m))continue;out.add(m);}return out;}
    private long resumeMinutes(){long total=0;for(MediaCard m:resume){long d=profiles.durationMs(m.id),p=profiles.progressMs(m.id);if(d>p)total+=(d-p)/60000L;}return total;}
    private String episodeTag(MediaCard m){return m.isEpisode()?"S"+m.seasonNumber+":E"+m.episodeNumber:"SERIES";}
    private String size(long b){if(b<=0)return "0 B";double g=b/(1024d*1024d*1024d);if(g>=1)return String.format(Locale.US,"%.1f GiB",g);return Math.max(1,b/(1024L*1024L))+" MiB";}
    private void focusWatchlist(){ToastCompat.show(this,"Watchlist row is below Continue Watching");}

    private TextView tab(String label,boolean active,Runnable action){TextView b=text(label,10,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setTextColor(active?Color.rgb(0,55,60):WHITE);b.setBackground(pill(false,active));b.setOnFocusChangeListener((v,f)->b.setBackground(pill(f,active)));b.setOnClickListener(v->action.run());return b;}
    private LinearLayout.LayoutParams tabParams(int width){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),dp(42));p.rightMargin=dp(10);return p;}

    private GradientDrawable cardBg(boolean focused){return round(focused?Color.rgb(33,39,49):PANEL,focused?CYAN:Color.argb(25,255,255,255),focused?2:1,12);}
    private GradientDrawable pill(boolean focused,boolean active){return round(active?CYAN:focused?Color.rgb(42,49,59):Color.rgb(28,33,41),focused?CYAN:Color.argb(22,255,255,255),focused?2:1,22);}
    private GradientDrawable navBackground(boolean active,boolean focused){return round(active?CYAN:focused?Color.argb(50,0,240,255):Color.TRANSPARENT,focused?CYAN:Color.TRANSPARENT,focused?2:0,12);}
    private GradientDrawable circle(boolean focused){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.rgb(28,38,47));g.setStroke(dp(focused?2:1),focused?CYAN:Color.argb(80,255,255,255));return g;}
    private GradientDrawable round(int fill,int stroke,int strokeDp,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}

    private TextView text(String value,int sp,boolean bold){TextView v=new TextView(this);v.setText(value==null?"":value);v.setTextColor(WHITE);v.setTextSize(sp);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private FrameLayout.LayoutParams frame(int w,int h,int gravity,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);if((gravity&Gravity.END)==Gravity.END)p.rightMargin=x;else p.leftMargin=x;if((gravity&Gravity.BOTTOM)==Gravity.BOTTOM)p.bottomMargin=y;else p.topMargin=y;return p;}
    private int dp(int v){return TvUi.dp(this,v);}
    private String profileInitial(){String p=profiles.activeProfile();return p==null||p.isEmpty()?"D":p.substring(0,1).toUpperCase(Locale.US);}
    private void openHub(String mode){Intent i=new Intent(this,MediaHubActivity.class);i.putExtra(MediaHubActivity.EXTRA_MODE,mode);startActivity(i);finish();overridePendingTransition(0,0);}
    private void open(Class<?> cls){startActivity(new Intent(this,cls));overridePendingTransition(0,0);}

    /** Tiny no-dependency toast helper; kept here so LibraryActivity stays self-contained. */
    private static final class ToastCompat { static void show(Activity a,String s){android.widget.Toast.makeText(a,s,android.widget.Toast.LENGTH_SHORT).show();} }
}
