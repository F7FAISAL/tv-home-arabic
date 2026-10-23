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
    public void theSoonerOfBothSourcesWinsAndTheSportsDbOnATie() throws Exception {
        JSONObject espn = new JSONObject().put("events", new JSONArray(
                EspnFallback.convert(new JSONObject(SCHEDULE), "afc.champions", Collections.singletonMap("8346", "137721"))));
        JSONObject cache = new JSONObject().put("team", "137721")
                .put("fetched", NOW.toEpochMilli()).put("body", new JSONObject("{\"events\":null}"))
                .put("espn_fetched", NOW.toEpochMilli()).put("espn_body", espn);
        MatchFeed.Match m = MatchFeed.pick(cache, NOW);
        assertNotNull(m);
        assertEquals(EspnFallback.SOURCE, m.source);

        JSONObject tsdb = new JSONObject("{\"events\":[{\"idEvent\":\"7\",\"strHomeTeam\":\"Al-Ahli\",\"strAwayTeam\":\"Al-Shabab\",\"idHomeTeam\":\"137721\","
                + "\"strTimestamp\":\"2026-10-25T18:00:00\",\"strStatus\":\"NS\",\"strPostponed\":\"no\"}]}");
        cache.put("body", tsdb);
        m = MatchFeed.pick(cache, NOW);
        assertEquals(MatchFeed.SOURCE, m.source);
        assertEquals("Al-Shabab", m.awayTeam);

        // A cup or AFC match before the league game TheSportsDB knows: ESPN's comes first.
        cache.put("body", new JSONObject(tsdb.toString().replace("2026-10-25T18:00:00", "2026-10-30T18:00:00")));
        m = MatchFeed.pick(cache, NOW);
        assertEquals(EspnFallback.SOURCE, m.source);
        assertEquals(Instant.parse("2026-10-26T16:00:00Z"), m.kickoff);

        cache.remove("espn_body");
        cache.put("body", new JSONObject("{\"events\":null}"));
        assertNull(MatchFeed.pick(cache, NOW));
    }

    /** soccer/all/teams/929/schedule, trimmed: Al-Hilal's next league game and the AFC match before it. */
    private static final String ALL_COMPETITIONS = "{\"events\":["
            + "{\"id\":\"501\",\"date\":\"2026-10-31T18:00Z\",\"league\":{\"slug\":\"ksa.1\",\"name\":\"Saudi Pro League\"},"
            + "\"competitions\":[{\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"929\",\"displayName\":\"Al Hilal\"}},"
            + "{\"homeAway\":\"away\",\"team\":{\"id\":\"2273\",\"displayName\":\"Al Fateh\"}}]}]},"
            + "{\"id\":\"502\",\"date\":\"2026-10-27T18:15Z\",\"league\":{\"slug\":\"afc.champions\",\"name\":\"AFC Champions League Elite\"},"
            + "\"competitions\":[{\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"21000\",\"displayName\":\"Al-Shamal\"}},"
            + "{\"homeAway\":\"away\",\"team\":{\"id\":\"929\",\"displayName\":\"Al Hilal\"}}]}]},"
            + "{\"id\":\"503\",\"date\":\"2026-12-02T17:00Z\",\"league\":{\"slug\":\"xyz.cup\",\"name\":\"Some New Cup\"},"
            + "\"competitions\":[{\"competitors\":[{\"homeAway\":\"home\",\"team\":{\"id\":\"929\",\"displayName\":\"Al Hilal\"}},"
            + "{\"homeAway\":\"away\",\"team\":{\"id\":\"1\",\"displayName\":\"Al Sadd\"}}]}]}"
            + "]}";

    @Test
    public void oneAllCompetitionsAnswerNamesEachMatchByItsOwnCompetition() throws Exception {
        List<JSONObject> events = EspnFallback.convert(new JSONObject(ALL_COMPETITIONS), "", Collections.singletonMap("929", "136013"),
                Collections.singletonMap("929", "Al-Hilal"));
        assertEquals(3, events.size());
        assertEquals("Saudi Pro League", events.get(0).getString("strLeague"));
        assertEquals("AFC Champions League Elite", events.get(1).getString("strLeague"));
        assertEquals("Some New Cup", events.get(2).getString("strLeague"));            // unknown code: ESPN's own name
        assertEquals("Al-Hilal", events.get(1).getString("strAwayTeam"));               // TheSportsDB's spelling
        assertEquals("الهلال", MatchFeed.teamAr(events.get(1).getString("strAwayTeam")));

        JSONObject cache = new JSONObject().put("team", "136013").put("fetched", 0)
                .put("espn_fetched", NOW.toEpochMilli()).put("espn_body", new JSONObject().put("events", new JSONArray(events)));
        MatchFeed.Match m = MatchFeed.pick(cache, NOW);
        assertEquals(Instant.parse("2026-10-27T18:15:00Z"), m.kickoff);                 // the AFC match, not the league's
        assertEquals("دوري أبطال آسيا للنخبة", MatchFeed.leagueAr(m.league));
    }
}
