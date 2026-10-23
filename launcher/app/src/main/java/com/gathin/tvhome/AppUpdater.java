package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * «تحديث الأداة»: asks GitHub for the latest release (config.xml update_releases_url, no account), downloads its APK into
 * the app's own cache, checks the SHA-256 published beside it, checks that it is this app, newer, and signed with the
 * same key as the installed one, then hands it to Android's installer, where the owner confirms with the remote. An
 * update keeps everything the launcher stored (choices, theme, the font on the TV). Run off the main thread.
 *
 * The checksum only proves the download is the file the release lists; what makes an update safe is Android's own rule
 * that only the key the TV already trusts can replace the app.
 */
final class AppUpdater {

    interface Progress {
        void percent(int p);
    }

    /** Why an update stopped, for the status line. */
    static final class Failure extends Exception {
        final int message;

        Failure(int message, String detail) {
            super(detail);
            this.message = message;
        }
    }

    static final String APK_FILE = "update.apk";
    private static final String PREFS = "app_update";
    private static final String KEY_SERVICES = "a11y_services";
    /** How long, after an update, Home waits for its key service to bind before bringing itself back anyway. */
    static final long BIND_WAIT_MS = 10_000L;

    private AppUpdater() {
    }

    static String installedName(Context ctx) {
        PackageInfo p = installed(ctx, 0);
        return p == null || p.versionName == null ? "?" : p.versionName;
    }

    static long installedCode(Context ctx) {
        PackageInfo p = installed(ctx, 0);
        if (p == null) return 0;
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    /** The latest release, or a Failure saying why there is none to show. */
    static AppUpdate.Release latest(Context ctx) throws Failure {
        String body;
        try {
            body = new String(get(ctx.getString(R.string.update_releases_url), 512 * 1024), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // GitHub answers 404 both for a repository with no release yet and for a private one.
            boolean none = String.valueOf(e.getMessage()).startsWith("HTTP 404");
            throw new Failure(none ? R.string.update_failed_no_release : R.string.update_failed_network, e.toString());
        }
        AppUpdate.Release r;
        try {
            r = AppUpdate.parse(new JSONObject(body));
        } catch (Exception e) {
            r = null;
        }
        if (r == null) throw new Failure(R.string.update_failed_release, "no tvhome-*.apk with its .sha256");
        return r;
    }

    /** Downloads and checks {@code r}; returns the verified APK in the cache, ready for {@link #install}. */
    static File download(Context ctx, AppUpdate.Release r, Progress progress) throws Failure {
        String expected;
        try {
            expected = AppUpdate.checksum(new String(get(r.shaUrl, 4096), StandardCharsets.UTF_8), r.apkName);
        } catch (IOException e) {
            throw new Failure(R.string.update_failed_network, e.toString());
        }
        if (expected == null) throw new Failure(R.string.update_failed_checksum, "no checksum in " + r.shaUrl);
        File apk = new File(ctx.getCacheDir(), APK_FILE);
        try {
            save(r.apkUrl, apk, r.apkSize, progress);
        } catch (IOException e) {
            apk.delete();
            throw new Failure(R.string.update_failed_network, e.toString());
        }
        try {
            if (!expected.equals(AppUpdate.sha256(apk))) {
                apk.delete();
                throw new Failure(R.string.update_failed_checksum, "SHA-256 differs");
            }
        } catch (IOException e) {
            apk.delete();
            throw new Failure(R.string.update_failed_checksum, e.toString());
        }
        checkSameApp(ctx, apk);
        return apk;
    }

    /** This package, a higher versionCode, and exactly the installed signing certificate; else a Failure. */
    private static void checkSameApp(Context ctx, File apk) throws Failure {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo theirs = ctx.getPackageManager().getPackageArchiveInfo(apk.getPath(), flags);
        PackageInfo ours = installed(ctx, flags);
        if (theirs == null || ours == null || !ctx.getPackageName().equals(theirs.packageName)) {
            apk.delete();
            throw new Failure(R.string.update_failed_release, "not this app");
        }
        long code = Build.VERSION.SDK_INT >= 28 ? theirs.getLongVersionCode() : theirs.versionCode;
        if (code <= installedCode(ctx)) {
            apk.delete();
            throw new Failure(R.string.update_none_newer, "versionCode " + code);
        }
        Set<String> a = signers(ours), b = signers(theirs);
        if (a.isEmpty() || !a.equals(b)) {
            apk.delete();
            throw new Failure(R.string.update_failed_signature, "signers " + a + " vs " + b);
        }
    }

    /** Opens Android's installer on {@code apk}; UpdateReceiver shows its confirmation. */
    static void install(Context ctx, File apk) throws Failure {
        rememberKeyServices(ctx);
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(ctx.getPackageName());
        params.setSize(apk.length());
        try {
            int id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("tvhome.apk", 0, apk.length())) {
                    byte[] b = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(b)) > 0) out.write(b, 0, n);
                    session.fsync(out);
                }
                Intent done = new Intent(ctx, UpdateReceiver.class);
                int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;   // the installer adds its status
                PendingIntent status = PendingIntent.getBroadcast(ctx, id, done, PendingIntent.FLAG_UPDATE_CURRENT | mutable);
                session.commit(status.getIntentSender());
            }
        } catch (IOException | RuntimeException e) {
            throw new Failure(R.string.update_failed_install, e.toString());
        }
    }

    /**
     * The key services enabled now, kept for {@link #restoreKeyServices} after the update: Android 12 keeps them
     * through an update, but some Android 11 TCL builds leave them crashed or dropped, and the remote's buttons die.
     */
    private static void rememberKeyServices(Context ctx) {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuilder ours = new StringBuilder();
        for (String s : enabled == null ? new String[0] : enabled.split(":")) {
            if (s.startsWith(ctx.getPackageName() + "/")) ours.append(ours.length() > 0 ? ":" : "").append(s);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SERVICES, ours.toString()).commit();
    }

    /**
     * After the update (MY_PACKAGE_REPLACED): puts back the key services remembered before it, and switches
     * accessibility off and on once so they bind. Needs WRITE_SECURE_SETTINGS, which build-and-install.sh grants over
     * adb; without it nothing changes and tools/update-tv.sh (or theme.sh) puts them back.
     */
    static void restoreKeyServices(Context ctx) {
        String ours = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVICES, "");
        if (ours.isEmpty() || ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        android.content.ContentResolver cr = ctx.getContentResolver();
        String enabled = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        StringBuilder list = new StringBuilder(enabled == null || "null".equals(enabled) ? "" : enabled);
        for (String s : ours.split(":")) {
            if (!(":" + list + ":").contains(":" + s + ":")) list.append(list.length() > 0 ? ":" : "").append(s);
        }
        try {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            Thread.sleep(1500);
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.toString());
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (SecurityException | InterruptedException e) {
            android.util.Log.w("TvHome.Update", "key services not restored: " + e);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_SERVICES).apply();
        new File(ctx.getCacheDir(), APK_FILE).delete();
    }

    /**
     * After this launcher was replaced (MY_PACKAGE_REPLACED, in the new version): bring Home back. Replacing itself,
     * the launcher's process and tasks go, Home's included, and Android brings forward the most recent other task
     * instead (on the G10 on 2026-10-24: a Netflix left in the background from earlier). Android lets an app start an
     * activity from the background while the system has one of its accessibility services bound, so this waits until
     * a key service of ours is bound again (BIND_WAIT_MS at most), then starts Home once. Lives in the new version so
     * it works whatever version did the install.
     */
    static void returnHomeWhenBound(Context ctx) {
        android.view.accessibility.AccessibilityManager am =
                (android.view.accessibility.AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
        long until = System.currentTimeMillis() + BIND_WAIT_MS;
        while (am != null && keyServicesListed(ctx) && System.currentTimeMillis() < until) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (android.accessibilityservice.AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) ids.add(i.getId());
            if (oursBound(ids, ctx.getPackageName())) break;
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return;
            }
        }
        goHome(ctx);
    }

    /** True when one of the running accessibility services ("pkg/.Class" ids) is this app's. */
    static boolean oursBound(java.util.List<String> runningIds, String pkg) {
        for (String id : runningIds) if (id != null && id.startsWith(pkg + "/")) return true;
        return false;
    }

    static void goHome(Context ctx) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage(ctx.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            android.util.Log.w("TvHome.Update", "Home not brought back after the update: " + e);
        }
    }

    /** True when this app's key services are listed as enabled (they will bind again after the update). */
    static boolean keyServicesListed(Context ctx) {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(ctx.getPackageName() + "/");
    }

    private static PackageInfo installed(Context ctx, int flags) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), flags);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo p) {
        Signature[] sigs;
        if (Build.VERSION.SDK_INT >= 28) {
            sigs = p.signingInfo == null ? null : p.signingInfo.getApkContentsSigners();
        } else {
            sigs = p.signatures;
        }
        Set<String> out = new HashSet<>();
        if (sigs == null) return out;
        for (Signature s : sigs) {
            try {
                out.add(AppUpdate.hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
            } catch (java.security.NoSuchAlgorithmException ignored) {
            }
        }
        return out;
    }

    private static byte[] get(String url, int max) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                buf.write(b, 0, n);
                if (buf.size() > max) throw new IOException("answer too large");
            }
            return buf.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static void save(String url, File to, long size, Progress progress) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            byte[] b = new byte[64 * 1024];
            long total = 0;
            int n, shown = -1;
            while ((n = in.read(b)) > 0) {
                total += n;
                if (total > size || total > AppUpdate.MAX_APK_BYTES) throw new IOException("larger than the release says");
                out.write(b, 0, n);
                int p = (int) (total * 100 / size);
                if (p != shown && progress != null) progress.percent(shown = p);
            }
            if (total != size) throw new IOException("got " + total + " of " + size + " bytes");
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("not https: " + url);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code + " from " + new URL(url).getHost());
        }
        return c;
    }
}
