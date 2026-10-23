package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Fills the clean Arabic home view (layout custom_home) and keeps it current: the clock and the next prayer
 * every minute (ACTION_TIME_TICK, so also right after midnight and after a clock or time zone change), the
 * Ramadan block, and the next match from {@link MatchFeed}. Shared by Home and the screensaver. A block that
 * cannot be worked out is hidden; it never takes Home down with it.
 */
final class HomeScene {

    private static final String TAG = "TvHome.Scene";
    /** Kick-off times are shown in Saudi time (tv-home-prompt.md), whatever the match's own time zone. */
    private static final ZoneId MATCH_ZONE = ZoneId.of("Asia/Riyadh");

    private final Context ctx;
    private final CustomHomeConfig cfg;
    private final Hijri hijri = new IcuHijri();
    /** One feed per chosen club (CustomHomeConfig.teams, or «الفرق» on the TV), and each one's next match. */
    private final java.util.Map<String, MatchFeed> feeds = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, MatchFeed.Match> matches = new java.util.HashMap<>();
    /** A ceiling on the matches taking turns; far more than anyone picks. */
    static final int MAX_MATCHES = 50;
    private java.util.List<MatchFeed.Match> shownMatches = java.util.Collections.emptyList();
    private int matchIndex;
    private Typeface regularFace;
    private final android.os.Handler rotator = new android.os.Handler(android.os.Looper.getMainLooper());
    /** The place on screen; the pill menu's «الموقع» changes it while Home runs (setCity). */
    private Cities.City city;
    private WeatherFeed weather;
    /** «إظهار وإخفاء»: the theme's show_* or the TV's choice, switched at once by setShown. */
    private boolean prayerOn, ramadanOn, datesOn;
    /** The iqama countdown after each adhan, and its minutes from Fajr to Isha (setShown follows the menu). */
    private boolean iqamaOn;
    private int[] iqamaMinutes;
    private final TextView prayerLabel;
    private WeatherFeed.Now weatherNow;
    private final View weatherRow;
    private final TextView dates;
    private final TextView weatherTemp, weatherCondition;
    private final WeatherIcon weatherIcon = new WeatherIcon();
    private boolean registered;

    /** pill: the menu button (it opened from the «TV» pill before); dreamButton starts the screensaver; buttons holds both. */
    final View root, content, pill, dreamButton, buttons;
    private final TextView time, amPm, greeting;
    private final View prayerBlock, ramadanBlock, matchBlock, ramadanSeparator, matchSeparator;
    private final TextView prayerName, prayerTime, prayerSub;
    private final TextView ramadanLabel, ramadanValue, ramadanSub;
    private final TextView matchLabel, matchValue, matchSub;

    /** Weights of the one font of the view (CustomHomeConfig.font): text and numbers, headline words and name. */
    private static final int REGULAR = 0, BOLD = 1;
    /** Every number in the view looks like the prayer time's digits. */
    private final NumberStyle numbers;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            update();
        }
    };

    HomeScene(Context ctx, View root, CustomHomeConfig cfg) {
        this(ctx, root, cfg, false);
    }

    /** {@code dream}: the screensaver's sizes (CustomHomeConfig.dreamSizes) instead of Home's. */
    HomeScene(Context ctx, View root, CustomHomeConfig cfg, boolean dream) {
        this.ctx = ctx;
        this.cfg = cfg;
        this.root = root;
        if (cfg.showMatch) for (String id : cfg.teams) feeds.put(id, new MatchFeed(ctx, id));
        weather = cfg.showWeather ? new WeatherFeed(ctx) : null;
        prayerOn = cfg.showPrayer;
        ramadanOn = cfg.showRamadan;
        datesOn = cfg.showDates;
        iqamaOn = cfg.showIqama;
        iqamaMinutes = cfg.iqamaMinutes;
        weatherRow = root.findViewById(R.id.weatherRow);
        dates = root.findViewById(R.id.customDates);
        weatherTemp = root.findViewById(R.id.weatherTemp);
        weatherCondition = root.findViewById(R.id.weatherCondition);
        ((android.widget.ImageView) root.findViewById(R.id.weatherIcon)).setImageDrawable(weatherIcon);
        content = root.findViewById(R.id.customContent);
        pill = root.findViewById(R.id.customPill);
        dreamButton = root.findViewById(R.id.customDreamButton);
        buttons = root.findViewById(R.id.customButtons);
        time = root.findViewById(R.id.customTime);
        amPm = root.findViewById(R.id.customAmPm);
        greeting = root.findViewById(R.id.customGreeting);
        prayerBlock = root.findViewById(R.id.prayerBlock);
        ramadanBlock = root.findViewById(R.id.ramadanBlock);
        matchBlock = root.findViewById(R.id.matchBlock);
        ramadanSeparator = root.findViewById(R.id.ramadanSeparator);
        matchSeparator = root.findViewById(R.id.matchSeparator);
        prayerName = root.findViewById(R.id.prayerName);
        prayerTime = root.findViewById(R.id.prayerTime);
        prayerSub = root.findViewById(R.id.prayerSub);
        ramadanLabel = root.findViewById(R.id.ramadanLabel);
        ramadanValue = root.findViewById(R.id.ramadanValue);
        ramadanSub = root.findViewById(R.id.ramadanSub);
        matchLabel = root.findViewById(R.id.matchLabel);
        matchValue = root.findViewById(R.id.matchValue);
        matchSub = root.findViewById(R.id.matchSub);

        prayerLabel = root.findViewById(R.id.prayerLabel);
        Typeface[] font = fonts(ctx, cfg.font);
        for (TextView v : new TextView[]{amPm, dates, prayerLabel, prayerTime, prayerSub, weatherTemp, weatherCondition,
                ramadanLabel, ramadanSub, matchLabel, matchSub}) {
            v.setTypeface(font[REGULAR]);
        }
        for (TextView v : new TextView[]{greeting, prayerName, ramadanValue, matchValue}) v.setTypeface(font[BOLD]);
        // The clock in the prayer time's style too: the regular weight, as every other number (NumberStyle).
        time.setTypeface(font[REGULAR]);
        numbers = new NumberStyle(digitFace(font[REGULAR]));
        regularFace = font[REGULAR];
        numbers.setArabic("arabic".equals(cfg.digits));
        applySizes(dream ? cfg.dreamSizes : cfg.sizes, prayerLabel);
        // Top left: the city in Arabic, so no letter spacing (it would break the joined letters) and no caps.
        greeting.setLetterSpacing(0f);
        greeting.setAllCaps(false);
        greeting.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        city = cfg.city;
        greeting.setText(city.name);
        // Set last, after every other property of the view, so nothing can bring the layout's font back.
        greeting.setTypeface(font[BOLD]);
    }

    private void sp(TextView v, float size) {
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /** Every text size from one set (see CustomHomeConfig.SIZE_KEYS); the icon, pill and separators follow. */
    private void applySizes(java.util.Map<String, Float> z, TextView prayerLabel) {
        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        sp(greeting, z.get("city"));
        weatherSize = z.get("weather");
        weatherAt(weatherSize);
        sp(time, z.get("clock"));
        sp(amPm, Math.max(10, z.get("clock") * 0.24f));
        sp(dates, z.get("dates"));
        for (TextView v : new TextView[]{prayerLabel, ramadanLabel, matchLabel}) sp(v, z.get("label"));
        for (TextView v : new TextView[]{prayerName, prayerTime, ramadanValue, matchValue}) sp(v, z.get("value"));
        for (TextView v : new TextView[]{prayerSub, ramadanSub, matchSub}) sp(v, z.get("sub"));
        // The two round buttons: as tall as the «TV» pill was at this size, the icon a quarter in from the edge.
        int d = Math.round(z.get("pill") * 3f * density), inset = Math.round(d * 0.24f);
        for (View b : new View[]{pill, dreamButton}) {
            android.view.ViewGroup.LayoutParams bl = b.getLayoutParams();
            bl.width = bl.height = d;
            b.setLayoutParams(bl);
            b.setPadding(inset, inset, inset, inset);
        }
        // Separators as tall as the blocks they divide.
        int sep = Math.round((z.get("label") + z.get("value") + z.get("sub")) * 1.55f * density);
        for (View s : new View[]{ramadanSeparator, matchSeparator}) {
            android.view.ViewGroup.LayoutParams sl = s.getLayoutParams();
            sl.height = sep;
            s.setLayoutParams(sl);
        }
    }

    /** The theme's weather size (sizes / screensaver_sizes), before fitTopLeft narrows it to the city's width. */
    private float weatherSize;

    /** The weather line at size {@code w} sp: the temperature, the condition at 77%, the icon and the gaps follow. */
    private void weatherAt(float w) {
        float density = ctx.getResources().getDisplayMetrics().scaledDensity;
        sp(weatherTemp, w);
        sp(weatherCondition, w * 0.77f);
        View icon = root.findViewById(R.id.weatherIcon);
        android.view.ViewGroup.LayoutParams lp = icon.getLayoutParams();
        lp.width = lp.height = Math.round(w * 1.3f * density);
        icon.setLayoutParams(lp);
        ((android.view.ViewGroup.MarginLayoutParams) weatherCondition.getLayoutParams()).rightMargin = Math.round(w * 0.7f * density);
        ((android.view.ViewGroup.MarginLayoutParams) weatherTemp.getLayoutParams()).rightMargin = Math.round(w * 0.6f * density);
    }

    /**
     * City and weather as one block: the weather line at the theme's size, made smaller as a whole when that would
     * be wider than the city's name, so it never reaches past it. Runs when either changes.
     */
    private void fitTopLeft() {
        if (weatherRow.getVisibility() != View.VISIBLE) return;
        weatherAt(weatherSize);
        int any = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        greeting.measure(any, any);
        weatherRow.measure(any, any);
        int city = greeting.getMeasuredWidth(), line = weatherRow.getMeasuredWidth();
        if (city > 0 && line > city) weatherAt(weatherSize * city / (float) line);
        weatherRow.requestLayout();
    }

    /** «الأرقام» from the pill menu: 0123 or ٠١٢٣ everywhere, at once. */
    void setDigits(String digits) {
        numbers.setArabic("arabic".equals(digits));
        update();
        if (!shownMatches.isEmpty()) fillMatch(shownMatches.get(matchIndex % shownMatches.size()));
    }

    /**
     * {regular, bold} of the named font. plex is bundled with both weights (and Arabic); outfit is bundled for
     * Latin, Arabic then comes from the system font; system is the TV's own sans-serif; thmanyah is the owner's
     * Thmanyah Sans, read from the TV (ThemeFonts; never inside the APK), and plex when the TV has no copy of it.
     */
    private static Typeface[] fonts(Context ctx, String name) {
        Resources res = ctx.getResources();
        switch (name) {
            case "thmanyah":
                Typeface[] own = ThemeFonts.thmanyah(ctx);
                if (own != null) return own;
                Log.w(TAG, "Thmanyah Sans is not on this TV (tools/install-thmanyah.py, then tools/theme.sh); using IBM Plex Sans Arabic");
                return fonts(ctx, "plex");
            case "outfit":
                return new Typeface[]{res.getFont(R.font.outfit_regular), res.getFont(R.font.outfit_medium)};
            case "system":
                return new Typeface[]{Typeface.create("sans-serif", Typeface.NORMAL),
                        Typeface.create("sans-serif", Typeface.BOLD)};
            default:
                return new Typeface[]{res.getFont(R.font.plex_arabic_regular), res.getFont(R.font.plex_arabic_bold)};
        }
    }

    /** Android's Arabic UI font, read from the TV itself (OFL, part of every Android build; nothing is bundled). */
    private static final String DIGIT_FONT = "/system/fonts/NotoNaskhArabicUI-Regular.ttf";

    /**
     * The digits of every number on Home and the screensaver: Noto Naskh Arabic UI, the font Android draws the apps
     * view clock's ٠١٢٣ with, which has 0123 too; letters stay in the view's font. {@code fallback} where it is missing.
     */
    private static Typeface digitFace(Typeface fallback) {
        try {
            java.io.File f = new java.io.File(DIGIT_FONT);
            return f.isFile() ? Typeface.createFromFile(f) : fallback;
        } catch (RuntimeException e) {
            Log.w(TAG, "digit font not read, using the view's own: " + e);
            return fallback;
        }
    }

    /** The view's one font at its regular weight, for the pill menu's lists too. */
    Typeface face() {
        return regularFace;
    }

    /** A new place from the pill menu: name, prayer times (and weather) follow at once. */
    void setCity(Cities.City c) {
        city = c;
        greeting.setText(c.name);
        fitTopLeft();
        weatherNow = null;   // the old city's reading; the new one is fetched by update()
        update();
    }

    void start() {
        if (!registered) {
            IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(tick, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                ctx.registerReceiver(tick, f);
            }
            registered = true;
        }
        for (java.util.Map.Entry<String, MatchFeed> f : feeds.entrySet()) matches.put(f.getKey(), f.getValue().current(Instant.now()));
        if (weather != null) weatherNow = weather.current(city, Instant.now());
        update();
        rotator.removeCallbacks(rotateMatch);
        rotator.postDelayed(rotateMatch, cfg.matchRotateSeconds * 1000L);
    }

    void stop() {
        rotator.removeCallbacks(rotateMatch);
        matchBlock.animate().cancel();
        matchBlock.setAlpha(1f);
        if (registered) {
            ctx.unregisterReceiver(tick);
            registered = false;
        }
    }

    void update() {
        ZonedDateTime now = ZonedDateTime.now();
        LocalTime t = now.toLocalTime();
        time.setText(numbers.apply(ArabicText.clock(t)));
        amPm.setText(ArabicText.amPm(t));
        boolean day = datesOn && safely("dates", () -> showDates(now.toLocalDate()));
        dates.setVisibility(day ? View.VISIBLE : View.GONE);
        boolean prayer = prayerOn && safely("prayer", () -> showPrayer(now));
        boolean ramadan = ramadanOn && safely("ramadan", () -> showRamadan(now.toLocalDate()));
        boolean game = !feeds.isEmpty() && safely("match", () -> showMatch(now.toInstant()));
        boolean sky = weather != null && safely("weather", () -> showWeather(now.toInstant()));
        if (sky != (weatherRow.getVisibility() == View.VISIBLE)) {
            weatherRow.setVisibility(sky ? View.VISIBLE : View.GONE);
        }
        if (sky) fitTopLeft();
        prayerBlock.setVisibility(prayer ? View.VISIBLE : View.GONE);
        ramadanBlock.setVisibility(ramadan ? View.VISIBLE : View.GONE);
        matchBlock.setVisibility(game ? View.VISIBLE : View.GONE);
        // A separator only between two blocks that are both shown.
        ramadanSeparator.setVisibility(ramadan && prayer ? View.VISIBLE : View.GONE);
        matchSeparator.setVisibility(game && (ramadan || prayer) ? View.VISIBLE : View.GONE);
        WeatherFeed w = weather;
        if (w != null) {
            w.refreshIfDue(city, now.toInstant(), () -> {
                if (weather != w) return;   // switched off in the meantime
                weatherNow = w.current(city, Instant.now());
                update();
            });
        }
        for (java.util.Map.Entry<String, MatchFeed> f : feeds.entrySet()) {
            String id = f.getKey();
            MatchFeed mf = f.getValue();
            mf.refreshIfDue(now.toInstant(), () -> {
                if (feeds.get(id) != mf) return;   // the club was dropped in the meantime
                matches.put(id, mf.current(Instant.now()));
                update();
            });
        }
    }

    private interface Block {
        boolean fill();
    }

    private static boolean safely(String what, Block block) {
        try {
            return block.fill();
        } catch (RuntimeException e) {
            Log.w(TAG, what + " block hidden: " + e);
            return false;
        }
    }

    /**
     * The next prayer; between an adhan and its iqama (show_iqama) the iqama instead: «إقامة صلاة», the prayer, the
     * iqama's time and «الإقامة بعد 12 دقيقة», until the iqama's minute brings the next prayer.
     */
    private boolean showPrayer(ZonedDateTime now) {
        PrayerTimes.Next iqama = iqamaOn ? PrayerTimes.iqama(now, city.latitude, city.longitude, hijri, iqamaMinutes) : null;
        if (iqama != null) {
            prayerLabel.setText(R.string.custom_iqama_label);
            prayerName.setText(PrayerTimes.NAMES_AR[iqama.prayer]);
            prayerTime.setText(numbers.apply(ArabicText.time(iqama.time.toLocalTime())));
            prayerSub.setText(numbers.apply(ctx.getString(R.string.custom_iqama_in, ArabicText.inTime(iqama.remaining))));
            return true;
        }
        prayerLabel.setText(R.string.custom_next_prayer);
        PrayerTimes.Next next = PrayerTimes.next(now, city.latitude, city.longitude, hijri);
        prayerName.setText(PrayerTimes.NAMES_AR[next.prayer]);
        prayerTime.setText(numbers.apply(ArabicText.time(next.time.toLocalTime())));
        prayerSub.setText(numbers.apply(ArabicText.inTime(next.remaining)));
        return true;
    }

    private boolean showRamadan(LocalDate today) {
        HijriDates.Status s = HijriDates.status(today, hijri);
        switch (s.kind) {
            case RAMADAN_DAY:
                ramadanLabel.setText(R.string.custom_ramadan);
                ramadanValue.setText(numbers.apply(ctx.getString(R.string.custom_ramadan_day, s.days)));
                ramadanSub.setText(R.string.custom_ramadan_kareem);
                return true;
            case EID_FITR:
            case EID_ADHA:
                ramadanLabel.setText(R.string.custom_eid);
                ramadanValue.setText(R.string.custom_eid_mubarak);
                ramadanSub.setText(s.kind == HijriDates.Kind.EID_FITR ? R.string.custom_eid_fitr : R.string.custom_eid_adha);
                return true;
            default:
                if (s.days <= 0) return false;
                ramadanLabel.setText(R.string.custom_ramadan);
                ramadanValue.setText(numbers.apply(ArabicText.days(s.days)));
                ramadanSub.setText(numbers.apply(ctx.getString(R.string.custom_ramadan_starts, ArabicText.dayAndDate(s.start))));
                return true;
        }
    }

    /** «الخميس 11 جمادى الأولى 1448 هـ | 22 أكتوبر 2026 م»; the civil date, so both turn over at midnight. */
    private boolean showDates(LocalDate today) {
        dates.setText(numbers.apply(MatchFeed.rtl(ArabicText.dayName(today) + " " + ArabicText.hijriDate(hijri.fromGregorian(today))
                + "   |   " + ArabicText.gregorianDate(today))));
        return true;
    }

    /** Icon, «26°C» and «صافٍ»; false (hidden) when offline or the last reading is over an hour old. */
    private boolean showWeather(Instant now) {
        WeatherFeed.Now w = weatherNow;
        if (w == null || java.time.Duration.between(w.fetched, now).compareTo(WeatherFeed.MAX_AGE) > 0) return false;
        weatherIcon.set(w.code, w.day);
        weatherTemp.setText(numbers.apply(WeatherFeed.temperature(w.temperature)));
        weatherCondition.setText(WeatherFeed.conditionAr(w.code, w.day));
        return true;
    }

    /**
     * «إظهار وإخفاء» from the pill menu: each block on or off at once. Weather and matches start their feeds
     * (cached readings come back at once) or drop them, so a hidden block fetches nothing.
     */
    void setShown(CustomHomeConfig c) {
        prayerOn = c.showPrayer;
        ramadanOn = c.showRamadan;
        datesOn = c.showDates;
        iqamaOn = c.showIqama;
        iqamaMinutes = c.iqamaMinutes;
        if (c.showWeather && weather == null) {
            weather = new WeatherFeed(ctx);
            weatherNow = weather.current(city, Instant.now());
        } else if (!c.showWeather) {
            weather = null;
            weatherNow = null;
        }
        if (c.showMatch && feeds.isEmpty()) {
            setTeams(c.teams);   // updates
            return;
        } else if (!c.showMatch) {
            feeds.clear();
            matches.clear();
        }
        update();
    }

    /** The clubs picked with «الفرق»: their feeds replace the old ones, cached matches come back at once. */
    void setTeams(java.util.List<String> ids) {
        feeds.clear();
        matches.clear();
        matchIndex = 0;
        for (String id : ids) {
            MatchFeed f = new MatchFeed(ctx, id);
            feeds.put(id, f);
            matches.put(id, f.current(Instant.now()));
        }
        update();
    }

    /**
     * One match at a time in large type: «مباراة برشلونة القادمة», the teams, the competition and kick-off. With
     * several chosen clubs the next match of each takes its turn (rotateMatch), a match two of them play once.
     */
    private boolean showMatch(Instant now) {
        java.util.List<MatchFeed.Match> list = MatchFeed.distinct(matches.values(), now, MAX_MATCHES);
        shownMatches = list;
        if (list.isEmpty()) return false;
        fillMatch(list.get(matchIndex % list.size()));
        return true;
    }

    private void fillMatch(MatchFeed.Match m) {
        String ours = feeds.containsKey(m.awayId) && !feeds.containsKey(m.homeId) ? m.awayTeam : m.homeTeam;
        // Names without an Arabic form stay in English, isolated so they cannot reorder the Arabic line.
        matchLabel.setText(MatchFeed.rtl(ctx.getString(R.string.custom_next_match, MatchFeed.isolate(MatchFeed.teamAr(ours)))));
        matchValue.setText(MatchFeed.rtl(MatchFeed.isolate(MatchFeed.teamAr(m.homeTeam)) + " × "
                + MatchFeed.isolate(MatchFeed.teamAr(m.awayTeam))));
        ZonedDateTime k = m.kickoff.atZone(MATCH_ZONE);
        String league = m.league.isEmpty() ? "" : MatchFeed.isolate(MatchFeed.leagueAr(m.league)) + "، ";
        matchSub.setText(numbers.apply(MatchFeed.rtl(league) + ArabicText.dayAndDate(k.toLocalDate()) + "، " + ArabicText.time(k.toLocalTime())));
    }

    /** Every match_rotate_seconds: fade the match out, show the next one, fade it in. Nothing to do for one. */
    private final Runnable rotateMatch = new Runnable() {
        @Override
        public void run() {
            java.util.List<MatchFeed.Match> list = shownMatches;
            if (list.size() > 1 && matchBlock.getVisibility() == View.VISIBLE) {
                matchBlock.animate().alpha(0f).setDuration(450).withEndAction(() -> {
                    matchIndex = (matchIndex + 1) % Math.max(1, shownMatches.size());
                    if (!shownMatches.isEmpty()) fillMatch(shownMatches.get(matchIndex % shownMatches.size()));
                    matchBlock.animate().alpha(1f).setDuration(450).start();
                }).start();
            }
            rotator.postDelayed(this, cfg.matchRotateSeconds * 1000L);
        }
    };
}
