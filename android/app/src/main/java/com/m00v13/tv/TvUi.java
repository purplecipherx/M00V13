package com.m00v13.tv;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.widget.Button;
import android.widget.TextView;

/** Lightweight reusable 10-foot UI primitives. */
public final class TvUi {
    public static final int BG = Color.rgb(7, 5, 12);
    public static final int PANEL = Color.rgb(18, 14, 25);
    public static final int CARD = Color.rgb(29, 23, 39);
    public static final int PURPLE = Color.rgb(146, 67, 214);
    public static final int BLUE = Color.rgb(72, 164, 255);
    public static final int WHITE = Color.rgb(245, 242, 248);
    public static final int MUTED = Color.rgb(182, 173, 193);

    private TvUi() {}

    public static Button button(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);
        b.setBackgroundTintList(ColorStateList.valueOf(CARD));
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setOnFocusChangeListener((v, focused) -> {
            b.setTextColor(focused ? BLUE : WHITE);
            b.setBackgroundTintList(ColorStateList.valueOf(focused ? Color.rgb(43, 34, 55) : CARD));
            b.animate().scaleX(focused ? 1.055f : 1f).scaleY(focused ? 1.055f : 1f).setDuration(90).start();
            b.setElevation(dp(c, focused ? 10 : 0));
            if (focused && new AppSettingsStore(c).clickSounds()) b.playSoundEffect(SoundEffectConstants.CLICK);
        });
        return b;
    }

    public static TextView text(Context c, String value, int sp, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(WHITE);
        v.setTextSize(sp);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
