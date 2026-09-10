package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.StatFs;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public final class DiagnosticsActivity extends Activity {
    private TextView log;
    @Override protected void onCreate(Bundle state){ super.onCreate(state); DebugLog.append(this,"DIAG","Diagnostics opened"); setContentView(build()); }
    private ScrollView build(){
        ScreenProfile sp=ScreenProfile.detect(this); ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,24),TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,32)); root.setBackgroundColor(TvUi.BG); scroll.addView(root);
        root.addView(TvUi.text(this,"Diagnostics",30,true));
        TextView device=TvUi.text(this,deviceSummary(sp),15,false); device.setTextColor(TvUi.MUTED); device.setPadding(0,TvUi.dp(this,8),0,TvUi.dp(this,14)); root.addView(device);
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh=TvUi.button(this,"Refresh"); refresh.setOnClickListener(v->load()); actions.addView(refresh,new LinearLayout.LayoutParams(TvUi.dp(this,150),TvUi.dp(this,50)));
        Button export=TvUi.button(this,"Export debug log"); export.setOnClickListener(v->export()); LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(TvUi.dp(this,210),TvUi.dp(this,50)); ep.setMarginStart(TvUi.dp(this,8)); actions.addView(export,ep); root.addView(actions);
        log=TvUi.text(this,"",13,false); log.setTextIsSelectable(true); log.setGravity(Gravity.START); log.setPadding(TvUi.dp(this,12),TvUi.dp(this,12),TvUi.dp(this,12),TvUi.dp(this,12)); log.setBackgroundColor(TvUi.PANEL); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,TvUi.dp(this,420)); lp.topMargin=TvUi.dp(this,12); root.addView(log,lp); load(); return scroll;
    }
    private String deviceSummary(ScreenProfile sp){ StatFs fs=new StatFs(getFilesDir().getAbsolutePath()); long free=fs.getAvailableBytes()/StoragePolicy.MIB; return "Layout: "+sp.kind+" • "+sp.widthPx+"×"+sp.heightPx+" • Free "+free+" MiB • RD "+(new DebridStore(this).isConnected()?"connected":"off")+" • Metadata "+(new MetadataStore(this).isConfigured()?"connected":"off"); }
    private void load(){ try{ File f=DebugLog.file(this); log.setText(f.exists()?read(f):"No log entries yet."); }catch(Exception e){ log.setText("Unable to read log: "+e.getMessage()); } }
    private String read(File file)throws Exception{ try(FileInputStream in=new FileInputStream(file); ByteArrayOutputStream out=new ByteArrayOutputStream()){ byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)out.write(b,0,n); return new String(out.toByteArray(),StandardCharsets.UTF_8); } }
    private void export(){ try{ Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE,"M00V13-debug.log"); startActivityForResult(i,51); }catch(Exception e){ DebugLog.append(this,"DIAG","Export failed: "+e); } }
    @Override protected void onActivityResult(int request,int result,Intent data){ super.onActivityResult(request,result,data); if(request==51&&result==RESULT_OK&&data!=null&&data.getData()!=null){ try(java.io.InputStream in=new java.io.FileInputStream(DebugLog.file(this)); java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){ if(out!=null){ byte[] b=new byte[8192]; int n; while((n=in.read(b))>0)out.write(b,0,n); } }catch(Exception e){ DebugLog.append(this,"DIAG","Export write failed: "+e); } } }
}
