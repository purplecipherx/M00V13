package com.m00v13.tv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Decodes the PurplecipherX-supplied cow artwork from a raw base64 resource. */
public final class SplashArtwork {
    private static volatile Bitmap cached;
    private SplashArtwork() {}

    public static boolean apply(ImageView image) {
        if (image == null) return false;
        try {
            Bitmap bitmap = cached;
            if (bitmap == null || bitmap.isRecycled()) {
                synchronized (SplashArtwork.class) {
                    bitmap = cached;
                    if (bitmap == null || bitmap.isRecycled()) {
                        bitmap = decode(image.getContext());
                        cached = bitmap;
                    }
                }
            }
            if (bitmap == null) return false;
            image.setImageBitmap(bitmap);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Bitmap decode(Context context) throws Exception {
        InputStream in = context.getResources().openRawResource(R.raw.splash_image_base64);
        ByteArrayOutputStream out = new ByteArrayOutputStream(24 * 1024);
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) >= 0) out.write(buffer, 0, n);
        in.close();
        String encoded = new String(out.toByteArray(), StandardCharsets.US_ASCII).trim();
        byte[] jpeg = Base64.decode(encoded, Base64.DEFAULT);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = false;
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, options);
    }
}
