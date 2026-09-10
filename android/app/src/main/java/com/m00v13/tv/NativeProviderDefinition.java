package com.m00v13.tv;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NativeProviderDefinition {
    public final String id,name,responseType,searchPath,queryParam,queryString,rowSelector,titleSelector,titleAttribute,detailsSelector,detailsAttribute,seedersSelector,sizeSelector,rowMagnetSelector,detailMagnetSelector;
    public final String jsonRowsPath,jsonExpandArrayPath,jsonTitlePath,jsonSeedersPath,jsonSizePath,jsonInfoHashPath,jsonQualityPath,jsonCodecPath,jsonAudioPath,jsonUrlPath;
    public final List<String> mirrors;
    public final int maxResults;

    public NativeProviderDefinition(String id,String name,List<String> mirrors,String responseType,String searchPath,String queryParam,String queryString,String rowSelector,String titleSelector,String titleAttribute,String detailsSelector,String detailsAttribute,String seedersSelector,String sizeSelector,String rowMagnetSelector,String detailMagnetSelector,String jsonRowsPath,String jsonExpandArrayPath,String jsonTitlePath,String jsonSeedersPath,String jsonSizePath,String jsonInfoHashPath,String jsonQualityPath,String jsonCodecPath,String jsonAudioPath,String jsonUrlPath,int maxResults){
        this.id=empty(id)?"unknown":id; this.name=empty(name)?this.id:name;
        this.mirrors=mirrors==null?Collections.emptyList():Collections.unmodifiableList(new ArrayList<>(mirrors));
        this.responseType=empty(responseType)?"html":responseType; this.searchPath=nz(searchPath); this.queryParam=nz(queryParam); this.queryString=nz(queryString);
        this.rowSelector=nz(rowSelector); this.titleSelector=nz(titleSelector); this.titleAttribute=nz(titleAttribute); this.detailsSelector=empty(detailsSelector)?this.titleSelector:detailsSelector; this.detailsAttribute=empty(detailsAttribute)?"href":detailsAttribute;
        this.seedersSelector=nz(seedersSelector); this.sizeSelector=nz(sizeSelector); this.rowMagnetSelector=nz(rowMagnetSelector); this.detailMagnetSelector=nz(detailMagnetSelector);
        this.jsonRowsPath=nz(jsonRowsPath); this.jsonExpandArrayPath=nz(jsonExpandArrayPath); this.jsonTitlePath=nz(jsonTitlePath); this.jsonSeedersPath=nz(jsonSeedersPath); this.jsonSizePath=nz(jsonSizePath); this.jsonInfoHashPath=nz(jsonInfoHashPath); this.jsonQualityPath=nz(jsonQualityPath); this.jsonCodecPath=nz(jsonCodecPath); this.jsonAudioPath=nz(jsonAudioPath); this.jsonUrlPath=nz(jsonUrlPath); this.maxResults=Math.max(1,maxResults);
    }
    public boolean isJson(){return "json".equalsIgnoreCase(responseType);}

    public static List<NativeProviderDefinition> load(Context context){
        ArrayList<NativeProviderDefinition> out=new ArrayList<>();
        try(InputStream in=context.getAssets().open("cardigann_providers.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)bytes.write(b,0,n);
            JSONObject root=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));JSONArray providers=root.optJSONArray("providers");
            if(providers!=null)for(int i=0;i<providers.length();i++){
                JSONObject o=providers.optJSONObject(i);if(o==null)continue;
                NativeProviderDefinition d=new NativeProviderDefinition(o.optString("id"),o.optString("name"),strings(o.optJSONArray("mirrors")),o.optString("responseType","html"),o.optString("searchPath"),o.optString("queryParam"),o.optString("queryString"),o.optString("rowSelector"),o.optString("titleSelector"),o.optString("titleAttribute"),o.optString("detailsSelector"),o.optString("detailsAttribute","href"),o.optString("seedersSelector"),o.optString("sizeSelector"),o.optString("rowMagnetSelector"),o.optString("detailMagnetSelector",o.optString("magnetSelector")),o.optString("jsonRowsPath"),o.optString("jsonExpandArrayPath"),o.optString("jsonTitlePath"),o.optString("jsonSeedersPath"),o.optString("jsonSizePath"),o.optString("jsonInfoHashPath"),o.optString("jsonQualityPath"),o.optString("jsonCodecPath"),o.optString("jsonAudioPath"),o.optString("jsonUrlPath"),o.optInt("maxResults",16));
                boolean validJson=d.isJson()&&!d.jsonRowsPath.isEmpty()&&!d.jsonTitlePath.isEmpty()&&!d.jsonInfoHashPath.isEmpty();
                boolean hasMagnet=!d.rowMagnetSelector.isEmpty()||!d.detailMagnetSelector.isEmpty();boolean validHtml=!d.isJson()&&!d.rowSelector.isEmpty()&&!d.titleSelector.isEmpty()&&hasMagnet;
                if(!d.mirrors.isEmpty()&&!d.searchPath.isEmpty()&&(validJson||validHtml))out.add(d);
            }
        }catch(Exception ignored){}
        return Collections.unmodifiableList(out);
    }
    private static List<String> strings(JSONArray a){if(a==null)return Collections.emptyList();ArrayList<String> out=new ArrayList<>();for(int i=0;i<a.length();i++){String s=a.optString(i,"");if(!s.isEmpty())out.add(s);}return out;}
    private static boolean empty(String s){return s==null||s.isEmpty();} private static String nz(String s){return s==null?"":s;}
}
