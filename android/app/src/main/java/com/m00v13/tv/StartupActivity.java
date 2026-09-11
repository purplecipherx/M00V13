package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

public final class StartupActivity extends Activity {
    private static final int RC=7001;
    @Override protected void onCreate(Bundle state){super.onCreate(state);TvUi.disableWindowAnimations(this);boolean asked=getSharedPreferences("m00v13_startup",MODE_PRIVATE).getBoolean("permissions_explained",false);if(!asked&&!PermissionManager.allGranted(this)){new AlertDialog.Builder(this).setTitle("Media access").setMessage("M00V13 can play local video and use a folder you choose for downloads. Internet access does not need a popup. Android will ask for video/media permission now; folder access is chosen separately in Settings.").setPositiveButton("Continue",(d,w)->{getSharedPreferences("m00v13_startup",MODE_PRIVATE).edit().putBoolean("permissions_explained",true).apply();requestPermissions(PermissionManager.runtimePermissions(),RC);}).setNegativeButton("Not now",(d,w)->{getSharedPreferences("m00v13_startup",MODE_PRIVATE).edit().putBoolean("permissions_explained",true).apply();launch();}).setOnCancelListener(d->launch()).show();}else launch();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants){super.onRequestPermissionsResult(requestCode,permissions,grants);if(requestCode==RC)launch();}
    private void launch(){if(isFinishing())return;startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();overridePendingTransition(0,0);}
}
