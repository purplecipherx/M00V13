package com.m00v13.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Zero-credential metadata/catalog fallback using the official Stremio Cinemeta addon protocol. */
public final class CinemetaClient {
    private static final String BASE="https://v3-cinemeta.strem.io/";

    public List<MediaCard> popularMovies() throws Exception { return catalog("movie","top",null); }
    public List<MediaCard> popularSeries() throws Exception { return catalog("series","imdbRating",null); }
    public List<MediaCard> searchMovies(String q) throws Exception { return catalog("movie","top","search="+Uri.encode(q)); }
    public List<MediaCard> searchSeries(String q) throws Exception { return catalog("series","imdbRating","search="+Uri.encode(q)); }
    public List<MediaCard> genre(String type,String genre) throws Exception {
        String id="series".equals(type)?"imdbRating":"top";
        return catalog(type,id,"genre="+Uri.encode(genre));
    }

    private List<MediaCard> catalog(String type,String id,String extra) throws Exception {
        String url=BASE+"catalog/"+type+"/"+id+(extra==null?"":"/"+extra)+".json";
        JSONObject root=get(url); JSONArray metas=root.optJSONArray("metas");
        if(metas==null)return Collections.emptyList();
        ArrayList<MediaCard> out=new ArrayList<>();
        for(int i=0;i<metas.length()&&out.size()<30;i++){
            JSONObject m=metas.optJSONObject(i); if(m==null)continue;
            String imdb=m.optString("id",""); String name=m.optString("name","");
            if(imdb.isEmpty()||name.isEmpty())continue;
            String poster=m.optString("poster","");
            String year=m.optString("releaseInfo",m.optString("year",""));
            String genre=""; JSONArray genres=m.optJSONArray("genres"); if(genres!=null&&genres.length()>0)genre=genres.optString(0,"");
            ArrayList<String> tags=new ArrayList<>(); if(!genre.isEmpty())tags.add(genre);
            boolean series="series".equals(type);
            out.add(new MediaCard("cinemeta_"+type+"_"+imdb,name,year,series,genre,tags,poster.isEmpty()?null:poster,null,0L,series?imdb:null,0,0,null,0));
        }
        return out;
    }

    private JSONObject get(String url)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(5000); c.setReadTimeout(7000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("Accept","application/json"); c.setRequestProperty("User-Agent","M00V13/0.1 Android");
        int code=c.getResponseCode(); InputStream raw=code>=400?c.getErrorStream():c.getInputStream();
        String body=raw==null?"":read(raw,2*1024*1024); c.disconnect();
        if(code<200||code>=300)throw new IllegalStateException("Cinemeta HTTP "+code);
        return new JSONObject(body);
    }

    private static String read(InputStream raw,int limit)throws Exception{
        try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int total=0;while(total<limit){int n=in.read(b,0,Math.min(b.length,limit-total));if(n<0)break;out.write(b,0,n);total+=n;}return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
