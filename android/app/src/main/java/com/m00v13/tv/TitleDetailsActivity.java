package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Movie/show details surface based on the Stitch M00V13 10-foot UI. */
public final class TitleDetailsActivity extends Activity {
    public static final String EXTRA_MEDIA_ID = "media_id";

    private static final int BG = Color.rgb(7, 9, 14);
    private static final int PANEL = Color.argb(225, 17, 21, 29);
    private static final int PANEL_SOFT = Color.argb(205, 25, 28, 34);
    private static final int WHITE = Color.rgb(238, 242, 248);
    private static final int MUTED = Color.rgb(178, 191, 198);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int GREEN = Color.rgb(107, 255, 143);
    private static final int VIOLET = Color.rgb(168, 85, 247);
    private static final int GOLD = Color.rgb(245, 158, 11);

    private final ExecutorService dataPool = Executors.newFixedThreadPool(2);
    private final ExecutorService artPool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, List<TitleDetailsData.Episode>> episodeCache = new LinkedHashMap<>();
    private final Map<Integer, TextView> seasonButtons = new LinkedHashMap<>();

    private CatalogStore catalog;
    private ProfileStore profiles;
    private ArtworkLoader artwork;
    private TitleDetailsRepository repository;
    private AppSettingsStore settings;
    private MediaCard media;
    private TitleDetailsData details;
    private MediaCard selectedPlayable;
    private int activeSeason;
    private boolean clickSounds;

    private ImageView backdrop;
    private TextView clock;
    private TextView titleView;
    private TextView eyebrow;
    private TextView synopsis;
    private LinearLayout badgeRow;
    private LinearLayout genreRow;
    private TextView playButton;
    private TextView sourceButton;
    private TextView trailerButton;
    private TextView watchlistButton;
    private LinearLayout seriesSection;
    private LinearLayout seasonTabs;
    private TextView episodeSummary;
    private TextView episodeStatus;
    private RecyclerView episodeList;
    private EpisodeAdapter episodeAdapter;
    private LinearLayout castChips;
    private TextView directorLine;
    private TextView writerLine;
    private LinearLayout specs;
    private TextView screenStatus;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        catalog = new CatalogStore(this);
        profiles = new ProfileStore(this);
        artwork = new ArtworkLoader(this);
        repository = new TitleDetailsRepository(this);
        settings = new AppSettingsStore(this);
        clickSounds = settings.clickSounds();

        String mediaId = getIntent().getStringExtra(EXTRA_MEDIA_ID);
        media = mediaId == null ? null : catalog.find(mediaId);
        if (media == null) {
            FrameLayout error = new FrameLayout(this); error.setBackgroundColor(BG);
            TextView text = text("Title metadata is unavailable.", 22, true); text.setGravity(Gravity.CENTER);
            error.addView(text, new FrameLayout.LayoutParams(-1, -1)); setContentView(error); return;
        }

        details = TitleDetailsData.fallback(media);
        setContentView(buildShell());
        renderDetails(details);
        updateClock();
        dataPool.submit(() -> {
            try {
                TitleDetailsData loaded = repository.load(media);
                runOnUiThread(() -> { if (!dead()) { details = loaded; renderDetails(loaded); } });
            } catch (Exception e) {
                DebugLog.append(this, "DETAILS", "Metadata load failed: " + msg(e));
                runOnUiThread(() -> { if (!dead()) screenStatus.setText("Using cached title metadata • " + msg(e)); });
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        clickSounds = new AppSettingsStore(this).clickSounds();
        if (media != null) {
            updateWatchlistButton();
            updatePrimaryTarget();
            updateEpisodeProgress();
            rebuildSpecs();
        }
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        dataPool.shutdownNow(); artPool.shutdownNow(); super.onDestroy();
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(BG);
        backdrop = new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP); backdrop.setAlpha(.42f); backdrop.setBackgroundColor(BG);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.argb(248,7,9,14), Color.argb(230,7,9,14), Color.argb(150,7,9,14), Color.argb(55,7,9,14)});
        shade.setBackground(g); root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View floor = new View(this);
        floor.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{Color.argb(255,7,9,14), Color.argb(245,7,9,14), Color.argb(20,7,9,14)}));
        root.addView(floor, frame(-1, dp(390), Gravity.BOTTOM, 0, 0));

        root.addView(buildTopBar(), frame(-1, dp(84), Gravity.TOP, 0, 0));
        root.addView(buildNavRail(), frame(dp(78), -1, Gravity.START | Gravity.TOP, dp(18), dp(42)));

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false); scroll.setSmoothScrollingEnabled(false);
        LinearLayout content = buildContent(); scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, -1);
        sp.leftMargin = dp(118); sp.rightMargin = dp(28); sp.topMargin = dp(84); sp.bottomMargin = dp(20);
        root.addView(scroll, sp);
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this); top.setBackgroundColor(Color.argb(185, 12, 14, 19));
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("M 0 0 V 1 3", 23, true); logo.setLetterSpacing(.22f);
        TextView sub = text("STORIES WITHOUT LIMITS", 9, true); sub.setTextColor(MUTED); sub.setLetterSpacing(.15f);
        brand.addView(logo); brand.addView(sub);
        top.addView(brand, frame(dp(290), dp(68), Gravity.START | Gravity.TOP, dp(28), dp(9)));

        TextView search = text("⌕   What do you want to watch or scrape?                                      🎙", 14, false);
        search.setGravity(Gravity.CENTER_VERTICAL); search.setPadding(dp(18),0,dp(18),0); search.setTextColor(Color.rgb(205,214,225));
        search.setFocusable(true); search.setClickable(true); search.setStateListAnimator(null); search.setBackground(pill(false));
        search.setOnFocusChangeListener((v,f)->{ search.setBackground(pill(f)); search.setScaleX(f?1.02f:1f); search.setScaleY(f?1.02f:1f); });
        search.setOnClickListener(v -> openHub(MediaHubActivity.MODE_SEARCH));
        top.addView(search, frame(Math.min(dp(570), (int)(getResources().getDisplayMetrics().widthPixels*.42f)), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(17)));

        LinearLayout user = new LinearLayout(this); user.setOrientation(LinearLayout.HORIZONTAL); user.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        clock = text("", 14, true); clock.setTextColor(WHITE); clock.setGravity(Gravity.CENTER_VERTICAL); user.addView(clock, new LinearLayout.LayoutParams(dp(104), dp(46)));
        TextView profile = text(profileInitial(), 14, true); profile.setGravity(Gravity.CENTER); profile.setFocusable(true); profile.setClickable(true); profile.setBackground(circle(false));
        profile.setOnFocusChangeListener((v,f)->profile.setBackground(circle(f))); profile.setOnClickListener(v->open(ProfileActivity.class));
        user.addView(profile, new LinearLayout.LayoutParams(dp(42), dp(42)));
        top.addView(user, frame(dp(165), dp(58), Gravity.END | Gravity.TOP, dp(22), dp(12)));
        return top;
    }

    private View buildNavRail() {
        LinearLayout rail = new LinearLayout(this); rail.setOrientation(LinearLayout.VERTICAL); rail.setGravity(Gravity.CENTER_HORIZONTAL); rail.setPadding(dp(8),dp(6),dp(8),dp(8));
        rail.setBackground(roundRect(Color.argb(218,12,14,19), Color.argb(25,255,255,255),1,26));
        TextView mark = navItem("▣", false, () -> {}); rail.addView(mark, navParams());
        addNav(rail,"⌂",false,()->openHub(MediaHubActivity.MODE_HOME));
        addNav(rail,"▣",!media.series,()->openHub(MediaHubActivity.MODE_MOVIES));
        addNav(rail,"▤",media.series,()->openHub(MediaHubActivity.MODE_TV));
        addNav(rail,"♡",false,()->openHub(MediaHubActivity.MODE_LIBRARY));
        addNav(rail,"⌕",false,()->openHub(MediaHubActivity.MODE_SEARCH));
        addNav(rail,"↗",false,()->open(DebridActivity.class));
        addNav(rail,"◉",false,()->open(ProviderSettingsActivity.class));
        addNav(rail,"♥",false,()->open(DonateActivity.class));
        View gap = new View(this); rail.addView(gap, new LinearLayout.LayoutParams(1,0,1f));
        addNav(rail,"⚙",false,()->open(SettingsActivity.class));
        return rail;
    }

    private void addNav(LinearLayout rail, String glyph, boolean active, Runnable action) { rail.addView(navItem(glyph,active,action), navParams()); }
    private TextView navItem(String glyph, boolean active, Runnable action) {
        TextView v=text(glyph,21,active); v.setGravity(Gravity.CENTER); v.setFocusable(true); v.setClickable(true); v.setStateListAnimator(null);
        v.setTextColor(active?Color.rgb(0,55,60):MUTED); v.setBackground(active?roundRect(CYAN,CYAN,1,13):null);
        v.setOnFocusChangeListener((view,f)->{ if(!active){v.setTextColor(f?WHITE:MUTED);v.setBackground(f?roundRect(Color.argb(100,40,48,58),CYAN,2,13):null);} if(f&&clickSounds)v.playSoundEffect(SoundEffectConstants.CLICK); });
        v.setOnClickListener(view->action.run()); return v;
    }
    private LinearLayout.LayoutParams navParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48));p.bottomMargin=dp(7);return p;}

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(6),0,dp(26));
        TextView crumbs = text((media.series?"Series Hub":"Movies") + "   ›   " + safe(media.genre, media.series?"Series":"Movie") + "   ›   " + media.title + "   ›   Details", 12, true);
        crumbs.setTextColor(Color.rgb(203,214,222)); root.addView(crumbs, new LinearLayout.LayoutParams(-1,dp(34)));
        root.addView(buildHeroPanel(), new LinearLayout.LayoutParams(-1, dp(455)));
        seriesSection = buildSeriesSection(); root.addView(seriesSection, new LinearLayout.LayoutParams(-1, dp(302)));
        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.HORIZONTAL); info.setGravity(Gravity.TOP);
        View people = buildPeoplePanel(); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(235), .68f); pp.rightMargin=dp(20); info.addView(people,pp);
        View spec = buildSpecsPanel(); info.addView(spec,new LinearLayout.LayoutParams(0,dp(235),.32f)); root.addView(info,new LinearLayout.LayoutParams(-1,dp(255)));
        screenStatus = text("",11,false); screenStatus.setTextColor(MUTED); screenStatus.setPadding(dp(14),0,dp(14),0); screenStatus.setGravity(Gravity.CENTER_VERTICAL); screenStatus.setBackground(roundRect(Color.argb(160,12,14,19),Color.argb(25,255,255,255),1,14));
        root.addView(screenStatus,new LinearLayout.LayoutParams(-1,dp(48)));
        return root;
    }

    private View buildHeroPanel() {
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(30),dp(24),dp(30),dp(22));
        hero.setBackground(roundRect(Color.argb(178,12,16,23), Color.argb(22,255,255,255),1,22));
        eyebrow = text("TITLE DETAILS",12,true); eyebrow.setTextColor(Color.rgb(187,250,255)); eyebrow.setLetterSpacing(.11f); hero.addView(eyebrow);
        titleView = text(media.title==null?"":media.title.toUpperCase(Locale.US),44,true); titleView.setMaxLines(2); titleView.setEllipsize(TextUtils.TruncateAt.END); hero.addView(titleView,new LinearLayout.LayoutParams(-1,-2));

        HorizontalScrollView badgeScroll = new HorizontalScrollView(this); badgeScroll.setHorizontalScrollBarEnabled(false); badgeRow=new LinearLayout(this); badgeRow.setOrientation(LinearLayout.HORIZONTAL); badgeScroll.addView(badgeRow,new HorizontalScrollView.LayoutParams(-2,dp(34))); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(38));bp.topMargin=dp(8);hero.addView(badgeScroll,bp);
        synopsis = text("Loading title details…",16,false); synopsis.setTextColor(Color.rgb(218,224,232)); synopsis.setLineSpacing(0,1.12f); synopsis.setMaxLines(4); synopsis.setEllipsize(TextUtils.TruncateAt.END); LinearLayout.LayoutParams synp=new LinearLayout.LayoutParams(-1,0,1f);synp.topMargin=dp(10);synp.bottomMargin=dp(9);hero.addView(synopsis,synp);

        HorizontalScrollView genreScroll=new HorizontalScrollView(this);genreScroll.setHorizontalScrollBarEnabled(false);genreRow=new LinearLayout(this);genreRow.setOrientation(LinearLayout.HORIZONTAL);genreScroll.addView(genreRow,new HorizontalScrollView.LayoutParams(-2,dp(34)));hero.addView(genreScroll,new LinearLayout.LayoutParams(-1,dp(40)));

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);
        playButton=action("▶  Play",true,()->playSelected(false)); sourceButton=action("ϟ  Scrape Sources & Debrid",false,()->playSelected(true)); trailerButton=action("▣  Watch Trailer",false,this::watchTrailer); watchlistButton=action("＋",false,this::toggleWatchlist);
        actions.addView(playButton,actionParams(dp(330))); actions.addView(sourceButton,actionParams(dp(260))); actions.addView(trailerButton,actionParams(dp(180))); actions.addView(watchlistButton,actionParams(dp(58)));
        hero.addView(actions,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView playback=action("☷  Playback Settings",false,()->open(SettingsActivity.class)); LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(dp(190),dp(44));pl.topMargin=dp(8);hero.addView(playback,pl);
        return hero;
    }

    private LinearLayout buildSeriesSection() {
        LinearLayout section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);section.setPadding(0,dp(16),0,0);
        LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView tabsScroll=new HorizontalScrollView(this);tabsScroll.setHorizontalScrollBarEnabled(false);seasonTabs=new LinearLayout(this);seasonTabs.setOrientation(LinearLayout.HORIZONTAL);tabsScroll.addView(seasonTabs,new HorizontalScrollView.LayoutParams(-2,dp(44)));heading.addView(tabsScroll,new LinearLayout.LayoutParams(0,dp(48),1f));
        episodeSummary=text("",12,false);episodeSummary.setTextColor(MUTED);episodeSummary.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);heading.addView(episodeSummary,new LinearLayout.LayoutParams(dp(310),dp(48)));section.addView(heading,new LinearLayout.LayoutParams(-1,dp(50)));
        FrameLayout body=new FrameLayout(this);episodeList=new RecyclerView(this);episodeList.setItemAnimator(null);episodeList.setOverScrollMode(View.OVER_SCROLL_NEVER);episodeList.setHorizontalScrollBarEnabled(false);episodeList.setLayoutManager(new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false));episodeList.setItemViewCacheSize(8);body.addView(episodeList,new FrameLayout.LayoutParams(-1,-1));
        episodeStatus=text("Loading episodes…",15,false);episodeStatus.setTextColor(MUTED);episodeStatus.setGravity(Gravity.CENTER);body.addView(episodeStatus,new FrameLayout.LayoutParams(-1,-1));section.addView(body,new LinearLayout.LayoutParams(-1,0,1f));
        return section;
    }

    private View buildPeoplePanel() {
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(12),0,0);
        TextView h=text("Key Cast & Crew",22,true);panel.addView(h,new LinearLayout.LayoutParams(-1,dp(40)));
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);castChips=new LinearLayout(this);castChips.setOrientation(LinearLayout.HORIZONTAL);sc.addView(castChips,new HorizontalScrollView.LayoutParams(-2,dp(66)));panel.addView(sc,new LinearLayout.LayoutParams(-1,dp(74)));
        directorLine=text("",13,false);directorLine.setTextColor(Color.rgb(213,220,229));directorLine.setGravity(Gravity.CENTER_VERTICAL);directorLine.setPadding(dp(16),0,dp(16),0);directorLine.setBackground(roundRect(PANEL_SOFT,Color.argb(20,255,255,255),1,14));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(48));dl.bottomMargin=dp(7);panel.addView(directorLine,dl);
        writerLine=text("",13,false);writerLine.setTextColor(Color.rgb(213,220,229));writerLine.setGravity(Gravity.CENTER_VERTICAL);writerLine.setPadding(dp(16),0,dp(16),0);writerLine.setBackground(roundRect(PANEL_SOFT,Color.argb(20,255,255,255),1,14));panel.addView(writerLine,new LinearLayout.LayoutParams(-1,dp(48)));
        return panel;
    }

    private View buildSpecsPanel() {
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        TextView h=text("Playback & Sources",22,true);panel.addView(h,new LinearLayout.LayoutParams(-1,dp(45)));
        specs=new LinearLayout(this);specs.setOrientation(LinearLayout.VERTICAL);specs.setPadding(dp(16),dp(12),dp(16),dp(10));specs.setBackground(roundRect(PANEL_SOFT,Color.argb(24,255,255,255),1,18));panel.addView(specs,new LinearLayout.LayoutParams(-1,0,1f));
        return panel;
    }

    private void renderDetails(TitleDetailsData d) {
        if (d == null) return;
        titleView.setText(media.title==null?"":media.title.toUpperCase(Locale.US));
        eyebrow.setText(media.series?"SERIES DETAILS":"MOVIE DETAILS");
        synopsis.setText(d.description.isEmpty()?fallbackDescription():d.description);
        badgeRow.removeAllViews();
        addBadge(first(d.releaseInfo, extractYear(media.subtitle)), Color.rgb(45,48,56), WHITE);
        if (!d.certification.isEmpty()) addBadge(d.certification, Color.rgb(45,48,56), WHITE);
        addBadge(media.series?seasonCountLabel(d):"MOVIE",Color.rgb(45,48,56),WHITE);
        if (!d.rating.isEmpty()) addBadge("★ " + d.rating,Color.rgb(18,75,69),GREEN);
        if (new DebridStore(this).isConnected()) addBadge("☁ DEBRID CONNECTED",Color.rgb(20,73,45),GREEN);

        genreRow.removeAllViews();
        List<String> genres=d.genres.isEmpty()?media.tags:d.genres;
        for (int i=0;i<genres.size()&&i<6;i++) addGenre(genres.get(i));

        String bg=first(d.backgroundUrl,media.artworkUrl); if(!bg.isEmpty()) artwork.load(backdrop,bg,1280,artPool);
        trailerButton.setVisibility(d.trailerUrl.isEmpty()?View.GONE:View.VISIBLE);
        updateWatchlistButton();
        rebuildPeople(d); rebuildSpecs();

        episodeCache.clear(); episodeCache.putAll(d.episodesBySeason);
        if (!media.series) {
            seriesSection.setVisibility(View.GONE); selectedPlayable=media; updatePrimaryTarget();
        } else {
            seriesSection.setVisibility(View.VISIBLE); rebuildSeasonTabs(d.seasons);
            int preferred = chooseInitialSeason(d.seasons); if (preferred > 0) selectSeason(preferred); else { episodeStatus.setText("Episode metadata is unavailable for this title."); episodeStatus.setVisibility(View.VISIBLE); }
        }
        screenStatus.setText(d.description.isEmpty()?"Cached catalog details":"Metadata ready");
    }

    private void rebuildSeasonTabs(List<Integer> seasons) {
        seasonTabs.removeAllViews(); seasonButtons.clear();
        for (Integer n : seasons) {
            TextView tab=text("Season " + n,12,true);tab.setGravity(Gravity.CENTER);tab.setFocusable(true);tab.setClickable(true);tab.setStateListAnimator(null);seasonButtons.put(n,tab);
            tab.setOnClickListener(v->selectSeason(n));tab.setOnFocusChangeListener((v,f)->styleSeason(n,tab,f));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(154),dp(42));p.rightMargin=dp(10);seasonTabs.addView(tab,p);
        }
    }

    private int chooseInitialSeason(List<Integer> seasons) {
        if (seasons==null||seasons.isEmpty()) return 0;
        long newest=-1; int best=seasons.get(0);
        for (Map.Entry<Integer,List<TitleDetailsData.Episode>> e:episodeCache.entrySet()) for (TitleDetailsData.Episode ep:e.getValue()) {
            long pos=profiles.progressMs(ep.card.id),updated=profiles.lastUpdatedMs(ep.card.id);
            if(pos>0&&!profiles.isWatched(ep.card.id)&&updated>newest){newest=updated;best=e.getKey();}
        }
        return best;
    }

    private void selectSeason(int season) {
        activeSeason=season; for(Map.Entry<Integer,TextView> e:seasonButtons.entrySet())styleSeason(e.getKey(),e.getValue(),e.getValue().hasFocus());
        List<TitleDetailsData.Episode> cached=episodeCache.get(season);
        if(cached!=null&&!cached.isEmpty()){showEpisodes(cached);return;}
        episodeList.setAdapter(null);episodeStatus.setVisibility(View.VISIBLE);episodeStatus.setText("Loading Season " + season + "…");
        dataPool.submit(()->{
            try{
                List<TitleDetailsData.Episode> loaded=repository.loadSeason(media,season);
                runOnUiThread(()->{if(dead()||activeSeason!=season)return;episodeCache.put(season,loaded);showEpisodes(loaded);});
            }catch(Exception e){DebugLog.append(this,"DETAILS","Season load failed: "+msg(e));runOnUiThread(()->{if(!dead()&&activeSeason==season){episodeStatus.setText("Season load failed: "+msg(e));episodeStatus.setVisibility(View.VISIBLE);}});}
        });
    }

    private void showEpisodes(List<TitleDetailsData.Episode> episodes) {
        if(episodes==null||episodes.isEmpty()){episodeList.setAdapter(null);episodeStatus.setText("No episodes found for Season "+activeSeason+".");episodeStatus.setVisibility(View.VISIBLE);return;}
        ArrayList<MediaCard> cards=new ArrayList<>();for(TitleDetailsData.Episode e:episodes)cards.add(e.card);catalog.upsertAll(cards);
        episodeStatus.setVisibility(View.GONE);episodeAdapter=new EpisodeAdapter(new ArrayList<>(episodes));episodeList.setAdapter(episodeAdapter);episodeList.scrollToPosition(0);updateEpisodeProgress();
        selectedPlayable=chooseResumeEpisode(episodes);updatePrimaryTarget();
    }

    private MediaCard chooseResumeEpisode(List<TitleDetailsData.Episode> episodes) {
        TitleDetailsData.Episode best=null;long newest=-1;
        for(TitleDetailsData.Episode e:episodes){long p=profiles.progressMs(e.card.id),u=profiles.lastUpdatedMs(e.card.id);if(p>0&&!profiles.isWatched(e.card.id)&&u>newest){best=e;newest=u;}}
        if(best!=null)return best.card;
        for(TitleDetailsData.Episode e:episodes)if(!profiles.isWatched(e.card.id))return e.card;
        return episodes.get(0).card;
    }

    private void updatePrimaryTarget() {
        if(!media.series)selectedPlayable=media;
        if(playButton==null)return;
        MediaCard target=selectedPlayable==null?media:selectedPlayable;
        long pos=profiles.progressMs(target.id);String prefix=pos>0&&!profiles.isWatched(target.id)?"▶  Resume ":"▶  Play ";
        if(target.isEpisode()){
            String epName=episodeName(target);playButton.setText(prefix+"S"+target.seasonNumber+":E"+target.episodeNumber+(epName.isEmpty()?"":" “"+epName+"”"));
        }else playButton.setText(prefix+media.title);
    }

    private String episodeName(MediaCard card) {
        if(card==null||card.subtitle==null)return"";int p=card.subtitle.indexOf('•');return p>=0?card.subtitle.substring(p+1).trim():"";
    }

    private void playSelected(boolean forcePicker) {
        MediaCard target=selectedPlayable==null?media:selectedPlayable;if(target==null)return;catalog.upsert(target);
        Intent i=new Intent(this,MediaOpenActivity.class);i.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID,target.id);i.putExtra(MediaOpenActivity.EXTRA_FORCE_PICKER,forcePicker);startActivity(i);overridePendingTransition(0,0);
    }

    private void watchTrailer() {
        if(details==null||details.trailerUrl.isEmpty())return;
        try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(details.trailerUrl)));}catch(Exception e){screenStatus.setText("No app can open this trailer link.");}
    }

    private void toggleWatchlist() {
        boolean next=!profiles.isInWatchlist(media.id);profiles.setWatchlist(media.id,next);updateWatchlistButton();screenStatus.setText(next?"Added to Library":"Removed from Library");
    }
    private void updateWatchlistButton(){if(watchlistButton!=null)watchlistButton.setText(profiles.isInWatchlist(media.id)?"✓":"＋");}

    private void rebuildPeople(TitleDetailsData d) {
        castChips.removeAllViews();
        if(d.cast.isEmpty()){TextView none=text("Cast information unavailable",12,false);none.setTextColor(MUTED);castChips.addView(none,new LinearLayout.LayoutParams(dp(220),dp(56)));}
        else for(int i=0;i<d.cast.size()&&i<8;i++){TextView chip=text(d.cast.get(i),12,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(14),0,dp(14),0);chip.setBackground(roundRect(PANEL_SOFT,Color.argb(24,255,255,255),1,15));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(150),dp(56));p.rightMargin=dp(9);castChips.addView(chip,p);}
        directorLine.setText(d.directors.isEmpty()?"Director • unavailable":"Director • "+join(d.directors,3));
        writerLine.setText(d.writers.isEmpty()?"Writer • unavailable":"Writer • "+join(d.writers,3));
    }

    private void rebuildSpecs() {
        if(specs==null)return;specs.removeAllViews();
        addSpec("Type",media.series?"Series":"Movie");
        if(details!=null&&!details.releaseInfo.isEmpty())addSpec("Release",details.releaseInfo);
        if(details!=null&&!details.runtime.isEmpty())addSpec(media.series?"Episode Runtime":"Runtime",details.runtime);
        if(details!=null&&!details.rating.isEmpty())addSpec("Rating",details.rating);
        addSpec("Preferred Quality",settings.maxQuality());
        MediaCard target=selectedPlayable==null?media:selectedPlayable;List<SourceOption> fresh=new SourceStore(this).getFresh(target.id);int cached=0;for(SourceOption s:fresh)if(Boolean.TRUE.equals(s.cached))cached++;
        addSpec("Fresh Sources",fresh.isEmpty()?"Not scanned":fresh.size()+" results"+(cached>0?" • "+cached+" RD cached":""));
        addSpec("Debrid",new DebridStore(this).isConnected()?"Real-Debrid connected":"Not connected");
    }

    private void addSpec(String label,String value){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView l=text(label,11,false);l.setTextColor(MUTED);TextView v=text(value,11,true);v.setTextColor("Debrid".equals(label)&&value.contains("connected")?GREEN:WHITE);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);row.addView(l,new LinearLayout.LayoutParams(0,dp(24),.48f));row.addView(v,new LinearLayout.LayoutParams(0,dp(24),.52f));specs.addView(row,new LinearLayout.LayoutParams(-1,dp(26)));}

    private void updateEpisodeProgress() {
        if(episodeAdapter!=null)episodeAdapter.notifyDataSetChanged();
        List<TitleDetailsData.Episode> eps=episodeCache.get(activeSeason);if(eps==null){episodeSummary.setText("");return;}int watched=0,progress=0;for(TitleDetailsData.Episode e:eps){if(profiles.isWatched(e.card.id))watched++;else if(profiles.progressMs(e.card.id)>0)progress++;}int unseen=Math.max(0,eps.size()-watched-progress);episodeSummary.setText("● "+watched+" Watched   •   ● "+progress+" In Progress   •   "+unseen+" Unwatched");
    }

    private final class EpisodeAdapter extends RecyclerView.Adapter<EpisodeHolder>{
        final List<TitleDetailsData.Episode> items;EpisodeAdapter(List<TitleDetailsData.Episode> items){this.items=items;setHasStableIds(true);}
        @Override public long getItemId(int position){return items.get(position).card.id.hashCode();}
        @Override public EpisodeHolder onCreateViewHolder(ViewGroup parent,int type){
            FrameLayout card=new FrameLayout(TitleDetailsActivity.this);RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(dp(260),dp(190));lp.rightMargin=dp(14);lp.topMargin=dp(8);lp.bottomMargin=dp(10);card.setLayoutParams(lp);card.setFocusable(true);card.setClickable(true);card.setStateListAnimator(null);card.setBackground(cardBox(false));card.setClipToOutline(true);
            ImageView image=new ImageView(TitleDetailsActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(Color.rgb(20,27,36));card.addView(image,new FrameLayout.LayoutParams(-1,dp(122),Gravity.TOP));
            TextView ep=text("",10,true);ep.setPadding(dp(9),0,dp(9),0);ep.setGravity(Gravity.CENTER_VERTICAL);ep.setBackgroundColor(Color.argb(190,5,8,12));FrameLayout.LayoutParams epP=new FrameLayout.LayoutParams(-2,dp(25),Gravity.TOP|Gravity.START);epP.leftMargin=dp(8);epP.topMargin=dp(8);card.addView(ep,epP);
            TextView state=text("",9,true);state.setPadding(dp(8),0,dp(8),0);state.setGravity(Gravity.CENTER);FrameLayout.LayoutParams stP=new FrameLayout.LayoutParams(-2,dp(24),Gravity.TOP|Gravity.END);stP.rightMargin=dp(8);stP.topMargin=dp(8);card.addView(state,stP);
            LinearLayout copy=new LinearLayout(TitleDetailsActivity.this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(10),dp(8),dp(8),dp(4));copy.setBackgroundColor(Color.rgb(24,28,34));TextView name=text("",13,true);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);TextView overview=text("",10,false);overview.setTextColor(Color.rgb(204,213,221));overview.setMaxLines(2);overview.setEllipsize(TextUtils.TruncateAt.END);copy.addView(name,new LinearLayout.LayoutParams(-1,dp(23)));copy.addView(overview,new LinearLayout.LayoutParams(-1,0,1f));card.addView(copy,new FrameLayout.LayoutParams(-1,dp(68),Gravity.BOTTOM));
            View progress=new View(TitleDetailsActivity.this);FrameLayout.LayoutParams pr=new FrameLayout.LayoutParams(0,dp(4),Gravity.BOTTOM|Gravity.START);card.addView(progress,pr);
            return new EpisodeHolder(card,image,ep,state,name,overview,progress);
        }
        @Override public void onBindViewHolder(EpisodeHolder h,int position){TitleDetailsData.Episode item=items.get(position);MediaCard c=item.card;h.ep.setText("S"+c.seasonNumber+":E"+c.episodeNumber);h.name.setText(item.title);h.overview.setText(item.overview);artwork.load(h.image,first(item.artworkUrl,c.artworkUrl),520,artPool);
            boolean watched=profiles.isWatched(c.id);long pos=profiles.progressMs(c.id),dur=profiles.durationMs(c.id);if(dur<=0)dur=item.durationMs;float ratio=dur>0?Math.min(1f,pos/(float)dur):0f;h.state.setText(watched?"✓":ratio>0?"IN PROGRESS":"");h.state.setTextColor(watched?Color.rgb(0,55,30):Color.rgb(0,55,60));h.state.setBackground((watched||ratio>0)?roundRect(watched?GREEN:CYAN,watched?GREEN:CYAN,1,14):null);
            FrameLayout.LayoutParams pp=(FrameLayout.LayoutParams)h.progress.getLayoutParams();pp.width=watched?dp(260):Math.round(dp(260)*ratio);h.progress.setLayoutParams(pp);h.progress.setBackgroundColor(watched?GREEN:CYAN);h.progress.setVisibility((watched||ratio>0)?View.VISIBLE:View.GONE);
            h.card.setBackground(cardBox(false));h.card.setScaleX(1f);h.card.setScaleY(1f);h.card.setOnFocusChangeListener((v,f)->{h.card.setBackground(cardBox(f));h.card.setScaleX(f?1.05f:1f);h.card.setScaleY(f?1.05f:1f);h.card.setTranslationZ(f?dp(12):0);if(f){selectedPlayable=c;updatePrimaryTarget();rebuildSpecs();if(clickSounds)h.card.playSoundEffect(SoundEffectConstants.CLICK);}});h.card.setOnClickListener(v->{selectedPlayable=c;updatePrimaryTarget();playSelected(false);});
        }
        @Override public int getItemCount(){return items.size();}
    }

    private static final class EpisodeHolder extends RecyclerView.ViewHolder{final FrameLayout card;final ImageView image;final TextView ep,state,name,overview;final View progress;EpisodeHolder(FrameLayout c,ImageView i,TextView e,TextView s,TextView n,TextView o,View p){super(c);card=c;image=i;ep=e;state=s;name=n;overview=o;progress=p;}}

    private void styleSeason(int season,TextView tab,boolean focused){boolean active=season==activeSeason;tab.setTextColor(active?Color.rgb(0,65,70):WHITE);tab.setBackground(active?roundRect(CYAN,CYAN,1,22):focused?roundRect(Color.argb(180,40,45,53),CYAN,2,22):roundRect(Color.argb(190,31,34,41),Color.argb(25,255,255,255),1,22));}
    private void addBadge(String value,int fill,int textColor){if(value==null||value.trim().isEmpty())return;TextView b=text(value.toUpperCase(Locale.US),10,true);b.setTextColor(textColor);b.setGravity(Gravity.CENTER);b.setPadding(dp(9),0,dp(9),0);b.setBackground(roundRect(fill,Color.argb(28,255,255,255),1,5));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(27));p.rightMargin=dp(7);badgeRow.addView(b,p);}
    private void addGenre(String value){if(value==null||value.trim().isEmpty())return;TextView b=text(value,11,false);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),0,dp(12),0);b.setBackground(roundRect(Color.argb(190,30,33,40),Color.argb(18,255,255,255),1,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(30));p.rightMargin=dp(8);genreRow.addView(b,p);}

    private TextView action(String label,boolean primary,Runnable click){TextView v=text(label,13,true);v.setGravity(Gravity.CENTER);v.setFocusable(true);v.setClickable(true);v.setStateListAnimator(null);v.setTextColor(primary?Color.rgb(0,57,62):WHITE);v.setBackground(primary?primaryAction(false):buttonBox(false));v.setOnFocusChangeListener((view,f)->{v.setBackground(primary?primaryAction(f):buttonBox(f));v.setScaleX(f?1.03f:1f);v.setScaleY(f?1.03f:1f);if(f&&clickSounds)v.playSoundEffect(SoundEffectConstants.CLICK);});v.setOnClickListener(view->click.run());return v;}
    private LinearLayout.LayoutParams actionParams(int width){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,dp(52));p.rightMargin=dp(10);return p;}
    private GradientDrawable primaryAction(boolean focused){return roundRect(focused?Color.rgb(91,250,255):CYAN,focused?Color.WHITE:CYAN,focused?2:1,12);}
    private GradientDrawable buttonBox(boolean focused){return roundRect(focused?Color.rgb(51,56,67):Color.rgb(38,42,50),focused?CYAN:Color.argb(22,255,255,255),focused?2:1,12);}
    private GradientDrawable cardBox(boolean focused){return roundRect(focused?Color.rgb(22,40,46):Color.rgb(23,27,33),focused?CYAN:Color.argb(30,255,255,255),focused?2:1,16);}
    private GradientDrawable pill(boolean focused){return roundRect(Color.argb(focused?230:205,29,32,39),focused?CYAN:Color.argb(24,255,255,255),focused?2:1,30);}
    private GradientDrawable circle(boolean focused){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.rgb(220,252,255));g.setStroke(dp(focused?2:1),focused?CYAN:Color.WHITE);return g;}
    private GradientDrawable roundRect(int fill,int stroke,int strokeDp,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));g.setStroke(dp(strokeDp),stroke);return g;}

    private TextView text(String value,int sp,boolean bold){TextView v=new TextView(this);v.setText(value==null?"":value);v.setTextColor(WHITE);v.setTextSize(sp);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private FrameLayout.LayoutParams frame(int w,int h,int gravity,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);if((gravity&Gravity.END)==Gravity.END||(gravity&Gravity.RIGHT)==Gravity.RIGHT)p.rightMargin=x;else p.leftMargin=x;if((gravity&Gravity.BOTTOM)==Gravity.BOTTOM)p.bottomMargin=y;else p.topMargin=y;return p;}
    private int dp(int v){return TvUi.dp(this,v);}
    private boolean dead(){return isFinishing()||isDestroyed();}
    private String profileInitial(){String p=profiles==null?"D":profiles.activeProfile();return p==null||p.isEmpty()?"D":p.substring(0,1).toUpperCase(Locale.US);}
    private void updateClock(){if(clock==null)return;clock.setText("◷  "+new SimpleDateFormat("h:mm a",Locale.US).format(new java.util.Date()));main.postDelayed(this::updateClock,30000L);}
    private void open(Class<?> cls){startActivity(new Intent(this,cls));overridePendingTransition(0,0);}
    private void openHub(String mode){Intent i=new Intent(this,MediaHubActivity.class);i.putExtra(MediaHubActivity.EXTRA_MODE,mode);startActivity(i);overridePendingTransition(0,0);}
    private String fallbackDescription(){return (media.series?"Series":"Movie")+(media.subtitle==null||media.subtitle.isEmpty()?"":" • "+media.subtitle)+(media.genre==null||media.genre.isEmpty()?"":" • "+media.genre);}
    private static String extractYear(String value){if(value==null)return"";java.util.regex.Matcher m=java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(value);return m.find()?m.group():"";}
    private static String safe(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
    private static String first(String... values){for(String v:values)if(v!=null&&!v.trim().isEmpty())return v.trim();return"";}
    private static String join(List<String> values,int max){StringBuilder b=new StringBuilder();for(int i=0;i<values.size()&&i<max;i++){if(i>0)b.append(" • ");b.append(values.get(i));}return b.toString();}
    private static String seasonCountLabel(TitleDetailsData d){int n=d.seasons.size();return n>0?n+(n==1?" SEASON":" SEASONS"):"SERIES";}
    private static String msg(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}
}
