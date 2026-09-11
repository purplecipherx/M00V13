package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Minimal branded five-second launcher. Startup must never depend on permissions or instrumentation. */
public final class StartupActivity extends Activity {
    private static final long SPLASH_MS = 5000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean launched;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { getWindow().setWindowAnimations(0); } catch (Throwable ignored) {}
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        try { getWindow().setStatusBarColor(Color.BLACK); } catch (Throwable ignored) {}
        try { getWindow().setNavigationBarColor(Color.BLACK); } catch (Throwable ignored) {}
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) {}
        setContentView(splashView());
        main.postDelayed(this::launchHome, SPLASH_MS);
    }

    private FrameLayout splashView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.BLACK);
        try {
            image.setImageResource(R.drawable.m00v13_loading);
        } catch (Throwable first) {
            try {
                image.setImageResource(R.drawable.loading_cow_hd);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } catch (Throwable ignored) {}
        }
        root.addView(image, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void launchHome() {
        if (launched || isFinishing() || isDestroyed()) return;
        launched = true;
        Intent i = new Intent(this, MediaHubActivity.class);
        i.putExtra(MediaHubActivity.EXTRA_MODE, MediaHubActivity.MODE_HOME);
        startActivity(i);
        finish();
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
