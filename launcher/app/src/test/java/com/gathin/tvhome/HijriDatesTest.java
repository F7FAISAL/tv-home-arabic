package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Ramadan and Eid with faked dates (here, never on the TV). */
public class HijriDatesTest {

    private final Hijri hijri = new Icu4jHijri();

    @Test
    public void countdownToRamadan1448() {
        HijriDates.Status s = HijriDates.status(LocalDate.of(2026, 10, 22), hijri);
        assertEquals(HijriDates.Kind.COUNTDOWN, s.kind);
        assertEquals(LocalDate.of(2027, 2, 8), s.start);   // aladhan: 15 Feb 2027 is 8 Ramadan 1448
        assertEquals(DayOfWeek.MONDAY, s.start.getDayOfWeek());
        assertEquals(109, s.days);
        assertEquals("يبدأ الاثنين 8 فبراير", "يبدأ " + ArabicText.dayAndDate(s.start));
    }

    @Test
    public void dayBeforeRamadanIsOneDayAway() {
        HijriDates.Status s = HijriDates.status(LocalDate.of(2027, 2, 7), hijri);
        assertEquals(HijriDates.Kind.COUNTDOWN, s.kind);
        assertEquals(1, s.days);
        assertEquals("يوم واحد", ArabicText.days(s.days));
    }

    @Test
    public void duringRamadanItCountsTheDay() {
        HijriDates.Status first = HijriDates.status(LocalDate.of(2027, 2, 8), hijri);
        assertEquals(HijriDates.Kind.RAMADAN_DAY, first.kind);
        assertEquals(1, first.days);
        assertEquals(8, HijriDates.status(LocalDate.of(2027, 2, 15), hijri).days);
        assertTrue(HijriDates.isRamadan(LocalDate.of(2027, 3, 1), hijri));
        assertFalse(HijriDates.isRamadan(LocalDate.of(2026, 10, 22), hijri));
    }

    @Test
    public void eidAlFitrIsTheFirstThreeDaysOfShawwal() {
        LocalDate eid = hijri.toGregorian(1448, HijriDates.SHAWWAL, 1);
        for (int i = 0; i < 3; i++) {
            assertEquals(HijriDates.Kind.EID_FITR, HijriDates.status(eid.plusDays(i), hijri).kind);
        }
        HijriDates.Status after = HijriDates.status(eid.plusDays(3), hijri);
        assertEquals(HijriDates.Kind.COUNTDOWN, after.kind);
        assertEquals(hijri.toGregorian(1449, HijriDates.RAMADAN, 1), after.start);   // next year's Ramadan
        assertEquals(HijriDates.Kind.RAMADAN_DAY, HijriDates.status(eid.minusDays(1), hijri).kind);
    }

    @Test
    public void eidAlAdhaIsTenthToThirteenthOfDhuAlHijjah() {
        LocalDate arafah = hijri.toGregorian(1448, HijriDates.DHU_AL_HIJJAH, 9);
        assertEquals(HijriDates.Kind.COUNTDOWN, HijriDates.status(arafah, hijri).kind);
        for (int i = 1; i <= 4; i++) {
            assertEquals(HijriDates.Kind.EID_ADHA, HijriDates.status(arafah.plusDays(i), hijri).kind);
        }
        assertEquals(HijriDates.Kind.COUNTDOWN, HijriDates.status(arafah.plusDays(5), hijri).kind);
    }

    @Test
    public void datesUnderTheClock() {
        LocalDate d = LocalDate.of(2026, 10, 22);   // aladhan: 11-05-1448
        assertEquals("11 جمادى الأولى 1448 هـ", ArabicText.hijriDate(hijri.fromGregorian(d)));
        assertEquals("22 أكتوبر 2026 م", ArabicText.gregorianDate(d));
        assertEquals("8 رمضان 1448 هـ", ArabicText.hijriDate(hijri.fromGregorian(LocalDate.of(2027, 2, 15))));
    }

    @Test
    public void conversionRoundTrips() {
        for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2028, 1, 1)); d = d.plusDays(17)) {
            int[] h = hijri.fromGregorian(d);
            assertEquals(d, hijri.toGregorian(h[0], h[1], h[2]));
        }
    }
}
