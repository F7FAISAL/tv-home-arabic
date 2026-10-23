package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

public class MatchFeedTest {

    /** Trimmed from TheSportsDB eventsnext.php?id=133739 with the free key, 2026-10-22. */
    private static final String ANSWER = "{\"events\":[{\"idEvent\":\"2506259\",\"strTimestamp\":\"2026-10-25T20:00:00\","
            + "\"strHomeTeam\":\"Barcelona\",\"strAwayTeam\":\"Real Madrid\",\"idHomeTeam\":\"133739\","
            + "\"idAwayTeam\":\"133738\",\"strLeague\":\"Spanish La Liga\",\"dateEvent\":\"2026-10-25\","
            + "\"strTime\":\"20:00:00\",\"strStatus\":\"NS\",\"strPostponed\":\"no\"}]}";
    private static final Instant NOW = Instant.parse("2026-10-22T01:30:00Z");
    private static final String TEAM = "133739";

    @Test
    public void parsesTheNextMatchInUtc() throws Exception {
        MatchFeed.Match m = MatchFeed.parse(new JSONObject(ANSWER), NOW);
        assertNotNull(m);
        assertEquals("Barcelona", m.homeTeam);
        assertEquals(Instant.parse("2026-10-25T20:00:00Z"), m.kickoff);
        // 23:00 in Riyadh, as the clean view shows it
        assertEquals("11:00 م", ArabicText.time(m.kickoff.atZone(java.time.ZoneId.of("Asia/Riyadh")).toLocalTime()));
        assertEquals("برشلونة", MatchFeed.teamAr(m.homeTeam));
        assertEquals("ريال مدريد", MatchFeed.teamAr(m.awayTeam));
        assertEquals("الدوري الإسباني", MatchFeed.leagueAr(m.league));
    }

    @Test
    public void teamIdTakesANumberOrAKnownClubName() {
        assertEquals("133739", MatchFeed.teamId("133739"));
        assertEquals("133604", MatchFeed.teamId(" 133604 "));
        assertEquals("136013", MatchFeed.teamId("Al Hilal"));
        assertEquals("136013", MatchFeed.teamId("al-hilal"));
        assertEquals("136013", MatchFeed.teamId("الهلال"));
        assertEquals("136022", MatchFeed.teamId("Al-Nassr"));
        assertEquals("133738", MatchFeed.teamId("Real Madrid"));
        assertEquals("133729", MatchFeed.teamId("Atlético Madrid"));
        assertNull(MatchFeed.teamId("Some New Club"));
        assertNull(MatchFeed.teamId(""));
    }

    @Test
    public void premierLeagueAndSaudiNamesAreArabic() {
        assertEquals("إيفرتون", MatchFeed.teamAr("Everton"));
        assertEquals("الفيصلي", MatchFeed.teamAr("Al-Faisaly"));
        assertEquals("الدوري الإنجليزي", MatchFeed.leagueAr("English Premier League"));
        assertEquals("دوري روشن السعودي", MatchFeed.leagueAr("Saudi-Arabian Pro League"));
        assertEquals("\u2068Everton FC\u2069", MatchFeed.isolate(MatchFeed.teamAr("Everton FC")));
    }

    @Test
    public void aSharedMatchShowsOnceAndTheSoonestComeFirst() {
        Instant k = Instant.parse("2026-10-25T20:00:00Z");
        MatchFeed.Match clasicoForBarca = new MatchFeed.Match("Barcelona", "133739", "Real Madrid", "133738", "Spanish La Liga", k, "2506259");
        MatchFeed.Match clasicoForMadrid = new MatchFeed.Match("Barcelona", "133739", "Real Madrid", "133738", "Spanish La Liga", k, "2506259");
        MatchFeed.Match nassr = new MatchFeed.Match("Al-Faisaly", "136021", "Al-Nassr", "136022", "Saudi-Arabian Pro League",
                Instant.parse("2026-10-23T13:30:00Z"), "999");
        java.util.List<MatchFeed.Match> out = MatchFeed.distinct(java.util.Arrays.asList(clasicoForBarca, null, nassr, clasicoForMadrid), NOW, 5);
        assertEquals(2, out.size());
        assertEquals("Al-Nassr", out.get(0).awayTeam);
        assertEquals("Barcelona", out.get(1).homeTeam);
        assertEquals(1, MatchFeed.distinct(java.util.Arrays.asList(clasicoForBarca, nassr), NOW, 1).size());
    }

    @Test
    public void aMatchBetweenTwoChosenClubsShowsOnceWhateverTheSource() {
        Instant k = Instant.parse("2026-10-30T18:00:00Z");
        MatchFeed.Match forShabab = new MatchFeed.Match("Al-Shabab", "136020", "Al-Ahli", "137721", "Saudi-Arabian Pro League", k, "2510001");
        MatchFeed.Match forAhli = new MatchFeed.Match("Al-Shabab", "136020", "Al-Ahli", "137721", "Saudi Pro League", k, "espn:401")
                .from(EspnFallback.SOURCE);
        assertEquals(1, MatchFeed.distinct(java.util.Arrays.asList(forShabab, forAhli), NOW, 5).size());
        // Opponents ESPN cannot name by TheSportsDB id stay apart by event.
        MatchFeed.Match a = new MatchFeed.Match("Al-Hilal", "136013", "Al-Shamal", "espn:21000", "AFC", k, "espn:1");
        MatchFeed.Match b = new MatchFeed.Match("Al-Hilal", "136013", "Al-Shamal", "espn:21000", "AFC", k, "espn:2");
        assertEquals(2, MatchFeed.distinct(java.util.Arrays.asList(a, b), NOW, 5).size());
    }

    @Test
    public void unknownNamesStayInEnglish() {
        assertEquals("Some New Club", MatchFeed.teamAr("Some New Club"));
        assertEquals("أتلتيكو مدريد", MatchFeed.teamAr("Atlético Madrid"));
    }

    @Test
    public void aMatchShowsUntilAboutTheEndOfPlay() throws Exception {
        Instant kickoff = Instant.parse("2026-10-25T20:00:00Z");
        assertNotNull(MatchFeed.parse(new JSONObject(ANSWER), kickoff.plus(Duration.ofMinutes(100))));
        assertNull(MatchFeed.parse(new JSONObject(ANSWER), kickoff.plus(Duration.ofMinutes(120))));
    }

    @Test
    public void noDataMeansNoBlock() throws Exception {
        assertNull(MatchFeed.parse(null, NOW));
        assertNull(MatchFeed.parse(new JSONObject("{\"events\":null}"), NOW));
        assertNull(MatchFeed.parse(new JSONObject("{}"), NOW));
        assertNull(MatchFeed.parse(new JSONObject(ANSWER.replace("\"no\"", "\"yes\"")), NOW));   // postponed
        assertNull(MatchFeed.parse(new JSONObject(ANSWER.replace("\"NS\"", "\"FT\"")), NOW));    // finished
        assertNull(MatchFeed.parse(new JSONObject(ANSWER.replace("2026-10-25T20:00:00", "soon")
                .replace("\"strTime\":\"20:00:00\"", "\"strTime\":null")), NOW));               // no kickoff
    }

    @Test
    public void kickoffFromDateAndTimeWhenTimestampIsMissing() throws Exception {
        MatchFeed.Match m = MatchFeed.parse(new JSONObject(ANSWER.replace("\"2026-10-25T20:00:00\"", "null")), NOW);
        assertNotNull(m);
        assertEquals(Instant.parse("2026-10-25T20:00:00Z"), m.kickoff);
    }

    private static JSONObject cache(long fetchedAgoMin, long triedAgoMin, String body) throws Exception {
        JSONObject c = new JSONObject();
        c.put("team", TEAM);
        c.put("fetched", fetchedAgoMin < 0 ? 0 : NOW.minus(Duration.ofMinutes(fetchedAgoMin)).toEpochMilli());
        c.put("tried", NOW.minus(Duration.ofMinutes(triedAgoMin)).toEpochMilli());
        if (body != null) c.put("body", new JSONObject(body));
        return c;
    }

    @Test
    public void refreshesAtMostEverySixHours() throws Exception {
        assertTrue(MatchFeed.due(null, NOW, TEAM));                               // first run
        assertFalse(MatchFeed.due(cache(10, 10, ANSWER), NOW, TEAM));             // fresh
        assertFalse(MatchFeed.due(cache(5 * 60, 5 * 60, ANSWER), NOW, TEAM));     // five hours
        assertTrue(MatchFeed.due(cache(6 * 60, 6 * 60, ANSWER), NOW, TEAM));      // six hours
        assertTrue(MatchFeed.due(cache(10, 10, ANSWER), NOW, "133738"));          // another team
    }

    @Test
    public void offlineKeepsTheLastAnswerAndRetriesHourlyWithoutOne() throws Exception {
        // Six hours old and the last try failed 20 minutes ago: keep showing it, try again later.
        assertFalse(MatchFeed.due(cache(6 * 60, 20, ANSWER), NOW, TEAM));
        assertNotNull(MatchFeed.parse(cache(6 * 60, 20, ANSWER).getJSONObject("body"), NOW));
        // Nothing usable (never fetched, offline): an hour between tries.
        assertFalse(MatchFeed.due(cache(-1, 30, null), NOW, TEAM));
        assertTrue(MatchFeed.due(cache(-1, 60, null), NOW, TEAM));
    }

    @Test
    public void aKickedOffMatchNeedsAnAnswerFromAfterKickoff() throws Exception {
        Instant kickoff = Instant.parse("2026-10-25T20:00:00Z");
        Instant during = kickoff.plus(Duration.ofMinutes(30));
        JSONObject body = new JSONObject(ANSWER);
        // Fetched the day before: the match has kicked off and nothing newer says it is on, so it hides.
        assertNull(MatchFeed.parse(body, during, kickoff.minus(Duration.ofDays(1))));
        // Fetched ten minutes after kick-off and still listed: it shows until about the end of play.
        assertNotNull(MatchFeed.parse(body, during, kickoff.plus(Duration.ofMinutes(10))));
        // Before kick-off an older answer is fine.
        assertNotNull(MatchFeed.parse(body, kickoff.minus(Duration.ofHours(2)), kickoff.minus(Duration.ofDays(1))));
    }

    @Test
    public void aKickedOffMatchWithAnOldAnswerMakesTheFeedDueWithinTheHour() throws Exception {
        Instant kickoff = Instant.parse("2026-10-25T20:00:00Z");
        Instant during = kickoff.plus(Duration.ofMinutes(30));
        JSONObject c = new JSONObject();
        c.put("team", TEAM);
        c.put("fetched", kickoff.minus(Duration.ofHours(2)).toEpochMilli());
        c.put("tried", kickoff.minus(Duration.ofHours(2)).toEpochMilli());
        c.put("body", new JSONObject(ANSWER));
        assertTrue(MatchFeed.due(c, during, TEAM));   // two hours old would not be due for a match still to come
        c.put("tried", during.minus(Duration.ofMinutes(20)).toEpochMilli());
        assertFalse(MatchFeed.due(c, during, TEAM));  // tried 20 minutes ago: wait for the hourly retry
    }

    @Test
    public void onlyAnAnswerWithACompleteEventReplacesTheCache() throws Exception {
        assertTrue(MatchFeed.wellFormed(new JSONObject(ANSWER), TEAM));
        assertFalse(MatchFeed.wellFormed(null, TEAM));
        assertFalse(MatchFeed.wellFormed(new JSONObject("{}"), TEAM));
        assertFalse(MatchFeed.wellFormed(new JSONObject("{\"events\":null}"), TEAM));
        assertFalse(MatchFeed.wellFormed(new JSONObject("{\"events\":[]}"), TEAM));
        assertFalse(MatchFeed.wellFormed(new JSONObject("{\"events\":[{\"strHomeTeam\":\"Barcelona\"}]}"), TEAM));
        assertFalse(MatchFeed.wellFormed(new JSONObject(ANSWER.replace("2026-10-25T20:00:00", "soon")
                .replace("\"2026-10-25\"", "null")), TEAM));
    }

    /** TheSportsDB eventsnext.php?id=133602 (Liverpool), 2026-10-23: a real fixture, but not one of Barcelona's. */
    private static final String OTHER_CLUB = "{\"events\":[{\"idEvent\":\"2494071\",\"strTimestamp\":\"2026-10-25T14:00:00\","
            + "\"strHomeTeam\":\"Liverpool\",\"strAwayTeam\":\"Brighton and Hove Albion\",\"idHomeTeam\":\"133602\","
            + "\"idAwayTeam\":\"133619\",\"strLeague\":\"English Premier League\",\"strStatus\":\"NS\",\"strPostponed\":\"no\"}]}";

    @Test
    public void anotherClubsMatchIsNeverShownOrCached() throws Exception {
        // The answer for Barcelona names Liverpool v Brighton: it must not replace the cache...
        assertFalse(MatchFeed.wellFormed(new JSONObject(OTHER_CLUB), TEAM));
        assertTrue(MatchFeed.wellFormed(new JSONObject(OTHER_CLUB), "133619"));    // Brighton plays in it
        // ...and a cache that already holds it shows nothing for Barcelona, whichever source it came from.
        JSONObject c = cache(10, 10, OTHER_CLUB);
        assertNull(MatchFeed.pick(c, NOW));
        c.put("body", new JSONObject("{\"events\":null}")).put("espn_body", new JSONObject(OTHER_CLUB))
                .put("espn_fetched", NOW.toEpochMilli());
        assertNull(MatchFeed.pick(c, NOW));
        // Barcelona as the away side still counts.
        String away = ANSWER.replace("\"idHomeTeam\":\"133739\"", "\"idHomeTeam\":\"133738\"")
                .replace("\"idAwayTeam\":\"133738\"", "\"idAwayTeam\":\"133739\"");
        assertNotNull(MatchFeed.pick(cache(10, 10, away), NOW));
        assertNotNull(MatchFeed.pick(cache(10, 10, ANSWER), NOW));
    }
}
