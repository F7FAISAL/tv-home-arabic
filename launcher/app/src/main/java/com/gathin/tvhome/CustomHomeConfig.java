package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

import org.json.JSONObject;

import java.io.File;

/**
 * Settings of the clean Arabic home view (custom_home in config.xml): the built-in values, overlaid by the same
 * theme config.json as {@link Config}, so a theme push changes them without a rebuild.
 */
final class CustomHomeConfig {

    /** Fonts the font key may name; anything else is plex. thmanyah needs the copy on the TV (ThemeFonts). */
    static final java.util.List<String> FONTS = java.util.Arrays.asList("plex", "outfit", "system", "thmanyah");

    /** Choices made on the TV (pill menu), kept apart from theme pushes: they win over the theme until changed. */
    static final String CHOICES = "custom_home";
    static final String CHOICE_CITY = "city";
    static final String CHOICE_TEAMS = "teams";
    static final String CHOICE_DIGITS = "digits";
    /** «إظهار وإخفاء» on the TV: one boolean per block, under the theme key's own name; set ones win over the theme. */
    static final String[] SHOW_KEYS = {"show_prayer", "show_ramadan", "show_match", "show_weather", "show_dates", "show_iqama"};
    /** Theme keys of iqama_minutes, in PrayerTimes order; the TV's choice is kept as iqama_<key>. */
    static final String[] IQAMA_KEYS = {"fajr", "dhuhr", "asr", "maghrib", "isha"};

    /**
     * Text sizes in sp, Home and screensaver, each key overridable in a theme's "sizes" / "screensaver_sizes":
     * city, weather (temperature; the condition and icon follow), clock, dates, label, value (the bottom row's
     * headlines), sub (its small lines), pill.
     */
    static final String[] SIZE_KEYS = {"city", "weather", "clock", "dates", "label", "value", "sub", "pill"};
    private static final float[] HOME_SIZES = {17, 20, 156, 19, 13, 29, 13, 13};
    private static final float[] DREAM_SIZES = {14.5f, 17, 96, 15, 11, 21, 11, 13};

    /** "latin" (0123, the default) or "arabic" (٠١٢٣): every number in the view; «الأرقام» on the TV overrides. */
    final String digits;
    final java.util.Map<String, Float> sizes, dreamSizes;
    /** Minutes between the screensaver's few-pixel shifts against burn-in (screensaver_shift_minutes). */
    final int dreamShiftMinutes;

    final boolean enabled;
    /** Shown top left, and the place prayer times and weather follow: the TV's choice, the theme's "city", or Madinah. */
    final Cities.City city;
    final String greetingName;
    final double latitude, longitude;
    final boolean showPrayer, showRamadan, showMatch, showWeather, showDates, showIqama;
    /** Minutes from adhan to iqama, Fajr to Isha (iqama_minutes; «أوقات الإقامة» on the TV overrides). */
    final int[] iqamaMinutes;
    final String teamId;
    /** The clubs whose next match the bottom bar shows: the TV's «الفرق» choice, the theme's "teams", or team_id. */
    final java.util.List<String> teams;
    /** Seconds each match shows before the next chosen club's (match_rotate_seconds, 3 to 120). */
    final int matchRotateSeconds;
    /** The clock's digit size in sp (clock_size). */
    final int clockSize;
    /** One font for the whole view (font): {@link #FONTS}. */
    final String font;
    /** The public gallery's wallpapers.json on raw.githubusercontent.com (wallpaper_gallery_url), or "". */
    final String wallpaperGalleryUrl;
    /** The Apps Script link listing the user's Drive wallpaper folder (config.local.json, wallpaper_list_url), or "". */
    final String wallpaperListUrl;
    /** Copy photos from a USB drive's TVWallpapers folder (usb_wallpapers, on unless a theme says false). */
    final boolean usbWallpapers;
    /** The theme's wallpapers/ folder; the screensaver shows the picture Home showed last from it. */
    final File wallpaperDir;
    /** The theme's wallpaper.jpg, or null (the screensaver draws it itself; Home uses WallpaperPlayer). */
    final File wallpaper;

    private CustomHomeConfig(String digits, java.util.Map<String, Float> sizes, java.util.Map<String, Float> dreamSizes,
                             int dreamShiftMinutes, boolean enabled, Cities.City city, String greetingName, double latitude,
                             double longitude, boolean showPrayer, boolean showRamadan, boolean showMatch, boolean showWeather,
                             boolean showDates, boolean showIqama, int[] iqamaMinutes, String teamId, java.util.List<String> teams, int clockSize, int matchRotateSeconds, String font,
                             File wallpaperDir, String wallpaperGalleryUrl, String wallpaperListUrl,
                             boolean usbWallpapers, File wallpaper) {
        this.enabled = enabled;
        this.digits = digits;
        this.sizes = sizes;
        this.dreamSizes = dreamSizes;
        this.dreamShiftMinutes = dreamShiftMinutes;
        this.city = city;
        this.greetingName = greetingName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.showPrayer = showPrayer;
        this.showRamadan = showRamadan;
        this.showMatch = showMatch;
        this.showWeather = showWeather;
        this.showDates = showDates;
        this.showIqama = showIqama;
        this.iqamaMinutes = iqamaMinutes;
        this.teamId = teamId;
        this.teams = java.util.Collections.unmodifiableList(teams);
        this.clockSize = clockSize;
        this.matchRotateSeconds = matchRotateSeconds;
        this.font = font;
        this.wallpaperDir = wallpaperDir;
        this.wallpaperGalleryUrl = wallpaperGalleryUrl;
        this.wallpaperListUrl = wallpaperListUrl;
        this.usbWallpapers = usbWallpapers;
        this.wallpaper = wallpaper;
    }

    static CustomHomeConfig load(Context ctx) {
        Resources res = ctx.getResources();
        boolean enabled = res.getBoolean(R.bool.custom_home);
        String name = res.getString(R.string.greeting_name).trim();
        double lat = number(res.getString(R.string.latitude), Double.NaN);
        double lon = number(res.getString(R.string.longitude), Double.NaN);
        boolean prayer = res.getBoolean(R.bool.show_prayer);
        boolean ramadan = res.getBoolean(R.bool.show_ramadan);
        boolean match = res.getBoolean(R.bool.show_match);
        boolean weatherOn = res.getBoolean(R.bool.show_weather);
        boolean datesOn = res.getBoolean(R.bool.show_dates);
        boolean iqamaOn = res.getBoolean(R.bool.show_iqama);
        int[] iqama = res.getIntArray(R.array.iqama_minutes).clone();
        String team = res.getString(R.string.team_id).trim();
        int clockSize = res.getInteger(R.integer.clock_size);
        int rotate = res.getInteger(R.integer.match_rotate_seconds);
        String font = res.getString(R.string.font).trim();
        String cityName = res.getString(R.string.city).trim();
        JSONObject json = Config.overlay(ctx);
        if (json != null) {
            enabled = json.optBoolean("custom_home", enabled);
            cityName = json.optString("city", cityName).trim();
            name = json.optString("greeting_name", name).trim();
            lat = json.optDouble("latitude", lat);
            lon = json.optDouble("longitude", lon);
            prayer = json.optBoolean("show_prayer", prayer);
            ramadan = json.optBoolean("show_ramadan", ramadan);
            match = json.optBoolean("show_match", match);
            weatherOn = json.optBoolean("show_weather", weatherOn);
            datesOn = json.optBoolean("show_dates", datesOn);
            iqamaOn = json.optBoolean("show_iqama", iqamaOn);
            JSONObject im = json.optJSONObject("iqama_minutes");
            for (int i = 0; im != null && i < IQAMA_KEYS.length; i++) iqama[i] = im.optInt(IQAMA_KEYS[i], iqama[i]);
            Object t = json.opt("team_id");
            if (t instanceof String || t instanceof Number) team = String.valueOf(t).trim();
            clockSize = json.optInt("clock_size", clockSize);
            rotate = json.optInt("match_rotate_seconds", rotate);
            font = json.optString("font", font).trim().toLowerCase(java.util.Locale.ROOT);
        }
        android.content.SharedPreferences choices = ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE);
        prayer = choices.getBoolean("show_prayer", prayer);
        ramadan = choices.getBoolean("show_ramadan", ramadan);
        match = choices.getBoolean("show_match", match);
        weatherOn = choices.getBoolean("show_weather", weatherOn);
        datesOn = choices.getBoolean("show_dates", datesOn);
        iqamaOn = choices.getBoolean("show_iqama", iqamaOn);
        for (int i = 0; i < IQAMA_KEYS.length; i++) {
            iqama[i] = Math.max(0, Math.min(90, choices.getInt("iqama_" + IQAMA_KEYS[i], iqama[i])));
        }
        String chosen = choices.getString(CHOICE_CITY, null);
        Cities.City city = Cities.find(chosen != null ? chosen : cityName);
        if (city == null) city = Cities.DEFAULT;
        lat = city.latitude;
        lon = city.longitude;
        // No place, no prayer times: a wrong place would give confident wrong times.
        boolean placeOk = !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 65 && Math.abs(lon) <= 180;
        // A number, or a club name MatchFeed knows; anything else hides the block, and says why in the log.
        String id = MatchFeed.teamId(team);
        if (id == null) {
            android.util.Log.w("TvHome.Match", "team_id \"" + team + "\" is not a TheSportsDB id or a known club name");
        } else {
            team = id;
        }
        // Clubs: picked on the TV, else the theme's "teams" (ids or known names), else team_id alone.
        java.util.List<String> teams = new java.util.ArrayList<>();
        String picked = choices.getString(CHOICE_TEAMS, null);
        if (picked != null) {
            for (String t : picked.split(",")) if (t.trim().matches("\\d{1,9}")) teams.add(t.trim());
        } else if (json != null && json.optJSONArray("teams") != null) {
            org.json.JSONArray a = json.optJSONArray("teams");
            for (int i = 0; i < a.length(); i++) {
                String t = MatchFeed.teamId(String.valueOf(a.opt(i)));
                if (t != null && !teams.contains(t)) teams.add(t);
            }
        } else if (id != null) {
            teams.add(id);
        }
        if (teams.isEmpty()) match = false;
        File dir = Config.dir(ctx);
        File wall = dir != null ? new File(dir, Config.WALLPAPER_NAME) : null;
        if (wall != null && !(wall.isFile() && wall.length() > 0)) wall = null;
        if (json != null && !json.optBoolean("wallpaper", true)) wall = null;
        if (!FONTS.contains(font)) font = "plex";
        String digits = choices.getString(CHOICE_DIGITS, json == null ? "latin" : json.optString("digits", "latin"));
        if (!"arabic".equals(digits)) digits = "latin";
        java.util.Map<String, Float> sizes = sizeMap(HOME_SIZES, json == null ? null : json.optJSONObject("sizes"));
        // clock_size (older key) still sets Home's clock unless "sizes" names it.
        if (json != null && json.has("clock_size") && (json.optJSONObject("sizes") == null
                || !json.optJSONObject("sizes").has("clock"))) sizes.put("clock", (float) Math.max(40, Math.min(300, clockSize)));
        java.util.Map<String, Float> dreamSizes = sizeMap(DREAM_SIZES, json == null ? null : json.optJSONObject("screensaver_sizes"));
        int shift = json == null ? 3 : Math.max(1, Math.min(60, json.optInt("screensaver_shift_minutes", 3)));
        return new CustomHomeConfig(digits, sizes, dreamSizes, shift, enabled, city, name, lat, lon,
                prayer && placeOk, ramadan, match, weatherOn, datesOn, iqamaOn, iqama, team, teams,
                Math.max(40, Math.min(300, clockSize)), Math.max(3, Math.min(120, rotate)), font,
                dir != null ? new File(dir, "wallpapers") : null,
                json == null ? "" : json.optString("wallpaper_gallery_url", "").trim(),
                json == null ? "" : json.optString("wallpaper_list_url", "").trim(),
                json == null || json.optBoolean("usb_wallpapers", true), wall);
    }

    private static java.util.Map<String, Float> sizeMap(float[] defaults, JSONObject over) {
        java.util.Map<String, Float> m = new java.util.HashMap<>();
        for (int i = 0; i < SIZE_KEYS.length; i++) {
            float v = defaults[i];
            if (over != null) {
                double d = over.optDouble(SIZE_KEYS[i], Double.NaN);
                if (!Double.isNaN(d) && d >= 6 && d <= 320) v = (float) d;
            }
            m.put(SIZE_KEYS[i], v);
        }
        return m;
    }

    static void saveDigits(Context ctx, String digits) {
        ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE).edit().putString(CHOICE_DIGITS, digits).apply();
    }

    /** Minutes from adhan to iqama for one prayer (PrayerTimes order), picked on the TV; kept over theme pushes. */
    static void saveIqama(Context ctx, int prayer, int minutes) {
        ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE).edit().putInt("iqama_" + IQAMA_KEYS[prayer], minutes).apply();
    }

    /** A block switched on or off on the TV ({@link #SHOW_KEYS}); it stays until switched again, theme pushes included. */
    static void saveShown(Context ctx, String key, boolean on) {
        ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply();
    }

    /** The city picked on the TV; it stays until another is picked, theme pushes included. */
    static void saveCity(Context ctx, String name) {
        ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE).edit().putString(CHOICE_CITY, name).apply();
    }

    /** The clubs picked on the TV (TheSportsDB ids); an empty list hides the matches until clubs are picked. */
    static void saveTeams(Context ctx, java.util.List<String> ids) {
        ctx.getSharedPreferences(CHOICES, Context.MODE_PRIVATE).edit()
                .putString(CHOICE_TEAMS, android.text.TextUtils.join(",", ids)).apply();
    }

    private static double number(String s, double fallback) {
        try {
            return s == null || s.trim().isEmpty() ? fallback : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
