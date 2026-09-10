package com.m00v13.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DebridActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private Button action;
    private volatile boolean cancelled;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override protected void onDestroy() {
        cancelled = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void render() {
        DebridStore store = new DebridStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(64), dp(48), dp(64), dp(48));
        root.setBackgroundColor(BG);

        root.addView(text("Debrid", 32, true));
        TextView explainer = text("M00V13 resolves torrent sources through Real-Debrid before playback. Login uses the official device-code flow; no password is stored in M00V13.", 17, false);
        explainer.setTextColor(Color.rgb(205, 190, 220));
        explainer.setPadding(0, dp(10), 0, dp(22));
        root.addView(explainer);

        status = text(store.isConnected() ? "Real-Debrid: connected" : "Real-Debrid: not connected", 21, true);
        status.setPadding(0, 0, 0, dp(18));
        root.addView(status);

        action = button(store.isConnected() ? "Disconnect Real-Debrid" : "Connect Real-Debrid");
        action.setOnClickListener(v -> {
            if (new DebridStore(this).isConnected()) {
                new DebridStore(this).disconnect();
                render();
            } else {
                beginAuth();
            }
        });
        root.addView(action, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));
        setContentView(root);
    }

    private void beginAuth() {
        action.setEnabled(false);
        status.setText("Requesting device code…");
        executor.submit(() -> {
            try {
                RealDebridClient client = new RealDebridClient(this);
                RealDebridClient.DeviceCode code = client.beginDeviceAuth();
                runOnUiThread(() -> showCode(code));

                long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
                while (!cancelled && System.currentTimeMillis() < deadline) {
                    RealDebridClient.UserCredentials credentials = client.pollUserCredentials(code.deviceCode);
                    if (credentials != null) {
                        client.finishDeviceAuth(code, credentials);
                        runOnUiThread(() -> {
                            status.setText("Real-Debrid connected ✓");
                            action.setEnabled(true);
                            action.setText("Disconnect Real-Debrid");
                            action.setOnClickListener(v -> {
                                new DebridStore(this).disconnect();
                                render();
                            });
                        });
                        return;
                    }
                    Thread.sleep(Math.max(1, code.intervalSeconds) * 1000L);
                }
                if (!cancelled) runOnUiThread(() -> { status.setText("Device code expired. Try Connect again."); action.setEnabled(true); });
            } catch (Exception e) {
                if (!cancelled) runOnUiThread(() -> { status.setText("Connection failed: " + message(e)); action.setEnabled(true); });
            }
        });
    }

    private void showCode(RealDebridClient.DeviceCode code) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(64), dp(48), dp(64), dp(48));
        root.setBackgroundColor(BG);
        root.addView(text("Connect Real-Debrid", 32, true));
        TextView line = text("On your phone or computer, open:", 18, false);
        line.setPadding(0, dp(18), 0, dp(8));
        root.addView(line);
        root.addView(text(code.verificationUrl, 22, true));
        TextView codeView = text("Code:  " + code.userCode, 34, true);
        codeView.setTextColor(Color.rgb(216, 180, 254));
        codeView.setPadding(0, dp(20), 0, dp(20));
        root.addView(codeView);
        Button open = button("Open verification page on this device");
        open.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl))); }
            catch (Exception ignored) {}
        });
        root.addView(open, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));
        status = text("Waiting for authorization…", 18, false);
        status.setPadding(0, dp(18), 0, 0);
        root.addView(status);
        action = open;
        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(Color.WHITE); b.setTextSize(17); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER); b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return v;
    }

    private static String message(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
