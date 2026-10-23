package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Wallpapers from a USB drive: a folder named TVWallpapers at the top of any drive plugged into the TV. While Home
 * is up the launcher looks for it once a minute; when its photos changed it copies them, as 1920×1080 JPEGs under
 * 500 KB, into files/usb/. The copies stay after the drive is unplugged; a drive with an emptied TVWallpapers
 * folder clears them. Needs READ_EXTERNAL_STORAGE (READ_MEDIA_IMAGES from Android 13), granted by
 * build-and-install.sh; without it the drive cannot be read and nothing changes.
 */
final class UsbWallpapers {

    static final String FOLDER = "TVWallpapers";
    private static final String TAG = "TvHome.Usb";
    private static final int MAX_PHOTOS = 60;
    private static boolean syncing;

    private UsbWallpapers() {
    }

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        return base == null ? null : new File(base, "usb");
    }

    static List<File> images(Context ctx) {
        File d = dir(ctx);
        return d == null ? new java.util.ArrayList<>() : WallpaperRotation.images(d);
    }

    /** /storage/<volume>/TVWallpapers on the first removable drive that has one, or null. */
    static File findFolder() {
        File[] volumes = new File("/storage").listFiles();
        if (volumes == null) return null;
        java.util.Arrays.sort(volumes);
        for (File v : volumes) {
            String n = v.getName();
            if (n.equals("emulated") || n.equals("self")) continue;   // the TV's own storage
            File f = new File(v, FOLDER);
            if (f.isDirectory()) return f;
        }
        return null;
    }

    /** Copies in the background when the drive's photos differ from the last copy; {@code onChanged} on main. */
    static void syncIfChanged(Context ctx, Runnable onChanged) {
        if (syncing) return;
        File d = dir(ctx);
        if (d == null) return;
        syncing = true;
        RemoteWallpapers.IO.execute(() -> {
            boolean changed = false;
            try {
                changed = sync(d);
            } catch (Exception e) {
                Log.w(TAG, "USB wallpapers not copied: " + e);
            }
            boolean report = changed;
            RemoteWallpapers.MAIN.post(() -> {
                syncing = false;
                if (report) onChanged.run();
            });
        });
    }

    private static boolean sync(File d) throws Exception {
        File folder = findFolder();
        if (folder == null) return false;   // no drive: keep what was copied
        List<File> photos = WallpaperRotation.images(folder);
        if (photos.size() > MAX_PHOTOS) photos = photos.subList(0, MAX_PHOTOS);
        StringBuilder key = new StringBuilder();
        for (File p : photos) key.append(p.getName()).append(':').append(p.length()).append(':').append(p.lastModified()).append(';');
        JSONObject old = RemoteWallpapers.readIndex(d);
        if (old != null && key.toString().equals(old.optString("key"))) return false;
        if (!d.isDirectory() && !d.mkdirs()) throw new java.io.IOException("cannot create " + d);
        Set<String> keep = new HashSet<>();
        boolean changed = false;
        for (File p : photos) {
            String name = "usb-" + p.getName().replaceAll("[^A-Za-z0-9_.-]", "_").replaceAll("\\.[^.]*$", "") + ".jpg";
            File out = new File(d, name);
            try {
                RemoteWallpapers.writeWallpaper(p, out);
                keep.add(name);
                changed = true;
            } catch (Exception e) {
                Log.w(TAG, "skipped " + p.getName() + ": " + e);
            }
        }
        changed |= RemoteWallpapers.deleteOthers(d, keep);
        RemoteWallpapers.writeText(new File(d, RemoteWallpapers.INDEX), new JSONObject().put("key", key.toString()).toString());
        Log.i(TAG, keep.size() + " photos copied from " + folder);
        return changed;
    }
}
