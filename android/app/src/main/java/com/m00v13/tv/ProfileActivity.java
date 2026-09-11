package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;

/** Profile/list preferences styled like the main hub: fixed geometry, blue focus, no animation. */
public final class ProfileActivity extends Activity {
    private static final int BG=Color.rgb(4,3,12),PANEL=Color.rgb(13,8,27),BLUE=Color.rgb(44,157,255),PURPLE=Color.rgb(180,78,255),WHITE=Color.rgb(247,245,250),MUTED=Color.rgb(190,181,202);
    private ProfileStore profiles;private LinearLayout profileList;private TextView active;
    @Override protected void onCreate(Bundle b){super.onCreate(b);TvUi.disableWindowAnimations(this);profiles=new ProfileStore(this);setContentView(build());}
    private ScrollView build(){ScreenProfile sp=ScreenProfile.detect(this);ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(sp.sidePaddingDp),dp(28),dp(sp.sidePaddingDp),dp(36));root.setBackgroundColor(BG);scroll.addView(root);
        TextView h=text("My Lists & Profiles",30,true);root.addView(h);active=text("",15,false);active.setTextColor(MUTED);active.setPadding(0,dp(5),0,dp(16));root.addView(active);
        TextView ph=text("Profiles",20,true);ph.setTextColor(PURPLE);root.addView(ph);profileList=new LinearLayout(this);profileList.setOrientation(LinearLayout.VERTICAL);profileList.setPadding(0,dp(7),0,0);root.addView(profileList);renderProfiles();
        TextView createTitle=text("Create profile",20,true);createTitle.setTextColor(PURPLE);createTitle.setPadding(0,dp(22),0,dp(7));root.addView(createTitle);EditText name=new EditText(this);name.setHint("Profile name");name.setSingleLine(true);name.setTextColor(WHITE);name.setHintTextColor(MUTED);name.setTextSize(17);name.setImeOptions(EditorInfo.IME_ACTION_DONE);name.setBackgroundTintList(android.content.res.ColorStateList.valueOf(BLUE));root.addView(name,row(56));Button create=hubButton("Create and switch");create.setOnClickListener(v->{String n=name.getText().toString().trim();if(profiles.createProfile(n)){profiles.setActiveProfile(n);name.setText("");renderProfiles();}});root.addView(create,row(54));
        TextView langTitle=text("Preferred playback language",20,true);langTitle.setTextColor(PURPLE);langTitle.setPadding(0,dp(22),0,dp(7));root.addView(langTitle);LinearLayout langs=new LinearLayout(this);langs.setOrientation(LinearLayout.HORIZONTAL);for(String code:Arrays.asList("en","es","fr","de","it","pt","ja")){Button x=hubButton(code.toUpperCase());x.setGravity(Gravity.CENTER);x.setOnClickListener(v->{profiles.setPreferredLanguage(code);renderProfiles();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(50),1f);lp.setMarginEnd(dp(7));langs.addView(x,lp);}root.addView(langs);return scroll;}
    private void renderProfiles(){if(profileList==null)return;profileList.removeAllViews();active.setText("Active: "+profiles.activeProfile()+"  •  language "+profiles.preferredLanguage().toUpperCase());List<String> names=profiles.profiles();for(String n:names){boolean selected=n.equals(profiles.activeProfile());Button b=hubButton((selected?"✓  ":"")+n);if(selected){b.setTextColor(BLUE);b.setBackground(box(true));}b.setOnClickListener(v->{profiles.setActiveProfile(n);renderProfiles();});profileList.addView(b,row(54));}}
    private Button hubButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(16);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPadding(dp(15),0,dp(15),0);b.setFocusable(true);b.setStateListAnimator(null);b.setBackground(box(false));b.setOnFocusChangeListener((v,f)->{b.setTextColor(f?BLUE:WHITE);b.setBackground(box(f));if(f&&new AppSettingsStore(this).clickSounds())b.playSoundEffect(SoundEffectConstants.CLICK);});return b;}
    private GradientDrawable box(boolean focus){GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(7));g.setStroke(dp(focus?3:1),focus?BLUE:Color.rgb(42,34,53));return g;}private LinearLayout.LayoutParams row(int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(h));p.bottomMargin=dp(7);return p;}private TextView text(String s,int z,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(z);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}private int dp(int x){return TvUi.dp(this,x);}
}
