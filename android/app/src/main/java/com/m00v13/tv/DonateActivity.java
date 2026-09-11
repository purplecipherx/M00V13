package com.m00v13.tv;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Cinematic 10-foot support surface. Donations remain optional and never alter app behavior. */
public final class DonateActivity extends Activity {
    private static final String ETH = "0x88675dB8404bBb447996f9aB51721763e136C808";
    private static final String BTC = "bc1qh2fmg2p5wlt3qw4sd8g8744skq8hyjap3c84cw";
    private static final String XMR = "43g7EHA92cWX8jFe7rWjAJ7m7ZNUn5Mqoi58bzRj7DAEeHeufT1ccCpbca36ngpGxubJmBeYptfcSLYVGwuE26RrSMyvZeT";
    private static final String CONTACT_EMAIL = "contact@m00v13.org";

    private static final int BG = Color.rgb(7, 9, 14);
    private static final int RAIL = Color.rgb(10, 14, 21);
    private static final int PANEL = Color.rgb(24, 28, 36);
    private static final int PANEL_HIGH = Color.rgb(43, 47, 56);
    private static final int WHITE = Color.rgb(239, 241, 246);
    private static final int MUTED = Color.rgb(171, 181, 193);
    private static final int CYAN = Color.rgb(0, 240, 255);
    private static final int GREEN = Color.rgb(107, 255, 143);
    private static final int VIOLET = Color.rgb(221, 183, 255);
    private static final int AMBER = Color.rgb(245, 158, 11);
    private static final int ORANGE = Color.rgb(249, 115, 22);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> tabViews = new LinkedHashMap<>();
    private String activeTab = "crypto";
    private LinearLayout contentHost;
    private TextView clock;
    private int screenWidth;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        setContentView(buildShell());
        updateClock();
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        View wash = new View(this);
        GradientDrawable washBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{Color.rgb(8, 24, 29), BG, Color.rgb(20, 10, 31)});
        wash.setBackground(washBg);
        root.addView(wash, new FrameLayout.LayoutParams(-1, -1));

        root.addView(buildRail(), frame(dp(88), -1, Gravity.START | Gravity.TOP, 0, 0));
        root.addView(buildTopBar(), frame(-1, dp(82), Gravity.TOP | Gravity.START, dp(88), 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFillViewport(true);
        scroll.addView(buildBody(), new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, frame(screenWidth - dp(112), -1, Gravity.TOP | Gravity.START, dp(112), dp(82)));
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);
        top.setBackgroundColor(Color.argb(220, 8, 12, 18));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("M 0 0 V 1 3", 23, true);
        logo.setLetterSpacing(.20f);
        TextView sub = text("STORIES WITHOUT LIMITS", 10, true);
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(.16f);
        brand.addView(logo);
        brand.addView(sub);
        top.addView(brand, frame(dp(300), dp(64), Gravity.START | Gravity.CENTER_VERTICAL, dp(24), 0));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);
        search.setTextSize(15);
        search.setHint("What do you want to watch or scrape?");
        search.setPadding(dp(22), 0, dp(22), 0);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setBackground(pill(false));
        search.setOnFocusChangeListener((v, focused) -> search.setBackground(pill(focused)));
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = search.getText() == null ? "" : search.getText().toString().trim();
                openUnifiedSearch(q);
                return true;
            }
            return false;
        });
        int searchW = Math.min(dp(520), (int)(screenWidth * .38f));
        top.addView(search, frame(searchW, dp(48), Gravity.CENTER, 0, 0));

        LinearLayout user = new LinearLayout(this);
        user.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        clock = text("", 14, true);
        user.addView(clock, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView profile = text("♙", 18, false);
        profile.setGravity(Gravity.CENTER);
        profile.setFocusable(true);
        profile.setClickable(true);
        profile.setBackground(circle(false));
        profile.setOnFocusChangeListener((v, f) -> profile.setBackground(circle(f)));
        profile.setOnClickListener(v -> open(ProfileActivity.class));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(42), dp(42));
        pp.leftMargin = dp(18);
        user.addView(profile, pp);
        top.addView(user, frame(dp(210), dp(60), Gravity.END | Gravity.CENTER_VERTICAL, dp(24), 0));
        return top;
    }

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(10), dp(18), dp(10), dp(18));
        rail.setBackgroundColor(RAIL);

        TextView brand = nav("▣", false, () -> openHub(MediaHubActivity.MODE_HOME));
        brand.setTextColor(CYAN);
        brand.setTextSize(23);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(52), dp(52));
        bp.bottomMargin = dp(30);
        rail.addView(brand, bp);

        addNav(rail, "⌂", false, () -> openHub(MediaHubActivity.MODE_HOME));
        addNav(rail, "▤", false, () -> openHub(MediaHubActivity.MODE_MOVIES));
        addNav(rail, "▭", false, () -> openHub(MediaHubActivity.MODE_TV));
        addNav(rail, "♡", false, () -> open(LibraryActivity.class));
        addNav(rail, "⌕", false, () -> openUnifiedSearch(""));
        addNav(rail, "↗", false, () -> open(DebridActivity.class));
        addNav(rail, "◉", false, () -> open(ProviderSettingsActivity.class));
        addNav(rail, "●", true, () -> {});
        addNav(rail, "♡", false, () -> {});

        View gap = new View(this);
        rail.addView(gap, new LinearLayout.LayoutParams(1, 0, 1f));
        addNav(rail, "⚙", false, () -> open(SettingsActivity.class));
        return rail;
    }

    private void addNav(LinearLayout rail, String label, boolean active, Runnable click) {
        TextView v = nav(label, active, click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(52), dp(52));
        p.bottomMargin = dp(10);
        rail.addView(v, p);
    }

    private TextView nav(String label, boolean active, Runnable click) {
        TextView v = text(label, 20, false);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(active ? Color.rgb(0, 95, 104) : Color.rgb(201, 211, 220));
        v.setFocusable(true);
        v.setClickable(true);
        v.setStateListAnimator(null);
        v.setBackground(active ? activeNav() : null);
        v.setOnFocusChangeListener((view, focused) -> {
            if (active) return;
            v.setBackground(focused ? roundRect(Color.argb(90, 0, 240, 255), Color.argb(220, 0, 240, 255), 2, 14) : null);
            v.setScaleX(focused ? 1.07f : 1f);
            v.setScaleY(focused ? 1.07f : 1f);
        });
        v.setOnClickListener(view -> click.run());
        return v;
    }

    private View buildBody() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(24), dp(40));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        TextView backing = badge("COMMUNITY BACKING", CYAN, Color.argb(55, 0, 240, 255));
        status.addView(backing);
        TextView dot = text("  •  ", 13, true);
        dot.setTextColor(Color.rgb(78, 92, 103));
        status.addView(dot);
        TextView open = text("OPEN SOURCE ECOSYSTEM", 12, true);
        open.setTextColor(Color.rgb(190, 211, 213));
        open.setLetterSpacing(.13f);
        status.addView(open);
        TextView truth = badge("●  DONATIONS OPTIONAL   •   NO FEATURE PAYWALLS", GREEN, Color.argb(110, 17, 30, 28));
        LinearLayout.LayoutParams truthLp = new LinearLayout.LayoutParams(-2, dp(34));
        truthLp.leftMargin = dp(20);
        status.addView(truth, truthLp);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView title = text("SUPPORT & DONATE", 38, true);
        title.setTextColor(Color.rgb(219, 252, 255));
        title.setLetterSpacing(.02f);
        root.addView(title);
        TextView subtitle = text("FUEL OPEN SCRAPERS, RESOLVER INFRASTRUCTURE & NEW 10-FOOT TV FEATURES", 14, false);
        subtitle.setTextColor(Color.rgb(185, 202, 203));
        subtitle.setLetterSpacing(.04f);
        root.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(roundRect(Color.argb(210, 12, 14, 19), Color.argb(50,255,255,255), 1, 24));
        addTab(tabs, "crypto", "Crypto Donations");
        addTab(tabs, "tiers", "Supporter Tiers");
        addTab(tabs, "costs", "Infrastructure Costs");
        addTab(tabs, "contact", "Contact & Bugs");
        LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(-2, dp(48));
        tabsLp.topMargin = dp(18);
        tabsLp.bottomMargin = dp(26);
        root.addView(tabs, tabsLp);

        contentHost = new LinearLayout(this);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentHost, new LinearLayout.LayoutParams(-1, -2));
        rebuildContent();
        return root;
    }

    private void addTab(LinearLayout parent, String key, String label) {
        TextView tab = text(label, 12, true);
        tab.setGravity(Gravity.CENTER);
        tab.setFocusable(true);
        tab.setClickable(true);
        tab.setStateListAnimator(null);
        tabViews.put(key, tab);
        tab.setOnFocusChangeListener((v, focused) -> styleTab(key, tab, focused));
        tab.setOnClickListener(v -> {
            activeTab = key;
            updateTabs();
            rebuildContent();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(key.equals("costs") ? 165 : key.equals("contact") ? 145 : 140), dp(40));
        p.rightMargin = dp(4);
        parent.addView(tab, p);
        styleTab(key, tab, false);
    }

    private void updateTabs() {
        for (Map.Entry<String, TextView> e : tabViews.entrySet()) styleTab(e.getKey(), e.getValue(), e.getValue().hasFocus());
    }

    private void styleTab(String key, TextView tab, boolean focused) {
        boolean active = key.equals(activeTab);
        tab.setTextColor(active ? Color.rgb(0, 68, 74) : WHITE);
        tab.setBackground(active ? roundRect(CYAN, CYAN, 1, 20)
            : focused ? roundRect(Color.argb(75,0,240,255), CYAN, 2, 20) : null);
        tab.setScaleX(focused ? 1.03f : 1f);
        tab.setScaleY(focused ? 1.03f : 1f);
    }

    private void rebuildContent() {
        if (contentHost == null) return;
        contentHost.removeAllViews();
        if ("tiers".equals(activeTab)) buildTiers(contentHost);
        else if ("costs".equals(activeTab)) buildCosts(contentHost);
        else if ("contact".equals(activeTab)) buildContactOnly(contentHost);
        else buildCrypto(contentHost);
    }

    private void buildCrypto(LinearLayout host) {
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(screenWidth < dp(1050) ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.TOP);
        addWallet(cards, "RECOMMENDED FOR PRIVACY", "P2P ANONYMOUS", "Monero", "XMR", XMR,
            "NETWORK", "Monero Mainnet • Zero Fee", AMBER, "Scan from Couch via Phone");
        addWallet(cards, "ON-CHAIN", "DIGITAL RESERVE", "Bitcoin", "BTC", BTC,
            "FORMAT", "Native SegWit (Bech32)", ORANGE, "Native SegWit Address");
        addWallet(cards, "ETH & EVM", "EVM ADDRESS", "Ethereum", "ETH", ETH,
            "NETWORK", "Ethereum Mainnet", VIOLET, "Ethereum Address");
        host.addView(cards, new LinearLayout.LayoutParams(-1, -2));

        TextView note = text("Donations are optional. They never unlock features, improve source ranking, change playback priority, or bypass app limits.", 14, false);
        note.setTextColor(MUTED);
        note.setPadding(dp(4), dp(14), dp(4), dp(18));
        host.addView(note);
        host.addView(contactCard(), centered(dp(520), -2));
        host.addView(legend(), new LinearLayout.LayoutParams(-1, -2));
    }

    private void addWallet(LinearLayout parent, String ribbon, String eyebrow, String name, String symbol,
                           String address, String infoLabel, String infoValue, int accent, String qrCaption) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(28), dp(24), dp(28), dp(28));
        card.setBackground(roundRect(Color.argb(238, 23, 27, 34), Color.argb(55,255,255,255), 1, 14));
        card.setFocusable(true);
        card.setStateListAnimator(null);
        card.setOnFocusChangeListener((v, focused) -> {
            card.setBackground(roundRect(focused ? Color.argb(250,27,33,42) : Color.argb(238,23,27,34), focused ? CYAN : Color.argb(55,255,255,255), focused ? 2 : 1, 14));
            card.setScaleX(focused ? 1.025f : 1f);
            card.setScaleY(focused ? 1.025f : 1f);
            card.setTranslationZ(focused ? dp(16) : 0);
        });

        TextView ribbonView = badge(ribbon, accent, Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)));
        card.addView(ribbonView, new LinearLayout.LayoutParams(-2, dp(28)));

        TextView eye = text(eyebrow, 11, true);
        eye.setTextColor(Color.rgb(146, 158, 167));
        eye.setLetterSpacing(.12f);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.topMargin = dp(22);
        card.addView(eye, ep);

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(name, 22, true);
        heading.addView(title);
        TextView sym = text("  (" + symbol + ")", 16, false);
        sym.setTextColor(name.equals("Ethereum") ? VIOLET : MUTED);
        heading.addView(sym);
        card.addView(heading);

        LinearLayout qrPlate = new LinearLayout(this);
        qrPlate.setOrientation(LinearLayout.VERTICAL);
        qrPlate.setGravity(Gravity.CENTER);
        qrPlate.setPadding(dp(12), dp(12), dp(12), dp(10));
        qrPlate.setBackground(roundRect(Color.WHITE, Color.WHITE, 1, 10));
        int qrDp = dp(190);
        ImageView qr = new ImageView(this);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setImageBitmap(qr(address, 480));
        qrPlate.addView(qr, new LinearLayout.LayoutParams(qrDp, qrDp));
        TextView qrc = text(qrCaption, 11, true);
        qrc.setTextColor(Color.rgb(35, 35, 35));
        qrc.setGravity(Gravity.CENTER);
        qrPlate.addView(qrc, new LinearLayout.LayoutParams(-1, dp(24)));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, -2);
        qp.topMargin = dp(18);
        qp.bottomMargin = dp(18);
        card.addView(qrPlate, qp);

        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(infoLabel, 11, true);
        label.setTextColor(Color.rgb(142, 154, 164));
        meta.addView(label, new LinearLayout.LayoutParams(0, dp(28), 1f));
        TextView value = text(infoValue, 11, true);
        value.setTextColor(accent == AMBER ? GREEN : accent);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        meta.addView(value, new LinearLayout.LayoutParams(-2, dp(28)));
        card.addView(meta);

        TextView addressView = text(address, 12, true);
        addressView.setSingleLine(true);
        addressView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        addressView.setPadding(dp(10), 0, dp(10), 0);
        addressView.setGravity(Gravity.CENTER_VERTICAL);
        addressView.setBackground(roundRect(PANEL_HIGH, Color.argb(45,255,255,255), 1, 4));
        addressView.setOnClickListener(v -> copy(symbol + " address", address));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(44));
        ap.bottomMargin = dp(12);
        card.addView(addressView, ap);

        TextView copy = action("▣  COPY " + (symbol.equals("XMR") ? "FULL KEY" : "ADDRESS"), () -> copy(symbol + " address", address));
        card.addView(copy, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout.LayoutParams cp;
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            cp = new LinearLayout.LayoutParams(0, -2, 1f);
            cp.rightMargin = dp(24);
        } else {
            cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(18);
        }
        parent.addView(card, cp);
    }

    private void buildTiers(LinearLayout host) {
        TextView h = sectionTitle("SUPPORTER TIERS");
        host.addView(h);
        host.addView(infoPanel("No paid feature tiers are configured", "M00V13 does not sell faster scraping, better source ranking, playback priority, or locked features. If community recognition or supporter badges are added later, they should remain cosmetic only."));
        host.addView(infoPanel("Support without an account", "Crypto donations above are direct and optional. No M00V13 account, subscription, or identity link is required by the app."));
        host.addView(infoPanel("Contribute another way", "Code review, provider maintenance, documentation, reproducible bug reports, and security disclosures can be as valuable as funding."));
        host.addView(contactCard(), centered(dp(600), -2));
    }

    private void buildCosts(LinearLayout host) {
        host.addView(sectionTitle("INFRASTRUCTURE COSTS"));
        host.addView(infoPanel("Scraper & provider maintenance", "Keeping provider definitions healthy, validating indexer changes, and running regression builds."));
        host.addView(infoPanel("Resolver & network infrastructure", "Optional resolver/proxy infrastructure, availability checks, and operational monitoring where deployed."));
        host.addView(infoPanel("Metadata, builds & releases", "Metadata services, CI builds, release hosting, test devices, and compatibility work for TV hardware."));
        TextView note = text("Live accounting is not wired into the app yet, so this screen intentionally does not invent monthly spend, funding percentage, or node counts.", 14, true);
        note.setTextColor(GREEN);
        note.setPadding(dp(18), dp(18), dp(18), dp(18));
        note.setBackground(roundRect(Color.argb(80, 20, 50, 31), Color.argb(100,107,255,143), 1, 12));
        host.addView(note);
    }

    private void buildContactOnly(LinearLayout host) {
        host.addView(sectionTitle("CONTACT & BUGS"));
        host.addView(contactCard(), centered(dp(650), -2));
        host.addView(infoPanel("Useful bug reports", "Include the title you searched, provider/source behavior, the exact failure text, device/Android TV version, and whether the issue reproduces after restart."));
        host.addView(infoPanel("Security disclosures", "Use direct email for vulnerabilities or sensitive implementation details rather than posting credentials, tokens, or private logs publicly."));
    }

    private View contactCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(26), dp(28), dp(28));
        card.setBackground(roundRect(Color.argb(242, 23, 27, 34), Color.argb(50,255,255,255), 1, 14));

        TextView icon = text("✉", 25, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundRect(PANEL_HIGH, Color.argb(45,255,255,255), 1, 10));
        card.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView eyebrow = text("DIRECT INQUIRIES & DISCLOSURES", 11, true);
        eyebrow.setTextColor(Color.rgb(145, 158, 167));
        eyebrow.setLetterSpacing(.10f);
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setPadding(0, dp(12), 0, 0);
        card.addView(eyebrow);
        TextView title = text("Get in Touch", 22, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);
        TextView desc = text("For bug reports, security disclosures, or developer inquiries, reach us directly by email.", 14, false);
        desc.setTextColor(MUTED);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, dp(8), 0, dp(14));
        card.addView(desc);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(8), 0);
        row.setBackground(roundRect(PANEL_HIGH, Color.argb(50,255,255,255), 1, 10));
        TextView email = text("@  " + CONTACT_EMAIL, 14, true);
        row.addView(email, new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView copy = action("COPY EMAIL", () -> copy("email", CONTACT_EMAIL));
        row.addView(copy, new LinearLayout.LayoutParams(dp(130), dp(38)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(52));
        rp.topMargin = dp(4);
        card.addView(row, rp);

        TextView emailApp = action("OPEN EMAIL APP", this::emailContact);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(180), dp(44));
        ep.topMargin = dp(12);
        card.addView(emailApp, ep);
        return card;
    }

    private View legend() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(14), dp(18), dp(14));
        bar.setBackground(roundRect(Color.argb(225, 10, 13, 18), Color.argb(40,255,255,255), 1, 10));
        bar.addView(legendItem("▲▼", "Navigate Sections"));
        bar.addView(legendItem("◀▶", "Select Crypto / QR"));
        bar.addView(legendItem("ENTER", "Copy Address / Full QR"));
        bar.addView(legendItem("BACK", "Return"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(28);
        return wrap(bar, p);
    }

    private View legendItem(String key, String label) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER_VERTICAL);
        TextView k = badge(key, Color.rgb(220,230,235), PANEL_HIGH);
        item.addView(k);
        TextView l = text("  " + label, 12, false);
        l.setTextColor(MUTED);
        item.addView(l);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, dp(34), 1f);
        item.setLayoutParams(ip);
        return item;
    }

    private View wrap(View child, LinearLayout.LayoutParams params) {
        LinearLayout holder = new LinearLayout(this);
        holder.addView(child, new LinearLayout.LayoutParams(-1, -2));
        holder.setLayoutParams(params);
        return holder;
    }

    private TextView sectionTitle(String value) {
        TextView v = text(value, 26, true);
        v.setTextColor(Color.rgb(219,252,255));
        v.setPadding(0, 0, 0, dp(16));
        return v;
    }

    private View infoPanel(String title, String body) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        panel.setBackground(roundRect(Color.argb(230, 23, 27, 34), Color.argb(45,255,255,255), 1, 12));
        TextView h = text(title, 18, true);
        panel.addView(h);
        TextView b = text(body, 14, false);
        b.setTextColor(MUTED);
        b.setPadding(0, dp(7), 0, 0);
        b.setLineSpacing(0, 1.12f);
        panel.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(14);
        panel.setLayoutParams(p);
        return panel;
    }

    private TextView action(String label, Runnable click) {
        TextView v = text(label, 12, true);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true);
        v.setClickable(true);
        v.setStateListAnimator(null);
        v.setBackground(roundRect(Color.rgb(42,46,55), Color.argb(55,255,255,255), 1, 10));
        v.setOnFocusChangeListener((view, focused) -> {
            v.setBackground(roundRect(focused ? Color.argb(110,0,240,255) : Color.rgb(42,46,55), focused ? CYAN : Color.argb(55,255,255,255), focused ? 2 : 1, 10));
            v.setScaleX(focused ? 1.035f : 1f);
            v.setScaleY(focused ? 1.035f : 1f);
        });
        v.setOnClickListener(view -> click.run());
        return v;
    }

    private TextView badge(String value, int textColor, int fill) {
        TextView v = text(value, 11, true);
        v.setTextColor(textColor);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), 0, dp(12), 0);
        v.setBackground(roundRect(fill, Color.argb(40,255,255,255), 1, 14));
        return v;
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private void emailContact() {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + CONTACT_EMAIL));
            startActivity(i);
        } catch (Exception e) {
            copy("email", CONTACT_EMAIL);
        }
    }

    private void openUnifiedSearch(String query) {
        Intent i = new Intent(this, MediaHubActivity.class);
        i.putExtra(MediaHubActivity.EXTRA_MODE, MediaHubActivity.MODE_SEARCH);
        if (query != null && !query.isEmpty()) i.putExtra("query", query);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    private void openHub(String mode) {
        Intent i = new Intent(this, MediaHubActivity.class);
        i.putExtra(MediaHubActivity.EXTRA_MODE, mode);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
        overridePendingTransition(0, 0);
    }

    private void updateClock() {
        if (clock == null) return;
        clock.setText(new SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date()));
        main.postDelayed(this::updateClock, 30_000L);
    }

    private Bitmap qr(String payload, int pixels) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixels, pixels);
            Bitmap bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.RGB_565);
            int[] colors = new int[pixels * pixels];
            for (int y = 0; y < pixels; y++) {
                int row = y * pixels;
                for (int x = 0; x < pixels; x++) colors[row + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
            bitmap.setPixels(colors, 0, pixels, 0, 0, pixels, pixels);
            return bitmap;
        } catch (Exception e) {
            DebugLog.append(this, "QR", "QR generation failed: " + e.getClass().getSimpleName());
            return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        }
    }

    private GradientDrawable activeNav() {
        GradientDrawable g = roundRect(CYAN, Color.rgb(128, 255, 255), 2, 14);
        return g;
    }

    private GradientDrawable pill(boolean focused) {
        return roundRect(Color.rgb(28,31,38), focused ? CYAN : Color.argb(65,255,255,255), focused ? 2 : 1, 25);
    }

    private GradientDrawable circle(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.rgb(219,252,255));
        g.setStroke(dp(focused ? 3 : 1), focused ? CYAN : Color.rgb(219,252,255));
        return g;
    }

    private GradientDrawable roundRect(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value == null ? "" : value);
        v.setTextColor(WHITE);
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private LinearLayout.LayoutParams centered(int width, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.topMargin = dp(18);
        return p;
    }

    private FrameLayout.LayoutParams frame(int w, int h, int gravity, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        if ((gravity & Gravity.END) == Gravity.END || (gravity & Gravity.RIGHT) == Gravity.RIGHT) p.rightMargin = x;
        else p.leftMargin = x;
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) p.bottomMargin = y;
        else p.topMargin = y;
        return p;
    }

    private int dp(int value) { return TvUi.dp(this, value); }
}
