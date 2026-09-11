package com.m00v13.tv;

import android.app.Activity;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public final class ProviderSettingsActivity extends Activity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);TvUi.disableWindowAnimations(this);setContentView(build());}
    private ScrollView build(){
        ScreenProfile sp=ScreenProfile.detect(this);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,24),TvUi.dp(this,sp.sidePaddingDp),TvUi.dp(this,36));root.setBackgroundColor(TvUi.BG);scroll.addView(root);
        root.addView(TvUi.text(this,"Providers",30,true));TextView hint=TvUi.text(this,"Tier 1 and Tier 2 are enabled by default. Disable individual providers here; Tier 3 remains off unless you enable it.",15,false);hint.setTextColor(TvUi.MUTED);hint.setPadding(0,TvUi.dp(this,8),0,TvUi.dp(this,16));root.addView(hint);
        AppSettingsStore settings=new AppSettingsStore(this);List<NativeProviderDefinition> all=NativeProviderDefinition.load(this,0);int tier=0;
        for(NativeProviderDefinition p:all){if(p.tier!=tier){tier=p.tier;TextView h=TvUi.text(this,"Tier "+tier,21,true);h.setTextColor(TvUi.PURPLE);h.setPadding(0,TvUi.dp(this,18),0,TvUi.dp(this,6));root.addView(h);}CheckBox c=new CheckBox(this);c.setText(p.name);c.setTextColor(TvUi.WHITE);c.setTextSize(17);c.setFocusable(true);c.setChecked(settings.providerEnabled(p.id,p.tier));c.setButtonTintList(android.content.res.ColorStateList.valueOf(TvUi.PURPLE));c.setOnCheckedChangeListener((b,v)->settings.setProviderEnabled(p.id,v));c.setOnFocusChangeListener((v,f)->c.setTextColor(f?TvUi.BLUE:TvUi.WHITE));root.addView(c,new LinearLayout.LayoutParams(-1,TvUi.dp(this,50)));}
        return scroll;
    }
}
