package com.m00v13.tv

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.SoundEffectConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Native Kotlin UI matching the approved M00V13 TV concept. */
class MainActivity : Activity() {
    private lateinit var profiles: ProfileStore
    private lateinit var catalog: CatalogStore
    private lateinit var discovery: DiscoveryStore
    private lateinit var screen: ScreenProfile

    private val artPool = Executors.newFixedThreadPool(3)
    private val discoveryPool = Executors.newSingleThreadExecutor()

    private var heroTitle: TextView? = null
    private var heroMeta: TextView? = null
    private var heroArt: ImageView? = null
    private var heroToken = 0

    private val bg = Color.rgb(4, 3, 12)
    private val panel = Color.rgb(12, 7, 25)
    private val purple = Color.rgb(155, 66, 255)
    private val blue = Color.rgb(35, 139, 255)
    private val white = Color.rgb(246, 244, 249)
    private val muted = Color.rgb(194, 178, 215)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        TvUi.disableWindowAnimations(this)
        window.decorView.setBackgroundColor(bg)
        profiles = ProfileStore(this)
        catalog = CatalogStore(this)
        discovery = DiscoveryStore(this)
        screen = ScreenProfile.detect(this)
        DebugLog.boot(this)
        setContentView(buildHome())
        refreshDiscovery()
    }

    override fun onResume() {
        super.onResume()
        if (::catalog.isInitialized) {
            screen = ScreenProfile.detect(this)
            setContentView(buildHome())
        }
    }

    override fun onDestroy() {
        artPool.shutdownNow()
        discoveryPool.shutdownNow()
        super.onDestroy()
    }

    private fun refreshDiscovery() {
        if (!discovery.stale()) return
        discoveryPool.submit {
            try {
                discovery.refresh()
                runOnUiThread { if (!isFinishing && !isDestroyed) setContentView(buildHome()) }
            } catch (e: Exception) {
                DebugLog.append(this, "DISCOVERY", "Refresh failed: ${e.message}")
            }
        }
    }

    private fun buildHome(): View {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        return if (screen.mobile()) buildMobile(w, h) else buildTv(w, h)
    }

    private fun buildTv(w: Int, h: Int): View {
        val root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val railW = (w * .158f).toInt().coerceIn(dp(230), dp(315))
        val topH = (h * .077f).toInt().coerceAtLeast(dp(68))
        val contentW = w - railW

        root.addView(sideRail(), FrameLayout.LayoutParams(railW, h))

        val body = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.addView(body, FrameLayout.LayoutParams(contentW, h).apply { leftMargin = railW })
        body.addView(topBar(), FrameLayout.LayoutParams(contentW, topH))

        val popularMovies = discovery.get(DiscoveryStore.POPULAR_MOVIES)
        val popularTv = discovery.get(DiscoveryStore.POPULAR_TV)
        val all = catalog.all()
        val continuing = continueWatching(all)
        val fallback = RecommendationEngine().rankOverall(all, profiles, 18)
        val featuredItem = continuing.firstOrNull() ?: popularMovies.firstOrNull() ?: fallback.firstOrNull() ?: mostRecent(all)

        val heroH = (h * .36f).toInt()
        val trendW = (contentW * .205f).toInt().coerceAtLeast(dp(245))
        val heroW = contentW - trendW

        val heroView = hero(featuredItem)
        body.addView(heroView, FrameLayout.LayoutParams(heroW, heroH).apply { topMargin = topH })
        body.addView(trendingStack((popularTv + popularMovies).distinctBy { it.id }.take(3)), FrameLayout.LayoutParams(trendW, heroH).apply {
            leftMargin = heroW
            topMargin = topH
        })

        val lower = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(5), dp(22), dp(8))
            setBackgroundColor(bg)
        }
        body.addView(lower, FrameLayout.LayoutParams(contentW, h - topH - heroH).apply { topMargin = topH + heroH })

        val continueItems = if (continuing.isNotEmpty()) continuing else (popularTv + popularMovies).take(10)
        addLandscapeRail(lower, "Continue Watching", continueItems, (h * .185f).toInt())
        val movieItems = if (popularMovies.isNotEmpty()) popularMovies else fallback.filter { !it.series }
        addPosterRail(lower, "Trending Movies", movieItems, (h * .275f).toInt())
        return root
    }

    private fun sideRail(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(28), dp(22), dp(19), dp(22))
        setBackgroundColor(Color.rgb(3, 3, 11))

        addView(text("M00V13", 31f, purple, true).apply { letterSpacing = .05f }, LinearLayout.LayoutParams(-1, dp(48)))
        addView(text("STREAM BEYOND LIMITS", 8.5f, Color.rgb(194, 128, 255)).apply { letterSpacing = .28f }, LinearLayout.LayoutParams(-1, dp(35)))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(16)))

        nav(this, "⌂", "Home", true) { }
        nav(this, "▦", "Movies") { browse(BrowseActivity.KIND_MOVIES) }
        nav(this, "▣", "TV Shows") { browse(BrowseActivity.KIND_TV) }
        nav(this, "⌕", "Search") { open(SearchActivity::class.java) }
        nav(this, "♡", "My Lists") { open(ProfileActivity::class.java) }
        nav(this, "↗", "Real Debrid") { open(DebridActivity::class.java) }
        nav(this, "◉", "Providers") { open(ProviderSettingsActivity::class.java) }
        nav(this, "⚙", "Settings") { open(SettingsActivity::class.java) }
        nav(this, "♬", "Support") { open(DonateActivity::class.java) }

        addView(View(this@MainActivity), LinearLayout.LayoutParams(1, 0, 1f))
        addView(text("YOUR\nCONTENT.\nYOUR WAY.", 13f, Color.rgb(178, 102, 255)).apply {
            letterSpacing = .22f
            setLineSpacing(0f, 1.22f)
        }, LinearLayout.LayoutParams(-1, dp(105)))
    }

    private fun nav(parent: LinearLayout, icon: String, label: String, selected: Boolean = false, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            stateListAnimator = null
            setPadding(dp(12), 0, dp(10), 0)
            background = navBg(selected)
        }
        val i = text(icon, 23f, if (selected) blue else Color.rgb(178, 100, 255)).apply { gravity = Gravity.CENTER }
        val t = text(label, 16f, if (selected) Color.rgb(130, 188, 255) else Color.rgb(210, 174, 255), true).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(i, LinearLayout.LayoutParams(dp(38), -1))
        row.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
        row.setOnFocusChangeListener { _, f ->
            row.background = navBg(f || selected)
            i.setTextColor(if (f || selected) blue else Color.rgb(178, 100, 255))
            t.setTextColor(if (f || selected) Color.rgb(130, 188, 255) else Color.rgb(210, 174, 255))
            if (f && AppSettingsStore(this).clickSounds()) row.playSoundEffect(SoundEffectConstants.CLICK)
        }
        row.setOnClickListener { action() }
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(57)).apply { bottomMargin = dp(7) })
    }

    private fun topBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        setPadding(dp(16), 0, dp(28), 0)
        setBackgroundColor(Color.rgb(5, 4, 16))
        addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        topAction(this, "⌕  Search") { open(SearchActivity::class.java) }
        topAction(this, if (DebridStore(this@MainActivity).isConnected()) "↗  Real Debrid   ● Connected" else "↗  Real Debrid") { open(DebridActivity::class.java) }
        topAction(this, "⚙  Settings") { open(SettingsActivity::class.java) }
        topAction(this, "♬  Support") { open(DonateActivity::class.java) }
        addView(text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()), 17f, Color.rgb(208, 171, 255)).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(115), dp(50)))
    }

    private fun topAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val v = text(label, 14f, Color.rgb(203, 165, 248)).apply {
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            stateListAnimator = null
            setPadding(dp(12), 0, dp(12), 0)
            background = box(Color.TRANSPARENT, Color.TRANSPARENT, 0, 7)
            setOnFocusChangeListener { _, f ->
                setTextColor(if (f) blue else Color.rgb(203, 165, 248))
                background = box(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) 2 else 0, 7)
            }
            setOnClickListener { action() }
        }
        parent.addView(v, LinearLayout.LayoutParams(-2, dp(48)))
    }

    private fun hero(item: MediaCard?): View {
        val root = FrameLayout(this).apply { setBackgroundColor(panel) }
        val art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(panel)
        }
        heroArt = art
        root.addView(art, FrameLayout.LayoutParams(-1, -1))
        if (item != null) loadHero(item)
        root.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.argb(245, 2, 2, 9), Color.argb(188, 4, 3, 12), Color.argb(25, 4, 3, 12))) }, FrameLayout.LayoutParams(-1, -1))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(46), dp(22), dp(18), dp(32))
        }
        root.addView(info, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * .47f).toInt(), -1))
        info.addView(text("FEATURED", 11f, Color.rgb(214, 169, 255)).apply { letterSpacing = .28f }, LinearLayout.LayoutParams(-1, dp(28)))

        heroTitle = text(item?.title ?: "M00V13", 34f, white, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        info.addView(heroTitle, LinearLayout.LayoutParams(-1, -2))
        heroMeta = text(item?.let { heroDetail(it) } ?: "Search • stream • download", 15f, Color.rgb(232, 224, 242)).apply { setPadding(0, dp(7), 0, dp(10)); maxLines = 2 }
        info.addView(heroMeta, LinearLayout.LayoutParams(-1, -2))
        info.addView(text(item?.let { chipLine(it) } ?: "4K   HDR   Dolby Vision   Atmos", 12f, Color.rgb(226, 217, 236)).apply { setPadding(0, 0, 0, dp(13)) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val play = actionButton("▶  Play", true).apply { setOnClickListener { if (item == null) open(SearchActivity::class.java) else openMedia(item) } }
        actions.addView(play, LinearLayout.LayoutParams(dp(220), dp(54)))
        val more = actionButton("ⓘ  More Info", false).apply { setOnClickListener { if (item != null) openMedia(item) } }
        actions.addView(more, LinearLayout.LayoutParams(dp(180), dp(54)).apply { leftMargin = dp(12) })
        val list = actionButton("＋  My List", false).apply { setOnClickListener { if (item != null) profiles.setWatchlist(item.id, !profiles.isInWatchlist(item.id)) } }
        actions.addView(list, LinearLayout.LayoutParams(dp(165), dp(54)).apply { leftMargin = dp(12) })
        info.addView(actions)
        return root
    }

    private fun trendingStack(items: List<MediaCard>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(9), dp(17), dp(10))
        setBackgroundColor(Color.rgb(7, 5, 17))
        addView(text("Trending Now", 15f, white, true), LinearLayout.LayoutParams(-1, dp(30)))
        items.take(3).forEach { item ->
            val card = FrameLayout(this@MainActivity).apply {
                isFocusable = true; isClickable = true; stateListAnimator = null
                background = box(panel, Color.TRANSPARENT, 0, 5)
            }
            val art = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel) }
            card.addView(art, FrameLayout.LayoutParams(-1, -1)); loadArt(art, item.artworkUrl, dp(520))
            card.addView(text(item.title, 15f, white, true).apply { gravity = Gravity.BOTTOM; setPadding(dp(11), 0, dp(8), dp(9)); maxLines = 2 }, FrameLayout.LayoutParams(-1, -1))
            card.setOnFocusChangeListener { _, f -> card.background = box(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) 3 else 0, 5); if (f) showHero(item) }
            card.setOnClickListener { openMedia(item) }
            addView(card, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(7) })
        }
    }

    private fun addLandscapeRail(parent: LinearLayout, title: String, items: List<MediaCard>, height: Int) {
        parent.addView(sectionTitle(title, null), LinearLayout.LayoutParams(-1, dp(31)))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val cardW = (screen.widthPx * .15f).toInt().coerceIn(dp(225), dp(315))
        val cardH = max(dp(105), height - dp(34))
        items.take(12).forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true; stateListAnimator = null
                setPadding(dp(3), dp(3), dp(3), dp(3)); background = box(Color.TRANSPARENT, Color.TRANSPARENT, 0, 5)
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel) }
            card.addView(image, LinearLayout.LayoutParams(cardW - dp(6), (cardH * .68f).toInt())); loadArt(image, item.artworkUrl, cardW * 2)
            val name = text(item.title, 13f, white, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
            card.addView(name, LinearLayout.LayoutParams(cardW - dp(6), dp(23)))
            card.addView(text(progressLine(item), 11f, muted), LinearLayout.LayoutParams(cardW - dp(6), dp(19)))
            card.setOnFocusChangeListener { _, f ->
                card.background = box(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) 3 else 0, 5)
                name.setTextColor(if (f) blue else white); if (f) showHero(item)
                if (f && AppSettingsStore(this).clickSounds()) card.playSoundEffect(SoundEffectConstants.CLICK)
            }
            card.setOnClickListener { openMedia(item) }
            row.addView(card, LinearLayout.LayoutParams(cardW, cardH).apply { rightMargin = dp(11) })
        }
        scroll.addView(row); parent.addView(scroll, LinearLayout.LayoutParams(-1, cardH))
    }

    private fun addPosterRail(parent: LinearLayout, title: String, items: List<MediaCard>, height: Int) {
        parent.addView(sectionTitle(title, "See All  ›"), LinearLayout.LayoutParams(-1, dp(32)))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; clipChildren = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        val cardW = (screen.widthPx * .108f).toInt().coerceIn(dp(158), dp(225))
        val cardH = height - dp(35)
        items.take(14).forEach { item ->
            val card = FrameLayout(this).apply {
                isFocusable = true; isClickable = true; stateListAnimator = null
                setPadding(dp(3), dp(3), dp(3), dp(3)); background = box(Color.TRANSPARENT, Color.TRANSPARENT, 0, 5)
            }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(panel) }
            card.addView(image, FrameLayout.LayoutParams(-1, -1)); loadArt(image, item.artworkUrl, cardW * 2)
            val name = text(item.title, 12f, white, true).apply { gravity = Gravity.BOTTOM; setPadding(dp(9), 0, dp(7), dp(8)); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setBackgroundColor(Color.argb(90, 0, 0, 0)) }
            card.addView(name, FrameLayout.LayoutParams(-1, dp(52), Gravity.BOTTOM))
            card.setOnFocusChangeListener { _, f ->
                card.background = box(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) 4 else 0, 5)
                name.setTextColor(if (f) Color.rgb(119, 187, 255) else white); if (f) showHero(item)
                if (f && AppSettingsStore(this).clickSounds()) card.playSoundEffect(SoundEffectConstants.CLICK)
            }
            card.setOnClickListener { openMedia(item) }
            card.setOnLongClickListener { profiles.setWatchlist(item.id, !profiles.isInWatchlist(item.id)); true }
            row.addView(card, LinearLayout.LayoutParams(cardW, cardH).apply { rightMargin = dp(11) })
        }
        scroll.addView(row); parent.addView(scroll, LinearLayout.LayoutParams(-1, cardH))
    }

    private fun buildMobile(w: Int, h: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(12), dp(15), dp(18)); setBackgroundColor(bg)
        val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("M00V13", 25f, purple, true), LinearLayout.LayoutParams(0, dp(48), 1f))
        topAction(top, "⌕") { open(SearchActivity::class.java) }; topAction(top, "⚙") { open(SettingsActivity::class.java) }
        addView(top)
        val movies = discovery.get(DiscoveryStore.POPULAR_MOVIES)
        addView(hero(movies.firstOrNull() ?: catalog.all().firstOrNull()), LinearLayout.LayoutParams(-1, (h * .35f).toInt()))
        addPosterRail(this, "Trending Movies", movies, (h * .43f).toInt())
    }

    private fun sectionTitle(left: String, right: String?): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(text(left, 18f, white, true), LinearLayout.LayoutParams(0, -1, 1f))
        if (right != null) addView(text(right, 12f, Color.rgb(190, 113, 255)).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END }, LinearLayout.LayoutParams(dp(100), -1))
    }

    private fun actionButton(label: String, primary: Boolean): TextView = text(label, 16f, white, true).apply {
        gravity = Gravity.CENTER; isFocusable = true; isClickable = true; stateListAnimator = null
        background = if (primary) box(Color.rgb(101, 45, 239), blue, 2, 6) else box(Color.rgb(20, 11, 42), Color.rgb(106, 57, 157), 1, 6)
        setOnFocusChangeListener { _, f ->
            background = if (f) box(if (primary) Color.rgb(92, 42, 229) else Color.rgb(25, 14, 49), blue, 3, 6) else if (primary) box(Color.rgb(101, 45, 239), Color.rgb(90, 80, 180), 1, 6) else box(Color.rgb(20, 11, 42), Color.rgb(106, 57, 157), 1, 6)
            setTextColor(if (f) Color.rgb(146, 201, 255) else white)
        }
    }

    private fun showHero(item: MediaCard) {
        heroTitle?.text = item.title; heroMeta?.text = heroDetail(item)
        heroArt?.setImageDrawable(null); loadHero(item)
    }

    private fun loadHero(item: MediaCard) {
        val view = heroArt ?: return
        val url = item.artworkUrl ?: return
        if (url.isBlank()) return
        val token = ++heroToken
        artPool.submit {
            try {
                val file = ArtworkCache(this).fetch(url, 88, min(screen.widthPx, 1920)) ?: return@submit
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@submit
                runOnUiThread { if (!isFinishing && !isDestroyed && token == heroToken && heroArt === view) view.setImageBitmap(bitmap) }
            } catch (e: Exception) { DebugLog.append(this, "ART", "Hero failed: ${e.message}") }
        }
    }

    private fun loadArt(view: ImageView, url: String?, target: Int) {
        if (url.isNullOrBlank()) return
        artPool.submit {
            try {
                val file = ArtworkCache(this).fetch(url, 84, min(target, screen.widthPx)) ?: return@submit
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@submit
                runOnUiThread { if (!isFinishing && !isDestroyed) view.setImageBitmap(bitmap) }
            } catch (e: Exception) { DebugLog.append(this, "ART", "Artwork failed: ${e.message}") }
        }
    }

    private fun continueWatching(all: List<MediaCard>) = all.filter {
        val p = profiles.progressMs(it.id); val d = profiles.durationMs(it.id)
        !profiles.isWatched(it.id) && p > 0 && d > 0
    }.sortedByDescending { profiles.lastUpdatedMs(it.id) }

    private fun mostRecent(all: List<MediaCard>): MediaCard? = all.maxByOrNull { profiles.lastUpdatedMs(it.id) }

    private fun heroDetail(item: MediaCard): String {
        val type = if (item.series) "TV Series" else "Movie"
        val sub = if (!item.subtitle.isNullOrBlank()) " • ${item.subtitle}" else ""
        val genre = if (item.genre.isNotBlank()) " • ${item.genre}" else ""
        return type + sub + genre
    }

    private fun chipLine(item: MediaCard): String = listOf(item.genre.takeIf { it.isNotBlank() } ?: "Featured", "4K", "HDR", "Dolby Vision", "Atmos").joinToString("   ")

    private fun progressLine(item: MediaCard): String {
        val p = profiles.progressMs(item.id); val d = profiles.durationMs(item.id)
        if (p > 0 && d > 0) return "${min(99, (p * 100 / d).toInt())}% watched • ${max(1, ((d - p) / 60000).toInt())}m left"
        return if (item.series) "TV Series" else "Movie"
    }

    private fun browse(kind: String) = startActivity(Intent(this, BrowseActivity::class.java).putExtra(BrowseActivity.EXTRA_KIND, kind))
    private fun open(clazz: Class<*>) = startActivity(Intent(this, clazz))
    private fun openMedia(item: MediaCard) { catalog.upsert(item); startActivity(Intent(this, MediaOpenActivity::class.java).putExtra(MediaOpenActivity.EXTRA_MEDIA_ID, item.id)) }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun navBg(on: Boolean) = box(if (on) Color.rgb(20, 12, 48) else Color.TRANSPARENT, if (on) blue else Color.TRANSPARENT, if (on) 2 else 0, 7)
    private fun box(fill: Int, stroke: Int, strokeDp: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
}
