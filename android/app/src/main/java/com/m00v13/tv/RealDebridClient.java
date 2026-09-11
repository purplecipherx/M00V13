package com.m00v13.tv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class RealDebridClient {
    public static final String OPEN_SOURCE_CLIENT_ID = "X245A4XAIBGVM";
    private static final String OAUTH = "https://api.real-debrid.com/oauth/v2/";
    private static final String REST = "https://api.real-debrid.com/rest/1.0/";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private final Context context;
    private final DebridStore store;

    public static final class InfringingSourceException extends IOException { InfringingSourceException(String m){super(m);} }
    public static final class DeviceCode { public final String deviceCode,userCode,verificationUrl; public final int intervalSeconds,expiresInSeconds; DeviceCode(String d,String u,String v,int i,int e){deviceCode=d;userCode=u;verificationUrl=v;intervalSeconds=i;expiresInSeconds=e;} }
    public static final class UserCredentials { public final String clientId,clientSecret; UserCredentials(String i,String s){clientId=i;clientSecret=s;} }
    public RealDebridClient(Context context) { this.context=context.getApplicationContext(); this.store=new DebridStore(this.context); }

    public DeviceCode beginDeviceAuth() throws Exception {JSONObject o=getJson(OAUTH+"device/code?client_id="+enc(OPEN_SOURCE_CLIENT_ID)+"&new_credentials=yes",null,false);return new DeviceCode(o.getString("device_code"),o.getString("user_code"),o.getString("verification_url"),Math.max(1,o.optInt("interval",5)),Math.max(60,o.optInt("expires_in",1800)));}
    public UserCredentials pollUserCredentials(String deviceCode) throws Exception {Response r=request("GET",OAUTH+"device/credentials?client_id="+enc(OPEN_SOURCE_CLIENT_ID)+"&code="+enc(deviceCode),null,null,false);if(r.code==403||r.code==404)return null;require2xx(r);JSONObject o=new JSONObject(r.body);String id=o.optString("client_id",""),s=o.optString("client_secret","");return id.isEmpty()||s.isEmpty()?null:new UserCredentials(id,s);}
    public void finishDeviceAuth(DeviceCode code,UserCredentials credentials)throws Exception{store.saveCredentials(credentials.clientId,credentials.clientSecret);saveTokenResponse(postFormJson(OAUTH+"token","client_id",credentials.clientId,"client_secret",credentials.clientSecret,"code",code.deviceCode,"grant_type","http://oauth.net/grant_type/device/1.0"));}
    public String ensureAccessToken()throws Exception{String current=store.accessToken();if(current!=null&&System.currentTimeMillis()+60000L<store.accessExpiresAtMs())return current;String id=store.clientId(),secret=store.clientSecret(),refresh=store.refreshToken();if(id==null||secret==null||refresh==null)throw new IOException("Real-Debrid is not connected");saveTokenResponse(postFormJson(OAUTH+"token","client_id",id,"client_secret",secret,"code",refresh,"grant_type","http://oauth.net/grant_type/device/1.0"));String updated=store.accessToken();if(updated==null)throw new IOException("Real-Debrid token refresh failed");return updated;}

    /** Probes in parallel and completely removes anything Real-Debrid rejects as infringing. */
    public List<SourceOption> probeCache(List<SourceOption> input,int maxProbes)throws Exception{
        if(input==null||input.isEmpty()||maxProbes<=0)return input;final String token=ensureAccessToken();int workers=SearchConcurrency.forJobs(context,Math.min(maxProbes,input.size()));ExecutorService pool=Executors.newFixedThreadPool(workers);ArrayList<Future<SourceOption>> futures=new ArrayList<>();int[] budget={0};
        for(SourceOption source:input){final boolean should=source.cached==null&&source.uri!=null&&source.uri.startsWith("magnet:")&&budget[0]++<maxProbes;futures.add(pool.submit(()->{
            Boolean cached=source.cached;try{if(should)cached=probeMagnetCached(source.uri,token);}catch(InfringingSourceException e){DebugLog.append(context,"DEBRID","Filtered infringing source from "+source.provider);return null;}
            int score=source.score+(Boolean.TRUE.equals(cached)?1200:Boolean.FALSE.equals(cached)?-40:0);return new SourceOption(source.provider,source.uri,source.quality,source.videoCodec,source.hdr,source.audioCodec,source.audioLayout,source.audioLanguages,source.subtitleLanguages,source.sizeBytes,source.seeders,cached,score);
        }));}
        ArrayList<SourceOption> out=new ArrayList<>();for(Future<SourceOption> f:futures){try{SourceOption s=f.get();if(s!=null)out.add(s);}catch(Exception e){DebugLog.append(context,"DEBRID","Cache probe failed: "+shortMessage(e));}}pool.shutdownNow();out.sort(Comparator.comparingInt((SourceOption s)->s.score).reversed().thenComparing(Comparator.comparingInt((SourceOption s)->s.seeders).reversed()));return out;
    }

    private Boolean probeMagnetCached(String magnet,String token)throws InfringingSourceException{
        String torrentId=null;try{JSONObject added=postAuthorizedForm(REST+"torrents/addMagnet",token,"magnet",magnet);torrentId=added.optString("id","");if(torrentId.isEmpty())return null;JSONObject info=getJson(REST+"torrents/info/"+encPath(torrentId),token,true);String fileId=chooseBestVideoFile(info.optJSONArray("files"));if(fileId==null)return null;postAuthorizedFormAllowEmpty(REST+"torrents/selectFiles/"+encPath(torrentId),token,"files",fileId);for(int i=0;i<3;i++){info=getJson(REST+"torrents/info/"+encPath(torrentId),token,true);JSONArray links=info.optJSONArray("links");if("downloaded".equals(info.optString("status"))&&links!=null&&links.length()>0)return Boolean.TRUE;String status=info.optString("status","");if("downloading".equals(status)||"queued".equals(status))return Boolean.FALSE;if("error".equals(status)||"magnet_error".equals(status)||"virus".equals(status)||"dead".equals(status))return Boolean.FALSE;Thread.sleep(180L);}return Boolean.FALSE;}catch(InfringingSourceException e){throw e;}catch(Exception ignored){return null;}finally{if(torrentId!=null&&!torrentId.isEmpty())try{delete(REST+"torrents/delete/"+encPath(torrentId),token);}catch(Exception ignored){}}}

    public String resolveMagnet(String magnet)throws Exception{
        if(magnet==null||!magnet.startsWith("magnet:"))throw new IOException("Not a magnet source");String token=ensureAccessToken();JSONObject added=postAuthorizedForm(REST+"torrents/addMagnet",token,"magnet",magnet);String torrentId=added.optString("id","");if(torrentId.isEmpty())throw new IOException("Real-Debrid did not return a torrent id");boolean keep=false;
        try{JSONObject info=getJson(REST+"torrents/info/"+encPath(torrentId),token,true);String fileId=chooseBestVideoFile(info.optJSONArray("files"));if(fileId==null)throw new IOException("No playable video file found in torrent");postAuthorizedFormAllowEmpty(REST+"torrents/selectFiles/"+encPath(torrentId),token,"files",fileId);JSONObject ready=null;for(int i=0;i<12;i++){info=getJson(REST+"torrents/info/"+encPath(torrentId),token,true);JSONArray links=info.optJSONArray("links");if("downloaded".equals(info.optString("status"))&&links!=null&&links.length()>0){ready=info;break;}String status=info.optString("status","");if("error".equals(status)||"magnet_error".equals(status)||"virus".equals(status)||"dead".equals(status))throw new IOException("Real-Debrid torrent status: "+status);Thread.sleep(650L);}if(ready==null)throw new IOException("Source is not instantly available in debrid cache");String restricted=ready.getJSONArray("links").getString(0);JSONObject unrestricted=postAuthorizedForm(REST+"unrestrict/link",token,"link",restricted);String download=unrestricted.optString("download","");if(download.isEmpty())throw new IOException("Real-Debrid returned no playable URL");keep=true;return download;}finally{if(!keep)try{delete(REST+"torrents/delete/"+encPath(torrentId),token);}catch(Exception ignored){}}
    }

    private String chooseBestVideoFile(JSONArray files)throws JSONException{if(files==null)return null;long best=-1;String bestId=null;for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);String path=f.optString("path","").toLowerCase(Locale.US);if(!isVideo(path))continue;long bytes=f.optLong("bytes",0);if(bytes>best){best=bytes;bestId=String.valueOf(f.optLong("id"));}}return bestId;}
    private static boolean isVideo(String p){return p.endsWith(".mkv")||p.endsWith(".mp4")||p.endsWith(".m4v")||p.endsWith(".webm")||p.endsWith(".avi")||p.endsWith(".ts")||p.endsWith(".m2ts");}
    private void saveTokenResponse(JSONObject token)throws Exception{String access=token.optString("access_token",""),refresh=token.optString("refresh_token","");long expires=token.optLong("expires_in",3600);if(access.isEmpty()||refresh.isEmpty())throw new IOException("Incomplete Real-Debrid token response");store.saveTokens(access,refresh,expires);}
    private JSONObject getJson(String url,String token,boolean authorized)throws Exception{Response r=request("GET",url,null,token,authorized);require2xx(r);return new JSONObject(r.body);}
    private JSONObject postFormJson(String url,String...pairs)throws Exception{Response r=request("POST",url,form(pairs),null,false);require2xx(r);return new JSONObject(r.body);}
    private JSONObject postAuthorizedForm(String url,String token,String...pairs)throws Exception{Response r=request("POST",url,form(pairs),token,true);require2xx(r);return r.body.trim().isEmpty()?new JSONObject():new JSONObject(r.body);}
    private void postAuthorizedFormAllowEmpty(String url,String token,String...pairs)throws Exception{Response r=request("POST",url,form(pairs),token,true);if(r.code==202||(r.code>=200&&r.code<300))return;require2xx(r);}
    private void delete(String url,String token)throws Exception{Response r=request("DELETE",url,null,token,true);require2xx(r);}
    private static final class Response{final int code;final String body;Response(int c,String b){code=c;body=b;}}
    private Response request(String method,String url,String body,String token,boolean authorized)throws IOException{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(CONNECT_TIMEOUT_MS);c.setReadTimeout(READ_TIMEOUT_MS);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","M00V13/0.1 AndroidTV");if(authorized&&token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");byte[] bytes=body.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}int code=c.getResponseCode();InputStream raw=code>=400?c.getErrorStream():c.getInputStream();String response=raw==null?"":read(raw);c.disconnect();return new Response(code,response);}
    private static String read(InputStream raw)throws IOException{try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))>=0){out.write(b,0,n);if(out.size()>2*1024*1024)throw new IOException("API response too large");}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static void require2xx(Response r)throws IOException{if(r.code>=200&&r.code<300)return;String message="HTTP "+r.code;int errorCode=-1;try{JSONObject o=new JSONObject(r.body);String e=o.optString("error","");errorCode=o.optInt("error_code",-1);if(!e.isEmpty())message+=": "+e;if(errorCode>=0)message+=" (code "+errorCode+")";}catch(Exception ignored){}String lower=(message+" "+r.body).toLowerCase(Locale.US);if(errorCode==35||lower.contains("infring"))throw new InfringingSourceException("Real-Debrid rejected infringing file");throw new IOException(message);}
    private static String form(String...pairs)throws Exception{if(pairs.length%2!=0)throw new IllegalArgumentException("form requires key/value pairs");List<String> parts=new ArrayList<>();for(int i=0;i<pairs.length;i+=2)parts.add(enc(pairs[i])+"="+enc(pairs[i+1]));return join(parts,"&");}
    private static String join(List<String> v,String sep){StringBuilder b=new StringBuilder();for(int i=0;i<v.size();i++){if(i>0)b.append(sep);b.append(v.get(i));}return b.toString();}
    private static String enc(String v)throws Exception{return URLEncoder.encode(v,"UTF-8");}
    private static String encPath(String v)throws Exception{return URLEncoder.encode(v,"UTF-8").replace("+","%20");}
    private static String shortMessage(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();String m=x.getMessage();return m==null?x.getClass().getSimpleName():m;}
}
