package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The screensaver with custom_home on (layout dream_custom): the clean view rearranged and smaller
 * (CustomHomeConfig.dreamSizes). The clock and dates sit top right, the city and weather top left, prayer, Ramadan
 * and the match bottom right, and there is no pill. The wallpapers keep rotating, and
 * every screensaver_shift_minutes the whole layout moves a few pixels against burn-in.
 */
final class CustomDreamScene implements DreamScene {

    private static final float SHIFT_DP = 8f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Context ctx;
    private final CustomHomeConfig cfg;
    private final HomeScene scene;
    private final ImageView wallpaper;
    private final float density, range;
    private final List<File> photos = new ArrayList<>();
    private int photo;
    private Bitmap shown;

    private final Runnable shift = new Runnable() {
        @Override
        public void run() {
            scene.content.animate()
                    .translationX((random.nextFloat() * 2 - 1) * range)
                    .translationY((random.nextFloat() * 2 - 1) * range)
                    .setDuration(2000).start();
            handler.postDelayed(this, cfg.dreamShiftMinutes * 60_000L);
        }
    };

    private final Runnable nextPhoto = new Runnable() {
        @Override
        public void run() {
            if (photos.size() > 1) {
                photo = (photo + 1) % photos.size();
                show(photos.get(photo));
            }
            handler.postDelayed(this, rotationMs());
        }
    };

    CustomDreamScene(Context ctx, View root, CustomHomeConfig cfg) {
        this.ctx = ctx;
        this.cfg = cfg;
        View home = root.findViewById(R.id.customHome);
        scene = new HomeScene(ctx, home, cfg, true);
        scene.buttons.setVisibility(View.GONE);   // the menu and screensaver buttons belong to Home
        density = ctx.getResources().getDisplayMetrics().density;
        range = SHIFT_DP * density;
        place(home.findViewById(R.id.customClockBlock), Gravity.TOP | Gravity.RIGHT, 0, 30, 40, 0);
        place(home.findViewById(R.id.customTopLeft), Gravity.TOP | Gravity.LEFT, 36, 26, 0, 0);
        place(home.findViewById(R.id.customInfo), Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 40, 44);
        wallpaper = root.findViewById(R.id.dreamWallpaper);

        photos.addAll(Config.load(ctx).wallpapers);
        if (photos.isEmpty() && cfg.wallpaper != null) photos.add(cfg.wallpaper);
        // Start on the picture Home showed last, so the screensaver picks up where Home was.
        String last = WallpaperPlayer.shownImage(ctx);
        for (int i = 0; i < photos.size(); i++) if (photos.get(i).getName().equals(last)) photo = i;
        if (!photos.isEmpty()) show(photos.get(photo));
    }

    private void place(View v, int gravity, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        lp.gravity = gravity;
        lp.setMargins(Math.round(left * density), Math.round(top * density), Math.round(right * density),
                Math.round(bottom * density));
        v.setLayoutParams(lp);
    }

    private long rotationMs() {
        int minutes = Config.load(ctx).wallpaperRotationMinutes;
        return (minutes > 0 ? minutes : 60) * 60_000L;
    }

    private void show(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;   // 4 MB at 1080p, as WallpaperPlayer
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return;
        wallpaper.setImageBitmap(b);
        if (shown != null) shown.recycle();
        shown = b;
    }

    @Override
    public void start() {
        scene.start();
        scene.content.setAlpha(0f);
        scene.content.animate().alpha(1f).setDuration(1200).start();
        handler.removeCallbacks(shift);
        handler.removeCallbacks(nextPhoto);
        handler.postDelayed(shift, cfg.dreamShiftMinutes * 60_000L);
        handler.postDelayed(nextPhoto, rotationMs());
    }

    @Override
    public void stop() {
        handler.removeCallbacks(shift);
        handler.removeCallbacks(nextPhoto);
        scene.content.animate().cancel();
        scene.stop();
    }
}
