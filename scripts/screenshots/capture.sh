#!/bin/bash
# BabaCut app screenshots on an Android emulator, driven by adb (am start, uiautomator, input, screencap).
# Method taken from the design-audit runs (docs/design/screenshots/app/2026-11-13*/shots*.sh.txt).
# Test data only: the emulator's synthetic camera scene ("-camera-back emulated") and the typed overlay text
# "BabaCut Test". No real camera content, no user media, no secrets.
#
# Usage: bash scripts/screenshots/capture.sh <tag> <wm-size|default> <wm-density|default> <locales...>
#   e.g. bash scripts/screenshots/capture.sh api35-small 720x1280 320 en fa ar
# Expects the debug APK at app/build/outputs/apk/debug/app-debug.apk and one booted emulator on adb.
# Output: screenshots/<tag>-<locale>-<nn>-<view>.png (+ uiautomator .xml dumps, device facts, logcat).
# Exit code 1 if any expected screenshot is missing (list in screenshots/<tag>-missing.txt).
set -u
TAG=$1; SIZE=$2; DENS=$3; shift 3; LOCALES="$*"
PKG=de.codinix.babacut
ACTIVITY=de.codinix.videoeditor.MainActivity
APK=app/build/outputs/apk/debug/app-debug.apk
OUT=screenshots; mkdir -p "$OUT"
VIEWS="01-record 02-settings-sheet 03-text-sheet 04-review 05-caption-sheet 06-export-sheet"

log(){ echo "== $*"; }
rawdump(){ adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1 || : > /tmp/u.xml; }
# a system "isn't responding" dialog blocks every tap: press "Wait" and dump again (hub knowledge #2277)
dump(){ rawdump; local i; for i in 1 2 3; do grep -q 'android:id/aerr_wait' /tmp/u.xml || return 0
  tapb "$(bounds 'resource-id="android:id/aerr_wait"')"; log "dismissed ANR dialog"; sleep 3; rawdump; done; }
bounds(){ tr '>' '\n' < /tmp/u.xml | grep "$1" | grep -o 'bounds="[^"]*"' | sed -n "${2:-1}p" | tr -c '0-9' ' '; }
tapb(){ local b=$1; [ -z "$b" ] && return 1; read -r x1 y1 x2 y2 <<< "$b"; adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); }
tapid(){ dump; tapb "$(bounds "resource-id=\"$PKG:id/$1\"")" && { log "tap $1"; sleep 1; } || { log "id $1 not found"; return 1; }; }
shot(){ sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$TAG-$1.png"; dump; cp /tmp/u.xml "$OUT/$TAG-$1.xml"; log "shot $TAG-$1"; }
back(){ adb shell input keyevent KEYCODE_BACK; sleep 1; }
start(){ adb shell am force-stop $PKG; adb shell am start -W -n $PKG/$ACTIVITY >/dev/null; sleep 8; dump; }
tagof(){ case $1 in en) echo en-US;; de) echo de-DE;; fa) echo fa-IR;; ar) echo ar-EG;; fr) echo fr-FR;; *) echo "$1";; esac; }

SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[ "$SDK" -ge 33 ] || { echo "API $SDK: per-app locales need API 33+"; exit 1; }

# device setup: screen size, launcher-ANR workaround (#2277), app install with all runtime permissions
[ "$SIZE" != default ] && adb shell wm size "$SIZE"
[ "$DENS" != default ] && adb shell wm density "$DENS"
adb shell settings put global hide_error_dialogs 1
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
adb shell settings put system font_scale 1.0
sleep 2
{
  echo "tag=$TAG sdk=$SDK release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  adb shell wm size; adb shell wm density
} > "$OUT/$TAG-device.txt" 2>&1
adb install -r -g "$APK" || { echo "install failed"; exit 1; }

for L in $LOCALES; do
  adb shell cmd locale set-app-locales $PKG --locales "$(tagof "$L")"
  p=$L
  start; shot "$p-01-record" 1
  tapid settingsButton && shot "$p-02-settings-sheet"
  # text sheet with typed test text (not confirmed, so no overlay remains)
  start; tapid addTextButton && { adb shell input text "BabaCut%sTest"; shot "$p-03-text-sheet" 2; }
  # one ~6 s segment of the synthetic emulator camera, then review and its sheets
  start
  if tapid recordButton; then
    sleep 6; tapid recordButton; sleep 2
    if tapid finishButton; then
      shot "$p-04-review" 8
      tapid captionsButton && { shot "$p-05-caption-sheet"; back; dump; grep -q 'design_bottom_sheet' /tmp/u.xml && back; }
      tapid reviewSaveButton && shot "$p-06-export-sheet"
    fi
  fi
done
adb logcat -d > "$OUT/$TAG-logcat.txt" 2>&1 || true

missing=0; : > "$OUT/$TAG-missing.txt"
for L in $LOCALES; do for V in $VIEWS; do
  f="$OUT/$TAG-$L-$V.png"
  if [ ! -s "$f" ]; then echo "$TAG-$L-$V" >> "$OUT/$TAG-missing.txt"; missing=$((missing+1)); fi
done; done
log "missing screenshots: $missing"; cat "$OUT/$TAG-missing.txt"
[ "$missing" -eq 0 ]
