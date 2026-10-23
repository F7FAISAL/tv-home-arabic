# Clean Arabic home view (`custom_home`)

Home opens on a clean view over a full-screen photo: the city and weather top left, two round buttons top right
(menu and screensaver), a large clock with Hijri and Gregorian dates on the right, and a bottom row with the next
prayer (and iqama countdown), the Ramadan countdown and the chosen clubs' next match.
**Down** slides the upstream dock and app grid up over it; **Back**, or **Up** from the top bar, returns. The clock
screensaver (`ClockDream`) shows the same view without the buttons, drifting a few dozen pixels a minute.

`custom_home` is `false` → the upstream home screen, unchanged.

## Data

| Block | Source | Network |
|---|---|---|
| Next prayer | `PrayerTimes`: Umm al-Qura (Fajr 18.5°, Asr standard, Isha = Maghrib + 90 min, + 120 in Ramadan), computed on the TV from `latitude`/`longitude` and the TV's clock and time zone. Checked against api.aladhan.com method 4 for Madinah (`PrayerTimesTest`). | none |
| Ramadan | `HijriDates` on `android.icu.util.IslamicCalendar`, `ISLAMIC_UMALQURA`. Days to 1 Ramadan, «اليوم X من رمضان» in Ramadan, «عيد مبارك» on 1–3 Shawwal and 10–13 Dhu al-Hijjah. Moon sighting decides the real start, hence «تقريباً». | none |
| Next match | `MatchFeed`: TheSportsDB `eventsnext.php?id=<team_id>` with the public test key `123` (no account, no secret). At most one request every six hours while a match is cached (an hour between retries when there is none), last good answer in `files/match-<id>.json`, kick-off shown in Asia/Riyadh. Only when TheSportsDB has no upcoming match for a club, `EspnFallback` asks ESPN's public site API (`/soccer/<league>/teams/<id>/schedule?fixture=true` for the club's league, and for Saudi clubs `afc.champions`, `afc.cup` and `ksa.kings.cup`; ids in `res/raw/espn_clubs.json` from `tools/espn_clubs.py`). A match that has kicked off shows only if the answer came after kick-off; an answer without a complete event never replaces the cached one. The menu names each club's source and last update. No data → the block is hidden. | with weather and the wallpaper lists, the only `INTERNET` use |

## Theme keys (`config.json`, no rebuild)

`custom_home`, `city` (a name from `Cities.java`, default «المدينة المنورة»), `teams` (TheSportsDB ids or known
names, default Barcelona), `wallpaper` (bool), `wallpaper_rotation_minutes`, `bundle_gallery` (theme.sh pushes the
repository's `wallpapers/`), `wallpaper_gallery_url`, `wallpaper_list_url` (Drive, in `config.local.json`),
`usb_wallpapers` (default true), `latitude`, `longitude`, `show_prayer`, `show_ramadan`, `show_match`, `show_weather`, `show_dates`, `team_id` (TheSportsDB id, e.g. 133739 = FC Barcelona,
or a club name from `MatchFeed.TEAM_IDS` such as "Al Hilal", "النصر", "Real Madrid"; anything else hides the block
and logs `TvHome.Match`),
`clock_size` (sp, 40–300, default 156) and `font`, one font for the whole view: `plex`
(IBM Plex Sans Arabic, default), `thmanyah` (your own copy, see below), `outfit` (Latin only) or `system`.
`themes/madinah` sets them all. Push changes with:

```sh
TV_HOST=<tv ip> tools/theme.sh themes/madinah
```

## Fonts and photo

- IBM Plex Sans Arabic (the whole view, clock included), SIL OFL 1.1: `launcher/FONT-LICENSE-IBM-PLEX-SANS-ARABIC-OFL.txt`
- The licence also ships inside the APK under `assets/licenses/`.
- Wallpapers, all in one rotation (`WallpaperSources`), no credit line on screen:
  1. bundled: the repository's `wallpapers/`, pushed with the theme (`"bundle_gallery": true`);
  2. online gallery: `wallpapers.json` + `wallpapers/` read from raw.githubusercontent.com (`wallpaper_gallery_url`),
     at boot and every six hours; `tools/gallery.py add` adds a photo, then commit and push;
  3. USB: a `TVWallpapers` folder at the top of any USB drive (`UsbWallpapers`), copied when it changes;
  4. Drive (optional): the user's own folder through their read-only Apps Script (`tools/drive-wallpapers/`; the
     link sits in the gitignored `config.local.json`).
  Every downloaded or copied photo is stored as a 1920x1080 JPEG under 500 KB.
- Font: `"font": "thmanyah"` packages your own Thmanyah Sans from the gitignored `launcher/fonts-local/`
  (`tools/install-thmanyah.py` puts it there). Its licence forbids redistribution: never commit it.
- Weather data: Open-Meteo, CC BY 4.0, credited in launcher settings.

## Tests

`./gradlew testReleaseUnitTest` (in `launcher/`): prayer times against published values, the minute before and at
each prayer, 7 PM before Isha, after Isha, midnight; Ramadan, its first day, Eid al-Fitr and al-Adha with faked
dates; Arabic plurals; match parsing, offline and refresh timing.
