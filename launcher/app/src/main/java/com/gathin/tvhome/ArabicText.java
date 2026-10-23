package com.gathin.tvhome;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Arabic wording for the clean view, with Western digits whatever the TV's locale (ar-EG would give ٤:٣٦):
 * times, day and month names, and counted nouns with the right plural form.
 */
final class ArabicText {

    private static final String[] DAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو",
            "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    private static final String[] HIJRI_MONTHS = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى",
            "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    static final String AM = "ص", PM = "م";

    private ArabicText() {
    }

    /** "4:36" in the 12-hour clock. */
    static String clock(LocalTime t) {
        int h = t.getHour() % 12;
        return String.format(Locale.US, "%d:%02d", h == 0 ? 12 : h, t.getMinute());
    }

    static String amPm(LocalTime t) {
        return t.getHour() < 12 ? AM : PM;
    }

    /** "4:36 ص". */
    static String time(LocalTime t) {
        return clock(t) + " " + amPm(t);
    }

    static String dayName(LocalDate d) {
        return DAYS[d.getDayOfWeek().getValue() - 1];
    }

    /** "الاثنين 8 فبراير". */
    static String dayAndDate(LocalDate d) {
        return dayName(d) + " " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
    }

    /** "11 جمادى الأولى 1448 هـ" from {year, month 1-12, day}. */
    static String hijriDate(int[] h) {
        return h[2] + " " + HIJRI_MONTHS[h[1] - 1] + " " + h[0] + " هـ";
    }

    /** "22 أكتوبر 2026 م". */
    static String gregorianDate(LocalDate d) {
        return d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear() + " م";
    }

    /**
     * A counted noun: {@code one} for 1, {@code two} for 2, "n {@code few}" for 3 to 10, and "n {@code many}" for
     * 11 to 99. Past a hundred the last two digits decide, and an even hundred (or 101, 102) takes the singular:
     * 100 يوم، 103 أيام، 111 يوماً.
     */
    static String count(int n, String one, String two, String few, String many, String hundredForm) {
        if (n == 1) return one;
        if (n == 2) return two;
        int rest = n % 100;
        if (n > 100 && rest <= 2) return n + " " + hundredForm;
        if (n >= 100 && rest == 0) return n + " " + hundredForm;
        if (rest >= 3 && rest <= 10) return n + " " + few;
        return n + " " + many;
    }

    static String days(int n) {
        return count(n, "يوم واحد", "يومين", "أيام", "يوماً", "يوم");
    }

    static String hours(int n) {
        return count(n, "ساعة", "ساعتين", "ساعات", "ساعة", "ساعة");
    }

    static String minutes(int n) {
        return count(n, "دقيقة", "دقيقتين", "دقائق", "دقيقة", "دقيقة");
    }

    /** "بعد 9 ساعات و38 دقيقة", "بعد ساعتين", "بعد 5 دقائق"; Duration is whole minutes (PrayerTimes.next). */
    static String inTime(Duration d) {
        long total = Math.max(1, d.toMinutes());
        int h = (int) (total / 60), m = (int) (total % 60);
        if (h == 0) return "بعد " + minutes(m);
        if (m == 0) return "بعد " + hours(h);
        return "بعد " + hours(h) + " و" + minutes(m);
    }
}
