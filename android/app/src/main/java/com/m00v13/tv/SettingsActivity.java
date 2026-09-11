package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.StatFs;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Cinematic settings/profile dashboard grounded in capabilities M00V13 actually implements. */
public final class SettingsActivity extends Activity {
    private static final int BG = Color.rgb(7, 9, 14);
    private static final int RAIL = Color.rgb(10, 14, 21);
    private static final int PANEL = Color.rgb(25, 28, 34);
    private static final int PANEL_HIGH = Color.rgb(39, 42, 49);
    private static final int WHITE = Color.rgb(232, 235, 242);
    private static final int MUTED = Color.rgb(181, 195, 198);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int VIOLET = Color.rgb(221, 183, 255);
    private static final int GREEN = Color.rgb(96, 245, 135);
    private static final int ERROR = Color.rgb(255, 180, 171);

    private AppSettingsStore prefs;
    private ProfileStore profiles;
    private ScrollView pageScroll;
    private View profileSection;
    private View playbackSection;
    private View interfaceSection;
    private View servicesSection;
    private View storageSection;
    private View telemetrySection;
    private int screenWidth;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        prefs = new AppSettingsStore(this);
        profiles = new ProfileStore(this);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        setContentView(buildShell());
    }

    @Override protected void onResume() {
        super.onResume();
        prefs = new AppSettingsStore(this);
        profiles = new ProfileStore(this);
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        View ambient = new View(this);
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(15, 9, 27), BG, Color.rgb(5, 24, 29)});
        ambient.setBackground(glow);
        root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildRail(), frame(dp(84), -1, Gravity.START | Gravity.TOP, 0, 0));
        root.addView(buildTopBar(), frame(screenWidth - dp(84), dp(80), Gravity.START | Gravity.TOP, dp(84), 0));

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageScroll.addView(buildBody(), new ScrollView.LayoutParams(-1, -2));
        root.addView(pageScroll, frame(screenWidth - dp(84), -1, Gravity.START | Gravity.TOP, dp(84), dp(80)));
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        top.setBackgroundColor(Color.argb(230, 9, 12, 18));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("M 0 0 V 1 3", 22, true);
        logo.setLetterSpacing(.22f);
        TextView sub = text("STORIES WITHOUT LIMITS", 9, true);
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(.16f);
        brand.addView(logo); brand.addView(sub);
        top.addView(brand, frame(dp(300), dp(64), Gravity.START | Gravity.CENTER_VERTICAL, dp(28), 0));

        TextView search = text("⌕   What do you want to watch or scrape?", 14, false);
        search.setTextColor(MUTED);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(20), 0, dp(18), 0);
        search.setFocusable(true); search.setClickable(true); search.setStateListAnimator(null);
        search.setBackground(pill(false, false));
        search.setOnFocusChangeListener((v, f) -> search.setBackground(pill(f, false)));
        search.setOnClickListener(v -> openHub(MediaHubActivity.MODE_SEARCH));
        top.addView(search, frame(Math.min(dp(560), (int)(screenWidth * .42f)), dp(46), Gravity.CENTER, 0, 0));

        LinearLayout status = new LinearLayout(this);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView clock = text(new SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date()), 14, true);
        clock.setPadding(0, 0, dp(14), 0);
        status.addView(clock, new LinearLayout.LayoutParams(-2, dp(46)));
        TextView avatar = text(profileInitial(), 15, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setFocusable(true); avatar.setClickable(true);
        avatar.setBackground(circle(false));
        avatar.setOnFocusChangeListener((v,f)->avatar.setBackground(circle(f)));
        avatar.setOnClickListener(v->open(ProfileActivity.class));
        status.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        top.addView(status, frame(dp(180), dp(54), Gravity.END | Gravity.CENTER_VERTICAL, dp(26), 0));
        return top;
    }

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(9), dp(46), dp(9), dp(16));
        rail.setBackgroundColor(RAIL);
        addNav(rail, "▣", false, () -> openHub(MediaHubActivity.MODE_HOME));
        addNav(rail, "⌂", false, () -> openHub(MediaHubActivity.MODE_HOME));
        addNav(rail, "▤", false, () -> openHub(MediaHubActivity.MODE_MOVIES));
        addNav(rail, "▥", false, () -> openHub(MediaHubActivity.MODE_TV));
        addNav(rail, "♡", false, () -> open(LibraryActivity.class));
        addNav(rail, "⌕", false, () -> openHub(MediaHubActivity.MODE_SEARCH));
        addNav(rail, "↗", false, () -> open(DebridActivity.class));
        addNav(rail, "▱", false, () -> open(ProviderSettingsActivity.class));
        addNav(rail, "♥", false, () -> open(DonateActivity.class));
        View gap = new View(this); rail.addView(gap, new LinearLayout.LayoutParams(1,0,1f));
        addNav(rail, "⚙", true, () -> {});
        return rail;
    }

    private void addNav(LinearLayout rail, String glyph, boolean active, Runnable action) {
        TextView b = text(glyph, 19, true);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true); b.setClickable(true); b.setStateListAnimator(null);
        b.setTextColor(active ? Color.rgb(0, 55, 60) : Color.rgb(198, 210, 220));
        b.setBackground(navBackground(active, false));
        b.setOnFocusChangeListener((v,f)->b.setBackground(navBackground(active,f)));
        b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(46),dp(46)); p.bottomMargin=dp(9); rail.addView(b,p);
    }

    private View buildBody() {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(34), dp(24), dp(30), dp(34));

        TextView kicker = text("●  SYSTEM CONFIGURATION • LOCAL PROFILE & PREFERENCES", 11, true);
        kicker.setTextColor(CYAN); kicker.setLetterSpacing(.16f);
        body.addView(kicker);
        TextView title = text("SETTINGS & PROFILE", 42, true);
        body.addView(title);
        TextView intro = text("Configure playback, profile, debrid, scraper behavior, storage and living-room interface settings.", 15, false);
        intro.setTextColor(MUTED); intro.setPadding(0, dp(3), 0, dp(14)); body.addView(intro);
        body.addView(buildCategories(), new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(screenWidth >= dp(1200) ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        columns.setPadding(0, dp(12), 0, 0);
        body.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this); left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        if (screenWidth >= dp(1200)) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, .62f); lp.rightMargin=dp(22); columns.addView(left,lp);
            columns.addView(right,new LinearLayout.LayoutParams(0,-2,.38f));
        } else {
            columns.addView(left,new LinearLayout.LayoutParams(-1,-2));
            columns.addView(right,new LinearLayout.LayoutParams(-1,-2));
        }

        profileSection = buildProfileCard(); left.addView(profileSection, sectionParams());
        playbackSection = buildPlaybackCard(); left.addView(playbackSection, sectionParams());
        interfaceSection = buildInterfaceCard(); left.addView(interfaceSection, sectionParams());

        servicesSection = buildServicesCard(); right.addView(servicesSection, sectionParams());
        storageSection = buildStorageCard(); right.addView(storageSection, sectionParams());
        telemetrySection = buildTelemetryCard(); right.addView(telemetrySection, sectionParams());
        right.addView(buildSystemActionsCard(), sectionParams());

        body.addView(buildFooter(), new LinearLayout.LayoutParams(-1, dp(70)));
        return body;
    }

    private View buildCategories() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(category("PROFILE & ACCOUNTS", true, () -> scrollTo(profileSection)), catParams());
        row.addView(category("PLAYER & PLAYBACK", false, () -> scrollTo(playbackSection)), catParams());
        row.addView(category("INTERFACE & SKIN", false, () -> scrollTo(interfaceSection)), catParams());
        row.addView(category("SCRAPER & WATERFALL", false, () -> open(ProviderSettingsActivity.class)), catParams());
        row.addView(category("SUBTITLES & AUDIO", false, () -> { scrollTo(playbackSection); Toast.makeText(this,"Language and passthrough are in Playback.",Toast.LENGTH_SHORT).show(); }), catParams());
        row.addView(category("DIAGNOSTICS", false, () -> open(DiagnosticsActivity.class)), catParams());
        return row;
    }

    private TextView category(String label, boolean active, Runnable action) {
        TextView v = text(label, 10, true); v.setGravity(Gravity.CENTER); v.setFocusable(true); v.setClickable(true); v.setStateListAnimator(null);
        v.setTextColor(active ? Color.rgb(0,55,60) : WHITE); v.setBackground(pill(false, active));
        v.setOnFocusChangeListener((x,f)->v.setBackground(pill(f,active))); v.setOnClickListener(x->action.run());
        return v;
    }
    private LinearLayout.LayoutParams catParams(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(38),1f);p.rightMargin=dp(7);return p; }

    private View buildProfileCard() {
        LinearLayout card = panelCard(true);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView cow = new ImageView(this); cow.setScaleType(ImageView.ScaleType.CENTER_CROP); cow.setImageResource(R.drawable.loading_cow); cow.setBackground(round(PANEL_HIGH,VIOLET,1,14));
        row.addView(cow,new LinearLayout.LayoutParams(dp(72),dp(72)));

        LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(16),0,dp(12),0);
        TextView name = text(profiles.activeProfile(), 22, true); info.addView(name);
        TextView meta = text("Local profile  •  Preferred language " + profiles.preferredLanguage().toUpperCase(Locale.US), 12, false); meta.setTextColor(MUTED); info.addView(meta);
        LinearLayout badges = new LinearLayout(this); badges.setOrientation(LinearLayout.HORIZONTAL); badges.setPadding(0,dp(8),0,0);
        badges.addView(badge("LOCAL PROFILE", GREEN), badgeParams());
        badges.addView(badge(new DebridStore(this).isConnected()?"REAL-DEBRID ACTIVE":"REAL-DEBRID OFF", new DebridStore(this).isConnected()?CYAN:ERROR), badgeParams());
        badges.addView(badge("DEVICE ONLY", VIOLET), badgeParams()); info.addView(badges);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL);
        TextView edit = smallButton("EDIT PROFILE", false); edit.setOnClickListener(v->open(ProfileActivity.class)); actions.addView(edit,new LinearLayout.LayoutParams(dp(126),dp(38)));
        TextView sw = smallButton("SWITCH", false); sw.setOnClickListener(v->open(ProfileActivity.class)); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(126),dp(38));sp.topMargin=dp(7);actions.addView(sw,sp);
        row.addView(actions);
        card.addView(row);
        return card;
    }

    private View buildPlaybackCard() {
        LinearLayout card = panelCard(false);
        card.addView(sectionHeader("▣  Playback Engine & Decoders", "MEDIA3 ENGINE"));
        card.addView(infoSetting("Default Video Player", "Media3 ExoPlayer • M00V13 renderer", "Hardware decoder selection and fallback are handled by Android Media3."), rowParams());
        card.addView(toggleSetting("Auto-Play Best Source", "Use smart one-click selection and fall back to the source picker when needed.", prefs.smartOneClickPlayback(), prefs::setSmartOneClickPlayback), rowParams());
        card.addView(choiceSetting("Maximum Video Quality", "Reject sources above the selected ceiling.", new String[]{"4K","1080p","720p","480p"}, prefs.maxQuality(), prefs::setMaxQuality), rowParams());
        card.addView(toggleSetting("Automatic Audio Passthrough", "Pass encoded audio when the active Android output route reports direct support.", prefs.automaticPassthrough(), prefs::setAutomaticPassthrough), rowParams());
        card.addView(toggleSetting("Exclude 3D Releases", "Filters stereoscopic releases from normal source selection.", prefs.exclude3d(), prefs::setExclude3d), rowParams());
        TextView audio = wideAction("DETECT / TEST CURRENT AUDIO OUTPUT"); audio.setOnClickListener(v->showAudioCapabilities()); card.addView(audio, wideParams());
        return card;
    }

    private View buildInterfaceCard() {
        LinearLayout card = panelCard(false);
        card.addView(sectionHeader("▱  Interface & Living Room Experience", "10-FOOT TV SKIN"));
        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout a = new LinearLayout(this); a.setOrientation(LinearLayout.HORIZONTAL);
        a.addView(infoTile("HOME SHELF LAYOUT", "Cinematic Focus Shelf", "▣"), tileParams());
        a.addView(infoTile("STARTUP SPLASH", "M00V13 Purple Cow Boot • 5s", "◉"), tileParams()); grid.addView(a);
        LinearLayout b = new LinearLayout(this); b.setOrientation(LinearLayout.HORIZONTAL);
        b.addView(infoTile("DISPLAY", displaySummary(), "▤"), tileParams());
        b.addView(infoTile("COLOR ACCENT", "Neon Cyan & Cyber Violet", "●"), tileParams()); grid.addView(b);
        card.addView(grid);
        card.addView(toggleSetting("Navigation Click Sounds", "Remote focus feedback while moving through TV controls.", prefs.clickSounds(), prefs::setClickSounds), rowParams());
        card.addView(toggleSetting("Cow Startup Sound", "Optional sound during the five-second cow splash.", prefs.startupCowSound(), prefs::setStartupCowSound), rowParams());
        return card;
    }

    private View buildServicesCard() {
        LinearLayout card = panelCard(false); card.addView(sectionHeader("☁  Connected Services", "ACTUAL STATE"));
        boolean rd = new DebridStore(this).isConnected();
        card.addView(serviceRow("RD", "Real-Debrid", rd?"Connected • official device-token login":"Not connected", rd?GREEN:ERROR, ()->open(DebridActivity.class)), serviceParams());
        boolean tmdb = new MetadataStore(this).isConfigured();
        card.addView(serviceRow("MD", tmdb?"TMDB":"Cinemeta", tmdb?"TMDB token configured":"Keyless metadata backend", CYAN, ()->open(MetadataActivity.class)), serviceParams());
        card.addView(serviceRow("PF", "Local Profiles", profiles.profiles().size()+" profile"+(profiles.profiles().size()==1?"":"s")+" • active: "+profiles.activeProfile(), VIOLET, ()->open(ProfileActivity.class)), serviceParams());
        String folder=getSharedPreferences("m00v13_storage",MODE_PRIVATE).getString("tree_uri","");
        card.addView(serviceRow("FS", "Media Folder", folder.isEmpty()?"No external folder selected":"Persisted Android document-tree access", folder.isEmpty()?MUTED:GREEN, this::chooseFolder), serviceParams());
        return card;
    }

    private View buildStorageCard() {
        LinearLayout card = panelCard(false); card.addView(sectionHeader("◉  Storage & Local Caches", humanBytes(freeBytes())+" FREE"));
        long artwork = dirBytes(new File(getCacheDir(),"artwork"));
        long cache = dirBytes(getCacheDir());
        long data = dirBytes(getFilesDir());
        long sourceCache = sharedPrefFile("m00v13_sources.xml").length();
        card.addView(storageLine("App files", data, CYAN));
        card.addView(storageLine("Total app cache", cache, VIOLET));
        card.addView(storageLine("Poster / fanart disk cache", artwork, VIOLET));
        card.addView(storageLine("Saved source-result cache", sourceCache, CYAN));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView purge=smallButton("PURGE CACHES",false);purge.setOnClickListener(v->purgeCaches());actions.addView(purge,new LinearLayout.LayoutParams(0,dp(42),1f));
        TextView downloads=smallButton("DOWNLOADS",false);downloads.setOnClickListener(v->open(DownloadsActivity.class));LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,dp(42),1f);d.leftMargin=dp(8);actions.addView(downloads,d);card.addView(actions);
        return card;
    }

    private View buildTelemetryCard() {
        LinearLayout card = panelCard(false); card.addView(sectionHeader("▦  Hardware & Engine Telemetry", versionName()));
        LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(telemetryTile("TARGET HOST", Build.MANUFACTURER+" "+Build.MODEL, "Android "+Build.VERSION.RELEASE+" • API "+Build.VERSION.SDK_INT), tileParams());
        r1.addView(telemetryTile("NETWORK LINK", networkSummary(), "Live Android transport state"), tileParams());card.addView(r1);
        LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);
        String abi=(Build.SUPPORTED_ABIS!=null&&Build.SUPPORTED_ABIS.length>0)?Build.SUPPORTED_ABIS[0]:"unknown";
        r2.addView(telemetryTile("RUNTIME", "Android / ART", abi+" • Media3 playback"), tileParams());
        r2.addView(telemetryTile("DISPLAY", displaySummary(), ScreenProfile.detect(this).kind.toString()), tileParams());card.addView(r2);
        return card;
    }

    private View buildSystemActionsCard() {
        LinearLayout card=panelCard(false);card.addView(sectionHeader("⚙  System Actions", "TOOLS"));
        TextView provider=wideAction("SCRAPER & WATERFALL SETTINGS");provider.setOnClickListener(v->open(ProviderSettingsActivity.class));card.addView(provider,wideParams());
        TextView diag=wideAction("DIAGNOSTICS & DEBUG LOG");diag.setOnClickListener(v->open(DiagnosticsActivity.class));card.addView(diag,wideParams());
        TextView perms=wideAction("APP PERMISSIONS");perms.setOnClickListener(v->PermissionManager.show(this));card.addView(perms,wideParams());
        TextView home=wideAction("SET M00V13 AS HOME APP");home.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){Toast.makeText(this,"Home-app chooser unavailable",Toast.LENGTH_SHORT).show();}});card.addView(home,wideParams());
        return card;
    }

    private View buildFooter() {
        LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.HORIZONTAL);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(14),dp(10),dp(14),dp(10));footer.setBackground(round(Color.argb(220,10,14,21),Color.argb(30,255,255,255),1,14));
        TextView help=text("▲▼ Navigate     ◀▶ Change     ENTER Select / Save     BACK Previous",11,false);help.setTextColor(MUTED);footer.addView(help,new LinearLayout.LayoutParams(0,-1,1f));
        TextView reset=smallButton("RESET DEFAULTS",false);reset.setTextColor(ERROR);reset.setOnClickListener(v->confirmReset());footer.addView(reset,new LinearLayout.LayoutParams(dp(140),dp(40)));
        TextView sw=smallButton("SWITCH PROFILE",false);sw.setTextColor(CYAN);sw.setOnClickListener(v->open(ProfileActivity.class));LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(dp(140),dp(40));s.leftMargin=dp(8);footer.addView(sw,s);return footer;
    }

    private View sectionHeader(String title, String badge) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView a=text(title,20,true);row.addView(a,new LinearLayout.LayoutParams(0,dp(34),1f));TextView b=text(badge,10,true);b.setTextColor(MUTED);b.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);row.addView(b,new LinearLayout.LayoutParams(-2,dp(34)));return row;
    }

    private View infoSetting(String title,String value,String detail){LinearLayout r=settingContainer();LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);TextView a=text(title,15,true);TextView d=text(detail,12,false);d.setTextColor(MUTED);d.setMaxLines(2);left.addView(a);left.addView(d);r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));TextView v=text(value,12,true);v.setTextColor(CYAN);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(round(Color.rgb(12,16,22),Color.argb(100,0,240,255),1,9));r.addView(v,new LinearLayout.LayoutParams(dp(210),dp(48)));return r;}

    private View toggleSetting(String title,String detail,boolean enabled,Consumer<Boolean> save){LinearLayout r=settingContainer();r.setFocusable(true);r.setClickable(true);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.addView(text(title,15,true));TextView d=text(detail,12,false);d.setTextColor(MUTED);d.setMaxLines(2);left.addView(d);r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));TextView state=toggleVisual(enabled,false);r.addView(state,new LinearLayout.LayoutParams(dp(88),dp(42)));r.setOnFocusChangeListener((v,f)->r.setBackground(settingBackground(f)));r.setOnClickListener(v->{boolean next=!enabled;save.accept(next);rebuild();});return r;}

    private View choiceSetting(String title,String detail,String[] options,String current,Consumer<String> save){LinearLayout r=settingContainer();r.setFocusable(true);r.setClickable(true);LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.addView(text(title,15,true));TextView d=text(detail,12,false);d.setTextColor(MUTED);left.addView(d);r.addView(left,new LinearLayout.LayoutParams(0,-2,1f));TextView value=text(current+"  ▾",13,true);value.setTextColor(WHITE);value.setGravity(Gravity.CENTER);value.setBackground(round(Color.rgb(12,16,22),Color.argb(35,255,255,255),1,9));r.addView(value,new LinearLayout.LayoutParams(dp(150),dp(44)));r.setOnFocusChangeListener((v,f)->r.setBackground(settingBackground(f)));r.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(title).setItems(options,(d,w)->{save.accept(options[w]);rebuild();}).show());return r;}

    private LinearLayout settingContainer(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(13),dp(8),dp(13),dp(8));r.setBackground(settingBackground(false));return r;}
    private GradientDrawable settingBackground(boolean focus){return round(focus?Color.rgb(47,51,59):Color.rgb(40,43,50),focus?CYAN:Color.TRANSPARENT,focus?2:0,10);}
    private TextView toggleVisual(boolean enabled,boolean focus){TextView v=text(enabled?"ON":"OFF",12,true);v.setGravity(Gravity.CENTER);v.setTextColor(enabled?Color.rgb(0,55,60):MUTED);v.setBackground(round(enabled?CYAN:Color.rgb(48,52,60),focus?WHITE:Color.TRANSPARENT,focus?1:0,22));return v;}

    private View infoTile(String label,String value,String glyph){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(12),dp(10),dp(12),dp(10));c.setBackground(round(Color.argb(170,40,43,50),Color.TRANSPARENT,0,10));TextView l=text(label,9,true);l.setTextColor(MUTED);c.addView(l);LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);TextView v=text(value,12,true);v.setMaxLines(2);v.setEllipsize(TextUtils.TruncateAt.END);row.addView(v,new LinearLayout.LayoutParams(0,-2,1f));TextView g=text(glyph,16,true);g.setTextColor(CYAN);row.addView(g);c.addView(row);return c;}
    private View telemetryTile(String label,String value,String detail){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(10),dp(8),dp(10),dp(8));c.setBackground(round(Color.argb(170,40,43,50),Color.TRANSPARENT,0,8));TextView l=text(label,9,true);l.setTextColor(MUTED);c.addView(l);TextView v=text(value,11,true);v.setMaxLines(2);c.addView(v);TextView d=text(detail,9,false);d.setTextColor(MUTED);d.setMaxLines(2);c.addView(d);return c;}
    private LinearLayout.LayoutParams tileParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(88),1f);p.rightMargin=dp(8);p.bottomMargin=dp(8);return p;}

    private View serviceRow(String icon,String title,String detail,int color,Runnable click){LinearLayout r=settingContainer();r.setFocusable(true);r.setClickable(true);TextView i=text(icon,11,true);i.setGravity(Gravity.CENTER);i.setTextColor(color);i.setBackground(round(Color.argb(38,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT,0,9));r.addView(i,new LinearLayout.LayoutParams(dp(44),dp(44)));LinearLayout in=new LinearLayout(this);in.setOrientation(LinearLayout.VERTICAL);in.setPadding(dp(10),0,dp(8),0);in.addView(text(title,13,true));TextView d=text(detail,11,false);d.setTextColor(MUTED);d.setMaxLines(2);in.addView(d);r.addView(in,new LinearLayout.LayoutParams(0,-2,1f));TextView arrow=text("›",20,true);arrow.setTextColor(color);arrow.setGravity(Gravity.CENTER);r.addView(arrow,new LinearLayout.LayoutParams(dp(28),dp(42)));r.setOnFocusChangeListener((v,f)->r.setBackground(settingBackground(f)));r.setOnClickListener(v->click.run());return r;}

    private View storageLine(String label,long bytes,int color){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(5),0,dp(5));TextView a=text("●  "+label,11,false);a.setTextColor(color);r.addView(a,new LinearLayout.LayoutParams(0,dp(28),1f));TextView b=text(humanBytes(bytes),11,true);b.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);r.addView(b,new LinearLayout.LayoutParams(-2,dp(28)));return r;}

    private LinearLayout panelCard(boolean focused){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(17),dp(15),dp(17),dp(15));c.setBackground(round(Color.argb(230,25,28,34),focused?CYAN:Color.argb(26,255,255,255),focused?2:1,15));return c;}
    private LinearLayout.LayoutParams sectionParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(18);return p;}
    private LinearLayout.LayoutParams rowParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));p.bottomMargin=dp(8);return p;}
    private LinearLayout.LayoutParams serviceParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(66));p.bottomMargin=dp(8);return p;}
    private LinearLayout.LayoutParams badgeParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(24));p.rightMargin=dp(6);return p;}
    private TextView badge(String value,int color){TextView b=text(value,9,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),0,dp(8),0);b.setTextColor(color);b.setBackground(round(Color.argb(32,Color.red(color),Color.green(color),Color.blue(color)),Color.TRANSPARENT,0,5));return b;}

    private TextView wideAction(String label){TextView b=smallButton(label,false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(dp(12),0,dp(12),0);return b;}
    private LinearLayout.LayoutParams wideParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));p.bottomMargin=dp(7);return p;}
    private TextView smallButton(String label,boolean active){TextView b=text(label,10,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setStateListAnimator(null);b.setTextColor(active?Color.rgb(0,55,60):WHITE);b.setBackground(pill(false,active));b.setOnFocusChangeListener((v,f)->b.setBackground(pill(f,active)));return b;}

    private void showAudioCapabilities(){String summary=AudioCapabilities.log(this);new AlertDialog.Builder(this).setTitle("Current audio output").setMessage(summary+"\n\nM00V13 uses Media3 and the platform audio sink. Encoded formats are passed through only when Android reports direct support.").setPositiveButton("OK",null).show();}

    private void chooseFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,41);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==41&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);try{getContentResolver().takePersistableUriPermission(uri,flags);getSharedPreferences("m00v13_storage",MODE_PRIVATE).edit().putString("tree_uri",uri.toString()).apply();Toast.makeText(this,"Media folder access saved",Toast.LENGTH_SHORT).show();rebuild();}catch(Exception e){DebugLog.append(this,"STORAGE","Could not persist tree permission: "+e.getMessage());}}}

    private void purgeCaches(){deleteChildren(new File(getCacheDir(),"artwork"));new SourceStore(this).clearAll();Toast.makeText(this,"Artwork and cached source results cleared",Toast.LENGTH_SHORT).show();rebuild();}
    private static void deleteChildren(File dir){File[] files=dir.listFiles();if(files==null)return;for(File f:files){if(f.isDirectory())deleteChildren(f);f.delete();}}

    private void confirmReset(){new AlertDialog.Builder(this).setTitle("Reset M00V13 settings?").setMessage("This restores playback, interface and scraper policy defaults. Profiles, Real-Debrid login and watch history are kept.").setPositiveButton("Reset",(d,w)->resetDefaults()).setNegativeButton("Cancel",null).show();}
    private void resetDefaults(){prefs.setSmartOneClickPlayback(true);prefs.setAutomaticPassthrough(true);prefs.setMaxQuality("4K");prefs.setExclude3d(true);prefs.setEpisodesAhead(2);prefs.setWifiOnlyDownloads(true);prefs.setClickSounds(true);prefs.setStartupCowSound(false);prefs.resetWaterfallPolicy();getSharedPreferences("m00v13_app_settings",MODE_PRIVATE).edit().remove("max_download_gib").apply();Toast.makeText(this,"Defaults restored",Toast.LENGTH_SHORT).show();rebuild();}

    private void rebuild(){setContentView(buildShell());}

    private void scrollTo(View target){if(pageScroll==null||target==null)return;pageScroll.post(()->{int[] t=new int[2],s=new int[2];target.getLocationOnScreen(t);pageScroll.getLocationOnScreen(s);int y=Math.max(0,pageScroll.getScrollY()+t[1]-s[1]-dp(90));pageScroll.smoothScrollTo(0,y);target.requestFocus();});}

    private String displaySummary(){android.util.DisplayMetrics m=getResources().getDisplayMetrics();float hz=0f;try{hz=getWindowManager().getDefaultDisplay().getRefreshRate();}catch(Exception ignored){}return m.widthPixels+"×"+m.heightPixels+(hz>0?" @ "+Math.round(hz)+"Hz":"");}
    private String networkSummary(){try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network n=cm==null?null:cm.getActiveNetwork();NetworkCapabilities c=n==null||cm==null?null:cm.getNetworkCapabilities(n);if(c==null)return"Offline / unknown";if(c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return"Ethernet";if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return"Wi-Fi";if(c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return"Cellular";return"Connected";}catch(Exception e){return"Unknown";}}
    private long freeBytes(){try{return new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes();}catch(Exception e){return 0L;}}
    private long dirBytes(File f){if(f==null||!f.exists())return 0L;if(f.isFile())return f.length();long n=0;File[] files=f.listFiles();if(files!=null)for(File x:files)n+=dirBytes(x);return n;}
    private File sharedPrefFile(String name){return new File(new File(getApplicationInfo().dataDir,"shared_prefs"),name);}
    private String versionName(){try{String v=getPackageManager().getPackageInfo(getPackageName(),0).versionName;return v==null?"BUILD":("v"+v);}catch(Exception e){return"BUILD";}}
    private String humanBytes(long b){if(b>=1024L*1024L*1024L)return String.format(Locale.US,"%.1f GB",b/(1024d*1024d*1024d));if(b>=1024L*1024L)return String.format(Locale.US,"%.1f MB",b/(1024d*1024d));if(b>=1024L)return String.format(Locale.US,"%.1f KB",b/1024d);return b+" B";}
    private String profileInitial(){String p=profiles==null?"D":profiles.activeProfile();return p==null||p.isEmpty()?"D":p.substring(0,1).toUpperCase(Locale.US);}

    private void openHub(String mode){Intent i=new Intent(this,MediaHubActivity.class);i.putExtra(MediaHubActivity.EXTRA_MODE,mode);startActivity(i);finish();overridePendingTransition(0,0);}
    private void open(Class<?> cls){startActivity(new Intent(this,cls));overridePendingTransition(0,0);}

    private GradientDrawable pill(boolean focused,boolean active){int fill=active?CYAN:focused?Color.rgb(46,50,58):Color.rgb(36,39,46);int stroke=focused?CYAN:Color.argb(25,255,255,255);return round(fill,stroke,focused?2:1,22);}
    private GradientDrawable navBackground(boolean active,boolean focused){int fill=active?CYAN:focused?Color.argb(50,0,240,255):Color.TRANSPARENT;int stroke=focused?CYAN:Color.TRANSPARENT;return round(fill,stroke,focused?2:0,11);}
    private GradientDrawable circle(boolean focused){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.rgb(28,38,47));g.setStroke(dp(focused?2:1),focused?CYAN:Color.argb(80,255,255,255));return g;}
    private GradientDrawable round(int fill,int stroke,int strokeDp,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}
    private TextView text(String value,int sp,boolean bold){TextView v=new TextView(this);v.setText(value==null?"":value);v.setTextColor(WHITE);v.setTextSize(sp);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private FrameLayout.LayoutParams frame(int w,int h,int gravity,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);if((gravity&Gravity.END)==Gravity.END)p.rightMargin=x;else p.leftMargin=x;if((gravity&Gravity.BOTTOM)==Gravity.BOTTOM)p.bottomMargin=y;else p.topMargin=y;return p;}
    private int dp(int value){return TvUi.dp(this,value);}
}
