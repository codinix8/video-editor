#!/bin/bash
# Design-audit screenshot script (task fa0e1afc). Test data only: the emulator's synthetic camera scene
# and the typed overlay text "BabaCut Test"; no real user media.
# Usage: bash shots.sh <tag> <wm-size> <wm-density> <locales...>   e.g. bash shots.sh api35 1170x2532 480 en de fa ar
set -u
TAG=$1; SIZE=$2; DENS=$3; shift 3; LOCALES="$*"
PKG=de.codinix.babacut
OUT=shots; mkdir -p "$OUT"
log(){ echo "== $*"; }
dump(){ adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1 || : > /tmp/u.xml; }
shot(){ sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$TAG-$1.png"; dump; cp /tmp/u.xml "$OUT/$TAG-$1.xml"; log "shot $TAG-$1"; }
bounds(){ tr '>' '\n' < /tmp/u.xml | grep "$1" | grep -o 'bounds="[^"]*"' | sed -n "${2:-1}p" | tr -c '0-9' ' '; }
tapb(){ local b=$1; [ -z "$b" ] && return 1; read -r x1 y1 x2 y2 <<< "$b"; adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); }
tapid(){ dump; tapb "$(bounds "resource-id=\"$PKG:id/$1\"")" && { log "tap $1"; sleep 1; } || { log "id $1 not found"; return 1; }; }
# tap at a fraction (per mille) of a view's bounds
tapfrac(){ dump; local b; b=$(bounds "resource-id=\"$PKG:id/$1\""); [ -z "$b" ] && { log "id $1 not found"; return 1; }
  read -r x1 y1 x2 y2 <<< "$b"; adb shell input tap $(( x1+(x2-x1)*$2/1000 )) $(( y1+(y2-y1)*$3/1000 )); log "tapfrac $1 $2 $3"; sleep 1; }
# last android.widget.Button on screen (Sheet puts the positive button last)
taplastbutton(){ dump; local n; n=$(tr '>' '\n' < /tmp/u.xml | grep -c 'class="android.widget.Button"'); [ "$n" -gt 0 ] || { log "no button"; return 1; }
  tapb "$(bounds 'class="android.widget.Button"' "$n")"; log "tap last button"; sleep 1; }
# n-th clickable node (1-based) of the current top window
tapclickable(){ dump; tapb "$(bounds 'clickable="true"' "$1")" && log "tap clickable $1"; sleep 1; }
back(){ adb shell input keyevent KEYCODE_BACK; sleep 1; }
closesheet(){ back; dump; grep -q 'design_bottom_sheet' /tmp/u.xml && back; true; }
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
start(){ adb shell am force-stop $PKG; adb shell am start -W -n $PKG/de.codinix.videoeditor.MainActivity >/dev/null; sleep 8; }
waitboot(){ adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done; sleep 10; }
setlocale(){ # $1 = BCP-47 tag
  if [ "$SDK" -ge 33 ]; then adb shell cmd locale set-app-locales $PKG --locales "$1"
  else # API < 33 has no per-app locale command: switch the system locale (google_apis image, adb root)
    adb root >/dev/null; sleep 3; adb shell "setprop persist.sys.locale $1; setprop ctl.restart zygote"; waitboot
    adb shell wm size $SIZE; adb shell wm density $DENS
  fi
}
tagof(){ case $1 in en) echo en-US;; de) echo de-DE;; fa) echo fa-IR;; ar) echo ar-EG;; *) echo "$1";; esac; }

{
  echo "tag=$TAG sdk=$SDK release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "abi=$(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
  adb shell wm size; adb shell wm density
} > "$OUT/$TAG-device.txt" 2>&1
adb shell wm size $SIZE; adb shell wm density $DENS; sleep 2
adb shell wm size >> "$OUT/$TAG-device.txt"; adb shell wm density >> "$OUT/$TAG-device.txt"
adb shell settings get secure navigation_mode >> "$OUT/$TAG-device.txt" 2>&1
adb shell settings put system font_scale 1.0
adb install -r -g app/build/outputs/apk/debug/app-debug.apk || { echo "install failed"; exit 1; }
adb logcat -c

for L in $LOCALES; do
  setlocale "$(tagof $L)"
  p=$L
  start; shot "$p-01-record-idle" 1
  tapid settingsButton && { shot "$p-02-settings-sheet"; closesheet; }
  # overlay editing: add a text overlay, it stays selected with its edit column
  start; tapid addTextButton && { adb shell input text "BabaCut%sTest"; sleep 1; taplastbutton; shot "$p-03-overlay-edit"; tapid removeOverlayButton; }
  # mosaic: layout popup, pick 2x2 (6th clickable entry: off, 2 rows, 2 cols, 3 rows, 3 cols, 2x2)
  start; tapid mosaicButton && { shot "$p-04-mosaic-popup"; tapclickable 6; sleep 1;
    # tile mode: select the top-left tile
    tapfrac gestureView 250 250; shot "$p-05-tile-mode"
    tapid mosaicButton && tapclickable 1; }
  # recording with segment bar
  start; tapid recordButton && { shot "$p-06-recording" 4; tapid recordButton; shot "$p-07-segment-bar" 3; }
  tapid finishButton && { shot "$p-08-review" 8
    tapfrac scrubBar 500 500; shot "$p-09-review-cut" 2
    tapid captionsButton && { shot "$p-10-caption-sheet"; closesheet; }
    tapid reviewSaveButton && { shot "$p-11-export-sheet"; closesheet; }
    tapid reviewDeleteButton && { sleep 1; taplastbutton; }; }
done
adb logcat -d > "$OUT/$TAG-logcat.txt" 2>&1 || true
exit 0
