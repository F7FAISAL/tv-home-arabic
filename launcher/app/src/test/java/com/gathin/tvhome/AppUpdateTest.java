package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class AppUpdateTest {

    private static final String SUM = "a192353f41b6f6899d53ce823a6f6cfb4cfe327b1582cb010727c44f80e07356";

    /** releases/latest as tools/release.sh publishes it, trimmed to what AppUpdate reads. */
    private static JSONObject release(String apkName, String apkUrl, long size, String shaName) throws Exception {
        String base = "https://github.com/F7FAISAL/tv-home-arabic/releases/download/v3.1/";
        return new JSONObject("{\"tag_name\":\"v3.1\",\"assets\":["
                + "{\"name\":\"" + apkName + "\",\"size\":" + size + ",\"browser_download_url\":\"" + apkUrl + "\"},"
                + "{\"name\":\"" + shaName + "\",\"size\":90,\"browser_download_url\":\"" + base + shaName + "\"},"
                + "{\"name\":\"notes.txt\",\"size\":5,\"browser_download_url\":\"" + base + "notes.txt\"}]}");
    }

    @Test
    public void findsTheLauncherAndItsChecksumInARelease() throws Exception {
        String url = "https://github.com/F7FAISAL/tv-home-arabic/releases/download/v3.1/tvhome-3.1-8.apk";
        AppUpdate.Release r = AppUpdate.parse(release("tvhome-3.1-8.apk", url, 965773, "tvhome-3.1-8.apk.sha256"));
        assertNotNull(r);
        assertEquals("3.1", r.versionName);
        assertEquals(8, r.versionCode);
        assertEquals(url, r.apkUrl);
        assertEquals(965773, r.apkSize);
        assertEquals("https://github.com/F7FAISAL/tv-home-arabic/releases/download/v3.1/tvhome-3.1-8.apk.sha256", r.shaUrl);
    }

    @Test
    public void aReleaseWithoutAPairOrOverHttpOrTooBigIsNotOffered() throws Exception {
        String url = "https://github.com/x/y/releases/download/v3.1/tvhome-3.1-8.apk";
        assertNull(AppUpdate.parse(release("tvhome-3.1-8.apk", url, 965773, "other.sha256")));          // no checksum
        assertNull(AppUpdate.parse(release("tvhome-3.1-8.apk", url.replace("https", "http"), 965773,
                "tvhome-3.1-8.apk.sha256")));                                                              // not https
        assertNull(AppUpdate.parse(release("tvhome-3.1-8.apk", url, AppUpdate.MAX_APK_BYTES + 1,
                "tvhome-3.1-8.apk.sha256")));                                                              // too big
        assertNull(AppUpdate.parse(release("launcher.apk", url, 965773, "launcher.apk.sha256")));      // not our name
        assertNull(AppUpdate.parse(new JSONObject("{\"message\":\"Not Found\"}")));                       // private repo
        assertNull(AppUpdate.parse(null));
    }

    @Test
    public void readsTheChecksumAsShasumWritesIt() {
        assertEquals(SUM, AppUpdate.checksum(SUM + "  tvhome-3.1-8.apk\n", "tvhome-3.1-8.apk"));
        assertEquals(SUM, AppUpdate.checksum(SUM.toUpperCase() + " *tvhome-3.1-8.apk", "tvhome-3.1-8.apk"));
        assertEquals(SUM, AppUpdate.checksum(SUM + "\n", "tvhome-3.1-8.apk"));                          // hex alone
        assertNull(AppUpdate.checksum(SUM + "  tvhome-3.0-7.apk\n", "tvhome-3.1-8.apk"));               // another file
        assertNull(AppUpdate.checksum("not a checksum", "tvhome-3.1-8.apk"));
        assertNull(AppUpdate.checksum(null, "tvhome-3.1-8.apk"));
    }

    @Test
    public void sha256OfAFile() throws Exception {
        File f = File.createTempFile("apk", ".bin");
        try {
            Files.write(f.toPath(), "abc".getBytes(StandardCharsets.US_ASCII));
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdate.sha256(f));
        } finally {
            f.delete();
        }
    }
}
