package com.gathin.tvhome;

import com.ibm.icu.util.IslamicCalendar;
import com.ibm.icu.util.TimeZone;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** IcuHijri for the JVM: ICU4J's Umm al-Qura calendar, the code android.icu is built from. */
final class Icu4jHijri implements Hijri {

    private final IslamicCalendar cal;

    Icu4jHijri() {
        cal = new IslamicCalendar(TimeZone.GMT_ZONE);
        cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
    }

    @Override
    public int[] fromGregorian(LocalDate date) {
        cal.setTimeInMillis(date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli());
        return new int[]{cal.get(IslamicCalendar.EXTENDED_YEAR), cal.get(IslamicCalendar.MONTH) + 1,
                cal.get(IslamicCalendar.DAY_OF_MONTH)};
    }

    @Override
    public LocalDate toGregorian(int year, int month, int day) {
        cal.clear();
        cal.set(IslamicCalendar.EXTENDED_YEAR, year);
        cal.set(IslamicCalendar.MONTH, month - 1);
        cal.set(IslamicCalendar.DAY_OF_MONTH, day);
        cal.set(IslamicCalendar.HOUR_OF_DAY, 12);
        return Instant.ofEpochMilli(cal.getTimeInMillis()).atZone(ZoneOffset.UTC).toLocalDate();
    }
}
