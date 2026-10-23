package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The clubs the TV pill's «الفرق» offers, by competition (res/raw/clubs.json: Roshn Saudi League, Premier League,
 * La Liga, Serie A, Bundesliga, Ligue 1 and the Champions League league phase, 2026-27, with TheSportsDB ids).
 * The free API cannot list a league's clubs (it answers with a sample league), so the list ships with the app.
 */
final class Clubs {

    static final class Club {
        final String id, name;

        Club(String id, String name) {
            this.id = id;
            this.name = name;
        }

        /** The Arabic name when there is one. */
        String label() {
            return MatchFeed.teamAr(name);
        }
    }

    static final class League {
        final String name;
        final List<Club> clubs;

        League(String name, List<Club> clubs) {
            this.name = name;
            this.clubs = Collections.unmodifiableList(clubs);
        }
    }

    private Clubs() {
    }

    static List<League> load(Context ctx) {
        List<League> out = new ArrayList<>();
        try (InputStream in = ctx.getResources().openRawResource(R.raw.clubs)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            JSONArray leagues = new JSONObject(buf.toString(StandardCharsets.UTF_8.name())).getJSONArray("leagues");
            for (int i = 0; i < leagues.length(); i++) {
                JSONObject l = leagues.getJSONObject(i);
                JSONArray teams = l.getJSONArray("teams");
                List<Club> clubs = new ArrayList<>();
                for (int j = 0; j < teams.length(); j++) {
                    JSONArray t = teams.getJSONArray(j);
                    clubs.add(new Club(t.getString(0), t.getString(1)));
                }
                Collections.sort(clubs, (a, c) -> a.label().compareTo(c.label()));
                out.add(new League(l.getString("name"), clubs));
            }
        } catch (Exception e) {
            android.util.Log.w("TvHome.Clubs", "club list unreadable: " + e);
        }
        return out;
    }
}
