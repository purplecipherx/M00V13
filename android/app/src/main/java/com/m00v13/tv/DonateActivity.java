package com.m00v13.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class DonateActivity extends Activity {
    private static final int BG = Color.rgb(9, 5, 15);
    private static final int PURPLE = Color.rgb(168, 85, 247);
    private static final int WHITE = Color.WHITE;

    // Donation destinations supplied by PurplecipherX. Keep these centralized so a future
    // release can update them without touching the rest of the UI.
    private static final String ETH = "0x88675dB8404bBb447996f9aB51721763e136C808";
    private static final String BTC = "bc1qh2fmg2p5wlt3qw4sd8g8744skq8hyjap3c84cw";
    private static final String XMR = "43g7EHA92cWX8jFe7rWjAJ7m7ZNUn5Mqoi58bzRj7DAEeHeufT1cccpbca36ngpGxubJmBeYptfCSLYVGWuE26RrSMyvZeT";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setBackgroundColor(BG);
        setContentView(build());
    }

    private ScrollView build() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(72), dp(42), dp(72), dp(54));
        root.setBackgroundColor(BG);
        scroll.addView(root);

        root.addView(text("Support M00V13 💜", 32, true));
        TextView intro = text("M00V13 is free. Donations are optional and never unlock features, improve search priority, or change playback behavior.", 18, false);
        intro.setTextColor(Color.rgb(210, 195, 225));
        intro.setPadding(0, dp(10), 0, dp(24));
        root.addView(intro);

        addWallet(root, "Ethereum (ETH)", ETH);
        addWallet(root, "Bitcoin (BTC)", BTC);
        addWallet(root, "Monero (XMR)", XMR);

        TextView warning = text("Always verify the full address on your sending device before confirming a transfer. Cryptocurrency transfers generally cannot be reversed.", 15, false);
        warning.setTextColor(Color.rgb(190, 175, 205));
        warning.setPadding(0, dp(24), 0, dp(10));
        root.addView(warning);

        Button back = button("Back");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        return scroll;
    }

    private void addWallet(LinearLayout root, String name, String address) {
        TextView h = text(name, 23, true);
        h.setPadding(0, dp(18), 0, dp(6));
        root.addView(h);

        TextView a = text(address, 17, false);
        a.setTextIsSelectable(true);
        a.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        a.setPadding(dp(18), dp(14), dp(18), dp(14));
        a.setBackgroundColor(Color.rgb(35, 20, 52));
        root.addView(a, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label); b.setTextColor(WHITE); b.setTextSize(17); b.setAllCaps(false); b.setFocusable(true);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(220), dp(58));
        p.gravity = Gravity.START; b.setLayoutParams(p); return b;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(WHITE); v.setTextSize(sp);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
