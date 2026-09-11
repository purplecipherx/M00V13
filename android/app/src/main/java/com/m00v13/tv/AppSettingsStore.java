package com.m00v13.tv;

import android.content.Context;
import android.content.SharedPreferences;

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
    private static String safe(String s){ return s==null?"unknown":s.replaceAll("[^A-Za-z0-9_-]","_"); }
}
