package com.m00v13.tv

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Native Kotlin 10-foot home shell.
 *
 * Design target: the approved M00V13 1920x1080 concept — black/purple chrome,
 * fixed left navigation rail, cinematic hero, right-side trending stack,
 * Continue Watching, poster rails, and electric-blue focus rings. There are no
 * positional/focus animations: selection snaps instantly for low-end TV sticks.
 */
class MainActivity : Activity() {
    private lateinit var profiles: ProfileStore
    private lateinit var catalog: CatalogStore
    private lateinit var discovery: DiscoveryStore
    private lateinit var screen: ScreenProfile

    private val artExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val discoveryExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var heroTitle: TextView? = null
    private var heroMeta: TextView? = null
    private var heroArt: ImageView? = null
    private var heroToken = 0

    private val bg = Color.rgb(4, 3, 12)
    private val panel = Color.rgb(11, 7, 23)
    private val panel2 = Color.rgb(17, 10, 34)
    private val purple = Color.rgb(156, 69, 255)
    private val blue = Color.rgb(42, 141, 255)
    private val white = Color.rgb(245, 242, 248)
    private val muted = Color.rgb(180, 169, 201)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        TvUi.disableWindowAnimations(this)
        window.decorView.setBackgroundColor(bg)
        profiles = ProfileStore(this)
        catalog = CatalogStore(this)
        discovery = DiscoveryStore(this)
        screen = ScreenProfile.detect(this)
        DebugLog.boot(this)
        DebugLog.append(this, "UI", "Kotlin cinematic home ${screen.kind} ${screen.widthPx}x${screen.heightPx}")
        setContentView(buildTvHome())
        refreshDiscovery()
    }

    override fun onResume() {
        super.onResume()
        if (::profiles.isInitialized) {
            screen = ScreenProfile.detect(this)
            setContentView(buildTvHome())
        }
    }

    override fun onDestroy() {
        artExecutor.shutdownNow()
        discoveryExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshDiscovery() {
        if (!::discovery.isInitialized || !discovery.stale()) return
        discoveryExecutor.submit {
            try {
                discovery.refresh()
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        screen = ScreenProfile.detect(this)
                        setContentView(buildTvHome())
                    }
                }
            } catch (e: Exception) {
                DebugLog.append(this, "DISCOVERY", "Refresh failed: ${e.message}")
            }
        }
    }

    private fun buildTvHome(): View {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val mobile = screen.mobile()

        // Phones still get a native responsive shell, but the TV geometry is the
        // primary visual target requested for this pass.
        return if (mobile) buildCompactHome(w, h) else buildTenFootHome(w, h)
    }

    private fun buildTenFootHome(w: Int, h: Int): View {
        val root = FrameLayout(this)
        root.setBackgroundColor(bg)

        val railW = (w * 0.158f).toInt().coerceIn(px(220), px(320))
        val topH = (h * 0.077f).toInt().coerceAtLeast(px(70))
        val contentW = w - railW

        root.addView(buildSideRail(railW, h), FrameLayout.LayoutParams(railW, h, Gravity.START))

        val body = FrameLayout(this)
        body.setBackgroundColor(bg)
        val bodyLp = FrameLayout.LayoutParams(contentW, h)
        bodyLp.leftMargin = railW
        root.addView(body, bodyLp)

        body.addView(buildTopBar(contentW, topH), FrameLayout.LayoutParams(contentW, topH, Gravity.TOP))

        val popularMovies = discovery.get(DiscoveryStore.POPULAR_MOVIES)
        val popularTv = discovery.get(DiscoveryStore.POPULAR_TV)
        val all = catalog.all()
        val continuing = continueWatching(all)
        val recommended = RecommendationEngine().rankOverall(all, profiles, 18)

        val hero = when {
            continuing.isNotEmpty() -> continuing.first()
            popularMovies.isNotEmpty() -> popularMovies.first()
            recommended.isNotEmpty() -> recommended.first()
            else -> mostRecentlyTouched(all)
        }

        val heroH = (h * 0.36f).toInt()
        val trendingW = (contentW * 0.205f).toInt().coerceAtLeast(px(245))
        val heroMainW = contentW - trendingW

        val hero = buildHero(hero)
        val heroLp = FrameLayout.LayoutParams(heroMainW, heroH)
        heroLp.topMargin = topH
        body.addView(hero, heroLp)

        val trending = buildTrendingStack((popularTv + popularMovies).distinctBy { it.id }.take(3), trendingW, heroH)
        val trendingLp = FrameLayout.LayoutParams(trendingW, heroH)
        trendingLp.leftMargin = heroMainW
        trendingLp.topMargin = topH
        body.addView(trending, trendingLp)

        val lower = LinearLayout(this)
        lower.orientation = LinearLayout.VERTICAL
        lower.setPadding(px(26), px(6), px(22), px(10))
        lower.setBackgroundColor(bg)
        val lowerLp = FrameLayout.LayoutParams(contentW, h - topH - heroH)
        lowerLp.topMargin = topH + heroH
        body.addView(lower, lowerLp)

        val continueItems = if (continuing.isNotEmpty()) continuing else (popularTv + popularMovies).take(10)
        addLandscapeRail(lower, "Continue Watching", continueItems, (h * 0.185f).toInt())

        val trendMovies = when {
            popularMovies.isNotEmpty() -> popularMovies
            else -> recommended.filter { !it.series }
        }
        addPosterRail(lower, "Trending Movies", trendMovies, (h * 0.275f).toInt())

        return root
    }

    private fun buildSideRail(width: Int, height: Int): View {
        val rail = LinearLayout(this)
        rail.orientation = LinearLayout.VERTICAL
        rail.setPadding(px(28), px(22), px(20), px(24))
        rail.setBackgroundColor(Color.rgb(3, 3, 11))

        val logo = TextView(this)
        logo.text = "M00V13"
        logo.setTextColor(purple)
        logo.textSize = 31f
        logo.typeface = android.graphics.Typeface.DEFAULT_BOLD
        logo.letterSpacing = 0.05f
        rail.addView(logo, LinearLayout.LayoutParams(-1, px(48)))

        val tag = TextView(this)
        tag.text = "STREAM BEYOND LIMITS"
        tag.setTextColor(Color.rgb(192, 126, 255))
        tag.textSize = 8.5f
        tag.letterSpacing = 0.28f
        rail.addView(tag, LinearLayout.LayoutParams(-1, px(34)))

        val spacer = View(this)
        rail.addView(spacer, LinearLayout.LayoutParams(1, px(18)))

        navItem(rail, "⌂", "Home", true) { }
        navItem(rail, "▦", "Movies") { openBrowse(BrowseActivity.KIND_MOVIES) }
        navItem(rail, "▣", "TV Shows") { openBrowse(BrowseActivity.KIND_TV) }
        navItem(rail, "⌕", "Search") { startActivity(Intent(this, SearchActivity::class.java)) }
        navItem(rail, "♡", "My Lists") { startActivity(Intent(this, ProfileActivity::class.java)) }
        navItem(rail, "↗", "Real Debrid") { startActivity(Intent(this, DebridActivity::class.java)) }
        navItem(rail, "◉", "Providers") { startActivity(Intent(this, ProviderSettingsActivity::class.java)) }
        navItem(rail, "⚙", "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        navItem(rail, "♬", "Support") { startActivity(Intent(this, DonateActivity::class.java)) }

        val filler = View(this)
        rail.addView(filler, LinearLayout.LayoutParams(1, 0, 1f))

        val slogan = TextView(this)
        slogan.text = "YOUR\nCONTENT.\nYOUR WAY."
        slogan.setTextColor(Color.rgb(176, 100, 255))
        slogan.textSize = 13f
        slogan.letterSpacing = 0.24f
        slogan.setLineSpacing(0f, 1.25f)
        rail.addView(slogan, LinearLayout.LayoutParams(-1, px(108)))
        return rail
    }

    private fun navItem(parent: LinearLayout, icon: String, label: String, selected: Boolean = false, click: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isFocusable = true
        row.isClickable = true
        row.stateListAnimator = null
        row.setPadding(px(14), 0, px(12), 0)
        row.background = navBackground(selected)

        val i = TextView(this)
        i.text = icon
        i.gravity = Gravity.CENTER
        i.setTextColor(if (selected) blue else Color.rgb(176, 99, 255))
        i.textSize = 23f
        row.addView(i, LinearLayout.LayoutParams(px(38), -1))

        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER_VERTICAL
        t.setTextColor(if (selected) Color.rgb(124, 181, 255) else Color.rgb(211, 173, 255))
        t.textSize = 16f
        t.typeface = android.graphics.Typeface.DEFAULT_BOLD
        row.addView(t, LinearLayout.LayoutParams(0, -1, 1f))

        row.setOnFocusChangeListener { _, focused ->
            row.background = navBackground(focused || selected)
            t.setTextColor(if (focused || selected) Color.rgb(126, 185, 255) else Color.rgb(211, 173, 255))
            i.setTextColor(if (focused || selected) blue else Color.rgb(176, 99, 255))
            if (focused && AppSettingsStore(this).clickSounds()) row.playSoundEffect(SoundEffectConstants.CLICK)
        }
        row.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(-1, px(57))
        lp.bottomMargin = px(7)
        parent.addView(row, lp)
    }

    private fun buildTopBar(width: Int, height: Int): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        bar.setPadding(px(18), 0, px(28), 0)
        bar.setBackgroundColor(Color.rgb(5, 4, 16))

        val spacer = View(this)
        bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        topAction(bar, "⌕  Search") { startActivity(Intent(this, SearchActivity::class.java)) }
        val rd = if (DebridStore(this).isConnected) "↗  Real Debrid   ● Connected" else "↗  Real Debrid"
        topAction(bar, rd) { startActivity(Intent(this, DebridActivity::class.java)) }
        topAction(bar, "⚙  Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        topAction(bar, "♬  Support") { startActivity(Intent(this, DonateActivity::class.java)) }

        val clock = TextView(this)
        clock.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        clock.setTextColor(Color.rgb(207, 170, 255))
        clock.textSize = 17f
        clock.gravity = Gravity.CENTER
        bar.addView(clock, LinearLayout.LayoutParams(px(118), height))
        return bar
    }

    private fun topAction(parent: LinearLayout, label: String, click: () -> Unit) {
        val t = TextView(this)
        t.text = label
        t.gravity = Gravity.CENTER
        t.setTextColor(Color.rgb(202, 164, 247))
        t.textSize = 14f
        t.isFocusable = true
        t.isClickable = true
        t.stateListAnimator = null
        t.setPadding(px(12), 0, px(12), 0)
        t.background = transparentFocusBackground()
        t.setOnFocusChangeListener { _, f ->
            t.setTextColor(if (f) blue else Color.rgb(202, 164, 247))
            t.background = if (f) outlineDrawable(Color.TRANSPARENT, blue, px(2), px(7)) else transparentFocusBackground()
        }
        t.setOnClickListener { click() }
        parent.addView(t, LinearLayout.LayoutParams(-2, px(48)))
    }

    private fun buildHero(item: MediaCard?): View {
        val hero = FrameLayout(this)
        hero.setBackgroundColor(panel)

        val art = ImageView(this)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.setBackgroundColor(Color.rgb(13, 8, 24))
        hero.addView(art, FrameLayout.LayoutParams(-1, -1))
        heroArt = art
        if (item != null) loadHero(item)

        val shade = View(this)
        shade.background = horizontalShade()
        hero.addView(shade, FrameLayout.LayoutParams(-1, -1))

        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        info.gravity = Gravity.BOTTOM
        info.setPadding(px(48), px(26), px(26), px(34))
        val infoLp = FrameLayout.LayoutParams((screen.widthPx * 0.48f).toInt(), -1, Gravity.START)
        hero.addView(info, infoLp)

        val feature = TextView(this)
        feature.text = "FEATURED"
        feature.setTextColor(Color.rgb(214, 169, 255))
        feature.textSize = 11f
        feature.letterSpacing = 0.28f
        info.addView(feature, LinearLayout.LayoutParams(-1, px(30)))

        val title = TextView(this)
        title.text = item?.title ?: "M00V13"
        title.setTextColor(white)
        title.textSize = 34f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        heroTitle = title
        info.addView(title, LinearLayout.LayoutParams(-1, -2))

        val meta = TextView(this)
        meta.text = item?.let { heroDetail(it) } ?: "Search • stream • download"
        meta.setTextColor(Color.rgb(233, 225, 242))
        meta.textSize = 15f
        meta.maxLines = 2
        meta.setPadding(0, px(8), 0, px(13))
        heroMeta = meta
        info.addView(meta, LinearLayout.LayoutParams(-1, -2))

        val chips = TextView(this)
        chips.text = item?.let { chipLine(it) } ?: "4K   HDR   Dolby Vision   Atmos"
        chips.setTextColor(Color.rgb(226, 217, 236))
        chips.textSize = 12f
        chips.setPadding(0, 0, 0, px(14))
        info.addView(chips, LinearLayout.LayoutParams(-1, -2))

        val actions = LinearLayout(this)
        actions.orientation = LinearLayout.HORIZONTAL
        val play = actionButton("▶  Play", true)
        play.setOnClickListener {
            if (item == null) startActivity(Intent(this, SearchActivity::class.java)) else openMedia(item)
        }
        actions.addView(play, LinearLayout.LayoutParams(px(220), px(54)))

        val more = actionButton("ⓘ  More Info", false)
        more.setOnClickListener { if (item != null) openMedia(item) }
        val mp = LinearLayout.LayoutParams(px(180), px(54)); mp.leftMargin = px(12)
        actions.addView(more, mp)

        val list = actionButton("＋  My List", false)
        list.setOnClickListener { if (item != null) profiles.setWatchlist(item.id, !profiles.isInWatchlist(item.id)) }
        val lp = LinearLayout.LayoutParams(px(165), px(54)); lp.leftMargin = px(12)
        actions.addView(list, lp)
        info.addView(actions)
        return hero
    }

    private fun buildTrendingStack(items: List<MediaCard>, width: Int, height: Int): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(px(16), px(10), px(18), px(12))
        wrap.setBackgroundColor(Color.rgb(7, 5, 17))

        val title = TextView(this)
        title.text = "Trending Now"
        title.setTextColor(white)
        title.textSize = 15f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        wrap.addView(title, LinearLayout.LayoutParams(-1, px(30)))

        val cardH = max(px(84), (height - px(54)) / 3)
        items.take(3).forEach { item ->
            val card = FrameLayout(this)
            card.isFocusable = true
            card.isClickable = true
            card.stateListAnimator = null
            card.background = outlineDrawable(panel2, Color.TRANSPARENT, 0, px(5))

            val art = ImageView(this)
            art.scaleType = ImageView.ScaleType.CENTER_CROP
            art.setBackgroundColor(panel2)
            card.addView(art, FrameLayout.LayoutParams(-1, -1))
            loadArtwork(art, item.artworkUrl, max(420, width))

            val shade = View(this)
            shade.setBackgroundColor(Color.argb(70, 0, 0, 0))
            card.addView(shade, FrameLayout.LayoutParams(-1, -1))

            val t = TextView(this)
            t.text = item.title
            t.setTextColor(white)
            t.textSize = 15f
            t.typeface = android.graphics.Typeface.DEFAULT_BOLD
            t.gravity = Gravity.BOTTOM or Gravity.START
            t.setPadding(px(12), px(8), px(10), px(10))
            t.maxLines = 2
            card.addView(t, FrameLayout.LayoutParams(-1, -1))

            card.setOnFocusChangeListener { _, f ->
                card.background = outlineDrawable(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) px(3) else 0, px(5))
                if (f) showHero(item)
            }
            card.setOnClickListener { openMedia(item) }
            val cp = LinearLayout.LayoutParams(-1, cardH)
            cp.bottomMargin = px(8)
            wrap.addView(card, cp)
        }
        return wrap
    }

    private fun addLandscapeRail(parent: LinearLayout, heading: String, items: List<MediaCard>, totalHeight: Int) {
        parent.addView(sectionHeader(heading, null), LinearLayout.LayoutParams(-1, px(32)))
        val hsv = HorizontalScrollView(this)
        hsv.isHorizontalScrollBarEnabled = false
        hsv.clipChildren = false
        hsv.clipToPadding = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.setPadding(0, px(2), px(20), px(2))

        val cardW = (screen.widthPx * 0.15f).toInt().coerceIn(px(230), px(320))
        val cardH = (totalHeight - px(38)).coerceAtLeast(px(105))
        items.take(12).forEach { row.addView(landscapeCard(it, cardW, cardH)) }
        hsv.addView(row)
        parent.addView(hsv, LinearLayout.LayoutParams(-1, totalHeight - px(32)))
    }

    private fun landscapeCard(item: MediaCard, width: Int, height: Int): View {
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        holder.isFocusable = true
        holder.isClickable = true
        holder.stateListAnimator = null
        holder.setPadding(px(3), px(3), px(3), px(3))
        holder.background = outlineDrawable(Color.TRANSPARENT, Color.TRANSPARENT, 0, px(5))

        val artH = (height * 0.70f).toInt()
        val art = ImageView(this)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.setBackgroundColor(panel2)
        holder.addView(art, LinearLayout.LayoutParams(width - px(6), artH))
        loadArtwork(art, item.artworkUrl, max(width * 2, 480))

        val t = TextView(this)
        t.text = item.title
        t.setTextColor(white)
        t.textSize = 13f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        holder.addView(t, LinearLayout.LayoutParams(width - px(6), px(24)))

        val sub = TextView(this)
        sub.text = progressLine(item)
        sub.setTextColor(Color.rgb(194, 164, 232))
        sub.textSize = 11f
        holder.addView(sub, LinearLayout.LayoutParams(width - px(6), px(20)))

        holder.setOnFocusChangeListener { _, f ->
            holder.background = outlineDrawable(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) px(3) else 0, px(5))
            t.setTextColor(if (f) blue else white)
            if (f) showHero(item)
            if (f && AppSettingsStore(this).clickSounds()) holder.playSoundEffect(SoundEffectConstants.CLICK)
        }
        holder.setOnClickListener { openMedia(item) }
        val lp = LinearLayout.LayoutParams(width, height)
        lp.rightMargin = px(12)
        holder.layoutParams = lp
        return holder
    }

    private fun addPosterRail(parent: LinearLayout, heading: String, items: List<MediaCard>, totalHeight: Int) {
        parent.addView(sectionHeader(heading, "See All  ›"), LinearLayout.LayoutParams(-1, px(34)))
        val hsv = HorizontalScrollView(this)
        hsv.isHorizontalScrollBarEnabled = false
        hsv.clipChildren = false
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.setPadding(0, px(2), px(20), px(2))

        val cardW = (screen.widthPx * 0.108f).toInt().coerceIn(px(158), px(228))
        val cardH = totalHeight - px(38)
        items.take(14).forEach { row.addView(posterCard(it, cardW, cardH)) }
        hsv.addView(row)
        parent.addView(hsv, LinearLayout.LayoutParams(-1, cardH))
    }

    private fun posterCard(item: MediaCard, width: Int, height: Int): View {
        val card = FrameLayout(this)
        card.isFocusable = true
        card.isClickable = true
        card.stateListAnimator = null
        card.setPadding(px(3), px(3), px(3), px(3))
        card.background = outlineDrawable(Color.TRANSPARENT, Color.TRANSPARENT, 0, px(5))

        val art = ImageView(this)
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.setBackgroundColor(panel2)
        card.addView(art, FrameLayout.LayoutParams(width - px(6), height - px(6)))
        loadArtwork(art, item.artworkUrl, max(width * 2, 420))

        val titleShade = View(this)
        titleShade.setBackgroundColor(Color.argb(105, 0, 0, 0))
        val slp = FrameLayout.LayoutParams(-1, px(50), Gravity.BOTTOM)
        slp.leftMargin = px(3); slp.rightMargin = px(3); slp.bottomMargin = px(3)
        card.addView(titleShade, slp)

        val title = TextView(this)
        title.text = item.title
        title.setTextColor(white)
        title.textSize = 12f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        title.gravity = Gravity.BOTTOM or Gravity.START
        title.setPadding(px(9), px(3), px(7), px(8))
        card.addView(title, FrameLayout.LayoutParams(-1, px(52), Gravity.BOTTOM))

        card.setOnFocusChangeListener { _, f ->
            card.background = outlineDrawable(Color.TRANSPARENT, if (f) blue else Color.TRANSPARENT, if (f) px(4) else 0, px(5))
            title.setTextColor(if (f) Color.rgb(115, 184, 255) else white)
            if (f) showHero(item)
            if (f && AppSettingsStore(this).clickSounds()) card.playSoundEffect(SoundEffectConstants.CLICK)
        }
        card.setOnClickListener { openMedia(item) }
        card.setOnLongClickListener {
            profiles.setWatchlist(item.id, !profiles.isInWatchlist(item.id)); true
        }
        val lp = LinearLayout.LayoutParams(width, height)
        lp.rightMargin = px(11)
        card.layoutParams = lp
        return card
    }

    private fun sectionHeader(left: String, right: String?): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this)
        title.text = left
        title.setTextColor(white)
        title.textSize = 18f
        title.typeface = android.graphics.Typeface.DEFAULT_BOLD
        row.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
        if (right != null) {
            val see = TextView(this)
            see.text = right
            see.setTextColor(Color.rgb(189, 112, 255))
            see.textSize = 12f
            see.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            row.addView(see, LinearLayout.LayoutParams(px(95), -1))
        }
        return row
    }

    private fun actionButton(label: String, primary: Boolean): TextView {
        val b = TextView(this)
        b.text = label
        b.gravity = Gravity.CENTER
        b.textSize = 16f
        b.typeface = android.graphics.Typeface.DEFAULT_BOLD
        b.setTextColor(white)
        b.isFocusable = true
        b.isClickable = true
        b.stateListAnimator = null
        b.background = if (primary) outlineDrawable(Color.rgb(102, 46, 239), blue, px(2), px(6)) else outlineDrawable(Color.argb(135, 18, 10, 42), Color.rgb(105, 56, 157), px(1), px(6))
        b.setOnFocusChangeListener { _, f ->
            b.background = if (f) outlineDrawable(if (primary) Color.rgb(93, 42, 231) else Color.rgb(24, 13, 48), blue, px(3), px(6)) else if (primary) outlineDrawable(Color.rgb(102, 46, 239), Color.rgb(90, 80, 180), px(1), px(6)) else outlineDrawable(Color.argb(135, 18, 10, 42), Color.rgb(105, 56, 157), px(1), px(6))
            b.setTextColor(if (f) Color.rgb(146, 201, 255) else white)
        }
        return b
    }

    private fun showHero(item: MediaCard) {
        heroTitle?.text = item.title
        heroMeta?.text = heroDetail(item)
        heroArt?.let {
            it.setImageDrawable(null)
            loadHero(item)
        }
    }

    private fun loadHero(item: MediaCard) {
        val target = heroArt ?: return
        val url = item.artworkUrl ?: return
        if (url.isBlank()) return
        val token = ++heroToken
        artExecutor.submit {
            try {
                val f = ArtworkCache(this).fetch(url, 88, min(screen.widthPx, 1920)) ?: return@submit
                val bitmap = BitmapFactory.decodeFile(f.absolutePath) ?: return@submit
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && token == heroToken && heroArt === target) target.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                DebugLog.append(this, "ART", "Hero artwork failed: ${e.message}")
            }
        }
    }

    private fun loadArtwork(image: ImageView, url: String?, targetPx: Int) {
        if (url.isNullOrBlank()) return
        artExecutor.submit {
            try {
                val f: File = ArtworkCache(this).fetch(url, 84, min(targetPx, screen.widthPx)) ?: return@submit
                val bitmap = BitmapFactory.decodeFile(f.absolutePath) ?: return@submit
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) image.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                DebugLog.append(this, "ART", "Artwork failed: ${e.message}")
            }
        }
    }

    private fun buildCompactHome(w: Int, h: Int): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(px(16), px(14), px(16), px(22))
        root.setBackgroundColor(bg)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val brand = TextView(this)
        brand.text = "M00V13"
        brand.setTextColor(purple)
        brand.textSize = 24f
        brand.typeface = android.graphics.Typeface.DEFAULT_BOLD
        top.addView(brand, LinearLayout.LayoutParams(0, px(48), 1f))
        topAction(top, "⌕") { startActivity(Intent(this, SearchActivity::class.java)) }
        topAction(top, "⚙") { startActivity(Intent(this, SettingsActivity::class.java)) }
        root.addView(top)

        val items = discovery.get(DiscoveryStore.POPULAR_MOVIES)
        val heroItem = items.firstOrNull() ?: catalog.all().firstOrNull()
        root.addView(buildHero(heroItem), LinearLayout.LayoutParams(-1, (h * .34f).toInt()))
        addPosterRail(root, "Trending Movies", items, (h * .42f).toInt())
        return root
    }

    private fun openBrowse(kind: String) {
        startActivity(Intent(this, BrowseActivity::class.java).putExtra(BrowseActivity.EXTRA_KIND, kind))
    }

    private fun openMedia(item: MediaCard) {
        catalog.upsert(item)
        startActivity(Intent(this, MediaOpenActivity::class.java).putExtra(MediaOpenActivity.EXTRA_MEDIA_ID, item.id))
    }

    private fun continueWatching(all: List<MediaCard>): List<MediaCard> = all
        .filter {
            val p = profiles.progressMs(it.id)
            val d = profiles.durationMs(it.id)
            !profiles.isWatched(it.id) && p > 0L && d > 0L
        }
        .sortedByDescending { profiles.lastUpdatedMs(it.id) }

    private fun mostRecentlyTouched(all: List<MediaCard>): MediaCard? = all.maxByOrNull { profiles.lastUpdatedMs(it.id) }

    private fun heroDetail(item: MediaCard): String {
        val type = if (item.series) "TV Series" else "Movie"
        val sub = item.subtitle?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        val genre = item.genre.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""
        val p = profiles.progressMs(item.id)
        val d = profiles.durationMs(item.id)
        val progress = if (p > 0 && d > 0) " • ${min(99, (p * 100 / d).toInt())}% watched" else ""
        return "$type$sub$genre$progress"
    }

    private fun chipLine(item: MediaCard): String {
        val year = item.subtitle?.takeIf { it.matches(Regex(".*\\d{4}.*")) } ?: ""
        val g = item.genre.takeIf { it.isNotBlank() } ?: "Featured"
        return listOf(year, g, "4K", "HDR", "Dolby Vision", "Atmos").filter { it.isNotBlank() }.joinToString("   ")
    }

    private fun progressLine(item: MediaCard): String {
        val p = profiles.progressMs(item.id)
        val d = profiles.durationMs(item.id)
        if (p > 0 && d > 0) {
            val mins = max(1, ((d - p) / 60000L).toInt())
            return "${min(99, (p * 100 / d).toInt())}% watched • ${mins}m left"
        }
        return if (item.series) "TV Series" else "Movie"
    }

    private fun navBackground(active: Boolean): GradientDrawable = outlineDrawable(
        if (active) Color.rgb(20, 12, 48) else Color.TRANSPARENT,
        if (active) blue else Color.TRANSPARENT,
        if (active) px(2) else 0,
        px(7)
    )

    private fun transparentFocusBackground(): GradientDrawable = outlineDrawable(Color.TRANSPARENT, Color.TRANSPARENT, 0, px(7))

    private fun outlineDrawable(fill: Int, stroke: Int, strokeWidth: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

    private fun horizontalShade(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.argb(238, 2, 2, 9), Color.argb(185, 4, 3, 12), Color.argb(30, 4, 3, 12))
    )

    private fun px(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
}