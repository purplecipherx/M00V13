package com.m00v13.tv;

import android.app.Activity;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/** Runtime layout profile for phones, tablets and 720p/1080p/4K TV surfaces. */
public final class ScreenProfile {
    public enum Kind { MOBILE_COMPACT, MOBILE, TV_720, TV_1080, TV_4K }

    public final Kind kind;
    public final int widthPx;
    public final int heightPx;
    public final float density;
    public final int sidePaddingDp;
    public final int posterWidthDp;
    public final int posterHeightDp;
    public final int railHeightDp;
    public final int heroHeightDp;
    public final int artworkWidthPx;

    private ScreenProfile(Kind kind, int widthPx, int heightPx, float density,
                          int sidePaddingDp, int posterWidthDp, int posterHeightDp,
                          int railHeightDp, int heroHeightDp, int artworkWidthPx) {
        this.kind = kind;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.density = density;
        this.sidePaddingDp = sidePaddingDp;
        this.posterWidthDp = posterWidthDp;
        this.posterHeightDp = posterHeightDp;
        this.railHeightDp = railHeightDp;
        this.heroHeightDp = heroHeightDp;
        this.artworkWidthPx = artworkWidthPx;
    }

    public static ScreenProfile detect(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        boolean television = (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
            == Configuration.UI_MODE_TYPE_TELEVISION;
        int shortestDp = Math.round(Math.min(dm.widthPixels, dm.heightPixels) / dm.density);

        if (!television) {
            if (shortestDp < 600) return new ScreenProfile(Kind.MOBILE_COMPACT, dm.widthPixels, dm.heightPixels, dm.density, 16, 112, 168, 218, 190, 342);
            return new ScreenProfile(Kind.MOBILE, dm.widthPixels, dm.heightPixels, dm.density, 24, 132, 198, 248, 220, 500);
        }
        int longEdge = Math.max(dm.widthPixels, dm.heightPixels);
        if (longEdge >= 3000) return new ScreenProfile(Kind.TV_4K, dm.widthPixels, dm.heightPixels, dm.density, 56, 166, 238, 286, 300, 780);
        if (longEdge >= 1600) return new ScreenProfile(Kind.TV_1080, dm.widthPixels, dm.heightPixels, dm.density, 48, 150, 214, 262, 260, 620);
        return new ScreenProfile(Kind.TV_720, dm.widthPixels, dm.heightPixels, dm.density, 34, 128, 182, 228, 220, 460);
    }

    public boolean mobile() { return kind == Kind.MOBILE || kind == Kind.MOBILE_COMPACT; }
}
