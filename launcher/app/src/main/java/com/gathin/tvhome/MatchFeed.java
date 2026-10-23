package com.gathin.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The team's next match for the clean view, from TheSportsDB's free API with its public test key ("123", no
 * account, no secret). The only part of the launcher that uses the internet: at most one request every six hours
 * while there is a usable match, the last good answer kept on disk, and nothing shown when there is none.
 */
final class MatchFeed {

    private static final String TAG = "TvHome.Match";
    /** Named in the pill menu's read-only source line. */
    static final String SOURCE = "TheSportsDB";
    private static final String URL_FMT = "https://www.thesportsdb.com/api/v1/json/123/eventsnext.php?id=%s";
    /** One cache per club: match-133739.json. */
    private static final String CACHE_FMT = "match-%s.json";
    static final long REFRESH_MS = 6 * 60 * 60 * 1000L;
    /** With no usable match cached (first boot, or offline last time) a failed fetch is retried sooner. */
    static final long RETRY_MS = 60 * 60 * 1000L;
    /** A match stays on screen until about the end of play. */
    static final Duration SHOWN_AFTER_KICKOFF = Duration.ofMinutes(115);

    static final class Match {
        final String homeTeam, awayTeam, homeId, awayId, league;
        final Instant kickoff;
        /** TheSportsDB idEvent, so a match between two chosen clubs shows once; teams and kickoff when absent. */
        final String eventId;
        /** Where it came from: {@link #SOURCE} or {@link EspnFallback#SOURCE}; named in the pill menu. */
        final String source;

        Match(String homeTeam, String homeId, String awayTeam, String awayId, String league, Instant kickoff) {
            this(homeTeam, homeId, awayTeam, awayId, league, kickoff, "");
        }

        Match(String homeTeam, String homeId, String awayTeam, String awayId, String league, Instant kickoff,
              String eventId) {
            this(homeTeam, homeId, awayTeam, awayId, league, kickoff, eventId, SOURCE);
        }

        Match(String homeTeam, String homeId, String awayTeam, String awayId, String league, Instant kickoff,
              String eventId, String source) {
            this.source = source;
            this.eventId = eventId == null || eventId.isEmpty() ? homeTeam + "|" + awayTeam + "|" + kickoff : eventId;
            this.homeTeam = homeTeam;
            this.homeId = homeId;
            this.awayTeam = awayTeam;
            this.awayId = awayId;
            this.league = league;
            this.kickoff = kickoff;
        }

        Match from(String source) {
            return new Match(homeTeam, homeId, awayTeam, awayId, league, kickoff, eventId, source);
        }

        boolean showAt(Instant now) {
            return now.isBefore(kickoff.plus(SHOWN_AFTER_KICKOFF));
        }
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private final Context ctx;
    private final File cache;
    private final String teamId;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean fetching;

    MatchFeed(Context ctx, String teamId) {
        this.ctx = ctx.getApplicationContext();
        this.cache = new File(ctx.getFilesDir(), String.format(Locale.US, CACHE_FMT, teamId));
        this.teamId = teamId;
    }

    /** The cached next match that is still to be shown, or null. Reads a small file; call off the hot path. */
    Match current(Instant now) {
        JSONObject c = readCache();
        return c == null || !teamId.equals(c.optString("team")) ? null : pick(c, now);
    }

    /**
     * TheSportsDB's next match, else the ESPN fallback's (kept in the same cache under espn_body when TheSportsDB had
     * no upcoming match at the last refresh), each judged by its own fetch time; null when neither has one.
     */
    static Match pick(JSONObject c, Instant now) {
        Match m = parse(c.optJSONObject("body"), now, Instant.ofEpochMilli(c.optLong("fetched", 0)));
        if (m != null) return m;
        m = parse(c.optJSONObject("espn_body"), now, Instant.ofEpochMilli(c.optLong("espn_fetched", 0)));
        return m != null ? m.from(EspnFallback.SOURCE) : null;
    }

    /** For the pill menu: the source of the club's shown match and when that source last answered (null: none). */
    static final class Info {
        final String source;
        final Instant updated;

        Info(String source, Instant updated) {
            this.source = source;
            this.updated = updated;
        }
    }

    Info info(Instant now) {
        JSONObject c = readCache();
        if (c == null || !teamId.equals(c.optString("team"))) return new Info(null, null);
        Match m = pick(c, now);
        long t = m != null && EspnFallback.SOURCE.equals(m.source) ? c.optLong("espn_fetched", 0) : c.optLong("fetched", 0);
        return new Info(m == null ? null : m.source, t > 0 ? Instant.ofEpochMilli(t) : null);
    }

    /**
     * «مصدر مباراة الأهلي: ESPN · آخر تحديث: 4:33 ص» for each chosen club (six at most): the source its shown match came
     * from and when that source last answered, with the day too when it was not today; in the view's digits.
     */
    static java.util.List<String> sourceLines(Context ctx, CustomHomeConfig c) {
        java.util.Map<String, String> names = new HashMap<>();
        for (Clubs.League l : Clubs.load(ctx)) for (Clubs.Club club : l.clubs) names.put(club.id, club.label());
        Instant now = Instant.now();
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String id : c.teams) {
            if (out.size() == 6) break;
            Info info = new MatchFeed(ctx, id).info(now);
            String when = ctx.getString(R.string.custom_match_source_never);
            if (info.updated != null) {
                java.time.ZonedDateTime z = info.updated.atZone(java.time.ZoneId.systemDefault());
                when = z.toLocalDate().equals(java.time.LocalDate.now()) ? ArabicText.time(z.toLocalTime())
                        : ArabicText.dayAndDate(z.toLocalDate()) + "، " + ArabicText.time(z.toLocalTime());
            }
            String source = info.source != null ? isolate(info.source) : ctx.getString(R.string.custom_match_source_none);
            String name = names.containsKey(id) ? names.get(id) : id;
            String line = rtl(ctx.getString(R.string.custom_match_source, isolate(name), source, when));
            out.add("arabic".equals(c.digits) ? NumberStyle.toArabic(line) : line);
        }
        return out;
    }

    /** Fetches in the background when the cache is due, then runs {@code onUpdate} on the main thread. */
    void refreshIfDue(Instant now, Runnable onUpdate) {
        if (fetching || !due(readCache(), now, teamId)) return;
        fetching = true;
        IO.execute(() -> {
            boolean changed = fetch(now);
            main.post(() -> {
                fetching = false;
                if (changed) onUpdate.run();
            });
        });
    }

    /** Due when there is no answer for this team, the last success is six hours old, or (no match) an hour after a failure. */
    static boolean due(JSONObject cache, Instant now, String teamId) {
        if (cache == null || !teamId.equals(cache.optString("team"))) return true;
        long ok = cache.optLong("fetched", 0), tried = cache.optLong("tried", 0);
        boolean usable = pick(cache, now) != null;
        long since = now.toEpochMilli() - Math.max(ok, tried);
        if (usable) return now.toEpochMilli() - ok >= REFRESH_MS && since >= RETRY_MS;
        return since >= RETRY_MS;
    }

    private boolean fetch(Instant now) {
        JSONObject old = readCache();
        boolean sameTeam = old != null && teamId.equals(old.optString("team"));
        JSONObject out = new JSONObject();
        try {
            out.put("team", teamId);
            out.put("tried", now.toEpochMilli());
            out.put("fetched", sameTeam ? old.optLong("fetched", 0) : 0);
            if (sameTeam && old.has("body")) out.put("body", old.get("body"));
            if (sameTeam && old.has("espn_body")) {
                out.put("espn_body", old.get("espn_body"));
                out.put("espn_fetched", old.optLong("espn_fetched", 0));
            }
            JSONObject body = new JSONObject(get(String.format(Locale.US, URL_FMT, teamId)));
            // Safe data: only an answer with a complete event replaces the cached one (and its fetch time).
            if (!wellFormed(body)) throw new IOException("answer has no complete event");
            out.put("body", body);
            out.put("fetched", now.toEpochMilli());
        } catch (Exception e) {
            // Offline, a timeout or a changed API: keep the last good answer and say nothing on screen.
            Log.w(TAG, "next match not updated: " + e);
        }
        // TheSportsDB has nothing upcoming for this club: ask ESPN (league, AFC, King's Cup), keep its last answer on failure.
        EspnFallback.Club espn = EspnFallback.club(ctx, teamId);
        if (espn != null && parse(out.optJSONObject("body"), now, Instant.ofEpochMilli(out.optLong("fetched", 0))) == null) {
            try {
                out.put("espn_body", EspnFallback.fetch(ctx, espn));
                out.put("espn_fetched", now.toEpochMilli());
            } catch (Exception e) {
                Log.w(TAG, "ESPN fallback not updated for " + teamId + ": " + e);
            }
        }
        writeCache(out);
        return true;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
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
                    if (buf.size() > 256 * 1024) throw new IOException("answer too large");
                }
                return buf.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /**
     * True when an eventsnext answer holds at least one event with both teams and a readable kick-off, whatever its
     * date: an empty, cut or changed answer is not worth more than the last good one in the cache.
     */
    static boolean wellFormed(JSONObject body) {
        JSONArray events = body == null ? null : body.optJSONArray("events");
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && !e.optString("strHomeTeam", "").trim().isEmpty() && !e.optString("strAwayTeam", "").trim().isEmpty()
                    && kickoff(e) != null) return true;
        }
        return false;
    }

    /**
     * The first event of an eventsnext answer that has not finished: kickoff from strTimestamp (UTC), or
     * dateEvent and strTime; postponed, cancelled or finished events are skipped. Null when nothing qualifies.
     */
    static Match parse(JSONObject body, Instant now) {
        return parse(body, now, now);
    }

    /**
     * As {@link #parse(JSONObject, Instant)}, for an answer fetched at {@code fetched}: a match that has kicked off
     * shows only when the answer came after its kick-off, so an old cache never keeps a match on screen that the
     * source may already have dropped; the next event in the answer, if any, takes its place.
     */
    static Match parse(JSONObject body, Instant now, Instant fetched) {
        JSONArray events = body == null ? null : body.optJSONArray("events");
        if (events == null) return null;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            if ("yes".equalsIgnoreCase(e.optString("strPostponed"))) continue;
            String status = e.optString("strStatus", "").toUpperCase(Locale.ROOT);
            if (status.matches("FT|AET|PEN|CANC|PST|ABD|AWD|WO|MATCH FINISHED")) continue;
            Instant kickoff = kickoff(e);
            String home = e.optString("strHomeTeam", "").trim(), away = e.optString("strAwayTeam", "").trim();
            if (kickoff == null || home.isEmpty() || away.isEmpty()) continue;
            Match m = new Match(home, e.optString("idHomeTeam"), away, e.optString("idAwayTeam"),
                    e.optString("strLeague", "").trim(), kickoff, e.optString("idEvent", ""));
            if (!now.isBefore(kickoff) && fetched.isBefore(kickoff)) continue;   // kicked off, no fresh word
            if (m.showAt(now)) return m;
        }
        return null;
    }

    private static Instant kickoff(JSONObject e) {
        String ts = e.optString("strTimestamp", "").trim();
        try {
            if (!ts.isEmpty() && !"null".equals(ts)) {
                if (ts.endsWith("Z") || ts.matches(".*[+-]\\d\\d:\\d\\d$")) return java.time.OffsetDateTime.parse(ts).toInstant();
                return LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC);
            }
            String date = e.optString("dateEvent", ""), time = e.optString("strTime", "");
            if (date.isEmpty() || "null".equals(date) || time.isEmpty() || "null".equals(time)) return null;
            return LocalDateTime.parse(date + "T" + time.substring(0, Math.min(8, time.length())))
                    .toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;   // an unreadable kickoff cannot be shown
        }
    }

    private JSONObject readCache() {
        if (!cache.isFile() || cache.length() > 512 * 1024) return null;
        try (InputStream in = new FileInputStream(cache)) {
            byte[] b = new byte[(int) cache.length()];
            int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            return new JSONObject(new String(b, 0, n, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(JSONObject o) {
        File tmp = new File(cache.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "could not cache the next match: " + e.getMessage());
            return;
        }
        if (!tmp.renameTo(cache)) tmp.delete();
    }

    // ---- Arabic names ---------------------------------------------------------------------------------------

    private static final Map<String, String> TEAMS = new HashMap<>();
    private static final Map<String, String> LEAGUES = new HashMap<>();
    private static final Map<String, String> TEAM_IDS = new HashMap<>();

    static {
        String[] teams = {
                "barcelona", "برشلونة", "real madrid", "ريال مدريد", "atletico madrid", "أتلتيكو مدريد",
                "sevilla", "إشبيلية", "valencia", "فالنسيا", "villarreal", "فياريال", "real betis", "ريال بيتيس",
                "real sociedad", "ريال سوسيداد", "athletic bilbao", "أتلتيك بلباو", "celta vigo", "سيلتا فيغو",
                "getafe", "خيتافي", "osasuna", "أوساسونا", "mallorca", "مايوركا", "girona", "جيرونا",
                "rayo vallecano", "رايو فاييكانو", "alaves", "ألافيس", "deportivo alaves", "ألافيس",
                "espanyol", "إسبانيول", "las palmas", "لاس بالماس", "leganes", "ليغانيس",
                "real valladolid", "بلد الوليد", "valladolid", "بلد الوليد", "elche", "إلتشي", "levante", "ليفانتي",
                "real oviedo", "ريال أوفييدو", "granada", "غرناطة", "cadiz", "قادش", "almeria", "ألميريا",
                "bayern munich", "بايرن ميونخ", "manchester city", "مانشستر سيتي", "liverpool", "ليفربول",
                "arsenal", "آرسنال", "chelsea", "تشيلسي", "manchester united", "مانشستر يونايتد",
                "tottenham hotspur", "توتنهام", "newcastle united", "نيوكاسل", "aston villa", "أستون فيلا",
                "paris sg", "باريس سان جيرمان", "paris saint-germain", "باريس سان جيرمان", "monaco", "موناكو",
                "juventus", "يوفنتوس", "inter milan", "إنتر ميلان", "inter", "إنتر ميلان", "ac milan", "ميلان",
                "milan", "ميلان", "napoli", "نابولي", "atalanta", "أتالانتا", "borussia dortmund", "بوروسيا دورتموند",
                "bayer leverkusen", "باير ليفركوزن", "rb leipzig", "لايبزيغ", "benfica", "بنفيكا", "porto", "بورتو",
                "sporting cp", "سبورتينغ لشبونة", "psv eindhoven", "آيندهوفن", "ajax", "أياكس",
                "club brugge", "كلوب بروج", "galatasaray", "غلطة سراي", "olympiacos", "أولمبياكوس",
                "slavia prague", "سلافيا براغ", "copenhagen", "كوبنهاغن", "al-hilal", "الهلال", "al hilal", "الهلال",
                "al-nassr", "النصر", "al nassr", "النصر", "al-ittihad", "الاتحاد", "al ittihad", "الاتحاد",
                "al-ahli", "الأهلي", "al ahli", "الأهلي",
                // Saudi Pro League, as TheSportsDB spells it
                "al-shabab", "الشباب", "al-ettifaq", "الاتفاق", "al-taawoun", "التعاون", "al-fateh", "الفتح",
                "al-fayha", "الفيحاء", "al-khaleej", "الخليج", "al-riyadh", "الرياض", "al-faisaly", "الفيصلي",
                "al-qadsiah", "القادسية", "al-kholood", "الخلود", "al-okhdood", "الأخدود", "damac", "ضمك",
                "al-hazem", "الحزم", "al-najma", "النجمة", "neom", "نيوم", "al-wehda", "الوحدة", "al-raed", "الرائد",
                "al-orobah", "العروبة", "al-taawon", "التعاون",
                // Premier League
                "bournemouth", "بورنموث", "brentford", "برينتفورد", "brighton and hove albion", "برايتون",
                "crystal palace", "كريستال بالاس", "everton", "إيفرتون", "fulham", "فولهام", "leeds united", "ليدز يونايتد",
                "nottingham forest", "نوتنغهام فورست", "sunderland", "سندرلاند", "west ham united", "وست هام",
                "wolverhampton wanderers", "وولفرهامبتون", "burnley", "بيرنلي", "coventry city", "كوفنتري سيتي",
                "ipswich town", "إيبسويتش", "leicester city", "ليستر سيتي", "southampton", "ساوثهامبتون",
                // others that meet Barcelona and the Saudi clubs
                "atlético madrid", "أتلتيكو مدريد", "eintracht frankfurt", "آينتراخت فرانكفورت", "lazio", "لاتسيو",
                "roma", "روما", "as roma", "روما", "olympique marseille", "مارسيليا", "lille", "ليل", "feyenoord", "فينورد",
                "celtic", "سلتيك", "red star belgrade", "النجم الأحمر", "young boys", "يونغ بويز",
                // the rest of the clubs in res/raw/clubs.json
                "abha", "أبها", "al-diriyah", "الدرعية", "hull city", "هال سيتي",
                "athletic bilbao", "أتلتيك بلباو", "deportivo de a coruna", "ديبورتيفو لاكورونيا", "malaga", "ملقة",
                "racing de santander", "راسينغ سانتاندير", "deportivo alaves", "ألافيس",
                "bologna", "بولونيا", "cagliari", "كالياري", "como", "كومو", "fiorentina", "فيورنتينا",
                "frosinone", "فروزينوني", "genoa", "جنوى", "lecce", "ليتشي", "monza", "مونزا", "parma", "بارما",
                "sassuolo", "ساسولو", "torino", "تورينو", "udinese", "أودينيزي", "venezia", "فينيسيا",
                "augsburg", "أوغسبورغ", "borussia monchengladbach", "بوروسيا مونشنغلادباخ", "elversberg", "إلفرسبرغ",
                "freiburg", "فرايبورغ", "hamburg", "هامبورغ", "hoffenheim", "هوفنهايم", "koln", "كولن", "mainz", "ماينز",
                "paderborn", "بادربورن", "schalke 04", "شالكه", "stuttgart", "شتوتغارت", "union berlin", "يونيون برلين",
                "werder bremen", "فيردر بريمن",
                "angers", "أنجيه", "auxerre", "أوكسير", "brest", "بريست", "le havre", "لوهافر", "le mans", "لومان",
                "lens", "لانس", "lorient", "لوريان", "lyon", "ليون", "marseille", "مارسيليا", "nice", "نيس",
                "paris fc", "باريس إف سي", "rennes", "رين", "strasbourg", "ستراسبورغ", "toulouse", "تولوز", "troyes", "تروا",
                "aek athens", "أيك أثينا", "bodo/glimt", "بودو غليمت", "bodø/glimt", "بودو غليمت", "fenerbahce", "فنربخشة", "lask", "لاسك",
                "psv eindhoven", "آيندهوفن", "sabah baku", "صباح باكو", "shakhtar donetsk", "شاختار دونيتسك",
                "slovan bratislava", "سلوفان براتيسلافا", "viking", "فايكنغ",
                // Saudi clubs as ESPN spells them (the fallback source)
                "al shabab", "الشباب", "al ettifaq", "الاتفاق", "al taawoun", "التعاون", "al fateh", "الفتح",
                "al fayha", "الفيحاء", "al khaleej", "الخليج", "al riyadh", "الرياض", "al faisaly", "الفيصلي",
                "al qadsiah", "القادسية", "al kholood", "الخلود", "al hazem", "الحزم", "al diriyah", "الدرعية",
                "neom sc", "نيوم", "al okhdood", "الأخدود", "al najma", "النجمة", "al ula", "العلا", "al-ula", "العلا",
                "al wehda", "الوحدة", "al raed", "الرائد", "al orobah", "العروبة", "al jabalain", "الجبلين",
                "al-jabalain", "الجبلين", "al adalah", "العدالة", "al-adalah", "العدالة", "al batin", "الباطن",
                "al-batin", "الباطن", "al tai", "الطائي", "al-tai", "الطائي", "al jandal", "الجندل", "al-jandal", "الجندل",
                // AFC Champions League Elite and Two (ESPN's spelling, then TheSportsDB's where it differs)
                "al ain", "العين", "al-ain", "العين", "al sadd", "السد", "al-sadd", "السد", "al wasl", "الوصل",
                "al-wasl", "الوصل", "al-gharafa", "الغرافة", "al gharafa", "الغرافة", "al-quwa al-jawiya", "القوة الجوية",
                "al quwa al jawiya", "القوة الجوية", "al-shamal", "الشمال", "al shamal", "الشمال",
                "shabab al ahli", "شباب الأهلي", "shabab al-ahli", "شباب الأهلي", "shabab al-ahli dubai", "شباب الأهلي",
                "al-duhail", "الدحيل", "al duhail", "الدحيل", "al-wakrah", "الوكرة", "al wakrah", "الوكرة",
                "al-rayyan sc", "الريان", "al-rayyan", "الريان", "al rayyan", "الريان", "al jazira club", "الجزيرة",
                "al jazira", "الجزيرة", "al-jazira", "الجزيرة", "al wahda fc", "الوحدة الإماراتي", "al-wahda", "الوحدة الإماراتي",
                "sharjah", "الشارقة", "al-sharjah", "الشارقة", "al-faisaly sc", "الفيصلي الأردني",
                "al-hussein sc", "الحسين إربد", "al-hussein", "الحسين إربد", "al-khaldiya sc", "الخالدية",
                "al-muharraq sc", "المحرق", "al-muharraq", "المحرق", "al-nahda club", "النهضة", "al-seeb club", "السيب",
                "al-seeb", "السيب", "al-shorta sc", "الشرطة", "al-shorta", "الشرطة", "al-zawraa", "الزوراء",
                "kuwait sc", "الكويت", "al-kuwait", "الكويت", "esteghlal fc", "الاستقلال", "esteghlal", "الاستقلال",
                "persepolis", "بيرسبوليس", "sepahan", "سباهان", "tractor", "تراكتور", "tractor sazi", "تراكتور",
                "gol gohar sirjan fc", "غول غوهر سيرجان", "pakhtakor", "باختاكور", "neftchi fergana", "نفتشي فرغانة",
                "fc nasaf", "ناساف", "nasaf", "ناساف", "arkadag fk", "أركاداغ", "arkadag", "أركاداغ",
                "beijing guoan", "بكين غوان", "shanghai port fc", "شنغهاي بورت", "shanghai port", "شنغهاي بورت",
                "shanghai shenhua", "شنغهاي شينهوا", "shandong taishan", "شاندونغ تايشان",
                "gamba osaka", "غامبا أوساكا", "kashima antlers", "كاشيما أنتلرز", "kashiwa reysol", "كاشيوا ريسول",
                "kyoto sanga", "كيوتو سانغا", "vissel kobe", "فيسل كوبي", "machida zelvia", "ماتشيدا زيلفيا",
                "kawasaki frontale", "كاواساكي فرونتالي", "yokohama f. marinos", "يوكوهاما إف مارينوس",
                "urawa red diamonds", "أوراوا ريد دايموندز", "sanfrecce hiroshima", "سانفريتشي هيروشيما",
                "jeonbuk hyundai motors", "جونبوك هيونداي", "pohang steelers", "بوهانغ ستيلرز",
                "daejeon hana citizen fc", "دايجون هانا سيتيزن", "daejeon hana citizen", "دايجون هانا سيتيزن",
                "fc seoul", "إف سي سيول", "gangwon fc", "غانغوون", "ulsan hd", "أولسان", "ulsan hyundai", "أولسان",
                "buriram united", "بوريرام يونايتد", "port fc", "بورت", "ratchaburi fc", "راتشابوري",
                "bg pathum united", "بي جي باتوم يونايتد", "johor darul ta'zim", "جوهور دار التعظيم",
                "kuching city", "كوتشينغ سيتي", "cong an ha noi", "كونغ آن هانوي", "the cong-viettel fc", "ذا كونغ فيتيل",
                "newcastle jets", "نيوكاسل جيتس", "adelaide united", "أديلايد يونايتد", "melbourne victory", "ملبورن فيكتوري",
                "melbourne city", "ملبورن سيتي", "central coast mariners", "سنترال كوست مارينرز",
                "persib bandung", "بيرسيب باندونغ", "lion city sailors fc", "ليون سيتي سيلرز",
                "tampines rovers fc", "تامبينيس روفرز", "kitchee sc", "كيتشي", "tai po fc", "تاي بو",
                "east bengal fc", "إيست بنغال", "phnom penh crown fc", "بنوم بنه كراون",
                "preah khan reach svay rieng fc", "برياه خان ريتش سفاي رينغ",
        };
        for (int i = 0; i < teams.length; i += 2) TEAMS.put(teams[i], teams[i + 1]);
        String[] leagues = {
                "spanish la liga", "الدوري الإسباني", "uefa champions league", "دوري أبطال أوروبا",
                "spanish copa del rey", "كأس ملك إسبانيا", "copa del rey", "كأس ملك إسبانيا",
                "spanish super cup", "كأس السوبر الإسباني", "supercopa de espana", "كأس السوبر الإسباني",
                "uefa super cup", "كأس السوبر الأوروبي", "fifa club world cup", "كأس العالم للأندية",
                "club friendlies", "مباراة ودية", "uefa europa league", "الدوري الأوروبي",
                "english premier league", "الدوري الإنجليزي", "saudi-arabian pro league", "دوري روشن السعودي",
                "saudi pro league", "دوري روشن السعودي", "italian serie a", "الدوري الإيطالي",
                "german bundesliga", "الدوري الألماني", "french ligue 1", "الدوري الفرنسي",
                "english fa cup", "كأس الاتحاد الإنجليزي", "english league cup", "كأس الرابطة الإنجليزية",
                "uefa europa conference league", "دوري المؤتمر الأوروبي", "uefa conference league", "دوري المؤتمر الأوروبي",
                "afc champions league", "دوري أبطال آسيا", "afc champions league elite", "دوري أبطال آسيا للنخبة",
                "saudi king's cup", "كأس الملك", "saudi super cup", "كأس السوبر السعودي",
                "afc champions league two", "دوري أبطال آسيا 2",
        };
        // Clubs a theme can name in team_id instead of the number; every id checked against lookupteam.php.
        String[] ids = {
                "133739", "barcelona", "برشلونة", "133738", "real madrid", "ريال مدريد",
                "133729", "atletico madrid", "أتلتيكو مدريد",
                "136013", "al-hilal", "الهلال", "136022", "al-nassr", "النصر", "137721", "al-ahli", "الأهلي",
                "133604", "arsenal", "آرسنال", "133602", "liverpool", "ليفربول", "133612", "manchester united", "مانشستر يونايتد",
                "133613", "manchester city", "مانشستر سيتي", "133664", "bayern munich", "بايرن ميونخ",
                "133667", "ac milan", "ميلان", "136018", "al-ittihad", "الاتحاد", "133610", "chelsea", "تشيلسي",
                "133714", "paris saint-germain", "باريس سان جيرمان", "133676", "juventus", "يوفنتوس",
                "133681", "inter milan", "إنتر ميلان",
        };
        for (int i = 0; i < ids.length; i += 3) {
            TEAM_IDS.put(key(ids[i + 1]), ids[i]);
            TEAM_IDS.put(key(ids[i + 2]), ids[i]);
        }
        for (int i = 0; i < leagues.length; i += 2) LEAGUES.put(leagues[i], leagues[i + 1]);
    }

    /**
     * A theme's team_id as TheSportsDB's number: digits as they are, or a club name from the table above (English
     * or Arabic, "Al Hilal" and "al-hilal" alike). Null when it is neither, and the match block stays hidden.
     */
    static String teamId(String value) {
        String v = value == null ? "" : value.trim();
        if (v.matches("\\d{1,9}")) return v;
        String id = TEAM_IDS.get(key(v));
        if (id == null) id = TEAM_IDS.get(key(v.replace(' ', '-')));
        return id;
    }

    /** Bidi isolation, so an English name keeps its place inside an Arabic line. */
    static String isolate(String s) {
        return "\u2068" + s + "\u2069";
    }

    /**
     * A right-to-left line. The launcher does not declare supportsRtl, so Android ignores textDirection="rtl" and
     * takes the direction from the first strong letter, and isolated names do not count: «النصر × الفيصلي» built
     * from two isolates came out left to right, away team first. The leading RLM makes every match line RTL.
     */
    static String rtl(String s) {
        return "\u200F" + s;
    }

    /**
     * One next match per chosen club, a match two of them play shown once, soonest first, at most {@code max}.
     */
    static java.util.List<Match> distinct(java.util.Collection<Match> matches, Instant now, int max) {
        java.util.Map<String, Match> byEvent = new java.util.LinkedHashMap<>();
        for (Match m : matches) if (m != null && m.showAt(now)) byEvent.putIfAbsent(m.eventId, m);
        java.util.List<Match> out = new java.util.ArrayList<>(byEvent.values());
        java.util.Collections.sort(out, (a, b) -> a.kickoff.compareTo(b.kickoff));
        return out.size() > max ? out.subList(0, max) : out;
    }

    static String teamAr(String english) {
        String ar = TEAMS.get(key(english));
        return ar != null ? ar : english;
    }

    static String leagueAr(String english) {
        String ar = LEAGUES.get(key(english));
        return ar != null ? ar : english;
    }

    /** Lower case without accents: "Atlético Madrid" and "Atletico Madrid" are one key. */
    private static String key(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }
}
