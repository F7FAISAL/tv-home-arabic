package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class PrayerTimesTest {

    private static final double LAT = 24.4686, LON = 39.6142;   // Madinah
    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private final Hijri hijri = new Icu4jHijri();

    /**
     * Reference: api.aladhan.com, method 4 (Umm al-Qura University, Makkah), same place and zone, fetched
     * 2026-10-22. Fajr, Dhuhr, Asr, Maghrib, Isha; within a minute, as published tables round differently.
     */
    private static final String[][] REFERENCE = {
            {"2026-10-22", "05:04", "12:06", "15:24", "17:49", "19:19"},
            {"2026-12-21", "05:37", "12:20", "15:18", "17:38", "19:08"},
            {"2027-02-15", "05:37", "12:36", "15:50", "18:16", "20:16"},   // 8 Ramadan: Isha + 120
            {"2027-03-01", "05:27", "12:34", "15:54", "18:24", "20:24"},   // 22 Ramadan
            {"2027-06-21", "04:02", "12:23", "15:44", "19:13", "20:43"},
            {"2027-09-23", "04:52", "12:14", "15:41", "18:17", "19:47"},
    };

    @Test
    public void matchesPublishedUmmAlQuraTimesForMadinah() {
        for (String[] row : REFERENCE) {
            LocalDate date = LocalDate.parse(row[0]);
            ZonedDateTime[] t = PrayerTimes.forDate(date, LAT, LON, RIYADH, HijriDates.isRamadan(date, hijri));
            for (int i = 0; i < 5; i++) {
                long diff = Math.abs(Duration.between(LocalTime.parse(row[i + 1]), t[i].toLocalTime()).toMinutes());
                assertTrue(row[0] + " " + PrayerTimes.NAMES_AR[i] + ": got " + t[i].toLocalTime() + ", want " + row[i + 1],
                        diff <= 1);
            }
        }
    }

    @Test
    public void ishaIsNinetyMinutesAfterMaghribAndTwoHoursInRamadan() {
        LocalDate d = LocalDate.of(2026, 10, 22);
        ZonedDateTime[] normal = PrayerTimes.forDate(d, LAT, LON, RIYADH, false);
        ZonedDateTime[] ramadan = PrayerTimes.forDate(d, LAT, LON, RIYADH, true);
        assertEquals(90, Duration.between(normal[PrayerTimes.MAGHRIB], normal[PrayerTimes.ISHA]).toMinutes());
        assertEquals(120, Duration.between(ramadan[PrayerTimes.MAGHRIB], ramadan[PrayerTimes.ISHA]).toMinutes());
    }

    private ZonedDateTime[] today() {
        return PrayerTimes.forDate(LocalDate.of(2026, 10, 22), LAT, LON, RIYADH, false);
    }

    @Test
    public void aSecondBeforeEachPrayerCountsDownToItAndItsMinuteMovesOn() {
        ZonedDateTime[] t = today();
        for (int i = 0; i < 5; i++) {
            PrayerTimes.Next before = PrayerTimes.next(t[i].minusSeconds(1), LAT, LON, hijri);
            assertEquals(i, before.prayer);
            assertEquals(Duration.ofMinutes(1), before.remaining);
            PrayerTimes.Next at = PrayerTimes.next(t[i], LAT, LON, hijri);
            assertEquals("at " + PrayerTimes.NAMES_AR[i], i == PrayerTimes.ISHA ? PrayerTimes.FAJR : i + 1, at.prayer);
        }
    }

    /** The bug the design file names: at 7 PM, with Isha still ahead, the next prayer is Isha, not Fajr. */
    @Test
    public void sevenInTheEveningBeforeIshaIsIsha() {
        ZonedDateTime seven = ZonedDateTime.of(2026, 10, 22, 19, 0, 0, 0, RIYADH);
        PrayerTimes.Next n = PrayerTimes.next(seven, LAT, LON, hijri);
        assertEquals(PrayerTimes.ISHA, n.prayer);
        assertEquals(seven.toLocalDate(), n.time.toLocalDate());
        assertEquals("بعد 19 دقيقة", ArabicText.inTime(n.remaining));
    }

    @Test
    public void afterIshaItIsTomorrowsFajr() {
        ZonedDateTime late = ZonedDateTime.of(2026, 10, 22, 21, 30, 0, 0, RIYADH);
        PrayerTimes.Next n = PrayerTimes.next(late, LAT, LON, hijri);
        assertEquals(PrayerTimes.FAJR, n.prayer);
        assertEquals(LocalDate.of(2026, 10, 23), n.time.toLocalDate());
    }

    @Test
    public void aroundMidnight() {
        ZonedDateTime beforeMidnight = ZonedDateTime.of(2026, 10, 22, 23, 59, 30, 0, RIYADH);
        ZonedDateTime afterMidnight = ZonedDateTime.of(2026, 10, 23, 0, 0, 30, 0, RIYADH);
        PrayerTimes.Next a = PrayerTimes.next(beforeMidnight, LAT, LON, hijri);
        PrayerTimes.Next b = PrayerTimes.next(afterMidnight, LAT, LON, hijri);
        assertEquals(PrayerTimes.FAJR, a.prayer);
        assertEquals(PrayerTimes.FAJR, b.prayer);
        assertEquals(a.time, b.time);   // the same Fajr on both sides of midnight
        assertEquals(LocalDate.of(2026, 10, 23), b.time.toLocalDate());
        assertEquals(1, a.remaining.toMinutes() - b.remaining.toMinutes());
    }

    @Test
    public void ramadanIshaIsUsedForTheNextPrayerInRamadan() {
        ZonedDateTime evening = ZonedDateTime.of(2027, 2, 15, 19, 0, 0, 0, RIYADH);   // 8 Ramadan 1448
        PrayerTimes.Next n = PrayerTimes.next(evening, LAT, LON, hijri);
        assertEquals(PrayerTimes.ISHA, n.prayer);
        assertEquals(LocalTime.of(20, 16), n.time.toLocalTime());
    }

    @Test
    public void afterEachAdhanTheIqamaCountsDownThenTheNextPrayerComes() {
        int[] offsets = {25, 20, 20, 10, 20};
        LocalDate date = LocalDate.of(2026, 10, 23);
        ZonedDateTime[] t = PrayerTimes.forDate(date, LAT, LON, RIYADH, HijriDates.isRamadan(date, hijri));
        for (int p = 0; p < t.length; p++) {
            // A second before the adhan: no iqama yet, the prayer is next.
            assertNull(PrayerTimes.iqama(t[p].minusSeconds(1), LAT, LON, hijri, offsets));
            // At the adhan's minute: the iqama, offsets[p] minutes on.
            PrayerTimes.Next q = PrayerTimes.iqama(t[p], LAT, LON, hijri, offsets);
            assertEquals(p, q.prayer);
            assertEquals(t[p].plusMinutes(offsets[p]), q.time);
            assertEquals(Duration.ofMinutes(offsets[p]), q.remaining);
            // Half a minute before the iqama: one minute left, rounded up.
            assertEquals(Duration.ofMinutes(1), PrayerTimes.iqama(t[p].plusMinutes(offsets[p]).minusSeconds(30), LAT, LON, hijri, offsets).remaining);
            // At the iqama's minute it is over.
            assertNull(PrayerTimes.iqama(t[p].plusMinutes(offsets[p]), LAT, LON, hijri, offsets));
        }
        // Zero minutes: no iqama window at all.
        assertNull(PrayerTimes.iqama(t[PrayerTimes.ASR], LAT, LON, hijri, new int[]{0, 0, 0, 0, 0}));
    }
}
