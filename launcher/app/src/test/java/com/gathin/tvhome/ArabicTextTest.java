package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public class ArabicTextTest {

    @Test
    public void twelveHourClockWithWesternDigits() {
        assertEquals("12:05", ArabicText.clock(LocalTime.of(0, 5)));
        assertEquals("ص", ArabicText.amPm(LocalTime.of(0, 5)));
        assertEquals("4:36 ص", ArabicText.time(LocalTime.of(4, 36)));
        assertEquals("12:00 م", ArabicText.time(LocalTime.of(12, 0)));
        assertEquals("11:59 م", ArabicText.time(LocalTime.of(23, 59)));
    }

    @Test
    public void days() {
        assertEquals("يوم واحد", ArabicText.days(1));
        assertEquals("يومين", ArabicText.days(2));
        assertEquals("3 أيام", ArabicText.days(3));
        assertEquals("10 أيام", ArabicText.days(10));
        assertEquals("11 يوماً", ArabicText.days(11));
        assertEquals("99 يوماً", ArabicText.days(99));
        assertEquals("100 يوم", ArabicText.days(100));
        assertEquals("102 يوم", ArabicText.days(102));
        assertEquals("109 أيام", ArabicText.days(109));
        assertEquals("111 يوماً", ArabicText.days(111));
        assertEquals("200 يوم", ArabicText.days(200));
    }

    @Test
    public void hoursAndMinutes() {
        assertEquals("ساعة", ArabicText.hours(1));
        assertEquals("ساعتين", ArabicText.hours(2));
        assertEquals("9 ساعات", ArabicText.hours(9));
        assertEquals("11 ساعة", ArabicText.hours(11));
        assertEquals("دقيقة", ArabicText.minutes(1));
        assertEquals("دقيقتين", ArabicText.minutes(2));
        assertEquals("5 دقائق", ArabicText.minutes(5));
        assertEquals("38 دقيقة", ArabicText.minutes(38));
    }

    @Test
    public void remainingTime() {
        assertEquals("بعد 9 ساعات و38 دقيقة", ArabicText.inTime(Duration.ofMinutes(9 * 60 + 38)));
        assertEquals("بعد ساعتين", ArabicText.inTime(Duration.ofMinutes(120)));
        assertEquals("بعد ساعة ودقيقتين", ArabicText.inTime(Duration.ofMinutes(62)));
        assertEquals("بعد دقيقة", ArabicText.inTime(Duration.ofMinutes(1)));
        assertEquals("بعد دقيقة", ArabicText.inTime(Duration.ZERO));
    }

    @Test
    public void dayAndDate() {
        assertEquals("الاثنين 8 فبراير", ArabicText.dayAndDate(LocalDate.of(2027, 2, 8)));
        assertEquals("الأحد 25 أكتوبر", ArabicText.dayAndDate(LocalDate.of(2026, 10, 25)));
    }
}
