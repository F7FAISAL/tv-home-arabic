package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wallpapers listed online, kept on the TV as 1920×1080 JPEGs under 500 KB. Two sources, each in its own folder:
 * <ul>
 * <li>{@link Source#GALLERY}: the project's public gallery, wallpapers.json and wallpapers/ in the repository,
 *     read from raw.githubusercontent.com ("wallpaper_gallery_url"). No key: the files are public.</li>
 * <li>{@link Source#DRIVE}: the user's own Google Drive folder, listed by their read-only Apps Script
 *     ("wallpaper_list_url" in the theme's gitignored config.local.json, tools/drive-wallpapers).</li>
 * </ul>
 * At boot and then every six hours the TV reads the list, downloads each new or changed photo, and deletes the
 * photos that left it. Offline, the photos already here keep rotating.
 */
final class RemoteWallpapers {

    enum Source {
        GALLERY("gallery", "gallery_wallpapers"),
        DRIVE("drive", "drive_wallpapers");

        final String folder, prefs;

        Source(String folder, String prefs) {
            this.folder = folder;
            this.prefs = prefs;
        }
    }

    private static final String TAG = "TvHome.Wallpapers";
    static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_PHOTOS = 60;
    private static final int WIDTH = 1920, HEIGHT = 1080, MAX_BYTES = 500 * 1024;
    private static final String THUMB = "https://drive.google.com/thumbnail?id=%s&sz=w1920";
    static final String INDEX = "index.json";
    private static final String LAST = "last_sync";

    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** Per source: a sync runs once after every boot (process start), then every six hours. */
    private static final boolean[] syncedThisBoot = new boolean[Source.values().length];
    private static final boolean[] syncing = new boolean[Source.values().length];

    private RemoteWallpapers() {
    }

    /** files/<folder>/ in the launcher's external files directory, where WallpaperPlayer reads them; null if none. */
    static File dir(Context ctx, Source s) {
        File base = Config.dir(ctx);
        return base == null ? null : new File(base, s.folder);
    }

    /** The synced photos of one source, sorted by name. */
    static List<File> images(Context ctx, Source s) {
        File d = dir(ctx, s);
        return d == null ? new ArrayList<>() : WallpaperRotation.images(d);
    }

    /** Syncs in the background when due; {@code onChanged} runs on the main thread if photos came or went. */
    static void syncIfDue(Context ctx, Source s, String listUrl, Runnable onChanged) {
        int i = s.ordinal();
        if (listUrl == null || listUrl.isEmpty() || syncing[i]) return;
        Context app = ctx.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(s.prefs, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (syncedThisBoot[i] && now - prefs.getLong(LAST, 0) < REFRESH_MS) return;
        File d = dir(app, s);
        if (d == null) return;
        syncing[i] = true;
        syncedThisBoot[i] = true;
        IO.execute(() -> {
            boolean changed = false;
            boolean listed = false;
            try {
                changed = sync(s, d, listUrl, new String(fetch(listUrl, 256 * 1024, false), StandardCharsets.UTF_8));
                listed = true;
            } catch (Exception e) {
                Log.w(TAG, s.folder + " wallpapers not synced (offline or the list failed): " + e);
            }
            // A failed list is tried again after the next interval too, not every minute.
            prefs.edit().putLong(LAST, now).apply();
            boolean report = changed;
            boolean ok = listed;
            MAIN.post(() -> {
                syncing[i] = false;
                if (ok) Log.i(TAG, s.folder + " wallpapers synced" + (report ? ", changed" : ""));
                if (report) onChanged.run();
            });
        });
    }

    /** One photo of a list: the name it is kept under, what marks a new version, and where to download it. */
    static final class Entry {
        final String name, version, url;

        Entry(String name, String version, String url) {
            this.name = name;
            this.version = version;
            this.url = url;
        }
    }

    /**
     * One photo of a list, or null for an entry that is not safe to use. Gallery: {"files": [{"file":
     * "wallpapers/01.jpg", "sha": "..."}]}, each file relative to the list's own address. Drive: {"files": [{"id":
     * "...", "modified": "..."}]}, downloaded as Google's 1920-px rendition.
     */
    static Entry entry(Source s, String listUrl, JSONObject f) {
        if (s == Source.DRIVE) {
            String id = f.optString("id", "");
            if (!id.matches("[A-Za-z0-9_-]{10,80}")) return null;
            return new Entry("drive-" + id + ".jpg", f.optString("modified", ""),
                    String.format(java.util.Locale.US, THUMB, id));
        }
        String file = f.optString("file", "");
        if (!file.matches("([A-Za-z0-9_-]+/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*\\.(jpg|jpeg|JPG|JPEG)")) return null;
        String name = file.substring(file.lastIndexOf('/') + 1);
        String base = listUrl.substring(0, listUrl.lastIndexOf('/') + 1);
        return new Entry(name, f.optString("sha", ""), base + file);
    }

    /** Downloads what is new or changed, deletes what left the list; true when the set of photos changed. */
    private static boolean sync(Source s, File d, String listUrl, String listJson) throws Exception {
        if (!d.isDirectory() && !d.mkdirs()) throw new IOException("cannot create " + d);
        JSONArray files = new JSONObject(listJson).getJSONArray("files");
        JSONObject old = readIndex(d);
        JSONObject index = new JSONObject();
        Set<String> keep = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < files.length() && keep.size() < MAX_PHOTOS; i++) {
            Entry e = entry(s, listUrl, files.getJSONObject(i));
            if (e == null || keep.contains(e.name)) continue;
            File out = new File(d, e.name);
            JSONObject before = old == null ? null : old.optJSONObject(e.name);
            boolean current = out.isFile() && before != null && e.version.equals(before.optString("version"));
            if (!current) {
                try {
                    writeWallpaper(fetch(e.url, 12 * 1024 * 1024, true), out);
                    changed = true;
                } catch (Exception x) {
                    Log.w(TAG, "skipped " + e.name + ": " + x);
                    if (!out.isFile()) continue;   // keep an older copy when the new one failed
                }
            }
            keep.add(e.name);
            index.put(e.name, new JSONObject().put("version", e.version));
        }
        changed |= deleteOthers(d, keep);
        writeText(new File(d, INDEX), index.toString());
        return changed;
    }

    /** Deletes the photos in {@code d} that are not in {@code keep}; true when one went. */
    static boolean deleteOthers(File d, Set<String> keep) {
        boolean changed = false;
        File[] present = d.listFiles();
        if (present != null) {
            for (File p : present) {
                if (p.isFile() && !p.getName().equals(INDEX) && !keep.contains(p.getName()) && p.delete()) changed = true;
            }
        }
        return changed;
    }

    /** A downloaded photo: scaled to cover 1920×1080, centre-cropped, JPEG under 500 KB. */
    static void writeWallpaper(byte[] data, File out) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        o = decodeOptions(o);
        writeWallpaper(BitmapFactory.decodeByteArray(data, 0, data.length, o), out);
    }

    /** A photo on disk (a USB drive), read without holding the whole file in memory. */
    static void writeWallpaper(File src, File out) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(src.getPath(), o);
        o = decodeOptions(o);
        writeWallpaper(BitmapFactory.decodeFile(src.getPath(), o), out);
    }

    private static BitmapFactory.Options decodeOptions(BitmapFactory.Options bounds) throws IOException {
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("not an image");
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= WIDTH && bounds.outHeight / (sample * 2) >= HEIGHT) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return o;
    }

    /** Written next to the target, then renamed, so the rotation never reads half a file. */
    private static void writeWallpaper(Bitmap src, File out) throws IOException {
        if (src == null) throw new IOException("undecodable");
        Bitmap dst = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        float scale = Math.max((float) WIDTH / src.getWidth(), (float) HEIGHT / src.getHeight());
        int cw = Math.round(WIDTH / scale), ch = Math.round(HEIGHT / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        new Canvas(dst).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, WIDTH, HEIGHT),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        byte[] jpeg = null;
        for (int q = 88; q >= 50; q -= 6) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            dst.compress(Bitmap.CompressFormat.JPEG, q, buf);
            jpeg = buf.toByteArray();
            if (jpeg.length <= MAX_BYTES) break;
        }
        dst.recycle();
        File tmp = new File(out.getPath() + ".tmp");
        try (FileOutputStream fo = new FileOutputStream(tmp)) {
            fo.write(jpeg);
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IOException("cannot write " + out);
        }
    }

    private static byte[] fetch(String url, int max, boolean image) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);   // the Apps Script answers through a redirect to googleusercontent.com
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            String type = c.getContentType() == null ? "" : c.getContentType();
            // raw.githubusercontent.com serves .jpg as image/jpeg; an HTML error page is refused.
            if (image && !type.startsWith("image/")) throw new IOException("not an image: " + type);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > max) throw new IOException("answer too large");
                }
                return buf.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    static JSONObject readIndex(File d) {
        File f = d == null ? null : new File(d, INDEX);
        if (f == null || !f.isFile() || f.length() > 256 * 1024) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    static void writeText(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(f)) tmp.delete();
    }
}
