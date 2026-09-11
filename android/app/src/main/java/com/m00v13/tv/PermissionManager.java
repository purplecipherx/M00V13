package com.m00v13.tv;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.List;

public final class PermissionManager {
    private static final int REQUEST_CODE=7001;
    private PermissionManager(){}

    public static String[] runtimePermissions(){
        List<String> out=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=33) out.add(Manifest.permission.READ_MEDIA_VIDEO);
        else out.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        return out.toArray(new String[0]);
    }

    public static boolean allGranted(Activity a){for(String p:runtimePermissions())if(a.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}

    public static void requestIfNeeded(Activity a){
        if(allGranted(a))return;
        a.requestPermissions(runtimePermissions(),REQUEST_CODE);
    }

    public static void show(Activity a){
        StringBuilder m=new StringBuilder("M00V13 uses:\n\n• Internet access — metadata, providers, debrid and streaming.\n• Video/media read access — local playback when you choose media on the device.\n• Folder access — granted separately with Android's folder picker for downloads/local media.\n\nRuntime media permission: ").append(allGranted(a)?"GRANTED":"NOT GRANTED");
        new AlertDialog.Builder(a).setTitle("App permissions").setMessage(m.toString()).setPositiveButton(allGranted(a)?"OK":"Grant media access",(d,w)->requestIfNeeded(a)).setNegativeButton("Close",null).show();
    }
}
