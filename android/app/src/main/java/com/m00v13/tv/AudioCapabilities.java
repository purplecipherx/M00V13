package com.m00v13.tv;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AudioCapabilities {
    public final boolean hdmiConnected,ac3,eac3,dts,dtsHd,trueHd,eac3Joc;
    public final int maxChannels;
    private AudioCapabilities(boolean h,boolean a,boolean e,boolean d,boolean dh,boolean t,boolean j,int m){hdmiConnected=h;ac3=a;eac3=e;dts=d;dtsHd=dh;trueHd=t;eac3Joc=j;maxChannels=m;}

    public static AudioCapabilities detect(Context context) {
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);boolean hdmi=false,ac3=false,eac3=false,dts=false,dtsHd=false,trueHd=false,joc=false;int maxChannels=2;
        if(manager!=null)for(AudioDeviceInfo device:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){int type=device.getType();boolean digital=type==AudioDeviceInfo.TYPE_HDMI||type==AudioDeviceInfo.TYPE_HDMI_ARC||type==AudioDeviceInfo.TYPE_HDMI_EARC;if(!digital)continue;hdmi=true;for(int c:device.getChannelCounts())maxChannels=Math.max(maxChannels,c);for(int encoding:device.getEncodings()){if(encoding==AudioFormat.ENCODING_AC3)ac3=true;else if(encoding==AudioFormat.ENCODING_E_AC3)eac3=true;else if(encoding==AudioFormat.ENCODING_DTS)dts=true;else if(encoding==AudioFormat.ENCODING_DTS_HD)dtsHd=true;else if(encoding==AudioFormat.ENCODING_DOLBY_TRUEHD)trueHd=true;else if(encoding==AudioFormat.ENCODING_E_AC3_JOC)joc=true;}}
        return new AudioCapabilities(hdmi,ac3,eac3,dts,dtsHd,trueHd,joc,maxChannels);
    }

    public String summary(){Set<String> codecs=new LinkedHashSet<>();if(ac3)codecs.add("DD");if(eac3)codecs.add("DD+");if(eac3Joc)codecs.add("Atmos/DD+");if(dts)codecs.add("DTS");if(dtsHd)codecs.add("DTS-HD");if(trueHd)codecs.add("TrueHD/Atmos");String list=codecs.isEmpty()?"PCM/unknown":String.join(", ",codecs);return(hdmiConnected?"HDMI":"Non-HDMI")+" • "+list+" • up to "+maxChannels+" channels";}

    /** Uses Android's direct-playback API when available, not just device-advertised encoding flags. */
    public static String directSupportSummary(Context context){
        if(Build.VERSION.SDK_INT<29)return "Direct bitstream API unavailable on Android "+Build.VERSION.SDK_INT;
        StringBuilder b=new StringBuilder();int[] enc={AudioFormat.ENCODING_AC3,AudioFormat.ENCODING_E_AC3,AudioFormat.ENCODING_DTS,AudioFormat.ENCODING_DTS_HD,AudioFormat.ENCODING_DOLBY_TRUEHD,AudioFormat.ENCODING_E_AC3_JOC};String[] names={"DD","DD+","DTS","DTS-HD","TrueHD","Atmos/DD+"};
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
        for(int i=0;i<enc.length;i++){boolean ok=false;try{AudioFormat f=new AudioFormat.Builder().setEncoding(enc[i]).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_5POINT1).build();ok=AudioTrack.isDirectPlaybackSupported(f,attrs);}catch(Exception ignored){}if(i>0)b.append(" • ");b.append(names[i]).append(ok?" ✓":" ✕");}
        String s=b.toString();DebugLog.append(context,"AUDIO","Direct support: "+s);return s;
    }
    public static String log(Context context){AudioCapabilities c=detect(context);String summary=c.summary()+"\n"+directSupportSummary(context);DebugLog.append(context,"AUDIO","Capabilities: "+summary.replace('\n',' '));return summary;}
}
