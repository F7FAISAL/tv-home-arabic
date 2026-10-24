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

## One repository, several TVs

This repository is the only source for every TV it runs on. Each TV has a gitignored folder `tvs/<tv>/`:

- `TV.md`: the private notes (model, IP, remote keycodes, quirks, which backups are its own, what to do next time).
  Read it before touching that TV.
- `tv.env`: `DEVICE=` (`getprop ro.product.device`, the model guard), `TV_HOST=` (default address),
  `THEME=themes/madinah`.
- `config.local.json`: that TV's own theme overlay, merged last (its remote's app buttons, `app_keys`; a list key is
  replaced whole, so list every button).

What all the owner's TVs share stays in the theme's gitignored `config.local.json` (font, Drive link). Backups stay
in `backup/<tv>-<date>/`; never run one TV's backup scripts on another.

### Updating a TV that already runs the launcher

1. The owner switches debugging on; probe read-only first (Home owner, `versionName`, disabled packages, key
   services, `appops get com.gathin.tvhome APP_AUTO_START`) and report before changing anything.
2. If the launcher's on-TV choices matter, save what can be read (`/sdcard/Android/data/com.gathin.tvhome/files/`,
   `pm path` + `tv.py --pull` for the installed APK) into `backup/`. SharedPreferences can't be read from a release
   build.
3. `tools/update-tv.sh tvs/<tv>`. It refuses a different model and a TV whose Home isn't this launcher, builds and
   installs in place (`STOCK_LAUNCHER=none`, both key services; nothing disabled), pushes `THEME` with the TV's
   overlay, and prints version, Home, key services and auto-start. A theme push keeps the choices made on the TV.
4. Check the remote's buttons with real presses, one at a time, and read the key events and app starts from
   `logcat -d -v time -T '<time>'` (`handleKeyBeforeQueueing`, `ActivityTaskManager: START`). Injected keys don't
   reliably reach the key services.
5. Bump `versionCode`/`versionName` in `launcher/app/build.gradle` whenever the launcher changes, so a probe can
   tell builds apart. Downgrading needs an uninstall, which wipes the on-TV choices.
6. Close debugging (step 7 above) and update `tvs/<tv>/TV.md`.

## Releases and «تحديث الأداة»

Launcher settings → «تحديث الأداة» (`AppUpdater`) reads `releases/latest` of this repository (only while it is
public), downloads `tvhome-<versionName>-<versionCode>.apk`, checks its `.sha256`, checks it is this app, newer and
signed with the installed certificate, and opens Android's installer. An update keeps all the launcher's data.
`build-and-install.sh` grants what it needs over adb: `appops REQUEST_INSTALL_PACKAGES allow` and
`WRITE_SECURE_SETTINGS`, which lets the launcher put the remote's key services back after an update
(`UpdateReceiver`, `MY_PACKAGE_REPLACED`).

- **The release key** is one key for every TV, kept only on the owner's Mac, never in git:
  `~/.tv-home-arabic/release.jks` (PKCS12, alias `tvhome-release`, mode 600). Its password is the macOS Keychain
  item `tv-home-arabic-release`. Read it with `security find-generic-password -s tv-home-arabic-release -w`; never
  print it, log it or write it to a file. Its public certificate is `launcher/release-cert.pem`;
  `tools/release_cert.py` prints its SHA-256 and the short hash `dumpsys package` shows (`signatures:[…]`).
- **Backups:** the owner keeps the key file and its password in **separate** places, never together: e.g. the
  `.jks` on an offline USB drive (and a second copy elsewhere), the password in a password manager. Losing either
  one means no TV can be updated in place again (only uninstall + reinstall, which wipes the on-TV choices). If both
  leak together, anyone could publish an update the TVs accept.
- `build-and-install.sh` and `tools/release.sh` sign with that key when it's there (`TVHOME_KEYSTORE`,
  `TVHOME_KS_PASS` for Gradle); without it a build is signed with the debug key and can't update the TVs.
  `tools/update-tv.sh` refuses a TV whose installed certificate isn't the release certificate.
- **Publishing:** bump `versionCode` and `versionName` in `launcher/app/build.gradle`, commit and push `main`, then
  `tools/release.sh` (`--dry-run` first). It refuses a dirty tree, an unpushed `main`, an existing tag, a
  `versionCode` not above the last release, failing tests, a signer other than the release certificate, and any
  Thmanyah file in the APK. Then it tags `v<versionName>` and creates the GitHub Release with the APK and its
  checksum. It needs `gh`, logged in (`gh auth login`; on the owner's Mac `gh` lives in `~/.local/bin`).

## Thmanyah font (optional)

The default font is IBM Plex Sans Arabic (OFL, bundled). Thmanyah's licence forbids redistributing, renaming or
modifying the font, so it is never in this repository and you never download it for the user. Ask them to get it
from Thmanyah's official font page (it arrives by email as a .zip), then run
`python3 tools/install-thmanyah.py <path to the .zip or folder> themes/<theme>`: it copies `thmanyahsans-Regular.otf`
and `thmanyahsans-Bold.otf` byte for byte into `launcher/fonts-local/thmanyah/` (gitignored) and sets
`"font": "thmanyah"` in the theme's `config.local.json`. Then push the theme (`tools/theme.sh` or
`tools/update-tv.sh`): it sends the two files, unmodified, to the launcher's `files/fonts/` on each TV, where
`ThemeFonts` reads them (falling back to IBM Plex). No rebuild. The font is never packaged into an APK: releases are
public, and `tools/release.sh` refuses an APK with a Thmanyah file in it. Never commit, convert, subset or rename the
files.

## Wallpapers

All sources rotate together, with no credit line on screen:

- Bundled: the repository's `wallpapers/`, pushed with the theme (`"bundle_gallery": true`).
- Online gallery: `wallpapers.json` + `wallpapers/` fetched from raw.githubusercontent.com
  (`wallpaper_gallery_url`; it must point at this repository's `main`). `tools/gallery.py add <photos>` crops to
  1920x1080, keeps it under 500 KB, strips metadata and rebuilds the list; commit and push and every TV picks it up
  within six hours. Photos uploaded straight to `wallpapers/` on GitHub (website) are converted the same way by the
  Wallpapers workflow (`.github/workflows/wallpapers.yml`, `tools/gallery.py normalize`), which commits the result;
  clean photos stay byte for byte. Originals stay in the public history, so remove location data before uploading.
  Only add photos the user has the right to publish.
- USB: a `TVWallpapers` folder at the top of any USB drive; needs the storage permission that
  `build-and-install.sh` grants.
- Drive (optional): the user's own folder and Apps Script, `tools/drive-wallpapers/README.md`; the `/exec` link goes
  only in `config.local.json`.

## Code

Launcher: `launcher/` (Java, minSdk 26). Main classes of the Arabic Home: HomeScene, CustomHomeConfig, PrayerTimes,
HijriDates, MatchFeed (TheSportsDB free key 123) and EspnFallback, WeatherFeed (Open-Meteo), WallpaperSources,
RemoteWallpapers, UsbWallpapers, CustomDreamScene, Cities, Clubs (`res/raw/clubs.json`), ThemeFonts, AppUpdate and
AppUpdater («تحديث الأداة»). Remote keys live in `launcher/app/src/main/res/values/config.xml`. Tests: `cd launcher && ./gradlew testReleaseUnitTest`, and
`python3 -m unittest discover -s tests` for the tools.

## Restore to stock

Run the restore script written in step 3 (it re-enables everything disabled and gives Home back to the stock
launcher), or `TV_HOST=<ip> tools/restore.sh examples/<model>/debloat.txt [stock launcher package]`.
Then optionally `pm uninstall com.gathin.tvhome`.
