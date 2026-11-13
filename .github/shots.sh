#!/bin/bash
# Temporary design-audit screenshot script (task a8ef1268, based on 9defaf40). Test data only:
# the emulator's synthetic camera scene and a typed demo text; no personal media.
set -u
API=${1:-35}
PKG=de.codinix.babacut
OUT=shots; mkdir -p "$OUT"
RES=app/src/main/res
log(){ echo "== $*"; }
str(){ # $1 = values dir suffix ("" for en), $2 = string name
  local f="$RES/values${1:+-$1}/strings.xml"
  grep -o "<string name=\"$2\">[^<]*" "$f" | head -1 | sed 's/.*">//; s/\\'"'"'/'"'"'/g'
}
dump(){ adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1 || : > /tmp/u.xml; }
shot(){ sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/api$API-$1.png"; dump; cp /tmp/u.xml "$OUT/api$API-$1.xml"; log "shot $1"; }
tapb(){ read -r x1 y1 x2 y2 <<< "$1"; adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); }
tapid(){
  dump
  local b; b=$(grep -o "resource-id=\"$PKG:id/$1\"[^>]*bounds=\"[^\"]*\"" /tmp/u.xml | grep -o 'bounds="[^"]*"' | head -1 | tr -c '0-9' ' ')
  if [ -z "$b" ]; then log "id $1 not found"; return 1; fi
  tapb "$b"; log "tap $1"
}
taptext(){
  dump
  local b; b=$(grep -o "text=\"$1\"[^>]*bounds=\"[^\"]*\"" /tmp/u.xml | grep -o 'bounds="[^"]*"' | tail -1 | tr -c '0-9' ' ')
  [ -z "$b" ] && { log "text $1 not found"; return 1; }
  tapb "$b"; log "tap text $1"
}
back(){ adb shell input keyevent KEYCODE_BACK; sleep 1; }
start(){ adb shell am force-stop $PKG; adb shell am start -W -n $PKG/de.codinix.videoeditor.MainActivity >/dev/null; sleep 8; }
record(){ tapid recordButton && sleep "${1:-6}" && tapid recordButton && sleep 3; }

{
  echo "sdk=$(adb shell getprop ro.build.version.sdk) release=$(adb shell getprop ro.build.version.release)"
  echo "abi=$(adb shell getprop ro.product.cpu.abilist)"
  adb shell wm size; adb shell wm density
} > "$OUT/device-api$API.txt" 2>&1
# 390x844 dp phone: 1170x2532 px at 480 dpi
adb shell wm size 1170x2532; adb shell wm density 480; sleep 2
adb shell wm size >> "$OUT/device-api$API.txt"; adb shell wm density >> "$OUT/device-api$API.txt"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk || { echo "install failed"; exit 1; }
adb logcat -c

flow(){ # $1 = prefix, $2 = values dir suffix
  local p=$1 v=$2
  local OK; OK=$(str "$v" ok); local SAVE; SAVE=$(str "$v" save)
  # settings sheet (A19, long translations, close-X target)
  start; shot "$p-01-record-idle" 1
  tapid settingsButton && shot "$p-02-settings-sheet" && { adb shell input swipe 585 2000 585 600 400; shot "$p-02b-settings-scrolled"; }
  # overlay editing: demo text overlay, selected (edit/remove buttons visible)
  start; tapid addTextButton && { sleep 2; adb shell input text "BabaCut%sDemo"; back; taptext "$OK"; shot "$p-11-overlay-selected" 2; }
  # tile/mosaic mode: layout popup, then 2x2 grid with tile buttons
  start; tapid mosaicButton && { shot "$p-12-mosaic-popup" 1
    b=$(grep -o 'class="android.widget.ImageView"[^>]*bounds="[^"]*"' /tmp/u.xml | grep -o 'bounds="[^"]*"' | sed -n 5p | tr -c '0-9' ' ')
    [ -n "$b" ] && { tapb "$b"; shot "$p-13-mosaic-2x2" 3; adb shell input tap 290 1500; shot "$p-13b-mosaic-tile-selected" 2; } || log "mosaic 2x2 not found"; }
  # recording, review with scrub bar, captions sheet, export sheet + progress
  start; tapid recordButton && { shot "$p-07-recording" 4; tapid recordButton; sleep 3; }
  tapid finishButton && shot "$p-09-review" 8
  adb shell input tap 585 1400; shot "$p-14-review-paused" 2
  tapid captionsButton && { shot "$p-15-captions-sheet" 2; back; sleep 1; }
  tapid reviewSaveButton && { shot "$p-16-export-sheet" 2; taptext "$SAVE" && { shot "$p-17-export-progress" 1; shot "$p-18-export-progress-later" 4; }; }
  shot "$p-19-after-export" 25
}

adb shell settings put system font_scale 1.0
flow en ""
if [ "$API" = "35" ]; then
  adb shell cmd locale set-app-locales $PKG --locales de-DE
  adb shell settings put system font_scale 1.3
  flow de130 de
  adb shell cmd locale set-app-locales $PKG --locales fa-IR
  adb shell settings put system font_scale 1.0
  flow fa fa
  adb shell cmd locale set-app-locales $PKG --locales ar
  start; tapid settingsButton && shot ar-02-settings-sheet
else
  adb shell cmd locale set-app-locales $PKG --locales fa-IR
  start; tapid settingsButton && shot fa-02-settings-sheet
fi
adb logcat -d > "$OUT/logcat-api$API.txt" 2>&1 || true
exit 0
