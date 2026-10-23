package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The Ramadan block of the clean view: a countdown to 1 Ramadan, the day of Ramadan, or Eid. Umm al-Qura
 * dates are the calendar's estimate; the real start follows the moon sighting, which is why the countdown
 * says «تقريباً». The civil date is used throughout (the Hijri day really begins at sunset).
 */
final class HijriDates {

    static final int RAMADAN = 9, SHAWWAL = 10, DHU_AL_HIJJAH = 12;

    enum Kind { COUNTDOWN, RAMADAN_DAY, EID_FITR, EID_ADHA }

    static final class Status {
        final Kind kind;
        /** COUNTDOWN: whole days until 1 Ramadan; RAMADAN_DAY: the day of Ramadan; Eid: unused (0). */
        final int days;
        /** COUNTDOWN: the Gregorian date of 1 Ramadan; null otherwise. */
        final LocalDate start;

        Status(Kind kind, int days, LocalDate start) {
            this.kind = kind;
            this.days = days;
            this.start = start;
        }
    }

    private HijriDates() {
    }

    static boolean isRamadan(LocalDate date, Hijri hijri) {
        return hijri.fromGregorian(date)[1] == RAMADAN;
    }

    static Status status(LocalDate today, Hijri hijri) {
        int[] h = hijri.fromGregorian(today);
        int year = h[0], month = h[1], day = h[2];
        if (month == RAMADAN) return new Status(Kind.RAMADAN_DAY, day, null);
        if (month == SHAWWAL && day <= 3) return new Status(Kind.EID_FITR, 0, null);
        if (month == DHU_AL_HIJJAH && day >= 10 && day <= 13) return new Status(Kind.EID_ADHA, 0, null);
        LocalDate start = hijri.toGregorian(month < RAMADAN ? year : year + 1, RAMADAN, 1);
        return new Status(Kind.COUNTDOWN, (int) ChronoUnit.DAYS.between(today, start), start);
    }
}
