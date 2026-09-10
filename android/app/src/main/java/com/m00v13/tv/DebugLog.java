package com.m00v13.tv;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DebugLog {
    private static final Object LOCK=new Object();
    private static final long MAX_BYTES=1024L*1024L;
    private DebugLog(){}
    public static File file(Context c){ return new File(c.getFilesDir(),"m00v13-debug.log"); }
    public static void append(Context c,String tag,String message){
        synchronized(LOCK){
            try{
                File f=file(c);
                if(f.length()>MAX_BYTES){ File old=new File(c.getFilesDir(),"m00v13-debug-prev.log"); if(old.exists()) old.delete(); f.renameTo(old); }
                String ts=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(new Date());
                String line=ts+" ["+tag+"] "+message+"\n";
                try(FileOutputStream out=new FileOutputStream(file(c),true)){ out.write(line.getBytes(StandardCharsets.UTF_8)); }
            }catch(Exception ignored){}
        }
    }
    public static void boot(Context c){ append(c,"BOOT","M00V13 start • "+Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE+" • SDK "+Build.VERSION.SDK_INT); }
}
