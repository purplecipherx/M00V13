package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.StatFs;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DiagnosticsActivity extends Activity {
    private TextView log;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    @Override protected void onCreate(Bundle state){super.onCreate(state);TvUi.disableWindowAnimations(this);DebugLog.append(this,"DIAG","Diagnostics opened");setContentView(build());}
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
    private ScrollView build(){
        ScreenProfile sp=ScreenProfile.detect(this);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,24),TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,32));root.setBackgroundColor(TvUi.BG);scroll.addView(root);
        root.addView(TvUi.text(this,"Diagnostics",30,true));TextView device=TvUi.text(this,deviceSummary(sp),15,false);device.setTextColor(TvUi.MUTED);device.setPadding(0,TvUi.dp(this,8),0,TvUi.dp(this,14));root.addView(device);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(sp.mobile()?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        addAction(actions,"Refresh",v->load(),sp);addAction(actions,"Export debug log",v->export(),sp);addAction(actions,"Upload to repo",v->upload(),sp);root.addView(actions);
        log=TvUi.text(this,"",13,false);log.setTextIsSelectable(true);log.setGravity(Gravity.START);log.setPadding(TvUi.dp(this,12),TvUi.dp(this,12),TvUi.dp(this,12),TvUi.dp(this,12));log.setBackgroundColor(TvUi.PANEL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,TvUi.dp(this,420));lp.topMargin=TvUi.dp(this,12);root.addView(log,lp);load();return scroll;
    }
    private void addAction(LinearLayout row,String label,android.view.View.OnClickListener l,ScreenProfile sp){Button b=TvUi.button(this,label);b.setOnClickListener(l);LinearLayout.LayoutParams p=sp.mobile()?new LinearLayout.LayoutParams(-1,TvUi.dp(this,50)):new LinearLayout.LayoutParams(TvUi.dp(this,label.length()>12?190:145),TvUi.dp(this,50));p.setMarginEnd(TvUi.dp(this,8));p.bottomMargin=TvUi.dp(this,6);row.addView(b,p);}
    private String deviceSummary(ScreenProfile sp){StatFs fs=new StatFs(getFilesDir().getAbsolutePath());long free=fs.getAvailableBytes()/StoragePolicy.MIB;return "Layout: "+sp.kind+" • "+sp.widthPx+"×"+sp.heightPx+" • Free "+free+" MiB • RD "+(new DebridStore(this).isConnected()?"connected":"off")+" • Metadata "+(new MetadataStore(this).isConfigured()?"TMDB":"keyless");}
    private void load(){try{File f=DebugLog.file(this);log.setText(f.exists()?read(f):"No log entries yet.");}catch(Exception e){log.setText("Unable to read log: "+e.getMessage());}}
    private String read(File file)throws Exception{try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private void export(){try{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"M00V13-debug.log");startActivityForResult(i,51);}catch(Exception e){DebugLog.append(this,"DIAG","Export failed: "+e);}}
    private void upload(){String token=SecretStore.get(this,GitHubDebugUploader.TOKEN_KEY);if(token==null||token.isEmpty()){EditText input=new EditText(this);input.setHint("Fine-grained GitHub token");input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("One-time GitHub setup").setMessage("Use a fine-grained token limited to purplecipherx/M00V13 with Contents: Read and write. It is encrypted with Android Keystore and is never written to the debug log.").setView(input).setPositiveButton("Save & upload",(d,w)->{try{String v=input.getText().toString().trim();if(v.isEmpty())return;SecretStore.put(this,GitHubDebugUploader.TOKEN_KEY,v);doUpload();}catch(Exception e){show("Could not save token: "+e.getMessage());}}).setNegativeButton("Cancel",null).show();return;}doUpload();}
    private void doUpload(){show("Uploading debug log…");executor.submit(()->{try{String path=GitHubDebugUploader.upload(this,DebugLog.file(this));runOnUiThread(()->show("Uploaded: "+path));}catch(Exception e){DebugLog.append(this,"DIAG","Repo upload failed: "+e.getMessage());runOnUiThread(()->show("Upload failed: "+e.getMessage()));}});}
    private void show(String m){if(log!=null)log.setText(m+"\n\n"+log.getText());}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==51&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(java.io.InputStream in=new java.io.FileInputStream(DebugLog.file(this));java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){if(out!=null){byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);}}catch(Exception e){DebugLog.append(this,"DIAG","Export write failed: "+e);}}}
}
