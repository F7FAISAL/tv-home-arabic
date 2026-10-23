package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The second match source: ESPN's public site API, asked at every refresh alongside TheSportsDB (MatchFeed), whose
 * free key often knows only a club's next league game. res/raw/espn_clubs.json (tools/espn_clubs.py) gives each
 * club's ESPN id; one request to {@code soccer/all/teams/<id>/schedule} lists its fixtures in every competition it
 * plays (league, domestic cups and super cups, AFC, UEFA, Club World Cup), each with its own competition, so a club
 * picked once shows its next match wherever it is. The answer is turned into TheSportsDB's eventsnext shape, so
 * MatchFeed.parse reads both alike. These are public endpoints, not a guaranteed contract: an answer with no readable
 * fixture is treated as a failure and the cached one is kept.
 */
final class EspnFallback {

    private static final String TAG = "TvHome.Match";
    static final String SOURCE = "ESPN";
    private static final String URL_FMT = "https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/%s/schedule?fixture=true";

    /** ESPN competition code to the league name MatchFeed.leagueAr knows (TheSportsDB's spelling where it has one). */
    private static final Map<String, String> LEAGUE_NAMES = new HashMap<>();

    static {
        String[] names = {
                "ksa.1", "Saudi Pro League", "eng.1", "English Premier League", "esp.1", "Spanish La Liga",
                "ita.1", "Italian Serie A", "ger.1", "German Bundesliga", "fra.1", "French Ligue 1",
                "uefa.champions", "UEFA Champions League", "afc.champions", "AFC Champions League Elite",
                "afc.cup", "AFC Champions League Two", "ksa.kings.cup", "Saudi King's Cup",
                "ksa.super_cup", "Saudi Super Cup", "eng.fa", "English FA Cup", "eng.league_cup", "English League Cup",
                "eng.charity", "English Community Shield", "esp.copa_del_rey", "Spanish Copa del Rey",
                "esp.super_cup", "Spanish Super Cup", "ita.coppa_italia", "Italian Coppa Italia",
                "ita.super_cup", "Italian Super Cup", "ger.dfb_pokal", "German DFB Pokal", "ger.super_cup", "German Super Cup",
                "fra.coupe_de_france", "French Coupe de France", "fra.super_cup", "French Super Cup",
                "uefa.europa", "UEFA Europa League", "uefa.europa.conf", "UEFA Europa Conference League",
                "uefa.super_cup", "UEFA Super Cup", "fifa.cwc", "FIFA Club World Cup",
        };
        for (int i = 0; i < names.length; i += 2) LEAGUE_NAMES.put(names[i], names[i + 1]);
    }

    /** A club's ESPN id. */
    static final class Club {
        final String espnId;

        Club(String espnId) {
            this.espnId = espnId;
        }
    }

    private static Map<String, Club> clubs;
    /** ESPN team id to TheSportsDB id, so a fixture names the chosen club by the id HomeScene knows it by. */
    private static Map<String, String> tsdbIds;
    /** ESPN team id to TheSportsDB's name ("Al-Hilal", not ESPN's "Al Hilal"), the spelling MatchFeed.teamAr knows. */
    private static Map<String, String> tsdbNames;

    /** The fallback entry for a TheSportsDB club id, or null when the club has none. */
    static synchronized Club club(Context ctx, String tsdbId) {
        if (clubs == null) load(ctx);
        return clubs.get(tsdbId);
    }

    private static void load(Context ctx) {
        clubs = new HashMap<>();
        tsdbIds = new HashMap<>();
        tsdbNames = new HashMap<>();
        try (InputStream in = ctx.getResources().openRawResource(R.raw.espn_clubs)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            JSONObject all = new JSONObject(buf.toString(StandardCharsets.UTF_8.name())).getJSONObject("clubs");
            for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) {
                String id = it.next();
                JSONObject c = all.getJSONObject(id);
                clubs.put(id, new Club(c.getString("espn")));
                tsdbIds.put(c.getString("espn"), id);
                if (!c.optString("name").isEmpty()) tsdbNames.put(c.getString("espn"), c.getString("name"));
            }
        } catch (Exception e) {
            Log.w(TAG, "espn_clubs.json not read: " + e);
        }
    }

    /**
     * The club's upcoming fixtures in every competition it has, soonest first, as an eventsnext-shaped answer.
     * Throws when no competition gave a readable fixture, so the caller keeps its last good answer.
     */
    static JSONObject fetch(Context ctx, Club club) throws Exception {
        if (clubs == null) club(ctx, "");
        List<JSONObject> events = convert(new JSONObject(get(String.format(Locale.US, URL_FMT, club.espnId))), "", tsdbIds, tsdbNames);
        if (events.isEmpty()) throw new IOException("no upcoming fixture in any competition");
        Collections.sort(events, (x, y) -> x.optString("strTimestamp").compareTo(y.optString("strTimestamp")));
        JSONArray out = new JSONArray();
        for (JSONObject e : events) out.put(e);
        return new JSONObject().put("events", out);
    }

    static List<JSONObject> convert(JSONObject answer, String code, Map<String, String> ids) throws Exception {
        return convert(answer, code, ids, null);
    }

    /**
     * One ESPN schedule answer to eventsnext events: not finished, both teams and an ISO kick-off present. Each event's
     * own competition (league.slug, as the all-competitions schedule gives it) names its league, else {@code code}.
     * {@code ids} turns ESPN team ids into TheSportsDB ids (others become "espn:<id>"), {@code names} into
     * TheSportsDB's spelling of the club (others keep ESPN's).
     */
    static List<JSONObject> convert(JSONObject answer, String code, Map<String, String> ids, Map<String, String> names)
            throws Exception {
        List<JSONObject> out = new ArrayList<>();
        JSONArray events = answer.optJSONArray("events");
        if (events == null) return out;
        for (int i = 0; i < events.length(); i++) {
            JSONObject ev = events.optJSONObject(i);
            JSONObject comp = ev == null ? null : ev.optJSONArray("competitions") == null ? null
                    : ev.optJSONArray("competitions").optJSONObject(0);
            if (comp == null) continue;
            JSONObject type = comp.optJSONObject("status") == null ? null : comp.optJSONObject("status").optJSONObject("type");
            if (type != null && (type.optBoolean("completed") || "post".equals(type.optString("state")))) continue;
            String when = ev.optString("date", "").trim();
            if (!when.matches("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d(:\\d\\d)?Z")) continue;
            JSONObject home = null, away = null;
            JSONArray cs = comp.optJSONArray("competitors");
            for (int j = 0; cs != null && j < cs.length(); j++) {
                JSONObject c = cs.optJSONObject(j);
                if (c == null || c.optJSONObject("team") == null) continue;
                if ("home".equals(c.optString("homeAway"))) home = c.getJSONObject("team");
                if ("away".equals(c.optString("homeAway"))) away = c.getJSONObject("team");
            }
            if (home == null || away == null || home.optString("displayName").trim().isEmpty()
                    || away.optString("displayName").trim().isEmpty()) continue;
            JSONObject competition = ev.optJSONObject("league");
            String slug = competition == null ? "" : competition.optString("slug", "").trim();
            String league = LEAGUE_NAMES.containsKey(slug) ? LEAGUE_NAMES.get(slug)
                    : !slug.isEmpty() ? competition.optString("name", "").trim()
                    : LEAGUE_NAMES.containsKey(code) ? LEAGUE_NAMES.get(code) : "";
            out.add(new JSONObject()
                    .put("idEvent", "espn:" + ev.optString("id"))
                    .put("strHomeTeam", teamName(home, names))
                    .put("idHomeTeam", teamId(home.optString("id"), ids))
                    .put("strAwayTeam", teamName(away, names))
                    .put("idAwayTeam", teamId(away.optString("id"), ids))
                    .put("strLeague", league)
                    .put("strTimestamp", when)
                    .put("strStatus", "NS")
                    .put("strPostponed", "no"));
        }
        return out;
    }

    private static String teamName(JSONObject team, Map<String, String> names) {
        String t = names == null ? null : names.get(team.optString("id"));
        return t != null ? t : team.optString("displayName").trim();
    }

    private static String teamId(String espnId, Map<String, String> ids) {
        String t = ids == null ? null : ids.get(espnId);
        return t != null ? t : "espn:" + espnId;
    }

    private static String get(String url) throws IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(10_000);
        c.setRequestProperty("User-Agent", "cleanyourtv-launcher");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) {
                    buf.write(b, 0, n);
                    if (buf.size() > 1024 * 1024) throw new IOException("answer too large");
                }
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }
}
