# README feedback: upgrade test on a TCL G10 (Android 12), 2026-10-23

The TV was already set up on 2026-10-22 from an older checkout (~/cleanyourtv) with an older build of
com.gathin.tvhome. This test upgrades it in place from this repository by following README.md.

## Unclear steps and missing instructions

1. **No upgrade path.** README.md only covers a fresh install (clone → backup → build → theme). It doesn't say
   what to do when the launcher is already installed: whether a new backup is needed, whether the existing debloat and
   Home owner are kept, whether the remote's key services and the TCL `APP_AUTO_START` appop survive a reinstall, or
   that choices made on the TV win over the theme (only CLAUDE.md says so).
2. **Setup steps the README leaves out for a new checkout.** The manual install starts with `tv.py --probe` but doesn't
   say to create `launcher/local.properties` (`sdk.dir=...`). Only CLAUDE.md mentions it.
3. **New checkout = new ADB key.** `tools/adbkey/` is per checkout (gitignored), so a TV that already trusts the old
   checkout asks «Allow USB debugging?» again. The README doesn't warn about this.
4. **Two kinds of restore record.** `~/.cleanyourtv/recovery-*.json` is written only by `tools/disable.sh`
   (README.en.md). The first session disabled packages with direct `pm disable-user` calls, so that folder was never
   created, and the only record is `backup/<tv>-<date>/restore.sh`. The Arabic README mentions neither the folder nor
   that `disable.sh` is what creates it.

5. **The version can't tell old from new.** The TV's build (installed 2026-10-22) and this repo both say
   versionName 2.5 / versionCode 6, so `dumpsys package` can't show whether an upgrade is needed or whether it
   happened. Only `lastUpdateTime` changes. Bump versionCode when the launcher changes, or show the build's git
   hash in «إعدادات المشغّل».
6. **Signing key on an upgrade.** Release builds use the debug keystore (`~/.android/debug.keystore`). An upgrade
   from another computer, or after that file is deleted, fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE, and the only
   way round it is an uninstall, which wipes the clubs, hidden apps and dock chosen on the TV. The README should say
   to upgrade from the same computer, or to keep that keystore.
7. **No storage permission after the first install.** The probe found READ_EXTERNAL_STORAGE not granted, although
   `build-and-install.sh` grants it. The older build may predate the USB feature. Either way the README doesn't say
   USB wallpapers need the reinstall.
   **Done in this test:** `launcher/app/build.gradle` is now versionName 3.0 / versionCode 7 (from 2.5 / 6). The
   rollback cost: Android refuses a downgrade to versionCode 6 without an uninstall, and an uninstall wipes the
   launcher's on-TV choices. A pre-upgrade backup should keep the old APK (`pm path` + `tv.py --pull`).
8. **The key services are switched off by every reinstall.** The script's comments say so, but the README's manual
   command shows the flags without saying they must be passed on every reinstall, upgrades included.

9. **On-TV choices can't be backed up.** The launcher keeps them in private SharedPreferences, and the release build
   has `allowBackup="false"` and isn't debuggable, so `run-as` and `adb backup` both fail. Only the theme files in
   `/sdcard/Android/data/com.gathin.tvhome/files/` can be saved. The README promises «كل شيء قابل للتراجع», but that
   doesn't cover them. An export/import of choices (e.g. to `files/choices.json`) would close the gap.
10. **A theme push silently resets some on-TV choices.** `tools/theme.sh` writes a new `theme_revision`, and the
    launcher then drops columns, rows below the dock, animations, advanced effects and the wallpaper choice
    (`Config.syncThemeChoices`). CLAUDE.md says «choices made later on the TV win over the theme», and the README
    tells users to re-push the theme after adding Thmanyah or a Drive link, without warning about this.
    **Fixed (uncommitted):** `Config` no longer clears them; only «Follow theme» on the TV does. `themes/README.md`,
    `README.en.md` and the `theme.sh` comment now say so (builds before 3.0 still clear them).
11. **Thmanyah gap on a new checkout.** The TV's theme already said `"font": "thmanyah"`, but a fresh clone has no
    `launcher/fonts-local/`, so the upgraded build falls back to IBM Plex until `install-thmanyah.py` is run again
    and the launcher rebuilt. An upgrade from a new checkout should install the font *before* building.

12. **Remapping one remote button means repeating the whole list.** `tools/theme.sh` merges `config.local.json`
    with a shallow `dict.update`, so `"app_keys"` there replaces config.xml's list instead of adding to it. To
    map Prime Video → Stremio, Netflix and YouTube had to be copied in again. Neither README says this, and neither
    shows how to remap a button.
13. **The settings panel hides clubs beyond the sixth.** `MatchFeed.sourceLines` stops at six lines, so a TV with nine
    saved clubs looked like it had six. That made the wrong extra picks below easy to miss, and it misled the first
    diagnosis. **Fixed:** launcher settings starts with «الأندية المختارة: N» and lists every club.
14. **`python3 -m unittest discover -s tests` (CLAUDE.md) hung for more than 12 minutes** on this Mac (Python 3.9).
    Every one of the 24 files passes when run alone (`discover -s tests -p <file>`). **Cause:** `test_debloat_paths`
    and `test_setupwraith_guard` run `disable.sh` against a fake TV client whose catch-all is `cat`. With the runner's
    stdin left open (a background job, CI), a call inherited it and `cat` waited forever. **Fixed:** both pass
    `stdin=subprocess.DEVNULL`, as `test_tvinput_guard` already did. 211 tests pass together, with stdin open too.
15. **No "update a TV" procedure, and one TV's private settings were in tracked files** (the old checkout's
    second TV's tracked theme held its remote mapping). **Done:** per-TV gitignored `tvs/<tv>/` (notes,
    `tv.env`, theme overlay), `tools/update-tv.sh tvs/<tv>` (model guard, in-place install, theme + overlay, report),
    `theme.sh`/`theme.ps1` take the TV overlay as a second argument, and CLAUDE.md has the procedure. `theme.ps1`
    is untested (no PowerShell on this Mac).

## Errors

1. **App buttons start the app twice** (`HOME_KEY_SERVICE=1` together with `INPUT_KEY_SERVICE=1`, as the README
   says). Both services read `app_keys` and both call `RemoteKeys.launch` on the same key: the log shows two
   `START ... com.netflix.ninja` 21 ms apart from uid 10101 after one Netflix press. Harmless on screen, but each
   press opens the app twice.
   **Fixed (uncommitted):** HomeKeyService leaves `app_keys` to InputKeyService when that one is enabled
   (`RemoteKeys.inputServiceListed`, unit test in `RemoteKeysTest`).
2. **Match card showed a club nobody picked.** Right after the upgrade, Home showed «مباراة ليفربول القادمة:
   ليفربول × برايتون». The owner had picked six clubs (Barcelona and five Saudi clubs).
   - **Cause:** Liverpool, Real Madrid and Inter Milan were saved in the TV's club list as well, nine clubs in all,
     read from «أنديتي»'s ticks with uiautomator. Nothing in the code adds clubs; only ticking in the picker does.
     Real Madrid was almost certainly ticked by the first session's own picker test on 2026-10-22 (screenshot
     `63-three-clubs`, 03:32: Hilal + Barcelona + Real Madrid) and not unticked. "Back to Barca" looked the same
     with the Clásico shared. The source of Liverpool and Inter is unknown. Before commit d224ae0 (2026-10-22 05:06) an
     open list stayed under Home and took later key presses, which is one possible path. The current build closes
     lists on Home and on pause. The three were unticked on the TV in this session.
   - Why nobody noticed: the panel lists at most six clubs (note 13), and the old picker listed Champions League
     clubs a second time, so a tick under «دوري أبطال أوروبا» was easy to miss.
   - **Fixed (uncommitted), as the owner asked:**
     - A club is listed once, under its home league (17 leagues; the Champions League group is gone; 15 clubs got
       their home league from `lookupteam.php`).
     - A club picked once shows its next match in any competition: ESPN's `soccer/all/teams/<id>/schedule` (one
       request per club) and TheSportsDB are both asked at every refresh, and the sooner match wins. Seen on the TV:
       Al-Nassr × Al-Wasl and Al-Shamal × Al-Hilal (AFC Champions League Elite), which the old per-league requests
       missed.
     - Only events the requested club plays in are accepted.
     - A match two chosen clubs share shows once even when it comes from different sources.
     - Tests: `ClubsTest`, plus new cases in `MatchFeedTest` and `EspnFallbackTest`.
3. **`build-and-install.sh` ended silently without `JAVA_HOME`.** The README says Claude Code can install JDK 17, and
   here it was in `~/Library/Java/temurin-17`, which `java_home` doesn't know. The script's last fallback
   (`java -XshowSettings` in a command substitution) failed under `set -e`/`pipefail` and the script stopped with no
   message, before building anything. Found when `tools/update-tv.sh` was first run without `JAVA_HOME`.
   **Fixed:** it also looks in `~/Library/Java/*` and `/Library/Java/JavaVirtualMachines/*` for a 17, and prints an
   ERROR when no JDK 17 is found.
4. **Another app came forward after «تحديث الأداة» (3.0 → 3.1).** Right after the install, Netflix appeared by
   itself. The log shows no key press and no `START`: Android force-stopped the launcher to replace it
   (`installPackageLI`), which removes its tasks, Home's included, and then resumed the most recent other task, a
   Netflix left in the background from the button test (`Moving to RESUMED … (in existing)`). Home only came back
   when the Home button was pressed 11 s later. **Fixed in 3.2:** the new version's `MY_PACKAGE_REPLACED` receiver
   waits for one of its key services to be bound again (which lets it start an activity from the background) and
   brings Home back. Checked on the G10: Netflix in front, `pm install -r`, Home back after 1.5 s.
