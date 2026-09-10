package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

public final class LoadingOverlay {
    private final FrameLayout root;

    private LoadingOverlay(Activity activity) {
        root = new FrameLayout(activity);
        root.setBackgroundColor(Color.rgb(9, 5, 15));
        root.setClickable(true);
        root.setFocusable(true);
        root.setElevation(1000f);

        ImageView image = new ImageView(activity);
        image.setImageResource(R.drawable.loading_cow);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        root.addView(image, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public static LoadingOverlay show(Activity activity) {
        LoadingOverlay overlay = new LoadingOverlay(activity);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        decor.addView(overlay.root, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.root.requestFocus();
        return overlay;
    }

    public void hide() {
        ViewGroup parent = (ViewGroup) root.getParent();
        if (parent != null) parent.removeView(root);
    }
}
