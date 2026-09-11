package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Full-screen branded loading surface used for every blocking load. */
public final class LoadingOverlay {
    private final FrameLayout root;

    private LoadingOverlay(Activity activity, String message) {
        root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);
        root.setClickable(true);
        root.setFocusable(true);
        root.setElevation(1000f);

        ImageView image = new ImageView(activity);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        image.setBackgroundColor(Color.BLACK);
        if (!SplashArtwork.apply(image)) {
            try {
                image.setImageResource(R.drawable.loading_cow_hd);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } catch (Throwable ignored) {}
        }
        root.addView(image, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (message != null && !message.trim().isEmpty()) {
            TextView status = TvUi.text(activity, message, 15, true);
            status.setTextColor(Color.WHITE);
            status.setGravity(Gravity.CENTER);
            status.setPadding(TvUi.dp(activity, 14), 0, TvUi.dp(activity, 14), 0);
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(Color.argb(185, 5, 3, 12));
            pill.setCornerRadius(TvUi.dp(activity, 10));
            pill.setStroke(TvUi.dp(activity, 1), Color.argb(210, 180, 78, 255));
            status.setBackground(pill);
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(activity, 42),
                Gravity.START | Gravity.BOTTOM);
            sp.leftMargin = TvUi.dp(activity, 24);
            sp.bottomMargin = TvUi.dp(activity, 22);
            root.addView(status, sp);
        }
    }

    public static LoadingOverlay show(Activity activity) { return show(activity, "Loading…"); }

    public static LoadingOverlay show(Activity activity, String message) {
        LoadingOverlay overlay = new LoadingOverlay(activity, message);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(overlay.root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.root.requestFocus();
        return overlay;
    }

    public void hide() {
        if (root.getParent() instanceof ViewGroup) {
            ((ViewGroup) root.getParent()).removeView(root);
        }
    }
}
