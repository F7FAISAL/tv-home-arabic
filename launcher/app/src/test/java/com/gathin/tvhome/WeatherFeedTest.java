package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;

public class WeatherFeedTest {

    /** Open-Meteo for Madinah, 2026-10-22 03:00. */
    private static final String ANSWER = "{\"current\":{\"time\":\"2026-10-22T03:00\",\"interval\":900,"
            + "\"temperature_2m\":26.3,\"weather_code\":0,\"is_day\":0}}";

    @Test
    public void parsesTheCurrentReading() throws Exception {
        WeatherFeed.Now w = WeatherFeed.parse(new JSONObject(ANSWER), Instant.EPOCH);
        assertEquals(26, w.temperature);
        assertEquals(0, w.code);
        assertFalse(w.day);
        assertEquals("26°C", WeatherFeed.temperature(w.temperature));
        assertEquals("صافٍ", WeatherFeed.conditionAr(w.code, w.day));
    }

    @Test
    public void noReadingNoWeather() throws Exception {
        assertNull(WeatherFeed.parse(null, Instant.EPOCH));
        assertNull(WeatherFeed.parse(new JSONObject("{}"), Instant.EPOCH));
        assertNull(WeatherFeed.parse(new JSONObject("{\"current\":{\"weather_code\":3}}"), Instant.EPOCH));
    }

    @Test
    public void arabicConditions() {
        assertEquals("مشمس", WeatherFeed.conditionAr(0, true));
        assertEquals("غائم جزئياً", WeatherFeed.conditionAr(2, true));
        assertEquals("غائم", WeatherFeed.conditionAr(3, true));
        assertEquals("ضباب", WeatherFeed.conditionAr(45, true));
        assertEquals("أمطار", WeatherFeed.conditionAr(63, true));
        assertEquals("أمطار", WeatherFeed.conditionAr(81, false));
        assertEquals("عواصف رعدية", WeatherFeed.conditionAr(95, true));
        assertEquals("-3°C", WeatherFeed.temperature(-3));
    }

    @Test
    public void anImpossibleReadingIsABrokenAnswer() throws Exception {
        assertNull(WeatherFeed.parse(new JSONObject(ANSWER.replace("26.3", "999")), Instant.EPOCH));
        assertNull(WeatherFeed.parse(new JSONObject(ANSWER.replace("26.3", "-80")), Instant.EPOCH));
        assertNull(WeatherFeed.parse(new JSONObject(ANSWER.replace("\"weather_code\":0", "\"weather_code\":250")), Instant.EPOCH));
        assertNull(WeatherFeed.parse(new JSONObject(ANSWER.replace("\"weather_code\":0,", "")), Instant.EPOCH));
    }
}
