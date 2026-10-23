package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather for the clean view's city from Open-Meteo (no key; free for non-commercial use; data CC BY 4.0,
 * credited in launcher settings). One request every 30 minutes while Home is up, the last answer kept on disk, and nothing
 * shown when the TV is offline or the answer is too old.
 */
final class WeatherFeed {

    private static final String TAG = "TvHome.Weather";
    private static final String URL_FMT = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
            + "&current=temperature_2m,weather_code,is_day&timezone=auto";
    private static final String CACHE = "weather.json";
    static final Duration REFRESH = Duration.ofMinutes(30);
    /** A reading older than this is not shown: the TV has been offline for two refreshes. */
    static final Duration MAX_AGE = Duration.ofMinutes(65);

    static final class Now {
        final int temperature;
        final int code;
        final boolean day;
        final Instant fetched;

        Now(int temperature, int code, boolean day, Instant fetched) {
            this.temperature = temperature;
            this.code = code;
            this.day = day;
            this.fetched = fetched;
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context ctx;
    private final File cache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean fetching;
    private Instant lastTry = Instant.EPOCH;

    WeatherFeed(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.cache = new File(ctx.getFilesDir(), CACHE);
    }

    /** What to show for {@code city} now, or null: offline, nothing cached for this city, or too old. */
    Now current(Cities.City city, Instant now) {
        if (!online()) return null;
        JSONObject c = read();
        if (c == null || !key(city).equals(c.optString("place"))) return null;
        Now w = parse(c.optJSONObject("body"), Instant.ofEpochMilli(c.optLong("fetched")));
        return w != null && Duration.between(w.fetched, now).compareTo(MAX_AGE) <= 0 ? w : null;
    }

    /** Fetches in the background when the reading for {@code city} is 30 minutes old (or another city's). */
    void refreshIfDue(Cities.City city, Instant now, Runnable onUpdate) {
        if (fetching || !online()) return;
        JSONObject c = read();
        boolean same = c != null && key(city).equals(c.optString("place"));
        long fetched = same ? c.optLong("fetched") : 0;
        // A failed try waits five minutes before the next, so an outage does not mean a request a minute.
        if (same && now.toEpochMilli() - fetched < REFRESH.toMillis()) return;
        if (same && Duration.between(lastTry, now).toMinutes() < 5) return;
        fetching = true;
        lastTry = now;
        String url = String.format(Locale.US, URL_FMT, city.latitude, city.longitude);
        String place = key(city);
        IO.execute(() -> {
            boolean ok = false;
            try {
                JSONObject body = new JSONObject(get(url));
                if (parse(body, now) != null) {
                    JSONObject out = new JSONObject();
                    out.put("place", place);
                    out.put("fetched", now.toEpochMilli());
                    out.put("body", body);
                    write(out);
                    ok = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "weather not updated: " + e);
            }
            boolean changed = ok;
            main.post(() -> {
                fetching = false;
                if (changed) onUpdate.run();
            });
        });
    }

    static Now parse(JSONObject body, Instant fetched) {
        JSONObject cur = body == null ? null : body.optJSONObject("current");
        if (cur == null || !cur.has("temperature_2m") || !cur.has("weather_code")) return null;
        double t = cur.optDouble("temperature_2m", Double.NaN);
        int code = cur.optInt("weather_code", -1);
        // Safe data: a reading outside what the weather can be is a broken answer; the cached one stays.
        if (Double.isNaN(t) || t < -60 || t > 60 || code < 0 || code > 99) return null;
        return new Now((int) Math.round(t), code, cur.optInt("is_day", 1) == 1, fetched);
    }

    /** WMO weather code to a short Arabic condition. */
    static String conditionAr(int code, boolean day) {
        if (code == 0) return day ? "مشمس" : "صافٍ";
        if (code == 1 || code == 2) return "غائم جزئياً";
        if (code == 3) return "غائم";
        if (code == 45 || code == 48) return "ضباب";
        if (code >= 51 && code <= 57) return "رذاذ";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "أمطار";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "ثلوج";
        if (code >= 95 && code <= 99) return "عواصف رعدية";
        return "";
    }

    /** "31°C". */
    static String temperature(int celsius) {
        return celsius + "°C";
    }

    private boolean online() {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static String key(Cities.City c) {
        return String.format(Locale.US, "%.4f,%.4f", c.latitude, c.longitude);
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > 64 * 1024) throw new IOException("answer too large");
                }
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    private JSONObject read() {
        if (!cache.isFile() || cache.length() > 64 * 1024) return null;
        try (InputStream in = new FileInputStream(cache)) {
            byte[] b = new byte[(int) cache.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void write(JSONObject o) {
        File tmp = new File(cache.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "could not cache the weather: " + e.getMessage());
            return;
        }
        if (!tmp.renameTo(cache)) tmp.delete();
    }
}
