package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

public final class LoadingOverlay {
    private final FrameLayout root;

    private LoadingOverlay(Activity activity) {
        root = new FrameLayout(activity);
        root.setBackgroundColor(TvUi.BG);
        root.setClickable(true);
        root.setFocusable(true);
        root.setElevation(1000f);

        FrameLayout panel = new FrameLayout(activity);
        panel.setBackgroundColor(Color.rgb(16, 10, 24));
        FrameLayout.LayoutParams panelP = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(panel, panelP);

        ImageView image = new ImageView(activity);
        image.setImageResource(R.drawable.loading_cow);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setAdjustViewBounds(true);
        // Current bundled JPEG is only 320x180. Never stretch it fullscreen.
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(320, 180, Gravity.CENTER);
        panel.addView(image, ip);

        TextView status = TvUi.text(activity, "Searching…", 18, true);
        status.setTextColor(TvUi.PURPLE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(activity, 46), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        sp.bottomMargin = TvUi.dp(activity, 52);
        panel.addView(status, sp);

        TextView credit = TvUi.text(activity, "Coded by PurplecipherX", 13, false);
        credit.setTextColor(Color.argb(180, 245, 242, 248));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, TvUi.dp(activity, 36), Gravity.END | Gravity.BOTTOM);
        cp.rightMargin = TvUi.dp(activity, 22); cp.bottomMargin = TvUi.dp(activity, 14);
        panel.addView(credit, cp);
    }

    public static LoadingOverlay show(Activity activity) {
        LoadingOverlay overlay = new LoadingOverlay(activity);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(overlay.root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.root.requestFocus();
        return overlay;
    }

    public void hide() {
        ViewGroup parent = (ViewGroup) root.getParent();
        if (parent != null) parent.removeView(root);
    }
}
