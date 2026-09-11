package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private AppSettingsStore prefs;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state){ super.onCreate(state); TvUi.disableWindowAnimations(this); prefs=new AppSettingsStore(this); setContentView(build()); }

    private View build(){
        ScreenProfile sp=ScreenProfile.detect(this);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setVerticalScrollBarEnabled(false);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,24),TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,40)); root.setBackgroundColor(TvUi.BG); scroll.addView(root);
        root.addView(TvUi.text(this,"M00V13 Settings",sp.mobile()?26:30,true));

        sub("Playback");
        toggle("Smart one-click playback",prefs.smartOneClickPlayback(),v->prefs.setSmartOneClickPlayback(v));
        TextView smart=TvUi.text(this,"Automatically chooses the highest-ranked compatible source; falls back to the source picker when needed.",14,false);smart.setTextColor(TvUi.MUTED);smart.setPadding(TvUi.dp(this,8),0,TvUi.dp(this,8),TvUi.dp(this,8));root.addView(smart);
        toggle("Automatic audio passthrough",prefs.automaticPassthrough(),v->prefs.setAutomaticPassthrough(v));
        action("Detect / test passthrough support",v->{String summary=AudioCapabilities.log(this);new AlertDialog.Builder(this).setTitle("Current audio output").setMessage(summary+"\n\nM00V13 uses Media3's platform audio sink. Encoded formats are passed through when the active Android output route reports direct support.").setPositiveButton("OK",null).show();});
        choice("Maximum video quality",new String[]{"4K","1080p","720p","480p"},prefs.maxQuality(),prefs::setMaxQuality);
        toggle("Exclude 3D releases",prefs.exclude3d(),v->prefs.setExclude3d(v));

        sub("Language & subtitles");
        TextView lang=TvUi.text(this,"Preferred language: "+new ProfileStore(this).preferredLanguage().toUpperCase(),16,false); lang.setTextColor(TvUi.MUTED); root.addView(lang);
        action("Profile language & subtitle defaults",v->startActivity(new Intent(this,ProfileActivity.class)));

        sub("Downloads");
        choice("Download episodes ahead",new String[]{"0","1","2","3","5","10"},String.valueOf(prefs.episodesAhead()),v->prefs.setEpisodesAhead(Integer.parseInt(v)));
        action("Per-download size limit: "+downloadLimitLabel(),v->showDownloadLimitDialog());
        toggle("Wi-Fi only automatic downloads",prefs.wifiOnlyDownloads(),v->prefs.setWifiOnlyDownloads(v));
        action("Downloads & storage",v->startActivity(new Intent(this,DownloadsActivity.class)));

        sub("Providers");
        toggle("Tier 1 providers",prefs.providerTier1(),v->prefs.setProviderTier(1,v));
        toggle("Tier 2 providers",prefs.providerTier2(),v->prefs.setProviderTier(2,v));
        toggle("Tier 3 providers",prefs.providerTier3(),v->prefs.setProviderTier(3,v));
        action("Configure individual providers",v->startActivity(new Intent(this,ProviderSettingsActivity.class)));
        action("Real-Debrid",v->startActivity(new Intent(this,DebridActivity.class)));
        action("Metadata & artwork",v->startActivity(new Intent(this,MetadataActivity.class)));

        sub("Interface");
        toggle("Navigation click sounds",prefs.clickSounds(),v->prefs.setClickSounds(v));
        toggle("Cow startup sound",prefs.startupCowSound(),v->prefs.setStartupCowSound(v));
        action("Set M00V13 as Home — launch after device startup",v->{try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception e){DebugLog.append(this,"SETTINGS","Home chooser unavailable: "+e.getMessage());}});

        sub("Files & diagnostics");
        action("Choose local media / download folder",v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,41);});
        String folder=getSharedPreferences("m00v13_storage",MODE_PRIVATE).getString("tree_uri","");
        if(!folder.isEmpty()){TextView f=TvUi.text(this,"Storage folder access saved ✓",14,false);f.setTextColor(TvUi.MUTED);root.addView(f);}
        action("App permissions",v->PermissionManager.show(this));
        action("Diagnostics & debug log",v->startActivity(new Intent(this,DiagnosticsActivity.class)));
        action("Support / Donate",v->startActivity(new Intent(this,DonateActivity.class)));
        return scroll;
    }

    private String downloadLimitLabel(){if(prefs.unlimitedDownloadSize())return "Unlimited";return prefs.maxDownloadGiB()+" GB";}
    private void showDownloadLimitDialog(){
        String[] options={"Use current free-space budget","Unlimited","2 GB","4 GB","8 GB","16 GB","Custom…"};
        new AlertDialog.Builder(this).setTitle("Per-download size limit").setItems(options,(d,w)->{
            if(w==0){getSharedPreferences("m00v13_app_settings",MODE_PRIVATE).edit().remove("max_download_gib").apply();rebuild();}
            else if(w==1){prefs.setMaxDownloadGiB(0);rebuild();}
            else if(w>=2&&w<=5){prefs.setMaxDownloadGiB(new int[]{2,4,8,16}[w-2]);rebuild();}
            else showCustomLimit();
        }).show();
    }
    private void showCustomLimit(){EditText e=new EditText(this);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);e.setHint("GB");new AlertDialog.Builder(this).setTitle("Custom download limit").setView(e).setPositiveButton("Save",(d,w)->{try{int gb=Integer.parseInt(e.getText().toString());if(gb>0)prefs.setMaxDownloadGiB(gb);}catch(Exception ignored){}rebuild();}).setNegativeButton("Cancel",null).show();}
    private void rebuild(){setContentView(build());}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==41&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData(); int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try{getContentResolver().takePersistableUriPermission(uri,flags);getSharedPreferences("m00v13_storage",MODE_PRIVATE).edit().putString("tree_uri",uri.toString()).apply();DebugLog.append(this,"STORAGE","Persisted media tree permission");rebuild();}
            catch(Exception e){DebugLog.append(this,"STORAGE","Could not persist tree permission: "+e.getMessage());}
        }
    }

    private void sub(String s){TextView h=TvUi.text(this,s,21,true);h.setTextColor(TvUi.PURPLE);h.setPadding(0,TvUi.dp(this,24),0,TvUi.dp(this,8));root.addView(h);}
    private void toggle(String label,boolean checked,java.util.function.Consumer<Boolean> save){CheckBox c=new CheckBox(this);c.setText(label);c.setTextColor(TvUi.WHITE);c.setTextSize(17);c.setChecked(checked);c.setFocusable(true);c.setButtonTintList(android.content.res.ColorStateList.valueOf(TvUi.PURPLE));c.setPadding(TvUi.dp(this,8),0,TvUi.dp(this,8),0);c.setOnCheckedChangeListener((b,v)->save.accept(v));c.setOnFocusChangeListener((v,f)->c.setTextColor(f?TvUi.BLUE:TvUi.WHITE));root.addView(c,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,TvUi.dp(this,52)));}
    private void action(String label,View.OnClickListener listener){Button b=TvUi.button(this,label);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnClickListener(listener);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,TvUi.dp(this,52));p.bottomMargin=TvUi.dp(this,6);root.addView(b,p);}
    private void choice(String label,String[] options,String current,java.util.function.Consumer<String> save){Button b=TvUi.button(this,label+":  "+current);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->new AlertDialog.Builder(this).setTitle(label).setItems(options,(d,w)->{save.accept(options[w]);b.setText(label+":  "+options[w]);}).show());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,TvUi.dp(this,52));p.bottomMargin=TvUi.dp(this,6);root.addView(b,p);}
}
