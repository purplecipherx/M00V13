package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fast TV shell for Home, Movies and TV.
 *
 * Performance rules:
 *  - horizontal rails are RecyclerViews, so off-screen poster views do not exist;
 *  - one recycled-view pool is shared by every rail;
 *  - artwork is memory + disk cached and only requested for bound/visible cells;
 *  - focus continuity is kept in RAM while navigating and persisted only when leaving;
 *  - metadata descriptions are debounced so fast D-pad movement never floods the network.
 */
public final class MediaHubActivity extends Activity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_HOME = "home", MODE_MOVIES = "movies", MODE_TV = "tv";

    private static final String[] GENRES = {
        "Action", "Comedy", "Drama", "Thriller", "Sci-Fi", "Horror", "Animation", "Documentary"
    };
    private static final int BG = Color.rgb(4, 3, 12);
    private static final int PANEL = Color.rgb(13, 8, 27);
    private static final int PURPLE = Color.rgb(180, 78, 255);
    private static final int BLUE = Color.rgb(44, 157, 255);
    private static final int WHITE = Color.rgb(247, 245, 250);
    private static final int MUTED = Color.rgb(190, 181, 202);

    private final ExecutorService artPool = Executors.newFixedThreadPool(2);
    private final ExecutorService dataPool = Executors.newFixedThreadPool(3);
    private final Map<String, List<MediaCard>> genreData = new ConcurrentHashMap<>();
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();
    private final Map<String, RailAdapter> railAdapters = new HashMap<>();
    private final RecyclerView.RecycledViewPool posterPool = new RecyclerView.RecycledViewPool();

    private ProfileStore profiles;
    private CatalogStore catalog;
    private DiscoveryStore discovery;
    private ArtworkLoader artwork;
    private String mode;
    private ImageView previewArt;
    private TextView previewTitle;
    private TextView previewMeta;
    private TextView previewDescription;
    private int previewToken;
    private boolean genresLoading;
    private String focusSection;
    private String focusMediaId;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        profiles = new ProfileStore(this);
        catalog = new CatalogStore(this);
        discovery = new DiscoveryStore(this);
        artwork = new ArtworkLoader(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (!MODE_MOVIES.equals(mode) && !MODE_TV.equals(mode)) mode = MODE_HOME;
        loadFocusMemory();
        posterPool.setMaxRecycledViews(0, 18);
        render();

        if (discovery.stale()) {
            dataPool.submit(() -> {
                try {
                    discovery.refresh();
                    runOnUiThread(() -> { if (!dead()) render(); });
                } catch (Exception e) {
                    DebugLog.append(this, "HUB", "Discovery " + msg(e));
                }
            });
        }
        if (!MODE_HOME.equals(mode)) loadGenres();
    }

    @Override protected void onPause() {
        persistFocusMemory();
        super.onPause();
    }

    @Override protected void onDestroy() {
        artPool.shutdownNow();
        dataPool.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int topH = Math.max(dp(58), (int) (height * .075f));

        railAdapters.clear();
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(topbar(), new FrameLayout.LayoutParams(-1, topH));

        int previewW = width < 1000 ? 0 : Math.max(dp(245), (int) (width * .20f));
        if (previewW > 0) {
            View pane = previewPane(previewW, height - topH);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(previewW, height - topH);
            p.topMargin = topH;
            root.addView(pane, p);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setSmoothScrollingEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(12), dp(22), dp(40));
        content.setBackgroundColor(BG);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(width - previewW, height - topH);
        cp.leftMargin = previewW;
        cp.topMargin = topH;
        root.addView(scroll, cp);

        List<Section> sections = sections();
        if (sections.isEmpty()) content.addView(text("Loading catalog…", 22, true));
        for (Section section : sections) addSection(content, section, height);

        setContentView(root);
        restoreFocus();
    }

    private LinearLayout topbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(10), 0);
        bar.setBackgroundColor(Color.rgb(5, 4, 16));
        TextView brand = text("M00V13", 22, true);
        brand.setTextColor(PURPLE);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -1, 1f));
        top(bar, "Home", MODE_HOME.equals(mode), () -> switchMode(MODE_HOME));
        top(bar, "Movies", MODE_MOVIES.equals(mode), () -> switchMode(MODE_MOVIES));
        top(bar, "TV Shows", MODE_TV.equals(mode), () -> switchMode(MODE_TV));
        top(bar, "⌕ Search", false, () -> open(SearchActivity.class));
        top(bar, "♡ My Lists", false, () -> open(ProfileActivity.class));
        top(bar, "↗ Real Debrid", false, () -> open(DebridActivity.class));
        top(bar, "◉ Providers", false, () -> open(ProviderSettingsActivity.class));
        top(bar, "⚙ Settings", false, () -> open(SettingsActivity.class));
        top(bar, "₿ Support", false, () -> open(DonateActivity.class));
        return bar;
    }

    private void top(LinearLayout parent, String label, boolean active, Runnable action) {
        TextView view = text(label, 13, active);
        view.setGravity(Gravity.CENTER);
        view.setFocusable(true);
        view.setClickable(true);
        view.setPadding(dp(9), 0, dp(9), 0);
        view.setStateListAnimator(null);
        view.setTextColor(active ? BLUE : Color.rgb(220, 198, 239));
        view.setBackground(active ? outline(true, 7) : null);
        view.setOnFocusChangeListener((v, focused) -> {
            view.setTextColor((focused || active) ? BLUE : Color.rgb(220, 198, 239));
            view.setBackground((focused || active) ? outline(true, 7) : null);
        });
        view.setOnClickListener(v -> action.run());
        parent.addView(view, new LinearLayout.LayoutParams(-2, -1));
    }

    private View previewPane(int width, int height) {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(14), dp(14), dp(14), dp(18));
        pane.setBackgroundColor(Color.rgb(3, 3, 11));
        previewArt = new ImageView(this);
        previewArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewArt.setBackgroundColor(PANEL);
        pane.addView(previewArt, new LinearLayout.LayoutParams(-1,
            Math.min((int) (height * .54f), (int) (width * 1.48f))));
        previewTitle = text("Select a title", 22, true);
        previewTitle.setPadding(0, dp(12), 0, dp(4));
        pane.addView(previewTitle);
        previewMeta = text("", 13, false);
        previewMeta.setTextColor(PURPLE);
        pane.addView(previewMeta);
        previewDescription = text("Move across a poster to preview it here.", 14, false);
        previewDescription.setTextColor(MUTED);
        previewDescription.setMaxLines(7);
        previewDescription.setEllipsize(TextUtils.TruncateAt.END);
        previewDescription.setPadding(0, dp(9), 0, 0);
        pane.addView(previewDescription, new LinearLayout.LayoutParams(-1, 0, 1f));
        return pane;
    }

    private List<Section> sections() {
        ArrayList<Section> out = new ArrayList<>();
        List<MediaCard> all = catalog.all();
        if (MODE_HOME.equals(mode)) {
            ArrayList<MediaCard> continueWatching = new ArrayList<>();
            for (MediaCard card : all) {
                long p = profiles.progressMs(card.id), d = profiles.durationMs(card.id);
                if (!profiles.isWatched(card.id) && p > 0 && d > 0) continueWatching.add(card);
            }
            continueWatching.sort((a, b) -> Long.compare(profiles.lastUpdatedMs(b.id), profiles.lastUpdatedMs(a.id)));
            if (!continueWatching.isEmpty()) out.add(new Section("Continue Watching", "continue", continueWatching));

            List<MediaCard> movies = nonEmpty(discovery.get(DiscoveryStore.POPULAR_MOVIES), filter(all, false));
            List<MediaCard> tv = nonEmpty(discovery.get(DiscoveryStore.POPULAR_TV), filter(all, true));
            out.add(new Section("Trending Movies", "trending_movies", movies));
            out.add(new Section("Trending TV Shows", "trending_tv", tv));
            out.add(new Section("New Movies", "new_movies", newest(movies)));
            out.add(new Section("New TV Shows", "new_tv", newest(tv)));
        } else {
            boolean series = MODE_TV.equals(mode);
            List<MediaCard> base = filter(all, series);
            out.add(new Section(series ? "Newest TV Shows" : "Newest Movies", "newest", newest(base)));
            for (String genre : GENRES) {
                List<MediaCard> list = genreData.get(genre);
                if (list == null || list.isEmpty()) list = genreFilter(base, genre);
                if (!list.isEmpty()) out.add(new Section(genre,
                    genre.toLowerCase(Locale.US).replace('-', '_'), newest(list)));
            }
        }
        return out;
    }

    private void addSection(LinearLayout root, Section section, int screenHeight) {
        if (section.items.isEmpty()) return;
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(section.title, 20, true), new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView seeAll = text("See All  ›", 13, true);
        seeAll.setTextColor(PURPLE);
        seeAll.setGravity(Gravity.CENTER);
        seeAll.setFocusable(true);
        seeAll.setClickable(true);
        seeAll.setPadding(dp(10), 0, dp(10), 0);
        seeAll.setOnFocusChangeListener((v, focused) -> {
            seeAll.setTextColor(focused ? BLUE : PURPLE);
            seeAll.setBackground(focused ? outline(true, 7) : null);
        });
        seeAll.setOnClickListener(v -> openGrid(section));
        header.addView(seeAll, new LinearLayout.LayoutParams(dp(110), dp(38)));
        root.addView(header);

        int cardH = Math.max(dp(190), Math.min(dp(270), (int) (screenHeight * .31f)));
        int cardW = (int) (cardH * .67f);
        RecyclerView rail = new RecyclerView(this);
        rail.setHasFixedSize(true);
        rail.setItemAnimator(null);
        rail.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rail.setHorizontalScrollBarEnabled(false);
        rail.setItemViewCacheSize(4);
        rail.setRecycledViewPool(posterPool);
        LinearLayoutManager manager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        manager.setInitialPrefetchItemCount(2);
        rail.setLayoutManager(manager);
        RailAdapter adapter = new RailAdapter(section, cardW, cardH);
        rail.setAdapter(adapter);
        railAdapters.put(section.key, adapter);
        root.addView(rail, new LinearLayout.LayoutParams(-1, cardH));
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(15)));
    }

    private final class RailAdapter extends RecyclerView.Adapter<PosterHolder> {
        private final Section section;
        private final int cardW;
        private final int cardH;
        private RecyclerView recycler;

        RailAdapter(Section section, int cardW, int cardH) {
            this.section = section;
            this.cardW = cardW;
            this.cardH = cardH;
            setHasStableIds(true);
        }

        @Override public long getItemId(int position) {
            return section.items.get(position).id.hashCode();
        }

        @Override public PosterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(MediaHubActivity.this);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(cardW, cardH);
            lp.rightMargin = dp(10);
            card.setLayoutParams(lp);
            card.setFocusable(true);
            card.setClickable(true);
            card.setStateListAnimator(null);
            card.setPadding(dp(3), dp(3), dp(3), dp(3));
            card.setBackground(outline(false, 6));

            ImageView image = new ImageView(MediaHubActivity.this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(PANEL);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1));

            TextView name = text("", 12, true);
            name.setGravity(Gravity.BOTTOM);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(7), 0, dp(6), dp(7));
            name.setBackgroundColor(Color.argb(95, 0, 0, 0));
            card.addView(name, new FrameLayout.LayoutParams(-1, (int) (cardH * .25f), Gravity.BOTTOM));
            return new PosterHolder(card, image, name);
        }

        @Override public void onBindViewHolder(PosterHolder holder, int position) {
            MediaCard media = section.items.get(position);
            holder.name.setText(media.title);
            holder.name.setTextColor(WHITE);
            holder.card.setBackground(outline(false, 6));
            holder.card.setTag(media.id);
            artwork.load(holder.image, media.artworkUrl,
                Math.min(cardW * 2, getResources().getDisplayMetrics().widthPixels), artPool);

            holder.card.setOnFocusChangeListener((v, focused) -> {
                holder.card.setBackground(outline(focused, 6));
                holder.name.setTextColor(focused ? BLUE : WHITE);
                if (focused) {
                    rememberInMemory(section.key, media.id);
                    showPreview(media);
                    if (new AppSettingsStore(MediaHubActivity.this).clickSounds())
                        holder.card.playSoundEffect(SoundEffectConstants.CLICK);
                }
            });
            holder.card.setOnClickListener(v -> openMedia(media));
            holder.card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN || section.items.size() < 2) return false;
                int current = holder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && current == 0) {
                    focusPosition(section.items.size() - 1);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && current == section.items.size() - 1) {
                    focusPosition(0);
                    return true;
                }
                return false;
            });
        }

        @Override public int getItemCount() { return section.items.size(); }

        @Override public void onAttachedToRecyclerView(RecyclerView recyclerView) {
            recycler = recyclerView;
        }

        void focusId(String id) {
            if (id == null || recycler == null) return;
            for (int i = 0; i < section.items.size(); i++) {
                if (id.equals(section.items.get(i).id)) {
                    focusPosition(i);
                    return;
                }
            }
        }

        void focusPosition(int position) {
            if (recycler == null || position < 0 || position >= section.items.size()) return;
            recycler.scrollToPosition(position);
            recycler.post(() -> {
                RecyclerView.ViewHolder vh = recycler.findViewHolderForAdapterPosition(position);
                if (vh != null) vh.itemView.requestFocus();
            });
        }
    }

    private static final class PosterHolder extends RecyclerView.ViewHolder {
        final FrameLayout card;
        final ImageView image;
        final TextView name;
        PosterHolder(FrameLayout card, ImageView image, TextView name) {
            super(card);
            this.card = card;
            this.image = image;
            this.name = name;
        }
    }

    private void showPreview(MediaCard media) {
        if (previewTitle == null) return;
        previewTitle.setText(media.title);
        previewMeta.setText(detail(media));
        previewDescription.setText(detail(media));
        artwork.load(previewArt, media.artworkUrl, 700, artPool);

        int token = ++previewToken;
        String cached = descriptions.get(media.id);
        if (cached != null) {
            previewDescription.setText(cached);
            return;
        }
        previewDescription.postDelayed(() -> {
            if (dead() || token != previewToken) return;
            dataPool.submit(() -> {
                String description;
                try { description = new CinemetaClient().description(media); }
                catch (Exception e) { description = ""; }
                if (description == null || description.trim().isEmpty()) description = detail(media);
                descriptions.put(media.id, description);
                final String result = description;
                runOnUiThread(() -> {
                    if (!dead() && token == previewToken) previewDescription.setText(result);
                });
            });
        }, 350);
    }

    private void openGrid(Section section) {
        ArrayList<String> ids = new ArrayList<>();
        for (MediaCard card : section.items) ids.add(card.id);
        Intent intent = new Intent(this, CatalogGridActivity.class);
        intent.putExtra(CatalogGridActivity.EXTRA_TITLE, section.title);
        intent.putStringArrayListExtra(CatalogGridActivity.EXTRA_IDS, ids);
        intent.putExtra(CatalogGridActivity.EXTRA_FOCUS_KEY, mode + ":" + section.key);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openMedia(MediaCard media) {
        Intent intent = new Intent(this, MediaOpenActivity.class);
        intent.putExtra(MediaOpenActivity.EXTRA_MEDIA_ID, media.id);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void switchMode(String next) {
        if (next.equals(mode)) return;
        persistFocusMemory();
        Intent intent = new Intent(this, MediaHubActivity.class);
        intent.putExtra(EXTRA_MODE, next);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    private void open(Class<?> activity) {
        startActivity(new Intent(this, activity));
        overridePendingTransition(0, 0);
    }

    private void loadFocusMemory() {
        SharedPreferences prefs = getSharedPreferences("m00v13_hub_focus", MODE_PRIVATE);
        focusSection = prefs.getString("section." + mode, null);
        focusMediaId = prefs.getString("id." + mode, null);
    }

    private void rememberInMemory(String section, String id) {
        focusSection = section;
        focusMediaId = id;
    }

    private void persistFocusMemory() {
        if (focusSection == null || focusMediaId == null) return;
        getSharedPreferences("m00v13_hub_focus", MODE_PRIVATE).edit()
            .putString("section." + mode, focusSection)
            .putString("id." + mode, focusMediaId)
            .apply();
    }

    private void restoreFocus() {
        if (focusSection == null || focusMediaId == null) return;
        RailAdapter adapter = railAdapters.get(focusSection);
        if (adapter != null) {
            String id = focusMediaId;
            previewDescription.postDelayed(() -> adapter.focusId(id), 40);
        }
    }

    private void loadGenres() {
        if (genresLoading) return;
        genresLoading = true;
        AtomicInteger left = new AtomicInteger(GENRES.length);
        for (String genre : GENRES) {
            dataPool.submit(() -> {
                try {
                    List<MediaCard> got = new CinemetaClient().genre(MODE_TV.equals(mode) ? "series" : "movie", genre);
                    genreData.put(genre, got);
                    catalog.upsertAll(got);
                } catch (Exception e) {
                    DebugLog.append(this, "HUB", "Genre " + genre + " " + msg(e));
                } finally {
                    if (left.decrementAndGet() == 0) {
                        runOnUiThread(() -> {
                            genresLoading = false;
                            if (!dead()) render();
                        });
                    }
                }
            });
        }
    }

    private static List<MediaCard> filter(List<MediaCard> all, boolean series) {
        ArrayList<MediaCard> out = new ArrayList<>();
        for (MediaCard card : all) if (card.series == series) out.add(card);
        return out;
    }

    private static List<MediaCard> genreFilter(List<MediaCard> all, String genre) {
        ArrayList<MediaCard> out = new ArrayList<>();
        for (MediaCard card : all) {
            if (card.genre.equalsIgnoreCase(genre)) {
                out.add(card);
                continue;
            }
            for (String tag : card.tags) {
                if (tag.equalsIgnoreCase(genre)) {
                    out.add(card);
                    break;
                }
            }
        }
        return out;
    }

    private static List<MediaCard> newest(List<MediaCard> in) {
        ArrayList<MediaCard> out = new ArrayList<>(in);
        out.sort((a, b) -> Integer.compare(year(b), year(a)));
        return out;
    }

    private static int year(MediaCard card) {
        if (card.subtitle == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(card.subtitle);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private static List<MediaCard> nonEmpty(List<MediaCard> a, List<MediaCard> b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private String detail(MediaCard media) {
        return (media.series ? "TV Series" : "Movie")
            + (media.subtitle == null || media.subtitle.isEmpty() ? "" : " • " + media.subtitle)
            + (media.genre.isEmpty() ? "" : " • " + media.genre);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextColor(WHITE);
        view.setTextSize(sp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable outline(boolean focused, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(PANEL);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(focused ? 3 : 1), focused ? BLUE : Color.rgb(42, 34, 53));
        return drawable;
    }

    private int dp(int value) { return TvUi.dp(this, value); }
    private boolean dead() { return isFinishing() || isDestroyed(); }
    private static String msg(Throwable throwable) {
        Throwable x = throwable;
        while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }

    private static final class Section {
        final String title;
        final String key;
        final List<MediaCard> items;
        Section(String title, String key, List<MediaCard> items) {
            this.title = title;
            this.key = key;
            this.items = items == null ? Collections.emptyList() : items;
        }
    }
}
