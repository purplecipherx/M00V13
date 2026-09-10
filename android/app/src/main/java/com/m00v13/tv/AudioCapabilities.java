package com.m00v13.tv;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AudioCapabilities {
    public final boolean hdmiConnected;
    public final boolean ac3;
    public final boolean eac3;
    public final boolean dts;
    public final boolean dtsHd;
    public final boolean trueHd;
    public final boolean eac3Joc;
    public final int maxChannels;

    private AudioCapabilities(boolean hdmi, boolean ac3, boolean eac3, boolean dts,
                              boolean dtsHd, boolean trueHd, boolean eac3Joc, int maxChannels) {
        this.hdmiConnected=hdmi; this.ac3=ac3; this.eac3=eac3; this.dts=dts;
        this.dtsHd=dtsHd; this.trueHd=trueHd; this.eac3Joc=eac3Joc; this.maxChannels=maxChannels;
    }

    public static AudioCapabilities detect(Context context) {
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        boolean hdmi=false, ac3=false, eac3=false, dts=false, dtsHd=false, trueHd=false, joc=false;
        int maxChannels=2;
        if(manager!=null){
            AudioDeviceInfo[] devices=manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for(AudioDeviceInfo device:devices){
                int type=device.getType();
                boolean digital=type==AudioDeviceInfo.TYPE_HDMI || type==AudioDeviceInfo.TYPE_HDMI_ARC || type==AudioDeviceInfo.TYPE_HDMI_EARC;
                if(!digital) continue;
                hdmi=true;
                for(int c:device.getChannelCounts()) maxChannels=Math.max(maxChannels,c);
                for(int encoding:device.getEncodings()){
                    if(encoding==AudioFormat.ENCODING_AC3) ac3=true;
                    else if(encoding==AudioFormat.ENCODING_E_AC3) eac3=true;
                    else if(encoding==AudioFormat.ENCODING_DTS) dts=true;
                    else if(encoding==AudioFormat.ENCODING_DTS_HD) dtsHd=true;
                    else if(encoding==AudioFormat.ENCODING_DOLBY_TRUEHD) trueHd=true;
                    else if(encoding==AudioFormat.ENCODING_E_AC3_JOC) joc=true;
                }
            }
        }
        return new AudioCapabilities(hdmi,ac3,eac3,dts,dtsHd,trueHd,joc,maxChannels);
    }

    public String summary(){
        Set<String> codecs=new LinkedHashSet<>();
        if(ac3)codecs.add("DD"); if(eac3)codecs.add("DD+"); if(eac3Joc)codecs.add("Atmos/DD+");
        if(dts)codecs.add("DTS"); if(dtsHd)codecs.add("DTS-HD"); if(trueHd)codecs.add("TrueHD/Atmos");
        String list=codecs.isEmpty()?"PCM/unknown":String.join(", ",codecs);
        return (hdmiConnected?"HDMI":"Non-HDMI")+" • "+list+" • up to "+maxChannels+" channels";
    }

    public static String log(Context context){
        AudioCapabilities c=detect(context);
        String summary=c.summary();
        DebugLog.append(context,"AUDIO","Capabilities: "+summary);
        return summary;
    }
}
