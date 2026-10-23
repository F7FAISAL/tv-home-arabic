package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EspnFallbackTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    /** ESPN's schedule answer, trimmed: a finished match, Al-Ahli v Al-Gharafa (AFC), and one with no kick-off. */
    private static final String SCHEDULE = "{\"events\":["
            + "{\"id\":\"1\",\"date\":\"2026-10-20T16:00Z\",\"competitions\":[{\"status\":{\"type\":{\"state\":\"post\",\"completed\":true}},"
            + "\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"8346\",\"displayName\":\"Al Ahli\"}},"
            + "{\"homeAway\":\"away\",\"team\":{\"id\":\"1\",\"displayName\":\"Al Sadd\"}}]}]},"
            + "{\"id\":\"401\",\"date\":\"2026-10-26T16:00Z\",\"competitions\":[{\"status\":{\"type\":{\"state\":\"pre\",\"completed\":false}},"
            + "\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"8346\",\"displayName\":\"Al Ahli\"}},"
            + "{\"homeAway\":\"away\",\"team\":{\"id\":\"9999\",\"displayName\":\"Al-Gharafa\"}}]}]},"
            + "{\"id\":\"402\",\"date\":\"TBD\",\"competitions\":[{\"competitors\":[]}]}"
            + "]}";

    @Test
    public void convertsOnlyFixturesStillToPlayIntoEventsnextShape() throws Exception {
        Map<String, String> ids = Collections.singletonMap("8346", "137721");
        List<JSONObject> events = EspnFallback.convert(new JSONObject(SCHEDULE), "afc.champions", ids);
        assertEquals(1, events.size());
        JSONObject e = events.get(0);
        assertEquals("Al Ahli", e.getString("strHomeTeam"));
        assertEquals("137721", e.getString("idHomeTeam"));          // our club keeps its TheSportsDB id
        assertEquals("espn:9999", e.getString("idAwayTeam"));
        assertEquals("AFC Champions League Elite", e.getString("strLeague"));
        assertEquals("espn:401", e.getString("idEvent"));

        MatchFeed.Match m = MatchFeed.parse(new JSONObject().put("events", new JSONArray(events)), NOW);
        assertNotNull(m);
        assertEquals(Instant.parse("2026-10-26T16:00:00Z"), m.kickoff);
        assertEquals("الغرافة", MatchFeed.teamAr(m.awayTeam));
        assertEquals("دوري أبطال آسيا للنخبة", MatchFeed.leagueAr(m.league));
    }

    @Test
    public void anEmptyOrBrokenAnswerGivesNoFixtures() throws Exception {
        assertEquals(0, EspnFallback.convert(new JSONObject("{}"), "ksa.1", null).size());
        assertEquals(0, EspnFallback.convert(new JSONObject("{\"events\":[{}]}"), "ksa.1", null).size());
    }

    @Test
    public void theSportsDbWinsAndEspnOnlyFillsIn() throws Exception {
        JSONObject espn = new JSONObject().put("events", new JSONArray(
                EspnFallback.convert(new JSONObject(SCHEDULE), "afc.champions", null)));
        JSONObject cache = new JSONObject()
                .put("fetched", NOW.toEpochMilli()).put("body", new JSONObject("{\"events\":null}"))
                .put("espn_fetched", NOW.toEpochMilli()).put("espn_body", espn);
        MatchFeed.Match m = MatchFeed.pick(cache, NOW);
        assertNotNull(m);
        assertEquals(EspnFallback.SOURCE, m.source);

        JSONObject tsdb = new JSONObject("{\"events\":[{\"idEvent\":\"7\",\"strHomeTeam\":\"Al-Ahli\",\"strAwayTeam\":\"Al-Shabab\","
                + "\"strTimestamp\":\"2026-10-25T18:00:00\",\"strStatus\":\"NS\",\"strPostponed\":\"no\"}]}");
        cache.put("body", tsdb);
        m = MatchFeed.pick(cache, NOW);
        assertEquals(MatchFeed.SOURCE, m.source);
        assertEquals("Al-Shabab", m.awayTeam);

        cache.remove("espn_body");
        cache.put("body", new JSONObject("{\"events\":null}"));
        assertNull(MatchFeed.pick(cache, NOW));
    }
}
