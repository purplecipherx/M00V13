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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Zero-credential metadata/catalog fallback using the Stremio Cinemeta addon protocol. */
public final class CinemetaClient {
    private static final String BASE="https://v3-cinemeta.strem.io/";

    public List<MediaCard> popularMovies() throws Exception { return catalog("movie","top",null); }
    public List<MediaCard> popularSeries() throws Exception { return catalog("series","imdbRating",null); }
    public List<MediaCard> searchMovies(String q) throws Exception { return searchWithFallback("movie","top",q); }
    public List<MediaCard> searchSeries(String q) throws Exception { return searchWithFallback("series","imdbRating",q); }
    public List<MediaCard> genre(String type,String genre) throws Exception { return catalog(type,"series".equals(type)?"imdbRating":"top","genre="+Uri.encode(genre)); }

    /** Returns wide fanart for the TV hero. Poster artwork must never be stretched into the hero. */
    public String background(MediaCard card) throws Exception {
        if(card==null||card.id==null||!card.id.startsWith("cinemeta_")) return null;
        String prefix=card.series?"cinemeta_series_":"cinemeta_movie_";
        if(!card.id.startsWith(prefix)) return null;
        String imdb=card.id.substring(prefix.length());
        if(imdb.isEmpty()) return null;
        JSONObject root=get(BASE+"meta/"+(card.series?"series":"movie")+"/"+Uri.encode(imdb)+".json");
        JSONObject meta=root.optJSONObject("meta");
        if(meta==null) return null;
        String bg=meta.optString("background","");
        return bg.isEmpty()?null:bg;
    }

    private List<MediaCard> searchWithFallback(String type,String id,String q)throws Exception{
        List<MediaCard> first=catalog(type,id,"search="+Uri.encode(q));if(!first.isEmpty())return rank(first,q);
        for(String alt:variants(q)){if(alt.equalsIgnoreCase(q))continue;List<MediaCard> found=catalog(type,id,"search="+Uri.encode(alt));if(!found.isEmpty()){DebugLogStatic.note("Cinemeta fuzzy fallback '"+q+"' -> '"+alt+"'");return rank(found,q);}}
        return Collections.emptyList();
    }

    private static List<String> variants(String q){
        Set<String> out=new LinkedHashSet<>();String n=q==null?"":q.toLowerCase(Locale.US).replaceAll("\\([^)]*\\)"," ").replaceAll("\\b(19|20)\\d{2}\\b"," ").replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
        if(!n.isEmpty())out.add(n);String[] parts=n.split(" ");if(parts.length>3)out.add(parts[0]+" "+parts[1]+" "+parts[2]);if(parts.length>2)out.add(parts[0]+" "+parts[1]);return new ArrayList<>(out);
    }

    private static List<MediaCard> rank(List<MediaCard> in,String q){ArrayList<MediaCard> out=new ArrayList<>(in);final String needle=norm(q);out.sort((a,b)->Double.compare(sim(norm(b.title),needle),sim(norm(a.title),needle)));return out;}
    private static String norm(String s){return s==null?"":s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]","");}
    private static double sim(String a,String b){if(a.equals(b))return 2.0;if(a.contains(b)||b.contains(a))return 1.5;int max=Math.max(a.length(),b.length());if(max==0)return 0;return 1.0-(double)lev(a,b)/max;}
    private static int lev(String a,String b){int[] prev=new int[b.length()+1],cur=new int[b.length()+1];for(int j=0;j<=b.length();j++)prev[j]=j;for(int i=1;i<=a.length();i++){cur[0]=i;for(int j=1;j<=b.length();j++)cur[j]=Math.min(Math.min(cur[j-1]+1,prev[j]+1),prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));int[] t=prev;prev=cur;cur=t;}return prev[b.length()];}

    private List<MediaCard> catalog(String type,String id,String extra) throws Exception {
        String url=BASE+"catalog/"+type+"/"+id+(extra==null?"":"/"+extra)+".json";JSONObject root=get(url);JSONArray metas=root.optJSONArray("metas");if(metas==null)return Collections.emptyList();ArrayList<MediaCard> out=new ArrayList<>();
        for(int i=0;i<metas.length()&&out.size()<30;i++){JSONObject m=metas.optJSONObject(i);if(m==null)continue;String imdb=m.optString("id",""),name=m.optString("name","");if(imdb.isEmpty()||name.isEmpty())continue;String poster=m.optString("poster","");String year=m.optString("releaseInfo",m.optString("year",""));String genre="";JSONArray genres=m.optJSONArray("genres");if(genres!=null&&genres.length()>0)genre=genres.optString(0,"");ArrayList<String> tags=new ArrayList<>();if(!genre.isEmpty())tags.add(genre);boolean series="series".equals(type);out.add(new MediaCard("cinemeta_"+type+"_"+imdb,name,year,series,genre,tags,poster.isEmpty()?null:poster,null,0L,series?imdb:null,0,0,null,0));}
        return out;
    }

    private JSONObject get(String url)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setInstanceFollowRedirects(true);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","M00V13/0.1 Android");int code=c.getResponseCode();InputStream raw=code>=400?c.getErrorStream():c.getInputStream();String body=raw==null?"":read(raw,2*1024*1024);c.disconnect();if(code<200||code>=300)throw new IllegalStateException("Cinemeta HTTP "+code);return new JSONObject(body);}
    private static String read(InputStream raw,int limit)throws Exception{try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int total=0;while(total<limit){int n=in.read(b,0,Math.min(b.length,limit-total));if(n<0)break;out.write(b,0,n);total+=n;}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}

    private static final class DebugLogStatic{static void note(String ignored){}}
}
