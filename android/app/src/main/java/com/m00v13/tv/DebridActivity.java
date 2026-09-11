package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebridActivity extends Activity {
    private static final int BG=Color.rgb(9,5,15);
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private TextView status;private Button action;private volatile boolean cancelled;
    @Override protected void onCreate(Bundle b){super.onCreate(b);TvUi.disableWindowAnimations(this);render();}
    @Override protected void onDestroy(){cancelled=true;main.removeCallbacksAndMessages(null);executor.shutdownNow();super.onDestroy();}
    private void render(){DebridStore store=new DebridStore(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(64),dp(48),dp(64),dp(48));root.setBackgroundColor(BG);root.addView(text("Real-Debrid",32,true));TextView e=text("M00V13 uses Real-Debrid's official device-code login. No RD password is stored.",17,false);e.setTextColor(TvUi.MUTED);e.setPadding(0,dp(10),0,dp(22));root.addView(e);status=text(store.isConnected()?"Connected":"Not connected",21,true);status.setTextColor(store.isConnected()?TvUi.BLUE:Color.WHITE);status.setPadding(0,0,0,dp(18));root.addView(status);action=button(store.isConnected()?"Disconnect Real-Debrid":"Connect Real-Debrid");action.setOnClickListener(v->{if(new DebridStore(this).isConnected()){new DebridStore(this).disconnect();render();}else beginAuth();});root.addView(action,new LinearLayout.LayoutParams(-1,dp(62)));setContentView(root);}
    private void beginAuth(){action.setEnabled(false);status.setText("Requesting device code…");executor.submit(()->{try{RealDebridClient client=new RealDebridClient(this);RealDebridClient.DeviceCode code=client.beginDeviceAuth();runOnUiThread(()->showCode(code));long deadline=System.currentTimeMillis()+code.expiresInSeconds*1000L;int delay=Math.max(5,code.intervalSeconds),failures=0;while(!cancelled&&System.currentTimeMillis()<deadline){try{RealDebridClient.UserCredentials creds=client.pollUserCredentials(code.deviceCode);if(creds!=null){client.finishDeviceAuth(code,creds);runOnUiThread(this::showConnectedAndClose);return;}failures=0;delay=Math.max(5,code.intervalSeconds);}catch(RealDebridClient.RateLimitedException e){failures++;delay=Math.min(30,Math.max(e.retrySeconds,delay+5));DebugLog.append(this,"DEBRID","Device auth throttled; retry in "+delay+"s");final int wait=delay;runOnUiThread(()->status.setText("Real-Debrid asked us to slow down — still waiting. Retrying in "+wait+"s…"));}catch(RealDebridClient.TransientApiException|IOException e){failures++;delay=Math.min(30,Math.max(delay,5)+(failures>=3?5:0));DebugLog.append(this,"DEBRID","Device auth transient: "+message(e)+"; retry in "+delay+"s");final int wait=delay;runOnUiThread(()->status.setText("Real-Debrid/network is slow — code is still active. Retrying in "+wait+"s…"));}Thread.sleep(delay*1000L);}if(!cancelled)runOnUiThread(()->{status.setText("Device code expired. Get a fresh code and try again.");action.setEnabled(true);});}catch(Exception e){DebugLog.append(this,"DEBRID","Device auth fatal: "+message(e));if(!cancelled)runOnUiThread(()->{status.setText("Connection failed: "+message(e));action.setEnabled(true);});}});}
    private void showConnectedAndClose(){DebugLog.append(this,"DEBRID","Real-Debrid connected");status.setText("Connected");status.setTextColor(TvUi.BLUE);action.setEnabled(false);FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(238,7,5,12));TextView connected=text("CONNECTED",54,true);connected.setTextColor(TvUi.BLUE);connected.setGravity(Gravity.CENTER);overlay.addView(connected,new FrameLayout.LayoutParams(-1,-1));ViewGroup decor=(ViewGroup)getWindow().getDecorView();decor.addView(overlay,new ViewGroup.LayoutParams(-1,-1));main.postDelayed(()->{if(overlay.getParent() instanceof ViewGroup)((ViewGroup)overlay.getParent()).removeView(overlay);},1000L);main.postDelayed(()->{if(!isFinishing()&&!isDestroyed())finish();},5000L);}
    private void showCode(RealDebridClient.DeviceCode code){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(64),dp(48),dp(64),dp(48));root.setBackgroundColor(BG);root.addView(text("Connect Real-Debrid",32,true));TextView line=text("On your phone or computer, open:",18,false);line.setPadding(0,dp(18),0,dp(8));root.addView(line);root.addView(text(code.verificationUrl,22,true));TextView cv=text("Code:  "+code.userCode,34,true);cv.setTextColor(Color.rgb(216,180,254));cv.setPadding(0,dp(20),0,dp(20));root.addView(cv);Button open=button("Open verification page on this device");open.setOnClickListener(v->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(code.verificationUrl)));}catch(Exception ignored){}});root.addView(open,new LinearLayout.LayoutParams(-1,dp(60)));status=text("Waiting for authorization…",18,false);status.setPadding(0,dp(18),0,0);root.addView(status);action=open;setContentView(root);}
    private Button button(String s){Button b=TvUi.button(this,s);b.setTextSize(17);return b;}private TextView text(String s,int z,boolean bold){return TvUi.text(this,s,z,bold);}private static String message(Throwable t){Throwable x=t;while(x.getCause()!=null)x=x.getCause();return x.getMessage()==null?x.getClass().getSimpleName():x.getMessage();}private int dp(int v){return TvUi.dp(this,v);}
}
