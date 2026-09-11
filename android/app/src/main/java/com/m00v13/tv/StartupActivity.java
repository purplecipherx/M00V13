package com.m00v13.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Branded five-second startup screen that uses the same artwork as all blocking loads. */
public final class StartupActivity extends Activity {
    private static final int RC = 7001;
    private static final long SPLASH_MS = 5000L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService prewarm = Executors.newSingleThreadExecutor();
    private long splashStarted;
    private boolean splashDone;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TvUi.disableWindowAnimations(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        splashStarted = SystemClock.elapsedRealtime();
        setContentView(splashView());

        // Spend the splash time parsing local stores so Home has less work on first frame.
        prewarm.submit(() -> {
            try {
                CatalogStore c = new CatalogStore(this);
                c.all();
                DiscoveryStore d = new DiscoveryStore(this);
                d.get(DiscoveryStore.POPULAR_MOVIES);
                d.get(DiscoveryStore.POPULAR_TV);
            } catch (Throwable t) {
                DebugLog.append(this, "STARTUP", "Prewarm: " + t.getClass().getSimpleName());
            }
        });

        main.postDelayed(this::finishSplash, SPLASH_MS);
    }

    private FrameLayout splashView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        ImageView image = new ImageView(this);
        image.setImageResource(R.drawable.m00v13_loading);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.BLACK);
        root.addView(image, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private void finishSplash() {
        if (splashDone || isFinishing() || isDestroyed()) return;
        splashDone = true;
        boolean asked = getSharedPreferences("m00v13_startup", MODE_PRIVATE)
            .getBoolean("permissions_explained", false);
        if (!asked && !PermissionManager.allGranted(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Media access")
                .setMessage("M00V13 can play local video and use a folder you choose for downloads. Internet access does not need a popup. Android will ask for video/media permission now; folder access is chosen separately in Settings.")
                .setPositiveButton("Continue", (d, w) -> {
                    getSharedPreferences("m00v13_startup", MODE_PRIVATE).edit()
                        .putBoolean("permissions_explained", true).apply();
                    requestPermissions(PermissionManager.runtimePermissions(), RC);
                })
                .setNegativeButton("Not now", (d, w) -> {
                    getSharedPreferences("m00v13_startup", MODE_PRIVATE).edit()
                        .putBoolean("permissions_explained", true).apply();
                    launch();
                })
                .setOnCancelListener(d -> launch())
                .show();
        } else {
            launch();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == RC) launch();
    }

    private void launch() {
        if (isFinishing()) return;
        Intent i = new Intent(this, MediaHubActivity.class);
        i.putExtra(MediaHubActivity.EXTRA_MODE, MediaHubActivity.MODE_HOME);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        prewarm.shutdownNow();
        super.onDestroy();
    }
}
