package com.m00v13.tv;

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class GitHubDebugUploader {
    public static final String TOKEN_KEY="github_debug_token";
    private static final String API="https://api.github.com/repos/purplecipherx/M00V13/contents/debug/";
    private GitHubDebugUploader(){}

    public static String upload(Context context,File file)throws Exception{
        String token=SecretStore.get(context,TOKEN_KEY);if(token==null||token.trim().isEmpty())throw new IllegalStateException("GitHub debug token is not configured");
        byte[] bytes=read(file,512*1024);SimpleDateFormat f=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));String name="M00V13-"+Build.MODEL.replaceAll("[^A-Za-z0-9_-]","_")+"-"+f.format(new Date())+".log";
        JSONObject body=new JSONObject();body.put("message","Upload M00V13 device debug log");body.put("content",Base64.encodeToString(bytes,Base64.NO_WRAP));
        HttpURLConnection c=(HttpURLConnection)new URL(API+name).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestMethod("PUT");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+token.trim());c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("X-GitHub-Api-Version","2022-11-28");c.setRequestProperty("Content-Type","application/json; charset=UTF-8");byte[] payload=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(payload);}int code=c.getResponseCode();String response=readText(code>=400?c.getErrorStream():c.getInputStream(),256*1024);c.disconnect();if(code<200||code>=300)throw new IllegalStateException("GitHub HTTP "+code+safeError(response));return "debug/"+name;
    }
    private static byte[] read(File f,int max)throws Exception{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int total=0,n;while((n=in.read(b))>0){if(total+n>max){out.write(b,0,max-total);break;}out.write(b,0,n);total+=n;}return out.toByteArray();}}
    private static String readText(InputStream raw,int max)throws Exception{if(raw==null)return"";try(InputStream in=new BufferedInputStream(raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int total=0,n;while(total<max&&(n=in.read(b,0,Math.min(b.length,max-total)))>0){out.write(b,0,n);total+=n;}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private static String safeError(String body){try{String m=new JSONObject(body).optString("message","");return m.isEmpty()?"":" — "+m;}catch(Exception e){return"";}}
}
