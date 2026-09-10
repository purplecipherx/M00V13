package com.m00v13.tv;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class NativeScraperEngine {
    private static final int CONNECT_TIMEOUT_MS=4500, READ_TIMEOUT_MS=5500, MAX_BODY_BYTES=2*1024*1024, MAX_TOTAL_RESULTS=30;
    private static final String USER_AGENT="Mozilla/5.0 (Linux; Android TV) AppleWebKit/537.36 Chrome/126 Safari/537.36 M00V13/0.1";
    private final Context context;
    private final int providerTier;
    public NativeScraperEngine(Context context){this(context,0);}
    public NativeScraperEngine(Context context,int providerTier){this.context=context.getApplicationContext();this.providerTier=Math.max(0,Math.min(3,providerTier));}

    public static final class SearchResult{public final List<SourceOption> sources;public final List<String> providerErrors;SearchResult(List<SourceOption>s,List<String>e){sources=s;providerErrors=e;}}
    private static final class Candidate{final NativeProviderDefinition provider;final String baseUrl,title,detailsUrl,directUri;final int seeders;final long sizeBytes;Candidate(NativeProviderDefinition p,String b,String t,String d,String u,int s,long z){provider=p;baseUrl=b;title=t;detailsUrl=d;directUri=u;seeders=s;sizeBytes=z;}}
    private static final class JsonRow{final JSONObject row,parent;JsonRow(JSONObject r,JSONObject p){row=r;parent=p;}}

    public SearchResult search(String rawQuery){
        String query=rawQuery==null?"":rawQuery.trim();if(query.isEmpty())return new SearchResult(Collections.emptyList(),Collections.emptyList());
        List<NativeProviderDefinition> providers=NativeProviderDefinition.load(context,providerTier);if(providers.isEmpty())return new SearchResult(Collections.emptyList(),Collections.singletonList(providerTier==0?"provider catalog is empty":"tier "+providerTier+" provider catalog is empty"));
        ExecutorService providerPool=Executors.newFixedThreadPool(Math.max(1,Math.min(6,providers.size())));ArrayList<Future<List<Candidate>>> futures=new ArrayList<>();ArrayList<String> errors=new ArrayList<>();
        for(NativeProviderDefinition p:providers)futures.add(providerPool.submit(new Callable<List<Candidate>>(){public List<Candidate> call()throws Exception{return searchProvider(p,query);}}));
        ArrayList<Candidate> candidates=new ArrayList<>();for(int i=0;i<futures.size();i++){try{candidates.addAll(futures.get(i).get());}catch(Exception e){errors.add(providers.get(i).name+": "+shortMessage(e));}}providerPool.shutdownNow();candidates.sort(Comparator.comparingInt((Candidate c)->c.seeders).reversed());
        if(candidates.size()>MAX_TOTAL_RESULTS)candidates=new ArrayList<>(candidates.subList(0,MAX_TOTAL_RESULTS));if(candidates.isEmpty())return new SearchResult(Collections.emptyList(),Collections.unmodifiableList(errors));
        ExecutorService detailPool=Executors.newFixedThreadPool(Math.max(1,Math.min(6,candidates.size())));ArrayList<Future<SourceOption>> dfs=new ArrayList<>();for(Candidate c:candidates)dfs.add(detailPool.submit(()->resolveCandidate(c)));
        ArrayList<SourceOption> sources=new ArrayList<>();Set<String> seen=new HashSet<>();for(int i=0;i<dfs.size();i++){try{SourceOption s=dfs.get(i).get();if(s!=null&&s.uri!=null&&seen.add(dedupeKey(s.uri)))sources.add(s);}catch(Exception e){errors.add(candidates.get(i).provider.name+" detail: "+shortMessage(e));}}detailPool.shutdownNow();
        sources.sort(Comparator.comparingInt((SourceOption s)->s.score).reversed().thenComparing(Comparator.comparingInt((SourceOption s)->s.seeders).reversed()));return new SearchResult(Collections.unmodifiableList(sources),Collections.unmodifiableList(errors));
    }

    private List<Candidate> searchProvider(NativeProviderDefinition p,String query)throws Exception{return p.isJson()?searchJsonProvider(p,query):searchHtmlProvider(p,query);}

    private List<Candidate> searchHtmlProvider(NativeProviderDefinition p,String query)throws Exception{
        Exception last=null;for(String mirror:p.mirrors){try{String base=trailing(mirror),path=p.searchPath.replace("{query}",Uri.encode(query));String html=fetch(new URL(new URL(base),path).toString(),"text/html,application/xhtml+xml");Document doc=Jsoup.parse(html,base);Elements rows=doc.select(p.rowSelector);ArrayList<Candidate> out=new ArrayList<>();
            for(Element row:rows){Element te=row.selectFirst(p.titleSelector);if(te==null)continue;String title=te.text().trim();if(title.isEmpty())continue;Element de=row.selectFirst(p.detailsSelector);String href=de==null?"":de.attr(p.detailsAttribute);String details="";if(!href.isEmpty())details=new URL(new URL(base),href).toString();String direct="";if(!p.rowMagnetSelector.isEmpty()){Element me=row.selectFirst(p.rowMagnetSelector);if(me!=null)direct=me.attr("href");}if(direct.isEmpty()&&details.isEmpty())continue;out.add(new Candidate(p,base,title,details,direct,parseInt(textOf(row,p.seedersSelector),-1),ReleaseMetadataParser.parseSizeBytes(textOf(row,p.sizeSelector))));if(out.size()>=p.maxResults)break;}if(!out.isEmpty())return out;
        }catch(Exception e){last=e;}}if(last!=null)throw last;return Collections.emptyList();
    }

    private List<Candidate> searchJsonProvider(NativeProviderDefinition p,String query)throws Exception{
        Exception last=null;for(String mirror:p.mirrors){try{String base=trailing(mirror),path=p.searchPath.replace("{query}",Uri.encode(query));String url=new URL(new URL(base),path).toString();if(!p.queryParam.isEmpty()&&!p.searchPath.contains("{query}"))url=appendQuery(url,p.queryParam,query);JSONObject root=new JSONObject(fetch(url,"application/json,text/plain;q=0.9,*/*;q=0.1"));JSONArray top=jsonArray(root,p.jsonRowsPath);if(top==null)continue;ArrayList<JsonRow> rows=new ArrayList<>();
            for(int i=0;i<top.length();i++){JSONObject parent=top.optJSONObject(i);if(parent==null)continue;if(p.jsonExpandArrayPath.isEmpty())rows.add(new JsonRow(parent,null));else{JSONArray children=jsonArray(parent,p.jsonExpandArrayPath);if(children!=null)for(int j=0;j<children.length();j++){JSONObject child=children.optJSONObject(j);if(child!=null)rows.add(new JsonRow(child,parent));}}}
            ArrayList<Candidate> out=new ArrayList<>();for(JsonRow jr:rows){if(out.size()>=p.maxResults)break;String title=jsonString(jr,p.jsonTitlePath),hash=jsonString(jr,p.jsonInfoHashPath);if(title.isEmpty()||hash.isEmpty())continue;String quality=jsonString(jr,p.jsonQualityPath),codec=jsonString(jr,p.jsonCodecPath),audio=jsonString(jr,p.jsonAudioPath);String decorated=title+(quality.isEmpty()?"":" "+quality)+(audio.isEmpty()?"":" "+audio)+(codec.isEmpty()?"":" "+codec);String magnet="magnet:?xt=urn:btih:"+hash;String direct=jsonString(jr,p.jsonUrlPath);if(direct.startsWith("magnet:"))magnet=direct;out.add(new Candidate(p,base,decorated,"",magnet,jsonInt(jr,p.jsonSeedersPath,-1),jsonLong(jr,p.jsonSizePath,-1L)));}if(!out.isEmpty())return out;
        }catch(Exception e){last=e;}}if(last!=null)throw last;return Collections.emptyList();
    }

    private SourceOption resolveCandidate(Candidate c)throws Exception{String uri=c.directUri;if(uri==null||uri.isEmpty()){if(c.detailsUrl==null||c.detailsUrl.isEmpty()||c.provider.detailMagnetSelector.isEmpty())return null;Document doc=Jsoup.parse(fetch(c.detailsUrl,"text/html,application/xhtml+xml"),c.baseUrl);Element m=doc.selectFirst(c.provider.detailMagnetSelector);if(m==null)return null;uri=m.attr("href");}if(uri==null||!uri.startsWith("magnet:"))return null;ReleaseMetadataParser.Parsed meta=ReleaseMetadataParser.parse(c.title);return new SourceOption(c.provider.name,uri,meta.quality,meta.videoCodec,meta.hdr,meta.audioCodec,meta.audioLayout,meta.audioLanguages,meta.subtitleLanguages,c.sizeBytes,c.seeders,null,score(meta,c.seeders,c.sizeBytes));}

    private String fetch(String url,String accept)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(READ_TIMEOUT_MS);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",USER_AGENT);c.setRequestProperty("Accept",accept);c.setRequestProperty("Accept-Language","en-US,en;q=0.8");int code=c.getResponseCode();InputStream raw=code>=400?c.getErrorStream():c.getInputStream();String body=raw==null?"":readLimited(raw,MAX_BODY_BYTES);c.disconnect();String lower=body.toLowerCase(Locale.US);if(code==403||code==503||lower.contains("cf-chl-")||lower.contains("cloudflare ray id")||lower.contains("just a moment..."))throw new IOException("challenge page detected; provider skipped");if(code<200||code>=300)throw new IOException("HTTP "+code);return body;}
    private static String appendQuery(String url,String key,String value){return url+(url.contains("?")?"&":"?")+Uri.encode(key)+"="+Uri.encode(value);}private static JSONArray jsonArray(JSONObject root,String path){Object v=jsonValue(root,path);return v instanceof JSONArray?(JSONArray)v:null;}
    private static Object jsonValue(JsonRow jr,String path){if(path==null||path.isEmpty())return null;JSONObject base=jr.row;String p=path;if(path.startsWith("..")){base=jr.parent;p=path.substring(2);if(p.startsWith("."))p=p.substring(1);}return jsonValue(base,p);}private static String jsonString(JsonRow jr,String path){Object v=jsonValue(jr,path);return v==null?"":String.valueOf(v);}private static int jsonInt(JsonRow jr,String path,int fallback){Object v=jsonValue(jr,path);if(v instanceof Number)return((Number)v).intValue();try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return fallback;}}private static long jsonLong(JsonRow jr,String path,long fallback){Object v=jsonValue(jr,path);if(v instanceof Number)return((Number)v).longValue();try{return Long.parseLong(String.valueOf(v));}catch(Exception e){return fallback;}}
    private static Object jsonValue(Object root,String path){if(root==null||path==null||path.isEmpty())return root;Object cur=root;for(String part:path.split("\\.")){if(part.isEmpty())continue;if(cur instanceof JSONObject)cur=((JSONObject)cur).opt(part);else return null;if(cur==null||cur==JSONObject.NULL)return null;}return cur;}
    private static String readLimited(InputStream raw,int max)throws IOException{try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int total=0;while(total<max){int n=in.read(b,0,Math.min(b.length,max-total));if(n<0)break;out.write(b,0,n);total+=n;}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String textOf(Element row,String selector){if(selector==null||selector.isEmpty())return"";Element e=row.selectFirst(selector);return e==null?"":e.text();}private static int parseInt(String text,int fallback){if(text==null)return fallback;String d=text.replaceAll("[^0-9]","");if(d.isEmpty())return fallback;try{return Integer.parseInt(d);}catch(Exception e){return fallback;}}
    private static int score(ReleaseMetadataParser.Parsed m,int seeders,long size){int s=0;if("2160p".equals(m.quality))s+=500;else if("1080p".equals(m.quality))s+=400;else if("720p".equals(m.quality))s+=300;else if("SD".equals(m.quality))s+=150;if("HEVC".equals(m.videoCodec)||"AV1".equals(m.videoCodec))s+=35;if(!m.hdr.isEmpty())s+=20;if(!m.audioLanguages.isEmpty())s+=20;s+=Math.max(0,Math.min(250,seeders));if(size>0)s+=5;return s;}private static String trailing(String v){return v.endsWith("/")?v:v+"/";}private static String shortMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();String m=x.getMessage();return m==null||m.trim().isEmpty()?x.getClass().getSimpleName():m;}private static String dedupeKey(String uri){String l=uri.toLowerCase(Locale.US);int i=l.indexOf("btih:");if(i>=0){int e=l.indexOf('&',i);return e<0?l.substring(i):l.substring(i,e);}return l;}
}
