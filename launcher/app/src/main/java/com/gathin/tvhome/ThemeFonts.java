package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The owner's Thmanyah Sans, which no APK carries: its licence forbids redistribution, and the released APK is public.
 * tools/theme.sh pushes the two files from launcher/fonts-local/thmanyah/ (tools/install-thmanyah.py) to the theme
 * folder's fonts/, unmodified and under their own names; they stay there through app updates. A byte-for-byte copy
 * is kept in internal storage, as Config keeps the theme, because Home can start before the card is mounted.
 */
final class ThemeFonts {

    private static final String TAG = "TvHome.Fonts";
    static final String DIR = "fonts";
    /** {regular, bold}, the names Thmanyah ships them under. */
    static final String[] THMANYAH = {"thmanyahsans-Regular.otf", "thmanyahsans-Bold.otf"};
    /** Larger than any weight of the family; a bigger file is not the font. */
    private static final long MAX_BYTES = 4L * 1024 * 1024;

    private ThemeFonts() {
    }

    /** {regular, bold} from the TV, or null when the files are not there (the caller falls back to IBM Plex). */
    static Typeface[] thmanyah(Context ctx) {
        File pushed = Config.dir(ctx) == null ? null : new File(Config.dir(ctx), DIR);
        File kept = new File(ctx.getFilesDir(), DIR);
        try {
            sync(pushed, kept, THMANYAH);
        } catch (IOException e) {
            Log.w(TAG, "font copy not refreshed: " + e);
        }
        File regular = new File(kept, THMANYAH[0]), bold = new File(kept, THMANYAH[1]);
        if (!usable(regular) || !usable(bold)) return null;
        try {
            return new Typeface[]{Typeface.createFromFile(regular), Typeface.createFromFile(bold)};
        } catch (RuntimeException e) {
            Log.w(TAG, "Thmanyah Sans unreadable: " + e);
            return null;
        }
    }

    static boolean usable(File f) {
        return f != null && f.isFile() && f.length() > 0 && f.length() <= MAX_BYTES;
    }

    /**
     * Makes {@code kept} hold the same {@code names} as {@code pushed}: copies a file that is new or differs in size or
     * time, and drops a kept file whose pushed one is gone. Nothing changes while {@code pushed} cannot be read
     * (storage not mounted yet), so the last copy keeps working.
     */
    static void sync(File pushed, File kept, String[] names) throws IOException {
        if (pushed == null || !pushed.isDirectory()) return;
        if (!kept.isDirectory() && !kept.mkdirs()) throw new IOException("cannot create " + kept);
        for (String name : names) {
            File from = new File(pushed, name), to = new File(kept, name);
            if (!usable(from)) {
                if (to.exists() && !to.delete()) throw new IOException("cannot remove " + to);
                continue;
            }
            if (to.isFile() && to.length() == from.length() && to.lastModified() == from.lastModified()) continue;
            File tmp = new File(kept, name + ".tmp");
            try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
                byte[] b = new byte[64 * 1024];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            if (!tmp.setLastModified(from.lastModified()) || !tmp.renameTo(to)) {
                tmp.delete();
                throw new IOException("cannot replace " + to);
            }
        }
    }
}
