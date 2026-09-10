package com.m00v13.tv;

import android.content.Context;
import android.net.Uri;
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
    public NativeScraperEngine(Context context){ this.context=context.getApplicationContext(); }

    public static final class SearchResult { public final List<SourceOption> sources; public final List<String> providerErrors; SearchResult(List<SourceOption>s,List<String>e){sources=s;providerErrors=e;} }
    private static final class Candidate { final NativeProviderDefinition provider; final String baseUrl,title,detailsUrl; final int seeders; final long sizeBytes; Candidate(NativeProviderDefinition p,String b,String t,String d,int s,long z){provider=p;baseUrl=b;title=t;detailsUrl=d;seeders=s;sizeBytes=z;} }

    public SearchResult search(String rawQuery){
        String query=rawQuery==null?"":rawQuery.trim(); if(query.isEmpty())return new SearchResult(Collections.emptyList(),Collections.emptyList());
        List<NativeProviderDefinition> providers=NativeProviderDefinition.load(context);
        if(providers.isEmpty())return new SearchResult(Collections.emptyList(),Collections.singletonList("provider catalog is empty"));
        ExecutorService providerPool=Executors.newFixedThreadPool(Math.max(1,Math.min(6,providers.size())));
        ArrayList<Future<List<Candidate>>> futures=new ArrayList<>(); ArrayList<String> errors=new ArrayList<>();
        for(NativeProviderDefinition p:providers)futures.add(providerPool.submit(new Callable<List<Candidate>>(){public List<Candidate> call() throws Exception{return searchProvider(p,query);}}));
        ArrayList<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<futures.size();i++){try{candidates.addAll(futures.get(i).get());}catch(Exception e){errors.add(providers.get(i).name+": "+shortMessage(e));}}
        providerPool.shutdownNow(); candidates.sort(Comparator.comparingInt((Candidate c)->c.seeders).reversed());
        if(candidates.size()>MAX_TOTAL_RESULTS)candidates=new ArrayList<>(candidates.subList(0,MAX_TOTAL_RESULTS));
        if(candidates.isEmpty())return new SearchResult(Collections.emptyList(),Collections.unmodifiableList(errors));
        ExecutorService detailPool=Executors.newFixedThreadPool(Math.max(1,Math.min(6,candidates.size()))); ArrayList<Future<SourceOption>> dfs=new ArrayList<>();
        for(Candidate c:candidates)dfs.add(detailPool.submit(()->resolveCandidate(c)));
        ArrayList<SourceOption> sources=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(int i=0;i<dfs.size();i++){try{SourceOption s=dfs.get(i).get();if(s!=null&&s.uri!=null&&seen.add(dedupeKey(s.uri)))sources.add(s);}catch(Exception e){errors.add(candidates.get(i).provider.name+" detail: "+shortMessage(e));}}
        detailPool.shutdownNow(); sources.sort(Comparator.comparingInt((SourceOption s)->s.score).reversed().thenComparing(Comparator.comparingInt((SourceOption s)->s.seeders).reversed()));
        return new SearchResult(Collections.unmodifiableList(sources),Collections.unmodifiableList(errors));
    }

    private List<Candidate> searchProvider(NativeProviderDefinition p,String query)throws Exception{
        Exception last=null; for(String mirror:p.mirrors){try{String base=trailing(mirror),path=p.searchPath.replace("{query}",Uri.encode(query));String html=fetch(new URL(new URL(base),path).toString());Document doc=Jsoup.parse(html,base);Elements rows=doc.select(p.rowSelector);ArrayList<Candidate> out=new ArrayList<>();for(Element row:rows){Element te=row.selectFirst(p.titleSelector);if(te==null)continue;String title=te.text().trim(),href=te.attr(p.detailsAttribute);if(title.isEmpty()||href.isEmpty())continue;out.add(new Candidate(p,base,title,new URL(new URL(base),href).toString(),parseInt(textOf(row,p.seedersSelector),-1),ReleaseMetadataParser.parseSizeBytes(textOf(row,p.sizeSelector))));if(out.size()>=p.maxResults)break;}if(!out.isEmpty())return out;}catch(Exception e){last=e;}}if(last!=null)throw last;return Collections.emptyList();
    }
    private SourceOption resolveCandidate(Candidate c)throws Exception{Document doc=Jsoup.parse(fetch(c.detailsUrl),c.baseUrl);Element m=doc.selectFirst(c.provider.magnetSelector);if(m==null)return null;String uri=m.attr("href");if(uri==null||!uri.startsWith("magnet:"))return null;ReleaseMetadataParser.Parsed meta=ReleaseMetadataParser.parse(c.title);return new SourceOption(c.provider.name,uri,meta.quality,meta.videoCodec,meta.hdr,meta.audioCodec,meta.audioLayout,meta.audioLanguages,meta.subtitleLanguages,c.sizeBytes,c.seeders,null,score(meta,c.seeders,c.sizeBytes));}
    private String fetch(String url)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(READ_TIMEOUT_MS);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent",USER_AGENT);c.setRequestProperty("Accept","text/html,application/xhtml+xml");c.setRequestProperty("Accept-Language","en-US,en;q=0.8");int code=c.getResponseCode();InputStream raw=code>=400?c.getErrorStream():c.getInputStream();String body=raw==null?"":readLimited(raw,MAX_BODY_BYTES);c.disconnect();String lower=body.toLowerCase(Locale.US);if(code==403||code==503||lower.contains("cf-chl-")||lower.contains("cloudflare ray id")||lower.contains("just a moment..."))throw new IOException("challenge page detected; provider skipped");if(code<200||code>=300)throw new IOException("HTTP "+code);return body;}
    private static String readLimited(InputStream raw,int max)throws IOException{try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int total=0;while(total<max){int n=in.read(b,0,Math.min(b.length,max-total));if(n<0)break;out.write(b,0,n);total+=n;}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String textOf(Element row,String selector){if(selector==null||selector.isEmpty())return"";Element e=row.selectFirst(selector);return e==null?"":e.text();}
    private static int parseInt(String text,int fallback){if(text==null)return fallback;String d=text.replaceAll("[^0-9]","");if(d.isEmpty())return fallback;try{return Integer.parseInt(d);}catch(Exception e){return fallback;}}
    private static int score(ReleaseMetadataParser.Parsed m,int seeders,long size){int s=0;if("2160p".equals(m.quality))s+=500;else if("1080p".equals(m.quality))s+=400;else if("720p".equals(m.quality))s+=300;else if("SD".equals(m.quality))s+=150;if("HEVC".equals(m.videoCodec)||"AV1".equals(m.videoCodec))s+=35;if(!m.hdr.isEmpty())s+=20;if(!m.audioLanguages.isEmpty())s+=20;s+=Math.max(0,Math.min(250,seeders));if(size>0)s+=5;return s;}
    private static String trailing(String v){return v.endsWith("/")?v:v+"/";} private static String shortMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();String m=x.getMessage();return m==null||m.trim().isEmpty()?x.getClass().getSimpleName():m;} private static String dedupeKey(String uri){String l=uri.toLowerCase(Locale.US);int i=l.indexOf("btih:");if(i>=0){int e=l.indexOf('&',i);return e<0?l.substring(i):l.substring(i,e);}return l;}
}
