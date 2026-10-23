#!/usr/bin/env bash
# Update one TV that already runs this launcher, from this repository: build and install in place (key services
# on, nothing disabled), push its theme with its own overlay, then report what the TV says.
#
#   tools/update-tv.sh tvs/<tv>                  # TV_HOST from tvs/<tv>/tv.env
#   TV_HOST=192.168.1.50 tools/update-tv.sh tvs/<tv>
#
# tvs/ is gitignored, one folder per TV (see CLAUDE.md):
#   tv.env             DEVICE=<getprop ro.product.device>   the model guard: any other TV is refused
#                      TV_HOST=<ip>                          default address (DHCP: check Settings -> Network)
#                      THEME=themes/madinah                  the theme to push
#   config.local.json  that TV's own overlay, merged last (its remote's app buttons, app_keys)
# A first install is not an update: follow README.md (backup, Home takeover) for a TV without the launcher.
set -euo pipefail
TV_DIR="${1:?usage: tools/update-tv.sh tvs/<tv>}"
TV_DIR="$(cd "$TV_DIR" && pwd)"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$TV_DIR/tv.env" ] || { echo "no $TV_DIR/tv.env (DEVICE=, TV_HOST=, THEME=)" >&2; exit 1; }
WANT_HOST="${TV_HOST:-}"
# shellcheck disable=SC1091
. "$TV_DIR/tv.env"
TV_HOST="${WANT_HOST:-${TV_HOST:-}}"
: "${TV_HOST:?set TV_HOST in $TV_DIR/tv.env or the environment}"
: "${DEVICE:?set DEVICE in $TV_DIR/tv.env (getprop ro.product.device)}"
THEME="${THEME:-themes/madinah}"
export TV_HOST
PY="$REPO/tools/venv/bin/python"
T() { "$PY" -I "$REPO/tools/tv.py" "$@"; }

dev="$(T 'getprop ro.product.device' | tr -d '\r[:space:]')"
[ "$dev" = "$DEVICE" ] || { echo "refusing: $TV_HOST is device '$dev', not $DEVICE ($(basename "$TV_DIR"))" >&2; exit 1; }
home="$(T 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1 | tr -d '\r')"
case "$home" in
  com.gathin.tvhome/*) ;;
  *) echo "refusing: Home on $TV_HOST is '$home', not this launcher; a first install follows README.md" >&2; exit 1 ;;
esac

# Android switches the accessibility key services off on every reinstall, so both flags go every time.
STOCK_LAUNCHER=none INPUT_KEY_SERVICE=1 HOME_KEY_SERVICE=1 "$REPO/launcher/build-and-install.sh"
OVERLAY=()
[ -f "$TV_DIR/config.local.json" ] && OVERLAY=("$TV_DIR/config.local.json")
# theme.sh restarts Home and rebinds the key services if Android left them crashed (TCL G05).
"$REPO/tools/theme.sh" "$REPO/$THEME" ${OVERLAY[@]+"${OVERLAY[@]}"}

echo "--- $(basename "$TV_DIR") ($TV_HOST, $dev)"
T 'dumpsys package com.gathin.tvhome' | grep -m1 -o 'versionName=[^ ]*' || true
T 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | tail -n 1
T 'settings get secure enabled_accessibility_services'
T 'dumpsys accessibility' | grep -m1 'Crashed services' || true
T 'appops get com.gathin.tvhome APP_AUTO_START' || true
echo "Check the remote's buttons on the TV, one at a time."
