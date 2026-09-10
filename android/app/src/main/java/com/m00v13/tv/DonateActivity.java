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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(TvUi.BG);
        setContentView(build());
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(64), dp(36), dp(64), dp(48));
        root.setBackgroundColor(TvUi.BG);
        scroll.addView(root);

        root.addView(TvUi.text(this, "Support M00V13 💜", 32, true));
        TextView intro = TvUi.text(this, "M00V13 is free. Donations are optional and never unlock features or change playback/search priority.", 17, false);
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
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(220), dp(58));
        back.setLayoutParams(bp);
        root.addView(back);
        return scroll;
    }

    private void addWallet(LinearLayout root, String name, String address) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundColor(TvUi.PANEL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(14);
        root.addView(card, cp);

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(qr(address, 420));
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qr.setAdjustViewBounds(true);
        card.addView(qr, new LinearLayout.LayoutParams(dp(250), dp(250)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(24), 0, 0, 0);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        card.addView(info, ip);

        TextView heading = TvUi.text(this, name, 23, true);
        heading.setTextColor(TvUi.BLUE);
        info.addView(heading);

        TextView a = TvUi.text(this, address, 16, false);
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
            for (int y = 0; y < pixels; y++) {
                for (int x = 0; x < pixels; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
            return bitmap;
        } catch (Exception e) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
        }
    }

    private int dp(int value) { return TvUi.dp(this, value); }
}
