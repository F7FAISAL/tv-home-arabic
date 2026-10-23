# CLAUDE.md

This repository is [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT, by Gathin) plus a clean Arabic Home
for Android TV / Google TV: city and weather, a large clock with Hijri and Gregorian dates, the next prayer and its
iqama (Umm al-Qura, computed offline), the Ramadan countdown or Eid, and the chosen clubs' next match, over rotating
wallpapers. The user guide is the Arabic `README.md`; design notes are in `docs/CUSTOM_HOME.md`; the upstream guide
(debloat, TV list) is `README.en.md` and `PROMPT.md`.

## How to work with the user

- Work on their real TV one step at a time, take a screenshot after each visible change
  (`tools/venv/bin/python tools/tv.py --screenshot shots/<name>.png`), and ask before anything that depends on taste.
- Before any change on the TV: back up (packages, `getprop`, settings, the current Home owner) into `backup/`
  (gitignored) and write a restore script there, as `PROMPT.md` describes. Disable, never uninstall.
- Read the user's model in `examples/` and `docs/devices/` first; a different model can need a different list.
- Keep everything about the user's own TV (IP address, device name, Wi-Fi name, account, links) out of tracked
  files: it goes in `backup/`, `shots/` or a theme's `config.local.json`, all gitignored.

## Setup on a new TV

1. Tools: `python3 -m venv tools/venv && tools/venv/bin/pip install --only-binary=cryptography -r tools/requirements.txt`.
2. The user enables Developer options (Settings → System → About → Build, seven presses), USB debugging and
   Network debugging, and gives you the TV's IP address (Settings → Network). Then
   `TV_HOST=<ip> tools/venv/bin/python tools/tv.py --probe` and the user presses «Allow» on the TV.
3. Backup and restore script (above), then the debloat list for their model if they want it
   (`TV_HOST=<ip> tools/disable.sh examples/<model>/debloat.txt`).
4. Build and install with the remote's key services (JDK 17, Android SDK; `launcher/local.properties` holds
   `sdk.dir`): `cd launcher && TV_HOST=<ip> INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 ./build-and-install.sh`.
   On Google TV, `com.google.android.tungsten.setupwraith` must be disabled before the stock launcher, or Home lands
   on its black recovery loop; check that Wi-Fi is not owned by setupwraith first. Reboot and check Home again.
5. Theme: copy `themes/madinah` if the user wants their own, set `city` and `teams`, then
   `TV_HOST=<ip> tools/theme.sh themes/madinah`. Choices made later on the TV win over the theme.
6. Screensaver (optional): `settings put secure screensaver_components com.gathin.tvhome/.ClockDream`.
7. When the user is done: switch USB and Network debugging off, revoke the authorisations, reboot, and check
   `tools/venv/bin/python tools/adb-closed.py <ip>` reports the port closed.

## Thmanyah font (optional)

The default font is IBM Plex Sans Arabic (OFL, bundled). Thmanyah's licence forbids redistributing, renaming or
modifying the font, so it is never in this repository and you never download it for the user. Ask them to get it
from Thmanyah's official font page (it arrives by email as a .zip), then run
`python3 tools/install-thmanyah.py <path to the .zip or folder> themes/<theme>`: it copies `thmanyahsans-Regular.otf`
and `thmanyahsans-Bold.otf` byte for byte into `launcher/fonts-local/thmanyah/` (gitignored) and sets
`"font": "thmanyah"` in the theme's `config.local.json`. Rebuild, reinstall, push the theme. Never commit,
convert, subset or rename the files.

## Wallpapers

All sources rotate together, with no credit line on screen:

- Bundled: the repository's `wallpapers/`, pushed with the theme (`"bundle_gallery": true`).
- Online gallery: `wallpapers.json` + `wallpapers/` fetched from raw.githubusercontent.com
  (`wallpaper_gallery_url`; it must point at this repository's `main`). `tools/gallery.py add <photos>` crops to
  1920x1080, keeps it under 500 KB, strips metadata and rebuilds the list; commit and push and every TV picks it up
  within six hours. Only add photos the user has the right to publish.
- USB: a `TVWallpapers` folder at the top of any USB drive; needs the storage permission that
  `build-and-install.sh` grants.
- Drive (optional): the user's own folder and Apps Script, `tools/drive-wallpapers/README.md`; the `/exec` link goes
  only in `config.local.json`.

## Code

Launcher: `launcher/` (Java, minSdk 26). Main classes of the Arabic Home: HomeScene, CustomHomeConfig, PrayerTimes,
HijriDates, MatchFeed (TheSportsDB free key 123) and EspnFallback, WeatherFeed (Open-Meteo), WallpaperSources,
RemoteWallpapers, UsbWallpapers, CustomDreamScene, Cities, Clubs (`res/raw/clubs.json`). Remote keys live in
`launcher/app/src/main/res/values/config.xml`. Tests: `cd launcher && ./gradlew testReleaseUnitTest`, and
`python3 -m unittest discover -s tests` for the tools.

## Restore to stock

Run the restore script written in step 3 (it re-enables everything disabled and gives Home back to the stock
launcher), or `TV_HOST=<ip> tools/restore.sh examples/<model>/debloat.txt [stock launcher package]`.
Then optionally `pm uninstall com.gathin.tvhome`.
