#!/usr/bin/env bash
# Build the launcher with the release key and publish it as a GitHub Release, for «تحديث الأداة» on the TVs.
#
#   tools/release.sh            # version from launcher/app/build.gradle (bump versionCode and versionName first)
#   tools/release.sh --dry-run  # every check and the build, nothing tagged or published
#
# Publishes tvhome-<versionName>-<versionCode>.apk and tvhome-<...>.apk.sha256 ("hex  name", as shasum writes it) on a
# release tagged v<versionName>. Refuses unless: gh is logged in; the tree is clean, on main and pushed; the tag is
# new and versionCode beats the latest release; both test suites pass; the APK is signed with the release key
# (launcher/release-cert.pem) and carries no Thmanyah file (its licence forbids redistribution; the TVs read it
# from their own storage, ThemeFonts). The release key: ~/.tv-home-arabic/release.jks, password in the Keychain
# item tv-home-arabic-release (CLAUDE.md, "Releases").
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"
DRY=0; [ "${1:-}" = --dry-run ] && DRY=1
die() { echo "release: $*" >&2; exit 1; }

command -v gh >/dev/null || die "gh is not installed (CLAUDE.md, Releases)"
gh auth status >/dev/null 2>&1 || die "gh is not logged in: run gh auth login"
[ -z "$(git status --porcelain)" ] || die "the working tree is not clean"
[ "$(git rev-parse --abbrev-ref HEAD)" = main ] || die "not on main"
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "main is not pushed (or is behind origin/main)"

GRADLE=launcher/app/build.gradle
NAME="$(sed -n "s/^[[:space:]]*versionName[[:space:]]*'\([^']*\)'.*/\1/p" "$GRADLE" | head -n 1)"
CODE="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -n 1)"
[ -n "$NAME" ] && [ -n "$CODE" ] || die "versionName/versionCode not found in $GRADLE"
TAG="v$NAME"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"
git ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1 && die "tag $TAG already exists on GitHub"
LAST="$(gh release view --json assets --jq '.assets[].name' 2>/dev/null | sed -n 's/^tvhome-.*-\([0-9][0-9]*\)\.apk$/\1/p' | head -n 1 || true)"
[ -z "$LAST" ] || [ "$CODE" -gt "$LAST" ] || die "versionCode $CODE is not above the latest release's $LAST"
echo "Releasing $NAME (versionCode $CODE) as $TAG"

KEY="$HOME/.tv-home-arabic/release.jks"
[ -f "$KEY" ] || die "no release key at $KEY"
TVHOME_KS_PASS="$(security find-generic-password -s tv-home-arabic-release -w 2>/dev/null)" || die "no Keychain item tv-home-arabic-release"
export TVHOME_KEYSTORE="$KEY" TVHOME_KS_PASS

# JDK 17 as build-and-install.sh finds it (JAVA_HOME, java_home, Homebrew, ~/Library/Java).
if [ -z "${JAVA_HOME:-}" ]; then
  for d in "$(/usr/libexec/java_home -v 17 2>/dev/null || true)" /opt/homebrew/opt/openjdk@17 \
           "$HOME"/Library/Java/*/Contents/Home /Library/Java/JavaVirtualMachines/*/Contents/Home; do
    [ -n "$d" ] && [ -x "$d/bin/java" ] || continue
    case "$("$d/bin/java" -version 2>&1 || true)" in *'version "17.'*) JAVA_HOME="$d"; break ;; esac
  done
fi
[ -n "${JAVA_HOME:-}" ] || die "no JDK 17 found"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' launcher/local.properties 2>/dev/null)}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)"
AAPT2="$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)"
[ -x "$APKSIGNER" ] && [ -x "$AAPT2" ] || die "apksigner/aapt2 not found under $SDK/build-tools"

echo "Tests..."
(cd launcher && ./gradlew testReleaseUnitTest -q --console=plain) || die "launcher tests failed"
python3 -m unittest discover -s tests < /dev/null >/dev/null 2>&1 || die "tools tests failed (python3 -m unittest discover -s tests)"

echo "Build..."
(cd launcher && ./gradlew clean assembleRelease -q --console=plain) || die "build failed"
APK=launcher/app/build/outputs/apk/release/app-release.apk
OUT="$REPO/launcher/build/release"
FILE="tvhome-$NAME-$CODE.apk"
rm -rf "$OUT"; mkdir -p "$OUT"; cp "$APK" "$OUT/$FILE"

echo "Checks..."
WANT="$(python3 -I tools/release_cert.py --sha256)"
GOT="$("$APKSIGNER" verify --print-certs "$OUT/$FILE" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
[ "$GOT" = "$WANT" ] || die "signed with $GOT, not the release key $WANT"
[ "$("$APKSIGNER" verify --print-certs "$OUT/$FILE" | grep -c '^Signer #')" = 1 ] || die "more than one signer"
if unzip -l "$OUT/$FILE" | grep -qi 'thmanyah'; then die "the APK contains a Thmanyah file; it must never be published"; fi
BADGE="$("$AAPT2" dump badging "$OUT/$FILE" | head -n 1)"
case "$BADGE" in *"name='com.gathin.tvhome'"*"versionCode='$CODE'"*"versionName='$NAME'"*) ;; *) die "unexpected package line: $BADGE" ;; esac
(cd "$OUT" && shasum -a 256 "$FILE" > "$FILE.sha256")
SUM="$(cut -d' ' -f1 "$OUT/$FILE.sha256")"
echo "  $FILE  $(wc -c < "$OUT/$FILE" | tr -d ' ') bytes"
echo "  sha256 $SUM"
echo "  signer $GOT"

if [ "$DRY" = 1 ]; then echo "Dry run: nothing tagged or published. Files in $OUT"; exit 0; fi
git tag -a "$TAG" -m "Launcher $NAME (versionCode $CODE)"
git push -q origin "$TAG"
gh release create "$TAG" "$OUT/$FILE" "$OUT/$FILE.sha256" --verify-tag --title "تحديث الأداة $NAME" --notes "Launcher $NAME (versionCode $CODE).

SHA-256 \`$SUM\`  $FILE
Signed with the release key, certificate SHA-256 \`$GOT\` (launcher/release-cert.pem).
On a TV: launcher settings → «تحديث الأداة». Thmanyah is not in this APK; TVs read their own copy."
echo "Published $TAG: $(gh release view "$TAG" --json url --jq .url)"
