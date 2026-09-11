package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Small persistent catalog index so home rails render instantly from cache. */
public final class DiscoveryStore {
    public static final String POPULAR_MOVIES="popular_movies";
    public static final String POPULAR_TV="popular_tv";
    public static final String BLOCKBUSTER_MOVIES="blockbuster_movies";
    public static final String BLOCKBUSTER_TV="blockbuster_tv";
    private static final String PREFS="m00v13_discovery";
    private static final long REFRESH_MS=12L*60L*60L*1000L;
    private static final Map<String,List<String>> ID_CACHE=new ConcurrentHashMap<>();
    private final Context context;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;

    public DiscoveryStore(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        catalog=new CatalogStore(this.context);
    }

    public boolean stale(){ return System.currentTimeMillis()-prefs.getLong("updated",0L)>REFRESH_MS; }

    /** Movies and TV feeds are independent, so fetch them in parallel on a cold/stale refresh. */
    public synchronized void refresh() throws Exception {
        ExecutorService fetchers=Executors.newFixedThreadPool(2);
        List<MediaCard> movies;
        List<MediaCard> tv;
        try{
            Future<List<MediaCard>> moviesFuture=fetchers.submit(()->new CinemetaClient().popularMovies());
            Future<List<MediaCard>> tvFuture=fetchers.submit(()->new CinemetaClient().popularSeries());
            movies=moviesFuture.get();
            tv=tvFuture.get();
        }finally{
            fetchers.shutdownNow();
        }

        ArrayList<MediaCard> all=new ArrayList<>(movies.size()+tv.size());
        all.addAll(movies);all.addAll(tv);catalog.upsertAll(all);
        putIds(POPULAR_MOVIES,movies);putIds(POPULAR_TV,tv);
        prefs.edit().remove("section."+BLOCKBUSTER_MOVIES).remove("section."+BLOCKBUSTER_TV).putLong("updated",System.currentTimeMillis()).apply();
        DebugLog.append(context,"DISCOVERY","Refreshed movies="+movies.size()+" tv="+tv.size()+" (parallel + batched)");
    }

    private void putIds(String section,List<MediaCard> cards){
        JSONArray ids=new JSONArray();ArrayList<String> memory=new ArrayList<>(cards.size());
        for(MediaCard card:cards){ids.put(card.id);memory.add(card.id);}
        ID_CACHE.put(section,Collections.unmodifiableList(memory));
        prefs.edit().putString("section."+section,ids.toString()).apply();
    }

    private List<String> ids(String section){
        List<String> hit=ID_CACHE.get(section);if(hit!=null)return hit;
        String raw=prefs.getString("section."+section,"[]");
        try{
            JSONArray json=new JSONArray(raw);ArrayList<String> out=new ArrayList<>(json.length());
            for(int i=0;i<json.length();i++){String id=json.optString(i);if(!id.isEmpty())out.add(id);}
            List<String> frozen=Collections.unmodifiableList(out);ID_CACHE.put(section,frozen);return frozen;
        }catch(Exception e){return Collections.emptyList();}
    }

    public List<MediaCard> get(String section){
        List<String> ids=ids(section);if(ids.isEmpty())return Collections.emptyList();
        ArrayList<MediaCard> out=new ArrayList<>(ids.size());for(String id:ids){MediaCard card=catalog.find(id);if(card!=null)out.add(card);}return out;
    }
}
