package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/** res/raw/clubs.json, as «أنديتي» lists it (unit tests run from the app module, so the path is the source file). */
public class ClubsTest {

    private static JSONObject read(String name) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(new File("src/main/res/raw/" + name).toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void everyClubIsListedOnceUnderItsHomeLeague() throws Exception {
        // A club picked once shows its next match in every competition (EspnFallback), so no club needs a second
        // row under a cup or the Champions League, where ticking one row and not the other was easy to miss.
        JSONArray leagues = read("clubs.json").getJSONArray("leagues");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < leagues.length(); i++) {
            JSONObject l = leagues.getJSONObject(i);
            assertFalse(l.getString("name"), l.getString("name").contains("أبطال"));
            JSONArray teams = l.getJSONArray("teams");
            for (int j = 0; j < teams.length(); j++) {
                String id = teams.getJSONArray(j).getString(0);
                assertTrue("listed twice: " + teams.getJSONArray(j), ids.add(id));
            }
        }
        assertEquals(129, ids.size());
    }

    @Test
    public void everyListedClubHasAnEspnIdForItsOtherCompetitions() throws Exception {
        JSONObject espn = read("espn_clubs.json").getJSONObject("clubs");
        JSONArray leagues = read("clubs.json").getJSONArray("leagues");
        for (int i = 0; i < leagues.length(); i++) {
            JSONArray teams = leagues.getJSONObject(i).getJSONArray("teams");
            for (int j = 0; j < teams.length(); j++) {
                String id = teams.getJSONArray(j).getString(0);
                assertTrue("no ESPN id: " + teams.getJSONArray(j), espn.has(id) && !espn.getJSONObject(id).optString("espn").isEmpty());
            }
        }
    }
}
