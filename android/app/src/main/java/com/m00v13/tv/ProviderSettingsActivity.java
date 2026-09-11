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
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Cinematic 10-foot provider waterfall control surface. */
public final class ProviderSettingsActivity extends Activity {
    private static final int BG = Color.rgb(7, 9, 14);
    private static final int RAIL = Color.rgb(10, 14, 21);
    private static final int PANEL = Color.rgb(20, 26, 36);
    private static final int PANEL_2 = Color.rgb(27, 34, 45);
    private static final int WHITE = Color.rgb(235, 239, 247);
    private static final int MUTED = Color.rgb(166, 178, 193);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int GREEN = Color.rgb(96, 245, 135);
    private static final int PURPLE = Color.rgb(221, 183, 255);
    private static final int ERROR = Color.rgb(255, 180, 171);

    private final ExecutorService benchPool = Executors.newFixedThreadPool(8);
    private final Map<String, BenchResult> benchmark = Collections.synchronizedMap(new HashMap<>());
    private AppSettingsStore settings;
    private int filterTier = 0;
    private boolean benchmarkRunning;
    private int screenWidth;
    private int screenHeight;

    private static final class BenchResult {
        final boolean online;
        final long latencyMs;
        final String detail;
        BenchResult(boolean online, long latencyMs, String detail) {
            this.online = online;
            this.latencyMs = latencyMs;
            this.detail = detail == null ? "" : detail;
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        settings = new AppSettingsStore(this);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        rebuild();
    }

    @Override protected void onDestroy() {
        benchPool.shutdownNow();
        super.onDestroy();
    }

    private void rebuild() {
        if (isFinishing() || isDestroyed()) return;
        setContentView(buildShell());
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        View haze = new View(this);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{Color.argb(0,0,240,255), Color.argb(18,0,240,255), Color.argb(22,120,40,170), Color.argb(0,0,0,0)});
        haze.setBackground(hg);
        root.addView(haze, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildNavRail(), frame(dp(78), -1, Gravity.START | Gravity.TOP, 0, 0));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(34), dp(14), dp(28), dp(18));
        root.addView(page, frame(screenWidth - dp(78), -1, Gravity.START | Gravity.TOP, dp(78), 0));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, dp(76)));
        page.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(150)));
        page.addView(buildFilterRow(), new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        page.addView(body, bodyLp);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listScroll.addView(buildProviderList());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -1, .68f);
        left.rightMargin = dp(22);
        body.addView(listScroll, left);

        ScrollView inspectorScroll = new ScrollView(this);
        inspectorScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        inspectorScroll.addView(buildInspector());
        body.addView(inspectorScroll, new LinearLayout.LayoutParams(0, -1, .32f));

        page.addView(buildFooter(), new LinearLayout.LayoutParams(-1, dp(62)));
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("M 0 0 V 1 3", 22, true);
        logo.setLetterSpacing(.24f);
        TextView sub = text("STORIES WITHOUT LIMITS", 9, true);
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(.18f);
        brand.addView(logo);
        brand.addView(sub);
        top.addView(brand, frame(dp(310), dp(64), Gravity.START | Gravity.TOP, dp(8), dp(4)));

        TextView search = text("⌕   What do you want to watch or scrape?", 14, false);
        search.setTextColor(Color.rgb(205, 214, 226));
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(22), 0, dp(20), 0);
        search.setFocusable(true);
        search.setClickable(true);
        search.setBackground(pill(false, false));
        search.setOnFocusChangeListener((v,f)->search.setBackground(pill(f,false)));
        search.setOnClickListener(v -> openHub(MediaHubActivity.MODE_SEARCH));
        top.addView(search, frame(Math.min(dp(560), (int)(screenWidth*.39f)), dp(50), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(8)));

        LinearLayout account = new LinearLayout(this);
        account.setOrientation(LinearLayout.HORIZONTAL);
        account.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView clock = text(new SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date()), 13, true);
        clock.setTextColor(WHITE);
        clock.setPadding(0,0,dp(16),0);
        account.addView(clock, new LinearLayout.LayoutParams(-2, dp(44)));
        TextView profile = text(profileInitial(), 15, true);
        profile.setGravity(Gravity.CENTER);
        profile.setFocusable(true);
        profile.setClickable(true);
        profile.setBackground(circle(false));
        profile.setOnFocusChangeListener((v,f)->profile.setBackground(circle(f)));
        profile.setOnClickListener(v->open(ProfileActivity.class));
        account.addView(profile, new LinearLayout.LayoutParams(dp(42),dp(42)));
        top.addView(account, frame(dp(190),dp(54),Gravity.END|Gravity.TOP,dp(6),dp(6)));
        return top;
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        row.setPadding(dp(10), dp(10), dp(8), dp(10));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow = text("●  SCRAPER WATERFALL & INDEXER ENGINE  •  MULTI-TIER RESOLUTION", 11, true);
        eyebrow.setTextColor(CYAN);
        eyebrow.setLetterSpacing(.12f);
        left.addView(eyebrow);
        TextView title = text("SOURCES & PROVIDERS", 38, true);
        title.setLetterSpacing(-.02f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(4);
        left.addView(title,tp);
        TextView desc = text("Configure scraper priority, provider health, tier participation, adaptive concurrency, and Real-Debrid cache verification.",14,false);
        desc.setTextColor(Color.rgb(200,208,220));
        desc.setMaxLines(2);
        left.addView(desc);
        row.addView(left, new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.BOTTOM|Gravity.END);
        summary.addView(summaryChip("AVG ENDPOINT", benchmarkAverage()));
        summary.addView(summaryChip("ENABLED", enabledSummary()));
        TextView bench = smallButton(benchmarkRunning ? "Benchmarking…" : "Benchmark All", true);
        bench.setEnabled(!benchmarkRunning);
        bench.setOnClickListener(v->benchmarkAll());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(128),dp(52)); bp.leftMargin=dp(8);
        summary.addView(bench,bp);
        row.addView(summary, new LinearLayout.LayoutParams(-2,-2));
        return row;
    }

    private View summaryChip(String label, String value) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.VERTICAL);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12),dp(5),dp(12),dp(5));
        chip.setBackground(round(PANEL, Color.argb(38,255,255,255),1,12));
        TextView a=text(label,9,true);a.setTextColor(MUTED);a.setLetterSpacing(.06f);
        TextView b=text(value,13,true);b.setGravity(Gravity.CENTER);b.setTextColor(WHITE);
        chip.addView(a);chip.addView(b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(120),dp(52));lp.leftMargin=dp(8);chip.setLayoutParams(lp);
        return chip;
    }

    private View buildFilterRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        List<NativeProviderDefinition> all = NativeProviderDefinition.loadAll(this);
        addFilter(row, 0, "All Providers  " + all.size());
        addFilter(row, 1, "Tier 1: Fast  " + countTier(all,1));
        addFilter(row, 2, "Tier 2: Extended  " + countTier(all,2));
        addFilter(row, 3, "Tier 3: Optional  " + countTier(all,3));
        TextView rules = smallButton("⚙  Waterfall Rules", false);
        rules.setOnClickListener(v->Toast.makeText(this,"Policy controls are on the right.",Toast.LENGTH_SHORT).show());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(160),dp(38));p.leftMargin=dp(10);row.addView(rules,p);
        return row;
    }

    private void addFilter(LinearLayout row, int tier, String label) {
        TextView b = smallButton(label, filterTier == tier);
        b.setOnClickListener(v->{filterTier=tier;rebuild();});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(8);row.addView(b,lp);
    }

    private View buildProviderList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8),dp(4),dp(8),dp(22));
        List<NativeProviderDefinition> all = NativeProviderDefinition.loadAll(this);
        for (int i=0;i<all.size();i++) {
            NativeProviderDefinition p=all.get(i);
            if(filterTier!=0&&p.tier!=filterTier)continue;
            list.addView(providerCard(p,i), providerCardParams());
        }
        if(list.getChildCount()==0){TextView empty=text("No providers in this tier.",17,false);empty.setTextColor(MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty,new LinearLayout.LayoutParams(-1,dp(180)));}
        return list;
    }

    private View providerCard(NativeProviderDefinition p, int rank) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12),dp(8),dp(10),dp(8));
        card.setFocusable(true);
        card.setClickable(true);
        card.setStateListAnimator(null);
        boolean enabled=settings.providerEnabled(p.id,p.tier);
        card.setBackground(providerBackground(false,enabled));
        card.setOnFocusChangeListener((v,f)->card.setBackground(providerBackground(f,enabled)));
        card.setOnClickListener(v->{settings.setProviderEnabled(p.id,!settings.providerEnabled(p.id,p.tier));rebuild();});

        TextView number=text(String.format(Locale.US,"#%02d",rank+1),15,true);
        number.setGravity(Gravity.CENTER);
        number.setTextColor(enabled?CYAN:MUTED);
        number.setBackground(round(enabled?Color.argb(35,0,240,255):Color.argb(45,255,255,255),Color.TRANSPARENT,0,8));
        card.addView(number,new LinearLayout.LayoutParams(dp(50),dp(42)));

        LinearLayout identity=new LinearLayout(this);identity.setOrientation(LinearLayout.VERTICAL);identity.setPadding(dp(12),0,dp(8),0);
        TextView name=text(p.name,16,true);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);identity.addView(name);
        String type=(p.isJson()?"JSON/API":p.isXml()?"XML/RSS":"HTML")+"  •  Tier "+p.tier+"  •  "+p.mirrors.size()+" mirror"+(p.mirrors.size()==1?"":"s")+"  •  max "+p.maxResults;
        TextView meta=text(type,11,false);meta.setTextColor(MUTED);meta.setMaxLines(1);meta.setEllipsize(TextUtils.TruncateAt.END);identity.addView(meta);
        card.addView(identity,new LinearLayout.LayoutParams(0,-2,1f));

        BenchResult br=benchmark.get(p.id);
        LinearLayout health=new LinearLayout(this);health.setOrientation(LinearLayout.VERTICAL);health.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView hl=text(br==null?"NOT TESTED":br.online?"ONLINE":"FAILED",10,true);hl.setTextColor(br==null?MUTED:br.online?GREEN:ERROR);hl.setGravity(Gravity.CENTER);
        TextView hv=text(br==null?"—":br.online?(br.latencyMs+"ms"):br.detail,11,false);hv.setTextColor(Color.rgb(205,212,222));hv.setGravity(Gravity.CENTER);hv.setMaxLines(1);hv.setEllipsize(TextUtils.TruncateAt.END);
        health.addView(hl);health.addView(hv);
        card.addView(health,new LinearLayout.LayoutParams(dp(104),dp(44)));

        TextView toggle=smallButton(enabled?"ON":"OFF",enabled);
        toggle.setTextColor(enabled?Color.rgb(0,54,58):MUTED);
        toggle.setOnClickListener(v->{settings.setProviderEnabled(p.id,!settings.providerEnabled(p.id,p.tier));rebuild();});
        LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(dp(68),dp(38));t.leftMargin=dp(8);card.addView(toggle,t);

        LinearLayout order=new LinearLayout(this);order.setOrientation(LinearLayout.VERTICAL);order.setGravity(Gravity.CENTER);
        TextView up=miniButton("▲");up.setOnClickListener(v->moveProvider(p.id,-1));
        TextView down=miniButton("▼");down.setOnClickListener(v->moveProvider(p.id,1));
        order.addView(up,new LinearLayout.LayoutParams(dp(34),dp(28)));order.addView(down,new LinearLayout.LayoutParams(dp(34),dp(28)));
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(dp(38),dp(58));op.leftMargin=dp(8);card.addView(order,op);
        return card;
    }

    private LinearLayout.LayoutParams providerCardParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(82));p.bottomMargin=dp(10);return p;}

    private View buildInspector() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0,dp(4),dp(2),dp(18));
        box.addView(searchPolicyCard(), inspectorParams());
        box.addView(discoveryCard(), inspectorParams());
        box.addView(quickActionsCard(), inspectorParams());
        return box;
    }

    private View searchPolicyCard() {
        LinearLayout card=panelCard();
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("⚡  Search Policy",20,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(34),1f));
        TextView smart=text("SMART ENGINE",9,true);smart.setTextColor(CYAN);smart.setGravity(Gravity.CENTER);smart.setBackground(round(Color.argb(32,0,240,255),Color.TRANSPARENT,0,16));head.addView(smart,new LinearLayout.LayoutParams(dp(105),dp(28)));card.addView(head);
        TextView copy=text("Controls that directly affect scraper fan-out, tier participation, and debrid verification.",13,false);copy.setTextColor(MUTED);copy.setPadding(0,dp(5),0,dp(10));card.addView(copy);

        card.addView(infoRule("Fast Source Target","10 usable sources","Provider detail resolution stops early after the fast target is reached."),ruleParams());
        card.addView(infoRule("Hard Search Budgets","5.5s / 4.5s","Provider fan-out / detail-page resolution hard cutoffs."),ruleParams());

        int override=settings.searchWorkerOverride();
        int workers=SearchConcurrency.recommended(this);
        TextView worker=ruleButton("Concurrent Workers",override>0?(workers+" fixed"):(workers+" adaptive"));
        worker.setOnClickListener(v->{settings.setSearchWorkerOverride(nextWorkerOverride(override));rebuild();});
        card.addView(worker,ruleParams());

        card.addView(tierToggle(1,"Tier 1",settings.providerTier1()),ruleParams());
        card.addView(tierToggle(2,"Tier 2",settings.providerTier2()),ruleParams());
        card.addView(tierToggle(3,"Tier 3",settings.providerTier3()),ruleParams());

        boolean rdConnected=new DebridStore(this).isConnected();
        boolean verify=settings.verifyDebridCache();
        TextView rd=ruleButton("Real-Debrid Cache Probe",(verify?"ON":"OFF")+(rdConnected?" • connected":" • not connected"));
        rd.setTextColor(verify&&rdConnected?GREEN:WHITE);
        rd.setOnClickListener(v->{settings.setVerifyDebridCache(!settings.verifyDebridCache());rebuild();});
        card.addView(rd,ruleParams());
        return card;
    }

    private View discoveryCard() {
        LinearLayout card=panelCard();
        TextView title=text("▥  Provider Mix",20,true);card.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        List<NativeProviderDefinition> all=NativeProviderDefinition.loadAll(this);
        int[] total={0,countTier(all,1),countTier(all,2),countTier(all,3)};
        int[] enabled={0,0,0,0};
        for(NativeProviderDefinition p:all)if(settings.providerEnabled(p.id,p.tier)){enabled[0]++;enabled[p.tier]++;}
        TextView cap=text(enabled[0]+" of "+all.size()+" providers enabled",12,false);cap.setTextColor(MUTED);card.addView(cap);

        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setPadding(0,dp(10),0,dp(8));
        if(enabled[0]>0){addBar(bar,enabled[1],CYAN);addBar(bar,enabled[2],PURPLE);addBar(bar,enabled[3],GREEN);}else{View empty=new View(this);empty.setBackgroundColor(PANEL_2);bar.addView(empty,new LinearLayout.LayoutParams(0,dp(12),1f));}
        card.addView(bar,new LinearLayout.LayoutParams(-1,dp(32)));
        card.addView(mixLine("Tier 1 • Fast",enabled[1],total[1],CYAN));
        card.addView(mixLine("Tier 2 • Extended",enabled[2],total[2],PURPLE));
        card.addView(mixLine("Tier 3 • Optional",enabled[3],total[3],GREEN));

        int tested=0,online=0;long sum=0;
        synchronized(benchmark){for(BenchResult b:benchmark.values()){tested++;if(b.online){online++;sum+=b.latencyMs;}}}
        TextView b=text(tested==0?"Run Benchmark All to measure the configured provider endpoints.":("Endpoint checks: "+online+"/"+tested+" online"+(online>0?" • "+(sum/online)+"ms avg":"")),12,false);
        b.setTextColor(tested==0?MUTED:online==tested?GREEN:ERROR);b.setPadding(0,dp(10),0,0);card.addView(b);
        return card;
    }

    private View quickActionsCard() {
        LinearLayout card=panelCard();
        card.addView(text("⊕  Quick Actions",20,true),new LinearLayout.LayoutParams(-1,dp(36)));
        List<NativeProviderDefinition> all=NativeProviderDefinition.loadAll(this);
        TextView catalog=wideAction("{}  Compiled Provider Catalog  •  "+all.size());
        catalog.setOnClickListener(v->{filterTier=0;rebuild();});card.addView(catalog,wideParams());
        TextView reset=wideAction("↻  Reset Waterfall to Defaults");
        reset.setOnClickListener(v->{settings.resetWaterfallPolicy();rebuild();Toast.makeText(this,"Waterfall defaults restored",Toast.LENGTH_SHORT).show();});card.addView(reset,wideParams());
        TextView purge=wideAction("⌫  Purge Cached Source Results");purge.setTextColor(ERROR);
        purge.setOnClickListener(v->{new SourceStore(this).clearAll();Toast.makeText(this,"Cached source results cleared",Toast.LENGTH_SHORT).show();});card.addView(purge,wideParams());
        return card;
    }

    private View buildFooter() {
        LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.HORIZONTAL);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(14),dp(8),dp(14),dp(8));footer.setBackground(round(Color.argb(210,10,14,21),Color.argb(28,255,255,255),1,14));
        TextView a=text("▲/▼ Navigate Providers     ENTER Toggle Enable     ◀/▶ Move with controls",11,false);a.setTextColor(MUTED);footer.addView(a,new LinearLayout.LayoutParams(0,-1,1f));
        TextView b=text("● Benchmark All     ● Reset Order     ● Real-Debrid Probe",11,false);b.setTextColor(Color.rgb(196,207,219));b.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);footer.addView(b,new LinearLayout.LayoutParams(-2,-1));
        return footer;
    }

    private View buildNavRail() {
        LinearLayout rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setGravity(Gravity.CENTER_HORIZONTAL);rail.setPadding(dp(9),dp(46),dp(9),dp(16));rail.setBackgroundColor(RAIL);
        addNav(rail,"▣",false,()->openHub(MediaHubActivity.MODE_HOME));
        addNav(rail,"⌂",false,()->openHub(MediaHubActivity.MODE_HOME));
        addNav(rail,"▤",false,()->openHub(MediaHubActivity.MODE_MOVIES));
        addNav(rail,"▥",false,()->openHub(MediaHubActivity.MODE_TV));
        addNav(rail,"⌕",false,()->openHub(MediaHubActivity.MODE_SEARCH));
        addNav(rail,"↗",false,()->open(DebridActivity.class));
        addNav(rail,"▱",true,()->{});
        addNav(rail,"♡",false,()->openHub(MediaHubActivity.MODE_LIBRARY));
        View gap=new View(this);rail.addView(gap,new LinearLayout.LayoutParams(1,0,1f));
        addNav(rail,"⚙",false,()->open(SettingsActivity.class));
        return rail;
    }

    private void addNav(LinearLayout rail,String glyph,boolean active,Runnable action){
        TextView b=text(glyph,20,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setTextColor(active?Color.rgb(0,55,60):Color.rgb(198,210,220));b.setBackground(navBackground(active,false));
        b.setOnFocusChangeListener((v,f)->b.setBackground(navBackground(active,f)));b.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(48));p.bottomMargin=dp(10);rail.addView(b,p);
    }

    private void benchmarkAll() {
        if(benchmarkRunning)return;
        benchmarkRunning=true;benchmark.clear();rebuild();
        final List<NativeProviderDefinition> providers=NativeProviderDefinition.loadAll(this);
        new Thread(()->{
            ArrayList<Future<?>> futures=new ArrayList<>();
            for(NativeProviderDefinition p:providers)futures.add(benchPool.submit(()->benchmark.put(p.id,probe(p))));
            for(Future<?> f:futures)try{f.get();}catch(Exception ignored){}
            benchmarkRunning=false;
            runOnUiThread(this::rebuild);
        },"m00v13-provider-benchmark").start();
    }

    private BenchResult probe(NativeProviderDefinition p) {
        String last="unreachable";
        for(String mirror:p.mirrors){
            HttpURLConnection c=null;
            long started=System.nanoTime();
            try{
                String base=mirror.endsWith("/")?mirror:mirror+"/";
                String path=p.searchPath.replace("{query}", Uri.encode("matrix 1999"));
                URL target=new URL(new URL(base),path);
                String url=target.toString();
                if(!p.queryParam.isEmpty()&&!p.searchPath.contains("{query}"))url+=(url.contains("?")?"&":"?")+Uri.encode(p.queryParam)+"="+Uri.encode("matrix 1999");
                c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(3200);c.setReadTimeout(3500);c.setInstanceFollowRedirects(true);c.setRequestMethod("GET");
                c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android TV) M00V13/0.1");
                c.setRequestProperty("Accept",p.isJson()?"application/json,*/*;q=0.8":p.isXml()?"application/xml,text/xml,*/*;q=0.8":"text/html,*/*;q=0.8");
                int code=c.getResponseCode();long ms=(System.nanoTime()-started)/1_000_000L;
                if(code>=200&&code<400)return new BenchResult(true,ms,"HTTP "+code);
                last="HTTP "+code;
            }catch(Exception e){last=shortMessage(e);}finally{if(c!=null)c.disconnect();}
        }
        return new BenchResult(false,0,last);
    }

    private void moveProvider(String id,int delta){
        List<NativeProviderDefinition> current=new ArrayList<>(NativeProviderDefinition.loadAll(this));
        int at=-1;for(int i=0;i<current.size();i++)if(current.get(i).id.equals(id)){at=i;break;}
        int to=at+delta;if(at<0||to<0||to>=current.size())return;
        Collections.swap(current,at,to);ArrayList<String> ids=new ArrayList<>();for(NativeProviderDefinition p:current)ids.add(p.id);settings.setProviderOrder(ids);rebuild();
    }

    private TextView tierToggle(int tier,String label,boolean enabled){
        TextView v=ruleButton(label,enabled?"ENABLED":"DISABLED");v.setTextColor(enabled?GREEN:MUTED);v.setOnClickListener(x->{settings.setProviderTier(tier,!enabled);rebuild();});return v;
    }

    private View infoRule(String title,String value,String detail){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(10),dp(7),dp(10),dp(7));box.setBackground(round(PANEL_2,Color.TRANSPARENT,0,10));
        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.HORIZONTAL);TextView a=text(title,12,true);TextView b=text(value,12,true);b.setTextColor(CYAN);b.setGravity(Gravity.END);top.addView(a,new LinearLayout.LayoutParams(0,dp(22),1f));top.addView(b,new LinearLayout.LayoutParams(-2,dp(22)));box.addView(top);
        TextView d=text(detail,10,false);d.setTextColor(MUTED);d.setMaxLines(2);box.addView(d);return box;
    }

    private TextView ruleButton(String title,String value){TextView v=text(title+"\n"+value,12,true);v.setPadding(dp(10),dp(6),dp(10),dp(6));v.setGravity(Gravity.CENTER_VERTICAL);v.setFocusable(true);v.setClickable(true);v.setBackground(pill(false,false));v.setOnFocusChangeListener((x,f)->v.setBackground(pill(f,false)));return v;}
    private LinearLayout.LayoutParams ruleParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.bottomMargin=dp(8);return p;}

    private View mixLine(String label,int enabled,int total,int color){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);TextView a=text("●  "+label,11,true);a.setTextColor(color);TextView b=text(enabled+" / "+total,11,true);b.setTextColor(WHITE);b.setGravity(Gravity.END);r.addView(a,new LinearLayout.LayoutParams(0,dp(25),1f));r.addView(b,new LinearLayout.LayoutParams(-2,dp(25)));return r;}
    private void addBar(LinearLayout bar,int weight,int color){if(weight<=0)return;View v=new View(this);v.setBackground(round(color,Color.TRANSPARENT,0,3));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(12),weight);p.rightMargin=dp(3);bar.addView(v,p);}

    private LinearLayout panelCard(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(Color.argb(225,20,26,36),Color.argb(30,255,255,255),1,16));return c;}
    private LinearLayout.LayoutParams inspectorParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);return p;}
    private TextView wideAction(String label){TextView b=text(label,12,true);b.setPadding(dp(12),0,dp(12),0);b.setFocusable(true);b.setClickable(true);b.setBackground(pill(false,false));b.setOnFocusChangeListener((v,f)->b.setBackground(pill(f,false)));return b;}
    private LinearLayout.LayoutParams wideParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(8);return p;}

    private TextView smallButton(String label,boolean active){TextView b=text(label,11,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setStateListAnimator(null);b.setTextColor(active?Color.rgb(0,55,60):WHITE);b.setBackground(pill(false,active));b.setOnFocusChangeListener((v,f)->b.setBackground(pill(f,active)));return b;}
    private TextView miniButton(String label){TextView b=text(label,10,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setBackground(round(PANEL_2,Color.argb(28,255,255,255),1,5));b.setOnFocusChangeListener((v,f)->b.setBackground(round(f?Color.argb(70,0,240,255):PANEL_2,f?CYAN:Color.argb(28,255,255,255),f?2:1,5)));return b;}

    private GradientDrawable providerBackground(boolean focused,boolean enabled){int fill=enabled?Color.argb(225,23,29,39):Color.argb(145,20,24,31);int stroke=focused?CYAN:enabled?Color.argb(30,255,255,255):Color.argb(18,255,255,255);return round(fill,stroke,focused?2:1,12);}
    private GradientDrawable pill(boolean focused,boolean active){int fill=active?CYAN:focused?Color.rgb(43,51,62):Color.rgb(29,35,44);int stroke=focused?CYAN:Color.argb(22,255,255,255);return round(fill,stroke,focused?2:1,24);}
    private GradientDrawable navBackground(boolean active,boolean focused){int fill=active?CYAN:focused?Color.argb(50,0,240,255):Color.TRANSPARENT;int stroke=focused?CYAN:Color.TRANSPARENT;return round(fill,stroke,focused?2:0,12);}
    private GradientDrawable circle(boolean focused){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(Color.rgb(28,38,47));g.setStroke(dp(focused?2:1),focused?CYAN:Color.argb(80,255,255,255));return g;}
    private GradientDrawable round(int fill,int stroke,int strokeDp,int radiusDp){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(radiusDp));if(strokeDp>0)g.setStroke(dp(strokeDp),stroke);return g;}

    private String benchmarkAverage(){int n=0;long sum=0;synchronized(benchmark){for(BenchResult b:benchmark.values())if(b.online){n++;sum+=b.latencyMs;}}return n==0?(benchmarkRunning?"RUNNING":"—"):(sum/n)+"ms";}
    private String enabledSummary(){List<NativeProviderDefinition> all=NativeProviderDefinition.loadAll(this);int e=0;for(NativeProviderDefinition p:all)if(settings.providerEnabled(p.id,p.tier))e++;return e+" / "+all.size();}
    private int countTier(List<NativeProviderDefinition> all,int tier){int c=0;for(NativeProviderDefinition p:all)if(p.tier==tier)c++;return c;}
    private int nextWorkerOverride(int current){int[] choices={0,10,16,24,32,48};for(int i=0;i<choices.length;i++)if(choices[i]==current)return choices[(i+1)%choices.length];return 0;}

    private void openHub(String mode){Intent i=new Intent(this,MediaHubActivity.class);i.putExtra(MediaHubActivity.EXTRA_MODE,mode);startActivity(i);finish();overridePendingTransition(0,0);}
    private void open(Class<?> cls){startActivity(new Intent(this,cls));overridePendingTransition(0,0);}
    private String profileInitial(){String p=new ProfileStore(this).activeProfile();return p==null||p.isEmpty()?"D":p.substring(0,1).toUpperCase(Locale.US);}
    private TextView text(String value,int sp,boolean bold){TextView v=new TextView(this);v.setText(value==null?"":value);v.setTextColor(WHITE);v.setTextSize(sp);v.setGravity(Gravity.CENTER_VERTICAL);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private FrameLayout.LayoutParams frame(int w,int h,int gravity,int x,int y){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);if((gravity&Gravity.END)==Gravity.END)p.rightMargin=x;else p.leftMargin=x;if((gravity&Gravity.BOTTOM)==Gravity.BOTTOM)p.bottomMargin=y;else p.topMargin=y;return p;}
    private int dp(int v){return TvUi.dp(this,v);}
    private static String shortMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();String m=x.getMessage();return m==null?x.getClass().getSimpleName():m;}
}
