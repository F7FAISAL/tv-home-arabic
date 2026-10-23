package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «تحديث الأداة», the part without Android: which asset of a GitHub release is the launcher, its version, and its
 * checksum. tools/release.sh publishes tvhome-&lt;versionName&gt;-&lt;versionCode&gt;.apk and the same name plus .sha256 on
 * each release (AppUpdater does the network and the install).
 */
final class AppUpdate {

    /** tvhome-3.1-8.apk: versionName 3.1, versionCode 8. */
    private static final Pattern APK = Pattern.compile("tvhome-([0-9][0-9A-Za-z._-]{0,30})-([0-9]{1,9})\\.apk");
    private static final Pattern HEX = Pattern.compile("(?i)\\b([0-9a-f]{64})\\b");
    /** No launcher build comes near this; a bigger answer is not ours. */
    static final long MAX_APK_BYTES = 50L * 1024 * 1024;

    static final class Release {
        final String versionName;
        final int versionCode;
        final String apkName, apkUrl, shaUrl;
        final long apkSize;

        Release(String versionName, int versionCode, String apkName, String apkUrl, long apkSize, String shaUrl) {
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.apkName = apkName;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
            this.shaUrl = shaUrl;
        }
    }

    private AppUpdate() {
    }

    /**
     * The launcher in GitHub's releases/latest answer: the one tvhome-*.apk asset that has its .sha256 beside it, both
     * served over https; null when the release has no such pair (a draft, a release from another tool, a broken one).
     */
    static Release parse(JSONObject latest) {
        JSONArray assets = latest == null ? null : latest.optJSONArray("assets");
        if (assets == null) return null;
        Release found = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "");
            Matcher m = APK.matcher(name);
            if (!m.matches()) continue;
            String url = a.optString("browser_download_url", "");
            String sha = assetUrl(assets, name + ".sha256");
            long size = a.optLong("size", -1);
            if (!url.startsWith("https://") || sha == null || size <= 0 || size > MAX_APK_BYTES) continue;
            if (found != null) return null;   // two launchers in one release: refuse to guess
            found = new Release(m.group(1), Integer.parseInt(m.group(2)), name, url, size, sha);
        }
        return found;
    }

    private static String assetUrl(JSONArray assets, String name) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a != null && name.equals(a.optString("name"))) {
                String url = a.optString("browser_download_url", "");
                return url.startsWith("https://") ? url : null;
            }
        }
        return null;
    }

    /** The checksum in a .sha256 file ("hex  name", as shasum writes it, or the hex alone), lower case; else null. */
    static String checksum(String text, String apkName) {
        if (text == null) return null;
        for (String line : text.split("\n")) {
            Matcher m = HEX.matcher(line);
            if (!m.find()) continue;
            String rest = line.substring(m.end()).trim().replaceFirst("^\\*", "");
            if (rest.isEmpty() || rest.equals(apkName)) return m.group(1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = new byte[64 * 1024];
            int n;
            while ((n = in.read(b)) > 0) d.update(b, 0, n);
            return hex(d.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) s.append(String.format(Locale.ROOT, "%02x", x));
        return s.toString();
    }
}
