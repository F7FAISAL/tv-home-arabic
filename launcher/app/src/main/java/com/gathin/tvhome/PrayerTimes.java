package com.gathin.tvhome;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * The five daily prayers by the Umm al-Qura method (Makkah): Fajr at the sun 18.5° below the horizon, Asr at a
 * shadow of one object length (standard school), Maghrib at sunset, Isha 90 minutes after Maghrib, 120 in
 * Ramadan. Computed offline from the date and the place, the same solar formulas as praytimes.org; a few seconds
 * from the published tables, rounded to the minute.
 */
final class PrayerTimes {

    static final int FAJR = 0, DHUHR = 1, ASR = 2, MAGHRIB = 3, ISHA = 4;
    static final String[] NAMES_AR = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};

    private static final double FAJR_ANGLE = 18.5;
    private static final double RISE_SET_ANGLE = 0.833;   // refraction plus the sun's radius
    private static final int ISHA_MINUTES = 90;
    private static final int ISHA_MINUTES_RAMADAN = 120;

    /** The prayer the clean view counts down to: which one, when, and how long until it. */
    static final class Next {
        final int prayer;
        final ZonedDateTime time;
        final Duration remaining;

        Next(int prayer, ZonedDateTime time, Duration remaining) {
            this.prayer = prayer;
            this.time = time;
            this.remaining = remaining;
        }
    }

    private PrayerTimes() {
    }

    /** Fajr, Dhuhr, Asr, Maghrib and Isha on {@code date} in {@code zone}, each rounded to the minute. */
    static ZonedDateTime[] forDate(LocalDate date, double latitude, double longitude, ZoneId zone, boolean ramadan) {
        double tz = zone.getRules().getOffset(date.atTime(12, 0)).getTotalSeconds() / 3600.0;
        double jd = julian(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - longitude / (15 * 24.0);

        // One refinement pass from rough guesses, as praytimes.org does: each time is recomputed with the
        // sun's position at that time of day.
        double fajr = sunAngleTime(jd, 5 / 24.0, FAJR_ANGLE, latitude, true);
        double dhuhr = midDay(jd, 12 / 24.0);
        double asr = asrTime(jd, 13 / 24.0, latitude);
        double maghrib = sunAngleTime(jd, 18 / 24.0, RISE_SET_ANGLE, latitude, false);

        double shift = tz - longitude / 15.0;
        double[] hours = {fajr + shift, dhuhr + shift, asr + shift, maghrib + shift,
                maghrib + shift + (ramadan ? ISHA_MINUTES_RAMADAN : ISHA_MINUTES) / 60.0};
        ZonedDateTime midnight = date.atStartOfDay(zone);
        ZonedDateTime[] out = new ZonedDateTime[hours.length];
        for (int i = 0; i < hours.length; i++) {
            long minutes = Math.round(hours[i] * 60);   // to the nearest minute
            out[i] = midnight.plusMinutes(minutes);
        }
        return out;
    }

    /**
     * The first prayer still ahead of {@code now} today; after Isha, tomorrow's Fajr. A prayer whose minute has
     * come is behind: at 4:36 sharp with Fajr at 4:36, Dhuhr is next. The remaining time is rounded up to the
     * minute so the countdown never says 0 while the prayer is still ahead.
     */
    static Next next(ZonedDateTime now, double latitude, double longitude, Hijri hijri) {
        LocalDate today = now.toLocalDate();
        ZonedDateTime[] times = forDate(today, latitude, longitude, now.getZone(), HijriDates.isRamadan(today, hijri));
        for (int i = 0; i < times.length; i++) {
            if (times[i].isAfter(now)) return new Next(i, times[i], ceilToMinute(Duration.between(now, times[i])));
        }
        LocalDate tomorrow = today.plusDays(1);
        ZonedDateTime fajr = forDate(tomorrow, latitude, longitude, now.getZone(),
                HijriDates.isRamadan(tomorrow, hijri))[FAJR];
        return new Next(FAJR, fajr, ceilToMinute(Duration.between(now, fajr)));
    }

    /**
     * The iqama still ahead for a prayer whose adhan has come: from the adhan's minute until {@code minutes[prayer]}
     * after it (Fajr, Dhuhr, Asr, Maghrib, Isha), the time of the iqama and what is left, rounded up to the minute;
     * null outside those windows. At the iqama's minute it is over, as next() treats a prayer's own minute.
     */
    static Next iqama(ZonedDateTime now, double latitude, double longitude, Hijri hijri, int[] minutes) {
        LocalDate today = now.toLocalDate();
        ZonedDateTime[] times = forDate(today, latitude, longitude, now.getZone(), HijriDates.isRamadan(today, hijri));
        for (int i = times.length - 1; i >= 0; i--) {
            if (now.isBefore(times[i])) continue;
            ZonedDateTime iqama = times[i].plusMinutes(Math.max(0, minutes[i]));
            return now.isBefore(iqama) ? new Next(i, iqama, ceilToMinute(Duration.between(now, iqama))) : null;
        }
        return null;
    }

    private static Duration ceilToMinute(Duration d) {
        long seconds = d.getSeconds() + (d.getNano() > 0 ? 1 : 0);
        return Duration.ofMinutes((seconds + 59) / 60);
    }

    // ---- solar position (praytimes.org) -------------------------------------------------------------------

    private static double julian(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** {declination in degrees, equation of time in hours} at Julian day {@code jd}. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15.0;
        double eqt = q / 15.0 - fixHour(ra);
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double dayPortion) {
        double eqt = sunPosition(jd + dayPortion)[1];
        return fixHour(12 - eqt);
    }

    /** When the sun is {@code angle} degrees below the horizon, before noon ({@code beforeNoon}) or after. */
    private static double sunAngleTime(double jd, double dayPortion, double angle, double lat, boolean beforeNoon) {
        double decl = sunPosition(jd + dayPortion)[0];
        double noon = midDay(jd, dayPortion);
        double cosT = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        double t = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cosT)))) / 15.0;
        return noon + (beforeNoon ? -t : t);
    }

    private static double asrTime(double jd, double dayPortion, double lat) {
        double decl = sunPosition(jd + dayPortion)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (1 + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jd, dayPortion, angle, lat, false);
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double fixAngle(double a) {
        a = a - 360.0 * Math.floor(a / 360.0);
        return a < 0 ? a + 360.0 : a;
    }

    private static double fixHour(double h) {
        h = h - 24.0 * Math.floor(h / 24.0);
        return h < 0 ? h + 24.0 : h;
    }
}
