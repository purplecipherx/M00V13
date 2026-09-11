package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AppSettingsStore {
    private static final String PREFS = "m00v13_app_settings";
    private final SharedPreferences p;
    private final Context context;
    public AppSettingsStore(Context c){ context=c.getApplicationContext(); p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }

    public boolean launchOnBoot(){ return p.getBoolean("launch_on_boot",false); }
    public void setLaunchOnBoot(boolean v){ p.edit().putBoolean("launch_on_boot",v).apply(); }
    public boolean clickSounds(){ return p.getBoolean("click_sounds",true); }
    public void setClickSounds(boolean v){ p.edit().putBoolean("click_sounds",v).apply(); }
    public boolean startupCowSound(){ return p.getBoolean("startup_cow_sound",false); }
    public void setStartupCowSound(boolean v){ p.edit().putBoolean("startup_cow_sound",v).apply(); }
    public boolean automaticPassthrough(){ return p.getBoolean("auto_passthrough",true); }
    public void setAutomaticPassthrough(boolean v){ p.edit().putBoolean("auto_passthrough",v).apply(); }
    public boolean smartOneClickPlayback(){ return p.getBoolean("smart_one_click_playback",true); }
    public void setSmartOneClickPlayback(boolean v){ p.edit().putBoolean("smart_one_click_playback",v).apply(); }
    public String maxQuality(){ return p.getString("max_quality","4K"); }
    public void setMaxQuality(String v){ p.edit().putString("max_quality",v).apply(); }
    public boolean exclude3d(){ return p.getBoolean("exclude_3d",true); }
    public void setExclude3d(boolean v){ p.edit().putBoolean("exclude_3d",v).apply(); }

    /** 0 means unlimited. If unset, default to the current usable free-space budget. */
    public int maxDownloadGiB(){
        if(p.contains("max_download_gib")) return Math.max(0,p.getInt("max_download_gib",0));
        long free=StoragePolicy.availableBytes(context.getFilesDir());
        long usable=Math.max(0L,free-StoragePolicy.SYSTEM_RESERVE_BYTES-StoragePolicy.DOWNLOAD_HEADROOM_BYTES);
        return (int)Math.max(1L,usable/(1024L*StoragePolicy.MIB));
    }
    public void setMaxDownloadGiB(int v){ p.edit().putInt("max_download_gib",Math.max(0,v)).apply(); }
    public boolean unlimitedDownloadSize(){ return p.contains("max_download_gib") && p.getInt("max_download_gib",0)==0; }

    public int episodesAhead(){ return p.getInt("episodes_ahead",2); }
    public void setEpisodesAhead(int v){ p.edit().putInt("episodes_ahead",Math.max(0,Math.min(20,v))).apply(); }
    public boolean wifiOnlyDownloads(){ return p.getBoolean("wifi_downloads",true); }
    public void setWifiOnlyDownloads(boolean v){ p.edit().putBoolean("wifi_downloads",v).apply(); }

    public boolean providerTier1(){ return p.getBoolean("provider_tier1",true); }
    public boolean providerTier2(){ return p.getBoolean("provider_tier2",true); }
    public boolean providerTier3(){ return p.getBoolean("provider_tier3",false); }
    public void setProviderTier(int tier,boolean v){ p.edit().putBoolean("provider_tier"+tier,v).apply(); }

    /** Providers default enabled in tiers 1-2 and disabled in tier 3. */
    public boolean providerEnabled(String id,int tier){
        String key="provider_"+safe(id);
        return p.contains(key)?p.getBoolean(key,true):tier<=2;
    }
    public void setProviderEnabled(String id,boolean enabled){ p.edit().putBoolean("provider_"+safe(id),enabled).apply(); }

    /** Optional explicit provider order. Unknown/new providers naturally fall behind known ordered entries. */
    public List<String> providerOrder(){
        String raw=p.getString("provider_order","");
        if(raw==null||raw.trim().isEmpty())return Collections.emptyList();
        ArrayList<String> out=new ArrayList<>();
        for(String s:raw.split(",")){String clean=s.trim();if(!clean.isEmpty()&&!out.contains(clean))out.add(clean);}
        return out;
    }
    public void setProviderOrder(List<String> ids){
        if(ids==null||ids.isEmpty()){p.edit().remove("provider_order").apply();return;}
        StringBuilder b=new StringBuilder();
        for(String id:ids){if(id==null||id.trim().isEmpty())continue;if(b.length()>0)b.append(',');b.append(id.trim());}
        if(b.length()==0)p.edit().remove("provider_order").apply();else p.edit().putString("provider_order",b.toString()).apply();
    }
    public void resetProviderOrder(){p.edit().remove("provider_order").apply();}

    /** 0 keeps adaptive SearchConcurrency behavior; otherwise use a bounded fixed worker target. */
    public int searchWorkerOverride(){return Math.max(0,Math.min(SearchConcurrency.MAX_THREADS,p.getInt("search_worker_override",0)));}
    public void setSearchWorkerOverride(int workers){p.edit().putInt("search_worker_override",Math.max(0,Math.min(SearchConcurrency.MAX_THREADS,workers))).apply();}

    /** Keep the fast Real-Debrid cache hint optional so source-list latency can be prioritized. */
    public boolean verifyDebridCache(){return p.getBoolean("verify_debrid_cache",true);}
    public void setVerifyDebridCache(boolean enabled){p.edit().putBoolean("verify_debrid_cache",enabled).apply();}

    public void resetWaterfallPolicy(){
        p.edit()
            .remove("provider_order")
            .remove("search_worker_override")
            .remove("verify_debrid_cache")
            .putBoolean("provider_tier1",true)
            .putBoolean("provider_tier2",true)
            .putBoolean("provider_tier3",false)
            .apply();
    }

    private static String safe(String s){ return s==null?"unknown":s.replaceAll("[^A-Za-z0-9_-]","_"); }
}
