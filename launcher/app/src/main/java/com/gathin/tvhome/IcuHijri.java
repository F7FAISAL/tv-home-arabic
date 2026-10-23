package com.gathin.tvhome;

import android.icu.util.IslamicCalendar;
import android.icu.util.TimeZone;

import java.time.LocalDate;
import java.time.ZoneOffset;

/** Umm al-Qura dates from the platform's ICU (android.icu, API 24+). Noon UTC keeps a date from slipping a day. */
final class IcuHijri implements Hijri {

    private final IslamicCalendar cal;

    IcuHijri() {
        cal = new IslamicCalendar(TimeZone.GMT_ZONE);
        cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
    }

    @Override
    public synchronized int[] fromGregorian(LocalDate date) {
        cal.setTimeInMillis(date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli());
        return new int[]{cal.get(IslamicCalendar.EXTENDED_YEAR), cal.get(IslamicCalendar.MONTH) + 1,
                cal.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    @Override
    public synchronized LocalDate toGregorian(int year, int month, int day) {
        cal.clear();
        cal.set(IslamicCalendar.EXTENDED_YEAR, year);
        cal.set(IslamicCalendar.MONTH, month - 1);
        cal.set(IslamicCalendar.DAY_OF_MONTH, day);
        cal.set(IslamicCalendar.HOUR_OF_DAY, 12);
        return java.time.Instant.ofEpochMilli(cal.getTimeInMillis()).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
