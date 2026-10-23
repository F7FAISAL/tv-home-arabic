package com.gathin.tvhome;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The clean view's one rotation, from every source at once: the theme's own wallpapers/ (bundled), the online
 * gallery, the user's Drive folder, then a USB drive's TVWallpapers. A picture that comes both bundled and from the
 * gallery (same file name) shows once.
 */
final class WallpaperSources {

    private WallpaperSources() {
    }

    static List<File> merge(List<File> bundled, Context ctx) {
        List<File> all = new ArrayList<>();
        Set<String> names = new HashSet<>();
        List<File> sources = new ArrayList<>(bundled);
        sources.addAll(RemoteWallpapers.images(ctx, RemoteWallpapers.Source.GALLERY));
        sources.addAll(RemoteWallpapers.images(ctx, RemoteWallpapers.Source.DRIVE));
        sources.addAll(UsbWallpapers.images(ctx));
        for (File f : sources) if (names.add(f.getName())) all.add(f);
        return all;
    }
}
