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
import kotlin.math.min

/** TV-first home. Geometry is based on actual screen pixels, not Android density buckets. */
class HomeActivity : Activity() {
    private lateinit var profiles: ProfileStore
    private lateinit var catalog: CatalogStore
    private lateinit var discovery: DiscoveryStore
    private val pool = Executors.newFixedThreadPool(3)
    private val refreshPool = Executors.newSingleThreadExecutor()
    private val bg = Color.rgb(4,3,12)
    private val panel = Color.rgb(13,8,27)
    private val purple = Color.rgb(180,78,255)
    private val blue = Color.rgb(44,157,255)
    private val white = Color.rgb(247,245,250)
    private var heroArt: ImageView? = null
    private var heroTitle: TextView? = null
    private var heroMeta: TextView? = null
    private var heroToken = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        TvUi.disableWindowAnimations(this)
        profiles=ProfileStore(this); catalog=CatalogStore(this); discovery=DiscoveryStore(this)
        setContentView(build())
        if(discovery.stale()) refreshPool.submit { try { discovery.refresh(); runOnUiThread { if(!isFinishing) setContentView(build()) } } catch(e:Exception){ DebugLog.append(this,"HOME","Discovery: ${e.message}") } }
    }
    override fun onResume(){ super.onResume(); if(::catalog.isInitialized) setContentView(build()) }
    override fun onDestroy(){ pool.shutdownNow(); refreshPool.shutdownNow(); super.onDestroy() }

    private fun build():View {
        val w=resources.displayMetrics.widthPixels; val h=resources.displayMetrics.heightPixels
        if(w < h || w < 1000) return mobile(w,h)
        val root=FrameLayout(this).apply{setBackgroundColor(bg)}
        val side=(w*.155f).toInt(); val top=(h*.075f).toInt(); val bodyW=w-side
        root.addView(sidebar(side,h),FrameLayout.LayoutParams(side,h))
        val body=FrameLayout(this).apply{setBackgroundColor(bg)}
        root.addView(body,FrameLayout.LayoutParams(bodyW,h).apply{leftMargin=side})
        body.addView(topbar(),FrameLayout.LayoutParams(bodyW,top))

        val movies=discovery.get(DiscoveryStore.POPULAR_MOVIES)
        val tv=discovery.get(DiscoveryStore.POPULAR_TV)
        val all=catalog.all()
        val cont=all.filter{ val p=profiles.progressMs(it.id); val d=profiles.durationMs(it.id); !profiles.isWatched(it.id)&&p>0&&d>0 }.sortedByDescending{profiles.lastUpdatedMs(it.id)}
        val featured=cont.firstOrNull()?:tv.firstOrNull()?:movies.firstOrNull()?:all.firstOrNull()

        val heroH=(h*.385f).toInt(); val trendW=(bodyW*.22f).toInt(); val heroW=bodyW-trendW
        body.addView(hero(featured,heroW,heroH),FrameLayout.LayoutParams(heroW,heroH).apply{topMargin=top})
        body.addView(trending((tv+movies).distinctBy{it.id}.take(3)),FrameLayout.LayoutParams(trendW,heroH).apply{leftMargin=heroW;topMargin=top})

        val lowerTop=top+heroH; val lowerH=h-lowerTop
        val lower=FrameLayout(this).apply{setBackgroundColor(bg);setPadding((bodyW*.025f).toInt(),0,(bodyW*.018f).toInt(),0)}
        body.addView(lower,FrameLayout.LayoutParams(bodyW,lowerH).apply{topMargin=lowerTop})
        val titleH=(lowerH*.10f).toInt().coerceAtLeast(28)
        val continueH=(lowerH*.38f).toInt()
        val posterTitleY=titleH+continueH
        val posterH=lowerH-posterTitleY-titleH
        lower.addView(section("Continue Watching",null),FrameLayout.LayoutParams(-1,titleH))
        lower.addView(landscapeRail(if(cont.isNotEmpty())cont else (tv+movies).take(10),continueH),FrameLayout.LayoutParams(-1,continueH).apply{topMargin=titleH})
        lower.addView(section("Trending Movies","See All  ›"),FrameLayout.LayoutParams(-1,titleH).apply{topMargin=posterTitleY})
        lower.addView(posterRail(if(movies.isNotEmpty())movies else all.filter{!it.series},posterH),FrameLayout.LayoutParams(-1,posterH).apply{topMargin=posterTitleY+titleH})
        return root
    }

    private fun sidebar(w:Int,h:Int)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding((w*.10f).toInt(),(h*.035f).toInt(),(w*.07f).toInt(),(h*.025f).toInt());setBackgroundColor(Color.rgb(3,3,11))
        addView(txt("M00V13",30f,purple,true),LinearLayout.LayoutParams(-1,(h*.055f).toInt()))
        addView(txt("STREAM BEYOND LIMITS",8f,Color.rgb(206,139,255)).apply{letterSpacing=.25f},LinearLayout.LayoutParams(-1,(h*.055f).toInt()))
        nav(this,"⌂","Home",true){}
        nav(this,"▦","Movies"){browse(BrowseActivity.KIND_MOVIES)}
        nav(this,"▣","TV Shows"){browse(BrowseActivity.KIND_TV)}
        nav(this,"⌕","Search"){open(SearchActivity::class.java)}
        nav(this,"♡","My Lists"){open(ProfileActivity::class.java)}
        nav(this,"↗","Real Debrid"){open(DebridActivity::class.java)}
        nav(this,"◉","Providers"){open(ProviderSettingsActivity::class.java)}
        nav(this,"⚙","Settings"){open(SettingsActivity::class.java)}
        nav(this,"♬","Support"){open(DonateActivity::class.java)}
    }
    private fun nav(p:LinearLayout,icon:String,label:String,on:Boolean=false,action:()->Unit){
        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;isFocusable=true;isClickable=true;stateListAnimator=null;setPadding(10,0,8,0);background=focusBg(on)}
        val i=txt(icon,21f,if(on)blue else purple); val t=txt(label,15f,if(on)Color.rgb(145,202,255) else Color.rgb(226,202,245),true)
        row.addView(i,LinearLayout.LayoutParams(38,-1));row.addView(t,LinearLayout.LayoutParams(0,-1,1f))
        row.setOnFocusChangeListener{_,f->row.background=focusBg(f||on);i.setTextColor(if(f||on)blue else purple);t.setTextColor(if(f||on)Color.rgb(145,202,255) else Color.rgb(226,202,245));if(f&&AppSettingsStore(this).clickSounds())row.playSoundEffect(SoundEffectConstants.CLICK)}
        row.setOnClickListener{action()};p.addView(row,LinearLayout.LayoutParams(-1,0,1f).apply{bottomMargin=5})
    }
    private fun topbar()=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL or Gravity.END;setPadding(10,0,18,0);setBackgroundColor(Color.rgb(5,4,16));addView(View(this@HomeActivity),LinearLayout.LayoutParams(0,1,1f))
        top(this,"⌕  Search"){open(SearchActivity::class.java)};top(this,"↗  Real Debrid"){open(DebridActivity::class.java)};top(this,"⚙  Settings"){open(SettingsActivity::class.java)};top(this,"♬  Support"){open(DonateActivity::class.java)}
        addView(txt(SimpleDateFormat("h:mm a",Locale.getDefault()).format(Date()),15f,Color.rgb(215,184,240)).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(105,-1))
    }
    private fun top(p:LinearLayout,s:String,a:()->Unit){val v=txt(s,13f,Color.rgb(220,198,239)).apply{gravity=Gravity.CENTER;isFocusable=true;isClickable=true;setPadding(12,0,12,0);setOnFocusChangeListener{_,f->setTextColor(if(f)blue else Color.rgb(220,198,239));background=if(f)stroke(blue,2,7)else null};setOnClickListener{a()}};p.addView(v,LinearLayout.LayoutParams(-2,-1))}

    private fun hero(item:MediaCard?,w:Int,h:Int)=FrameLayout(this).apply{
        setBackgroundColor(panel);val image=ImageView(this@HomeActivity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)};heroArt=image;addView(image,FrameLayout.LayoutParams(-1,-1));if(item!=null)loadHero(item)
        addView(View(this@HomeActivity).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(Color.argb(250,3,2,10),Color.argb(190,4,3,12),Color.argb(25,4,3,12)))},FrameLayout.LayoutParams(-1,-1))
        val info=LinearLayout(this@HomeActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.BOTTOM;setPadding((w*.055f).toInt(),(h*.05f).toInt(),15,(h*.07f).toInt())};addView(info,FrameLayout.LayoutParams((w*.60f).toInt(),-1))
        info.addView(txt("FEATURED",10f,Color.rgb(216,172,255)).apply{letterSpacing=.25f},LinearLayout.LayoutParams(-1,(h*.09f).toInt()))
        heroTitle=txt(item?.title?:"M00V13",32f,white,true).apply{maxLines=2;ellipsize=TextUtils.TruncateAt.END};info.addView(heroTitle)
        heroMeta=txt(item?.let{detail(it)}?:"Search • stream • download",14f,Color.rgb(231,224,239));info.addView(heroMeta,LinearLayout.LayoutParams(-1,(h*.10f).toInt()))
        val actions=LinearLayout(this@HomeActivity);val play=button("▶  Play",true).apply{setOnClickListener{if(item==null)open(SearchActivity::class.java)else openMedia(item)}};actions.addView(play,LinearLayout.LayoutParams((w*.22f).toInt(),(h*.13f).toInt()));val more=button("ⓘ  More Info",false).apply{setOnClickListener{if(item!=null)openMedia(item)}};actions.addView(more,LinearLayout.LayoutParams((w*.19f).toInt(),(h*.13f).toInt()).apply{leftMargin=10});info.addView(actions)
    }
    private fun trending(items:List<MediaCard>)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(12,8,12,8);setBackgroundColor(Color.rgb(7,5,17));addView(txt("Trending Now",14f,white,true),LinearLayout.LayoutParams(-1,30))
        items.forEach{m->val c=FrameLayout(this@HomeActivity).apply{isFocusable=true;isClickable=true;background=fill(panel,5)};val im=ImageView(this@HomeActivity).apply{scaleType=ImageView.ScaleType.CENTER_CROP};c.addView(im,FrameLayout.LayoutParams(-1,-1));load(im,m.artworkUrl,500);c.addView(txt(m.title,13f,white,true).apply{gravity=Gravity.BOTTOM;setPadding(8,0,6,7);setBackgroundColor(Color.argb(75,0,0,0))},FrameLayout.LayoutParams(-1,-1));c.setOnFocusChangeListener{_,f->c.background=if(f)stroke(blue,3,5)else fill(panel,5);if(f)showHero(m)};c.setOnClickListener{openMedia(m)};addView(c,LinearLayout.LayoutParams(-1,0,1f).apply{bottomMargin=6})}
    }
    private fun landscapeRail(items:List<MediaCard>,h:Int):View{val s=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val cw=(resources.displayMetrics.widthPixels*.145f).toInt();items.take(12).forEach{m->val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;isFocusable=true;isClickable=true;setPadding(3,3,3,3)};val im=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)};c.addView(im,LinearLayout.LayoutParams(cw-6,(h*.70f).toInt()));load(im,m.artworkUrl,cw*2);val name=txt(m.title,12f,white,true).apply{maxLines=1;ellipsize=TextUtils.TruncateAt.END};c.addView(name,LinearLayout.LayoutParams(cw-6,0,1f));c.setOnFocusChangeListener{_,f->c.background=if(f)stroke(blue,3,5)else null;name.setTextColor(if(f)blue else white);if(f)showHero(m)};c.setOnClickListener{openMedia(m)};r.addView(c,LinearLayout.LayoutParams(cw,h).apply{rightMargin=10})};s.addView(r);return s}
    private fun posterRail(items:List<MediaCard>,h:Int):View{val s=HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false};val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};val cw=(h*.62f).toInt().coerceAtLeast(95);items.take(14).forEach{m->val c=FrameLayout(this).apply{isFocusable=true;isClickable=true;setPadding(3,3,3,3)};val im=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackgroundColor(panel)};c.addView(im,FrameLayout.LayoutParams(-1,-1));load(im,m.artworkUrl,cw*2);val name=txt(m.title,11f,white,true).apply{gravity=Gravity.BOTTOM;setPadding(7,0,5,6);maxLines=2;setBackgroundColor(Color.argb(85,0,0,0))};c.addView(name,FrameLayout.LayoutParams(-1,(h*.30f).toInt(),Gravity.BOTTOM));c.setOnFocusChangeListener{_,f->c.background=if(f)stroke(blue,3,5)else null;name.setTextColor(if(f)blue else white);if(f)showHero(m)};c.setOnClickListener{openMedia(m)};r.addView(c,LinearLayout.LayoutParams(cw,h).apply{rightMargin=10})};s.addView(r);return s}
    private fun section(l:String,r:String?)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;addView(txt(l,17f,white,true),LinearLayout.LayoutParams(0,-1,1f));if(r!=null)addView(txt(r,11f,purple).apply{gravity=Gravity.CENTER_VERTICAL or Gravity.END},LinearLayout.LayoutParams(95,-1))}
    private fun mobile(w:Int,h:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,14,14);setBackgroundColor(bg);addView(txt("M00V13",25f,purple,true),LinearLayout.LayoutParams(-1,50));val m=discovery.get(DiscoveryStore.POPULAR_MOVIES);addView(hero(m.firstOrNull()?:catalog.all().firstOrNull(),w,(h*.36f).toInt()),LinearLayout.LayoutParams(-1,(h*.36f).toInt()));addView(section("Trending Movies",null),LinearLayout.LayoutParams(-1,40));addView(posterRail(m,(h*.40f).toInt()),LinearLayout.LayoutParams(-1,(h*.40f).toInt()))}

    private fun showHero(m:MediaCard){heroTitle?.text=m.title;heroMeta?.text=detail(m);heroArt?.setImageDrawable(null);loadHero(m)}
    private fun loadHero(m:MediaCard){val v=heroArt?:return;val token=++heroToken;pool.submit{try{val wide=try{CinemetaClient().background(m)}catch(_:Exception){null};val url=wide?:m.artworkUrl?:return@submit;val f=ArtworkCache(this).fetch(url,88,min(resources.displayMetrics.widthPixels,1920))?:return@submit;val b=BitmapFactory.decodeFile(f.absolutePath)?:return@submit;runOnUiThread{if(!isFinishing&&token==heroToken&&heroArt===v)v.setImageBitmap(b)}}catch(e:Exception){DebugLog.append(this,"ART","Hero ${e.message}")}}}
    private fun load(v:ImageView,url:String?,target:Int){if(url.isNullOrBlank())return;pool.submit{try{val f=ArtworkCache(this).fetch(url,86,min(target,resources.displayMetrics.widthPixels))?:return@submit;val b=BitmapFactory.decodeFile(f.absolutePath)?:return@submit;runOnUiThread{if(!isFinishing)v.setImageBitmap(b)}}catch(_:Exception){}}}
    private fun detail(m:MediaCard)=(if(m.series)"TV Series" else "Movie")+(if(m.subtitle.isNullOrBlank())"" else " • ${m.subtitle}")+(if(m.genre.isBlank())"" else " • ${m.genre}")
    private fun button(s:String,primary:Boolean)=txt(s,15f,white,true).apply{gravity=Gravity.CENTER;isFocusable=true;isClickable=true;background=if(primary)GradientDrawable().apply{setColor(Color.rgb(98,44,230));cornerRadius=7f;setStroke(2,blue)}else fill(Color.rgb(22,13,43),7);setOnFocusChangeListener{_,f->if(f){background=stroke(blue,3,7);setTextColor(blue)}else{background=if(primary)GradientDrawable().apply{setColor(Color.rgb(98,44,230));cornerRadius=7f;setStroke(2,blue)}else fill(Color.rgb(22,13,43),7);setTextColor(white)}}}
    private fun txt(s:String,size:Float,color:Int,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);includeFontPadding=false;if(bold)typeface=Typeface.DEFAULT_BOLD}
    private fun focusBg(on:Boolean)=if(on)GradientDrawable().apply{setColor(Color.rgb(19,12,47));cornerRadius=7f;setStroke(2,blue)}else fill(Color.TRANSPARENT,7)
    private fun fill(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=r.toFloat()}
    private fun stroke(c:Int,n:Int,r:Int)=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=r.toFloat();setStroke(n,c)}
    private fun browse(k:String)=startActivity(Intent(this,BrowseActivity::class.java).putExtra(BrowseActivity.EXTRA_KIND,k))
    private fun open(c:Class<*>)=startActivity(Intent(this,c))
    private fun openMedia(m:MediaCard){catalog.upsert(m);startActivity(Intent(this,MediaOpenActivity::class.java).putExtra(MediaOpenActivity.EXTRA_MEDIA_ID,m.id))}
}
