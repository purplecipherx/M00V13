package com.m00v13.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

/**
 * One cinematic TV surface for Home, Movies, Series, Library, search and title details.
 * Search is intentionally in-place: it replaces the shelf data instead of opening another screen.
 */
public final class MediaHubActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_HOME = "home";
    public static final String MODE_MOVIES = "movies";
    public static final String MODE_TV = "tv";
    public static final String MODE_LIBRARY = "library";
    public static final String MODE_SEARCH = "search";

    private static final int BG = Color.rgb(4, 7, 13);
    private static final int PANEL = Color.argb(165, 14, 21, 33);
    private static final int PANEL_STRONG = Color.argb(215, 13, 19, 30);
    private static final int WHITE = Color.rgb(246, 249, 255);
    private static final int MUTED = Color.rgb(180, 190, 205);
    private static final int ICE = Color.rgb(210, 228, 255);
    private static final int BLUE = Color.rgb(130, 180, 255);

    private final ExecutorService dataPool = Executors.newFixedThreadPool(3);
    private final ExecutorService artPool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> navViews = new HashMap<>();

    private ProfileStore profiles;
    private CatalogStore catalog;
    private DiscoveryStore discovery;
    private ArtworkLoader artwork;
    private DescriptionCacheStore descriptions;
    private SearchHistoryStore searchHistory;

    private String mode = MODE_HOME;
    private boolean clickSounds;
    private boolean searchRunning;
    private int heroToken;
    private int screenWidth;
    private int screenHeight;

    private ImageView heroBackground;
    private TextView heroEyebrow;
    private TextView heroTitle;
    private TextView heroTagline;
    private LinearLayout heroBadges;
    private TextView heroDescription;
    private TextView playButton;
    private TextView moreButton;
    private TextView watchlistButton;
    private TextView shelfTitle;
    private TextView emptyState;
    private EditText searchInput;
    private TextView clock;
    private TextView profileBubble;
    private RecyclerView shelf;
    private MediaAdapter adapter;
    private MediaCard selected;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        profiles = new ProfileStore(this);
        catalog = new CatalogStore(this);
        discovery = new DiscoveryStore(this);
        artwork = new ArtworkLoader(this);
        descriptions = new DescriptionCacheStore(this);
        searchHistory = new SearchHistoryStore(this);
        clickSounds = new AppSettingsStore(this).clickSounds();

        String requested = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_MOVIES.equals(requested) || MODE_TV.equals(requested) || MODE_LIBRARY.equals(requested)) mode = requested;

        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        setContentView(buildShell());
        updateClock();
        loadMode(mode, false);

        if (discovery.stale()) {
            dataPool.submit(() -> {
                try {
                    discovery.refresh();
                    runOnUiThread(() -> {
                        if (!dead() && !MODE_SEARCH.equals(mode)) loadMode(mode, false);
                    });
                } catch (Exception e) {
                    DebugLog.append(this, "HUB", "Discovery refresh: " + msg(e));
                }
            });
        }
    }

    @Override protected void onResume() {
        super.onResume();
        clickSounds = new AppSettingsStore(this).clickSounds();
        if (profileBubble != null) profileBubble.setText(profileInitial());
        if (MODE_LIBRARY.equals(mode)) loadMode(mode, false);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        dataPool.shutdownNow();
        artPool.shutdownNow();
        super.onDestroy();
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        heroBackground = new ImageView(this);
        heroBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBackground.setBackgroundColor(BG);
        heroBackground.setAlpha(.63f);
        root.addView(heroBackground, new FrameLayout.LayoutParams(-1, -1));

        View vignette = new View(this);
        GradientDrawable shade = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.argb(248,3,6,10), Color.argb(220,3,6,10), Color.argb(110,3,6,10), Color.argb(18,3,6,10)});
        vignette.setBackground(shade);
        root.addView(vignette, new FrameLayout.LayoutParams(-1, -1));

        View floor = new View(this);
        GradientDrawable floorShade = new GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            new int[]{Color.argb(250,2,4,8), Color.argb(210,3,7,14), Color.argb(0,3,7,14)});
        floor.setBackground(floorShade);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, Math.max(dp(270), (int)(screenHeight * .34f)), Gravity.BOTTOM);
        root.addView(floor, fp);

        root.addView(buildTopBar(), frame(-1, dp(92), Gravity.TOP | Gravity.START, 0, 0));
        root.addView(buildNavRail(), frame(dp(76), screenHeight - dp(112), Gravity.TOP | Gravity.START, dp(24), dp(56)));
        root.addView(buildHero(), frame(Math.min(dp(690), (int)(screenWidth * .48f)), dp(415), Gravity.TOP | Gravity.START, dp(136), dp(125)));
        root.addView(buildShelfArea(), frame(screenWidth - dp(120), Math.max(dp(235), (int)(screenHeight * .28f)), Gravity.BOTTOM | Gravity.START, dp(112), dp(14)));
        return root;
    }

    private View buildTopBar() {
        FrameLayout top = new FrameLayout(this);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = text("M 0 0 V 1 3", 24, false);
        logo.setLetterSpacing(.18f);
        TextView sub = text("STORIES WITHOUT LIMITS", 9, false);
        sub.setTextColor(MUTED);
        sub.setLetterSpacing(.22f);
        brand.addView(logo);
        brand.addView(sub);
        top.addView(brand, frame(dp(320), dp(70), Gravity.TOP | Gravity.START, dp(136), dp(15)));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setTextColor(WHITE);
        searchInput.setHintTextColor(Color.rgb(190, 200, 215));
        searchInput.setTextSize(15);
        searchInput.setHint("What do you feel like watching?");
        searchInput.setPadding(dp(26), 0, dp(26), 0);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackground(pill(false));
        searchInput.setOnFocusChangeListener((v, focused) -> searchInput.setBackground(pill(focused)));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                beginSearch();
                return true;
            }
            return false;
        });
        int searchW = Math.min(dp(540), (int)(screenWidth * .36f));
        FrameLayout.LayoutParams sp = frame(searchW, dp(52), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(20));
        top.addView(searchInput, sp);

        LinearLayout user = new LinearLayout(this);
        user.setOrientation(LinearLayout.HORIZONTAL);
        user.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        clock = text("", 14, false);
        clock.setTextColor(Color.rgb(225, 231, 240));
        clock.setPadding(0, 0, dp(16), 0);
        user.addView(clock, new LinearLayout.LayoutParams(-2, dp(48)));
        profileBubble = text(profileInitial(), 15, true);
        profileBubble.setGravity(Gravity.CENTER);
        profileBubble.setFocusable(true);
        profileBubble.setClickable(true);
        profileBubble.setBackground(circle(false));
        profileBubble.setOnFocusChangeListener((v, f) -> profileBubble.setBackground(circle(f)));
        profileBubble.setOnClickListener(v -> open(ProfileActivity.class));
        user.addView(profileBubble, new LinearLayout.LayoutParams(dp(44), dp(44)));
        top.addView(user, frame(dp(210), dp(62), Gravity.TOP | Gravity.END, dp(24), dp(13)));
        return top;
    }

    private View buildNavRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(8), dp(10), dp(8), dp(10));
        rail.setBackground(roundRect(PANEL, Color.argb(40,255,255,255), 1, 30));

        addNav(rail, MODE_HOME, "⌂\nHome", () -> loadMode(MODE_HOME, true));
        addNav(rail, MODE_MOVIES, "▣\nMovies", () -> loadMode(MODE_MOVIES, true));
        addNav(rail, MODE_TV, "▤\nSeries", () -> loadMode(MODE_TV, true));
        addNav(rail, MODE_LIBRARY, "♡\nLibrary", () -> loadMode(MODE_LIBRARY, true));
        addNav(rail, MODE_SEARCH, "⌕\nSearch", () -> {
            mode = MODE_SEARCH;
            updateNavStyles();
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        });

        View gap = new View(this);
        rail.addView(gap, new LinearLayout.LayoutParams(1, 0, 1f));
        addNav(rail, "debrid", "↗\nDebrid", () -> open(DebridActivity.class));
        addNav(rail, "sources", "◉\nSources", () -> open(ProviderSettingsActivity.class));
        addNav(rail, "support", "₿\nSupport", () -> open(DonateActivity.class));
        addNav(rail, "settings", "⚙\nSettings", () -> open(SettingsActivity.class));
        return rail;
    }

    private void addNav(LinearLayout rail, String key, String label, Runnable action) {
        TextView item = text(label, 10, false);
        item.setGravity(Gravity.CENTER);
        item.setTextColor(MUTED);
        item.setFocusable(true);
        item.setClickable(true);
        item.setStateListAnimator(null);
        item.setOnClickListener(v -> action.run());
        item.setOnFocusChangeListener((v, focused) -> styleNav(key, item, focused));
        navViews.put(key, item);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(58), dp(52));
        p.bottomMargin = dp(3);
        rail.addView(item, p);
    }

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setPadding(0, 0, dp(10), dp(6));

        heroEyebrow = text("TRENDING NOW", 11, true);
        heroEyebrow.setTextColor(Color.rgb(205, 214, 225));
        heroEyebrow.setLetterSpacing(.22f);
        hero.addView(heroEyebrow);

        heroTitle = text("M00V13", 46, false);
        heroTitle.setLetterSpacing(.08f);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        hero.addView(heroTitle);

        heroTagline = text("STORIES WITHOUT LIMITS", 12, true);
        heroTagline.setTextColor(Color.rgb(205, 214, 225));
        heroTagline.setLetterSpacing(.14f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(4); tp.bottomMargin = dp(10);
        hero.addView(heroTagline, tp);

        heroBadges = new LinearLayout(this);
        heroBadges.setOrientation(LinearLayout.HORIZONTAL);
        heroBadges.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(34));
        bp.bottomMargin = dp(10);
        hero.addView(heroBadges, bp);

        heroDescription = text("Choose something from the shelf below.", 14, false);
        heroDescription.setTextColor(Color.rgb(210, 217, 228));
        heroDescription.setMaxLines(4);
        heroDescription.setEllipsize(TextUtils.TruncateAt.END);
        heroDescription.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2);
        dpv.bottomMargin = dp(18);
        hero.addView(heroDescription, dpv);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        playButton = action("▶   Play", true, () -> playSelected());
        moreButton = action("More Info", false, () -> showFullDetails());
        watchlistButton = action("＋", false, () -> toggleWatchlist());
        actions.addView(playButton, actionParams(dp(154)));
        actions.addView(moreButton, actionParams(dp(134)));
        actions.addView(watchlistButton, actionParams(dp(54)));
        hero.addView(actions, new LinearLayout.LayoutParams(-1, dp(56)));
        return hero;
    }

    private View buildShelfArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.BOTTOM);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        shelfTitle = text("Trending Now", 13, false);
        shelfTitle.setTextColor(Color.rgb(215, 222, 232));
        shelfTitle.setLetterSpacing(.12f);
        heading.addView(shelfTitle, new LinearLayout.LayoutParams(0, dp(28), 1f));
        TextView footer = text("MOVIES   •   SERIES   •   FOR YOU", 10, false);
        footer.setTextColor(Color.rgb(145, 155, 170));
        footer.setLetterSpacing(.12f);
        heading.addView(footer, new LinearLayout.LayoutParams(-2, dp(28)));
        area.addView(heading, new LinearLayout.LayoutParams(-1, dp(30)));

        FrameLayout content = new FrameLayout(this);
        shelf = new RecyclerView(this);
        shelf.setItemAnimator(null);
        shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setVerticalScrollBarEnabled(false);
        shelf.setItemViewCacheSize(10);
        shelf.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        content.addView(shelf, new FrameLayout.LayoutParams(-1, -1));

        emptyState = text("", 18, false);
        emptyState.setTextColor(MUTED);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setVisibility(View.GONE);
        content.addView(emptyState, new FrameLayout.LayoutParams(-1, -1));
        area.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        return area;
    }

    private void loadMode(String next, boolean focusShelf) {
        mode = next;
        hideKeyboard();
        searchInput.clearFocus();
        updateNavStyles();
        searchRunning = false;

        List<MediaCard> all = catalog.all();
        List<MediaCard> cards;
        String title;
        if (MODE_MOVIES.equals(next)) {
            title = "Movies";
            cards = discovery.get(DiscoveryStore.POPULAR_MOVIES);
            if (cards.isEmpty()) cards = filter(all, false);
        } else if (MODE_TV.equals(next)) {
            title = "Series";
            cards = discovery.get(DiscoveryStore.POPULAR_TV);
            if (cards.isEmpty()) cards = filter(all, true);
        } else if (MODE_LIBRARY.equals(next)) {
            title = "Your Library";
            cards = libraryItems(all);
        } else {
            mode = MODE_HOME;
            title = "Trending Now";
            cards = homeItems(all);
        }
        showMedia(cards, title, focusShelf);
    }

    private List<MediaCard> homeItems(List<MediaCard> all) {
        ArrayList<MediaCard> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addUnique(out, seen, profiles.continueWatching(all));
        addUnique(out, seen, discovery.get(DiscoveryStore.POPULAR_MOVIES));
        addUnique(out, seen, discovery.get(DiscoveryStore.POPULAR_TV));
        if (out.isEmpty()) addUnique(out, seen, all);
        return out;
    }

    private List<MediaCard> libraryItems(List<MediaCard> all) {
        Set<String> wanted = new HashSet<>(profiles.watchlistMediaIds());
        ArrayList<MediaCard> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MediaCard c : all) if (wanted.contains(c.id) && seen.add(c.id)) out.add(c);
        addUnique(out, seen, profiles.continueWatching(all));
        return out;
    }

    private static void addUnique(List<MediaCard> out, Set<String> seen, List<MediaCard> input) {
        if (input == null) return;
        for (MediaCard c : input) {
            if (c != null && c.id != null && seen.add(c.id)) out.add(c);
            if (out.size() >= 50) return;
        }
    }

    private void beginSearch() {
        if (searchRunning) return;
        String q = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (q.isEmpty()) return;
        mode = MODE_SEARCH;
        updateNavStyles();
        hideKeyboard();
        searchInput.clearFocus();
        searchRunning = true;
        shelfTitle.setText("Searching for “" + q + "”…");
        emptyState.setVisibility(View.GONE);
        searchHistory.add(q);
        DebugLog.append(this, "SEARCH", "Unified metadata query='" + q + "'");

        dataPool.submit(() -> {
            ArrayList<MediaCard> found = new ArrayList<>();
            String error = null;
            try {
                MetadataStore ms = new MetadataStore(this);
                if (ms.isConfigured()) {
                    TmdbClient tmdb = new TmdbClient(ms.tmdbToken());
                    for (TmdbClient.Result r : tmdb.searchMulti(q)) found.add(tmdb.toCard(r));
                } else {
                    CinemetaClient cm = new CinemetaClient();
                    found.addAll(cm.searchMovies(q));
                    found.addAll(cm.searchSeries(q));
                }
            } catch (Exception e) {
                error = msg(e);
            }
            final String fail = error;
            runOnUiThread(() -> {
                if (dead()) return;
                searchRunning = false;
                if (fail != null) {
                    showMedia(Collections.emptyList(), "Search", false);
                    emptyState.setText("Search failed: " + fail);
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                catalog.upsertAll(found);
                showMedia(found, "Results for “" + q + "”", true);
            });
        });
    }

    private void showMedia(List<MediaCard> cards, String title, boolean focusShelf) {
        List<MediaCard> safe = cards == null ? Collections.emptyList() : cards;
        shelfTitle.setText(title);
        emptyState.setVisibility(safe.isEmpty() ? View.VISIBLE : View.GONE);
        if (safe.isEmpty()) {
            emptyState.setText(MODE_LIBRARY.equals(mode) ? "Your library is empty. Add titles with +." : "Nothing to show yet.");
            shelf.setAdapter(null);
            clearHero();
            return;
        }
        int width = MODE_SEARCH.equals(mode) ? dp(174) : dp(220);
        int height = MODE_SEARCH.equals(mode) ? dp(112) : dp(142);
        adapter = new MediaAdapter(new ArrayList<>(safe.subList(0, Math.min(50, safe.size()))), width, height);
        shelf.setAdapter(adapter);
        shelf.scrollToPosition(0);
        selectHero(adapter.items.get(0));
        if (focusShelf) {
            shelf.post(() -> {
                RecyclerView.ViewHolder h = shelf.findViewHolderForAdapterPosition(0);
                if (h != null) h.itemView.requestFocus();
            });
        }
    }

    private void selectHero(MediaCard media) {
        if (media == null) return;
        selected = media;
        int token = ++heroToken;
        heroDescription.setMaxLines(4);
        heroEyebrow.setText(MODE_LIBRARY.equals(mode) ? "FROM YOUR LIBRARY" : MODE_SEARCH.equals(mode) ? "SEARCH RESULT" : "FEATURED FOR YOU");
        heroTitle.setText(media.title == null ? "" : media.title.toUpperCase(Locale.US));
        heroTagline.setText(media.genre == null || media.genre.isEmpty() ? (media.series ? "SERIES" : "MOVIE") : media.genre.toUpperCase(Locale.US));
        rebuildBadges(media);
        String cached = descriptions.get(media.id);
        heroDescription.setText(cached == null || cached.isEmpty() ? fallbackDescription(media) : cached);
        watchlistButton.setText(profiles.isInWatchlist(media.id) ? "✓" : "＋");

        if (media.artworkUrl != null) artwork.load(heroBackground, media.artworkUrl, Math.min(screenWidth, 1280), artPool);
        main.postDelayed(() -> {
            if (dead() || token != heroToken || selected != media) return;
            dataPool.submit(() -> {
                String desc = cached;
                String bg = null;
                try {
                    CinemetaClient cm = new CinemetaClient();
                    if (desc == null || desc.isEmpty()) desc = cm.description(media);
                    bg = cm.background(media);
                } catch (Exception ignored) {}
                final String readyDesc = desc;
                final String readyBg = bg;
                if (readyDesc != null && !readyDesc.isEmpty()) descriptions.put(media.id, readyDesc);
                runOnUiThread(() -> {
                    if (dead() || token != heroToken || selected != media) return;
                    if (readyDesc != null && !readyDesc.isEmpty()) heroDescription.setText(readyDesc);
                    if (readyBg != null && !readyBg.isEmpty()) artwork.load(heroBackground, readyBg, Math.min(screenWidth, 1280), artPool);
                });
            });
        }, 220);
    }

    private void rebuildBadges(MediaCard media) {
        heroBadges.removeAllViews();
        String year = extractYear(media.subtitle);
        if (!year.isEmpty()) addBadge(year);
        addBadge(media.series ? "TV Series" : "Movie");
        if (media.genre != null && !media.genre.isEmpty()) addBadge(media.genre);
        if (media.isEpisode()) addBadge("S" + media.seasonNumber + " E" + media.episodeNumber);
    }

    private void addBadge(String value) {
        TextView badge = text(value, 11, true);
        badge.setTextColor(Color.rgb(220, 227, 236));
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(11), 0, dp(11), 0);
        badge.setBackground(roundRect(Color.argb(150,16,24,38), Color.argb(48,255,255,255), 1, 16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(28));
        p.rightMargin = dp(7);
        heroBadges.addView(badge, p);
    }

    private void clearHero() {
        selected = null;
        heroTitle.setText("M00V13");
        heroEyebrow.setText("STORIES WITHOUT LIMITS");
        heroTagline.setText("");
        heroBadges.removeAllViews();
        heroDescription.setText("Use the search bar above or choose another section.");
        heroBackground.setImageDrawable(null);
        watchlistButton.setText("＋");
    }

    private void playSelected() {
        if (selected == null) return;
        Intent intent = new Intent(this, MediaOpenActivity.class);
        intent.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID, selected.id);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void showFullDetails() {
        if (selected == null) return;
        Intent intent = new Intent(this, TitleDetailsActivity.class);
        intent.putExtra(TitleDetailsActivity.EXTRA_MEDIA_ID, selected.id);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void toggleWatchlist() {
        if (selected == null) return;
        boolean next = !profiles.isInWatchlist(selected.id);
        profiles.setWatchlist(selected.id, next);
        watchlistButton.setText(next ? "✓" : "＋");
        if (MODE_LIBRARY.equals(mode) && !next) loadMode(MODE_LIBRARY, false);
    }

    private final class MediaAdapter extends RecyclerView.Adapter<MediaHolder> {
        final List<MediaCard> items;
        final int cardW, cardH;
        MediaAdapter(List<MediaCard> items, int cardW, int cardH) {
            this.items = items; this.cardW = cardW; this.cardH = cardH; setHasStableIds(true);
        }
        @Override public long getItemId(int position) { return items.get(position).id.hashCode(); }
        @Override public MediaHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(MediaHubActivity.this);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cardW, cardH);
            lp.rightMargin = dp(MODE_SEARCH.equals(mode) ? 10 : 15);
            lp.topMargin = dp(10); lp.bottomMargin = dp(16);
            card.setLayoutParams(lp);
            card.setFocusable(true); card.setClickable(true); card.setStateListAnimator(null);
            card.setBackground(cardBox(false));
            card.setClipToOutline(true);

            ImageView image = new ImageView(MediaHubActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(PANEL_STRONG);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1));

            TextView title = text("", MODE_SEARCH.equals(mode) ? 11 : 12, true);
            title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.BOTTOM | Gravity.START);
            title.setPadding(dp(10), dp(8), dp(8), dp(9));
            title.setBackgroundColor(Color.argb(115, 0, 0, 0));
            card.addView(title, new FrameLayout.LayoutParams(-1, Math.max(dp(42), cardH / 3), Gravity.BOTTOM));
            return new MediaHolder(card, image, title);
        }
        @Override public void onBindViewHolder(MediaHolder holder, int position) {
            MediaCard media = items.get(position);
            holder.title.setText(media.title);
            holder.card.setScaleX(1f); holder.card.setScaleY(1f); holder.card.setTranslationZ(0f);
            holder.card.setBackground(cardBox(false));
            artwork.load(holder.image, media.artworkUrl, Math.min(cardW * 2, 560), artPool);
            holder.card.setOnFocusChangeListener((v, focused) -> {
                holder.card.setBackground(cardBox(focused));
                holder.card.setScaleX(focused ? 1.07f : 1f);
                holder.card.setScaleY(focused ? 1.07f : 1f);
                holder.card.setTranslationZ(focused ? dp(14) : 0);
                holder.title.setTextColor(focused ? ICE : WHITE);
                if (focused) {
                    selectHero(media);
                    if (clickSounds) holder.card.playSoundEffect(SoundEffectConstants.CLICK);
                }
            });
            holder.card.setOnClickListener(v -> {
                selectHero(media);
                playButton.requestFocus();
            });
        }
        @Override public int getItemCount() { return items.size(); }
    }

    private static final class MediaHolder extends RecyclerView.ViewHolder {
        final FrameLayout card; final ImageView image; final TextView title;
        MediaHolder(FrameLayout c, ImageView i, TextView t) { super(c); card = c; image = i; title = t; }
    }

    private TextView action(String label, boolean primary, Runnable click) {
        TextView v = text(label, 14, primary);
        v.setGravity(Gravity.CENTER);
        v.setFocusable(true); v.setClickable(true); v.setStateListAnimator(null);
        v.setTextColor(primary ? Color.BLACK : WHITE);
        v.setBackground(primary ? primaryAction(false) : pill(false));
        v.setOnFocusChangeListener((view, focused) -> {
            v.setBackground(primary ? primaryAction(focused) : pill(focused));
            v.setScaleX(focused ? 1.04f : 1f); v.setScaleY(focused ? 1.04f : 1f);
        });
        v.setOnClickListener(view -> click.run());
        return v;
    }

    private LinearLayout.LayoutParams actionParams(int width) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, dp(52));
        p.rightMargin = dp(12);
        return p;
    }

    private void updateNavStyles() {
        for (Map.Entry<String, TextView> e : navViews.entrySet()) styleNav(e.getKey(), e.getValue(), e.getValue().hasFocus());
    }

    private void styleNav(String key, TextView view, boolean focused) {
        boolean active = key.equals(mode);
        view.setTextColor((active || focused) ? ICE : MUTED);
        view.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        view.setBackground((active || focused) ? roundRect(Color.argb(active ? 70 : 45, 210, 228, 255), Color.argb(80,255,255,255), 1, 16) : null);
        view.setElevation((active || focused) ? dp(10) : 0);
    }

    private void updateClock() {
        if (clock == null) return;
        clock.setText(new SimpleDateFormat("h:mm a", Locale.US).format(new java.util.Date()));
        main.postDelayed(this::updateClock, 30_000L);
    }

    private String profileInitial() {
        String p = profiles == null ? "D" : profiles.activeProfile();
        return p == null || p.isEmpty() ? "D" : p.substring(0, 1).toUpperCase(Locale.US);
    }

    private void hideKeyboard() {
        if (searchInput == null) return;
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
        overridePendingTransition(0, 0);
    }

    private static List<MediaCard> filter(List<MediaCard> all, boolean series) {
        ArrayList<MediaCard> out = new ArrayList<>();
        for (MediaCard c : all) if (c.series == series) out.add(c);
        return out;
    }

    private String fallbackDescription(MediaCard media) {
        String type = media.series ? "TV Series" : "Movie";
        String year = extractYear(media.subtitle);
        return type + (year.isEmpty() ? "" : " • " + year) + (media.genre == null || media.genre.isEmpty() ? "" : " • " + media.genre);
    }

    private static String extractYear(String value) {
        if (value == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(value);
        return m.find() ? m.group() : "";
    }

    private GradientDrawable cardBox(boolean focused) {
        return roundRect(focused ? Color.argb(80,210,228,255) : Color.argb(70,16,24,38), focused ? Color.WHITE : Color.argb(60,255,255,255), focused ? 2 : 1, 16);
    }

    private GradientDrawable pill(boolean focused) {
        return roundRect(Color.argb(focused ? 190 : 155,16,24,38), Color.argb(focused ? 145 : 55,255,255,255), focused ? 2 : 1, 28);
    }

    private GradientDrawable primaryAction(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? Color.rgb(225,235,250) : Color.WHITE);
        g.setCornerRadius(dp(28));
        g.setStroke(dp(focused ? 3 : 1), focused ? BLUE : Color.WHITE);
        return g;
    }

    private GradientDrawable circle(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.argb(190, 28, 37, 51));
        g.setStroke(dp(focused ? 2 : 1), focused ? ICE : Color.argb(90,255,255,255));
        return g;
    }

    private GradientDrawable roundRect(int fill, int stroke, int strokeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp)); g.setStroke(dp(strokeDp), stroke);
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

    private FrameLayout.LayoutParams frame(int w, int h, int gravity, int x, int y) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        if ((gravity & Gravity.RIGHT) == Gravity.RIGHT || (gravity & Gravity.END) == Gravity.END) p.rightMargin = x; else p.leftMargin = x;
        if ((gravity & Gravity.BOTTOM) == Gravity.BOTTOM) p.bottomMargin = y; else p.topMargin = y;
        return p;
    }

    private int dp(int value) { return TvUi.dp(this, value); }
    private boolean dead() { return isFinishing() || isDestroyed(); }
    private static String msg(Throwable t) {
        Throwable x = t; while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }
}
