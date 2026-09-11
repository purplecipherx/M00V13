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

/** Minimal branded launcher. Show the cow immediately, then hand off as soon as Home can start. */
public final class StartupActivity extends Activity {
    private static final long MIN_SPLASH_MS = 350L;
    private static final long MAX_SPLASH_MS = 5000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean launched;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { getWindow().setWindowAnimations(0); } catch (Throwable ignored) {}
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        try { getWindow().setStatusBarColor(Color.BLACK); } catch (Throwable ignored) {}
        try { getWindow().setNavigationBarColor(Color.BLACK); } catch (Throwable ignored) {}
        try { getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); } catch (Throwable ignored) {}

        FrameLayout splash = splashView();
        setContentView(splash);

        // Do not sit on a black screen for five seconds. Give the supplied artwork one frame,
        // then open Home. MAX_SPLASH_MS is only a safety bound if the short handoff is delayed.
        splash.postDelayed(this::launchHome, MIN_SPLASH_MS);
        main.postDelayed(this::launchHome, MAX_SPLASH_MS);
    }

    private FrameLayout splashView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.BLACK);

        if (!SplashArtwork.apply(image)) {
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
        try {
            Intent i = new Intent(this, MediaHubActivity.class);
            i.putExtra(MediaHubActivity.EXTRA_MODE, MediaHubActivity.MODE_HOME);
            startActivity(i);
            finish();
            try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            launched = false;
            DebugLog.append(this, "STARTUP", "Home launch failed: " + t.getClass().getSimpleName());
        }
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
