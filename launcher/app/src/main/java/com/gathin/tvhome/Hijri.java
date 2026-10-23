package com.gathin.tvhome;

import java.time.LocalDate;

/**
 * Gregorian to Umm al-Qura Hijri and back. On the TV this is {@link IcuHijri} (android.icu); unit tests plug
 * in ICU4J, the same calendar code, so HijriDates is tested against the real tables.
 */
interface Hijri {
    /** {year, month 1-12, day} of {@code date}. */
    int[] fromGregorian(LocalDate date);

    LocalDate toGregorian(int year, int month, int day);
}
