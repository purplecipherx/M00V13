package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class DonateActivity extends Activity {
    private static final String ETH = "0x88675dB8404bBb447996f9aB51721763e136C808";
    private static final String BTC = "bc1qh2fmg2p5wlt3qw4sd8g8744skq8hyjap3c84cw";
    private static final String XMR = "43g7EHA92cWX8jFe7rWjAJ7m7ZNUn5Mqoi58bzRj7DAEeHeufT1ccCpbca36ngpGxubJmBeYptfcSLYVGwuE26RrSMyvZeT";
    private ScreenProfile screen;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        screen = ScreenProfile.detect(this);
        getWindow().getDecorView().setBackgroundColor(TvUi.BG);
        setContentView(build());
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(screen.sidePaddingDp), dp(screen.mobile() ? 20 : 36), dp(screen.sidePaddingDp), dp(48));
        root.setBackgroundColor(TvUi.BG);
        scroll.addView(root);

        root.addView(TvUi.text(this, "Support M00V13 💜", screen.mobile() ? 26 : 32, true));
        TextView intro = TvUi.text(this, "M00V13 is free. Donations are optional and never unlock features or change playback/search priority.", screen.mobile() ? 14 : 17, false);
        intro.setTextColor(TvUi.MUTED);
        intro.setPadding(0, dp(8), 0, dp(18));
        root.addView(intro);

        addWallet(root, "Ethereum (ETH)", ETH);
        addWallet(root, "Bitcoin (BTC)", BTC);
        addWallet(root, "Monero (XMR)", XMR);

        TextView warning = TvUi.text(this, "Verify the full address on your sending device before confirming. Crypto transfers are generally irreversible.", 14, false);
        warning.setTextColor(TvUi.MUTED);
        warning.setPadding(0, dp(18), 0, dp(12));
        root.addView(warning);

        Button back = TvUi.button(this, "Back");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(180), dp(54)));
        return scroll;
    }

    private void addWallet(LinearLayout root, String name, String address) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(screen.mobile() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(screen.mobile() ? 14 : 18), dp(18), dp(screen.mobile() ? 14 : 18), dp(18));
        card.setBackgroundColor(TvUi.PANEL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(14);
        root.addView(card, cp);

        int qrDp = screen.mobile() ? Math.min(280, Math.max(180, screen.sidePaddingDp + 180)) : (screen.kind == ScreenProfile.Kind.TV_4K ? 290 : 250);
        int qrPixels = Math.min(900, Math.max(420, screen.artworkWidthPx));
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qr(address, qrPixels));
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setAdjustViewBounds(true);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(qrDp), dp(qrDp));
        if (screen.mobile()) qp.gravity = Gravity.CENTER_HORIZONTAL;
        card.addView(qr, qp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(screen.mobile() ? 0 : dp(24), screen.mobile() ? dp(14) : 0, 0, 0);
        LinearLayout.LayoutParams ip = screen.mobile()
            ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            : new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(info, ip);

        TextView heading = TvUi.text(this, name, screen.mobile() ? 19 : 23, true);
        heading.setTextColor(TvUi.BLUE);
        info.addView(heading);

        TextView a = TvUi.text(this, address, screen.mobile() ? 12 : 16, false);
        a.setTextIsSelectable(true);
        a.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        a.setSingleLine(false);
        a.setHorizontallyScrolling(false);
        a.setBreakStrategy(TextView.BREAK_STRATEGY_SIMPLE);
        a.setTextColor(TvUi.WHITE);
        a.setPadding(0, dp(10), 0, 0);
        info.addView(a, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private Bitmap qr(String payload, int pixels) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, pixels, pixels);
            Bitmap bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.RGB_565);
            for (int y = 0; y < pixels; y++) for (int x = 0; x < pixels; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bitmap;
        } catch (Exception e) {
            DebugLog.append(this, "QR", "QR generation failed for " + nameFor(payload) + ": " + e.getMessage());
            return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        }
    }

    private String nameFor(String payload) { return payload.equals(ETH) ? "ETH" : payload.equals(BTC) ? "BTC" : "XMR"; }
    private int dp(int value) { return TvUi.dp(this, value); }
}
