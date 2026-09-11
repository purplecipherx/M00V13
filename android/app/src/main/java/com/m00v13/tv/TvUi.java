package com.m00v13.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.SoundEffectConstants;
import android.widget.Button;
import android.widget.TextView;

/** Lightweight reusable 10-foot UI primitives. No focus/transition animations. */
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
        final boolean clickSounds = new AppSettingsStore(c).clickSounds();
        b.setText(label);
        b.setTextColor(WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);
        b.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        b.setStateListAnimator(null);
        b.setBackground(focusBackground(c,false,dp(c,8)));
        b.setOnFocusChangeListener((v, focused) -> {
            b.animate().cancel();
            b.setScaleX(1f); b.setScaleY(1f); b.setTranslationX(0f); b.setTranslationY(0f); b.setElevation(0f);
            b.setTextColor(focused ? BLUE : WHITE);
            b.setBackground(focusBackground(c,focused,dp(c,8)));
            if (focused && clickSounds) b.playSoundEffect(SoundEffectConstants.CLICK);
        });
        return b;
    }

    public static GradientDrawable focusBackground(Context c, boolean focused, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? Color.rgb(37,30,50) : CARD);
        g.setCornerRadius(radiusPx);
        g.setStroke(dp(c, focused ? 3 : 1), focused ? BLUE : Color.rgb(51,43,62));
        return g;
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

    public static void disableWindowAnimations(android.app.Activity activity) {
        try { activity.getWindow().setWindowAnimations(0); activity.overridePendingTransition(0,0); } catch (Throwable ignored) {}
        // Instrumentation must never be able to prevent an Activity from opening.
        try { PerfMonitor.attach(activity); } catch (Throwable ignored) {}
    }

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
}
