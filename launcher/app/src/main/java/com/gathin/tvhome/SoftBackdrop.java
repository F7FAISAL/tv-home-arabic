package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import java.io.File;

/**
 * A stand-in for the blur Android 11 cannot do behind a window: the picture Home shows now, shrunk to 64×36, box
 * blurred and stretched back with filtering, under a light dark tint. About 9 KB; the plain tint when there is no
 * picture or it cannot be read.
 */
final class SoftBackdrop {

    private static final int W = 64, H = 36;
    /** Over the soft picture, so white text and glass stay readable. */
    static final int TINT = 0x66000000;
    /** Without a picture: the dark veil alone. */
    static final int PLAIN = 0x99000000;

    private SoftBackdrop() {
    }

    static Drawable create(Context ctx) {
        Bitmap soft = null;
        try {
            File f = shownPicture(ctx);
            if (f != null) soft = soften(f);
        } catch (RuntimeException | OutOfMemoryError e) {
            soft = null;   // the plain tint is a fine fallback
        }
        if (soft == null) return new ColorDrawable(PLAIN);
        BitmapDrawable picture = new BitmapDrawable(ctx.getResources(), soft);
        picture.setFilterBitmap(true);
        return new LayerDrawable(new Drawable[]{picture, new ColorDrawable(TINT)});
    }

    /** The photo Home showed last (WallpaperPlayer), else the rotation's first, else the theme's wallpaper.jpg. */
    private static File shownPicture(Context ctx) {
        String last = WallpaperPlayer.shownImage(ctx);
        File first = null;
        for (File f : Config.load(ctx).wallpapers) {
            if (first == null) first = f;
            if (f.getName().equals(last)) return f;
        }
        if (first != null) return first;
        return CustomHomeConfig.load(ctx).wallpaper;
    }

    private static Bitmap soften(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 16;   // 1920×1080 decodes as 120×68
        Bitmap small = BitmapFactory.decodeFile(f.getPath(), o);
        if (small == null) return null;
        Bitmap tiny = Bitmap.createScaledBitmap(small, W, H, true);
        if (tiny != small) small.recycle();
        int[] px = new int[W * H];
        tiny.getPixels(px, 0, W, 0, 0, W, H);
        for (int pass = 0; pass < 3; pass++) px = boxBlur(px, W, H, 2);
        Bitmap out = Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888);
        tiny.recycle();
        return out;
    }

    /** One horizontal and one vertical box pass of radius {@code r}, edges clamped. */
    static int[] boxBlur(int[] in, int w, int h, int r) {
        int[] tmp = new int[in.length], out = new int[in.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) tmp[y * w + x] = average(in, w, h, x, y, r, true);
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out[y * w + x] = average(tmp, w, h, x, y, r, false);
        }
        return out;
    }

    private static int average(int[] px, int w, int h, int x, int y, int r, boolean across) {
        int rs = 0, gs = 0, bs = 0, n = 0;
        for (int d = -r; d <= r; d++) {
            int xx = across ? Math.max(0, Math.min(w - 1, x + d)) : x;
            int yy = across ? y : Math.max(0, Math.min(h - 1, y + d));
            int c = px[yy * w + xx];
            rs += (c >> 16) & 0xFF;
            gs += (c >> 8) & 0xFF;
            bs += c & 0xFF;
            n++;
        }
        return 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
    }
}
