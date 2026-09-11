package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Small persistent catalog index so home rails render instantly from cache. */
public final class DiscoveryStore {
    public static final String POPULAR_MOVIES="popular_movies";
    public static final String POPULAR_TV="popular_tv";
    public static final String BLOCKBUSTER_MOVIES="blockbuster_movies";
    public static final String BLOCKBUSTER_TV="blockbuster_tv";
    private static final String PREFS="m00v13_discovery";
    private static final long REFRESH_MS=12L*60L*60L*1000L;
    private final Context context;
    private final SharedPreferences prefs;
    private final CatalogStore catalog;

    public DiscoveryStore(Context context){
        this.context=context.getApplicationContext();
        prefs=this.context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        catalog=new CatalogStore(this.context);
    }

    public boolean stale(){ return System.currentTimeMillis()-prefs.getLong("updated",0L)>REFRESH_MS; }

    public synchronized void refresh() throws Exception {
        CinemetaClient client=new CinemetaClient();
        List<MediaCard> movies=client.popularMovies();
        List<MediaCard> tv=client.popularSeries();

        // Home no longer needs genre-specific "Action" rails. Do not spend startup/refresh time
        // fetching them just to throw them away. Movies/TV genre tabs load those on demand.
        ArrayList<MediaCard> all=new ArrayList<>(movies.size()+tv.size());
        all.addAll(movies); all.addAll(tv);
        catalog.upsertAll(all);

        putIds(POPULAR_MOVIES,movies);
        putIds(POPULAR_TV,tv);
        prefs.edit()
            .remove("section."+BLOCKBUSTER_MOVIES)
            .remove("section."+BLOCKBUSTER_TV)
            .putLong("updated",System.currentTimeMillis())
            .apply();
        DebugLog.append(context,"DISCOVERY","Refreshed movies="+movies.size()+" tv="+tv.size()+" (batched)");
    }

    private void putIds(String section,List<MediaCard> cards){
        JSONArray ids=new JSONArray();
        for(MediaCard card:cards) ids.put(card.id);
        prefs.edit().putString("section."+section,ids.toString()).apply();
    }

    public List<MediaCard> get(String section){
        String raw=prefs.getString("section."+section,"[]");
        try{
            JSONArray ids=new JSONArray(raw); ArrayList<MediaCard> out=new ArrayList<>(ids.length());
            for(int i=0;i<ids.length();i++){MediaCard card=catalog.find(ids.optString(i));if(card!=null)out.add(card);}
            return out;
        }catch(Exception e){return Collections.emptyList();}
    }
}
