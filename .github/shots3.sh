#!/bin/bash
# Design-audit screenshot script (task fa0e1afc, round 3 task a8ef1268). Test data only: the emulator's synthetic camera scene
# and the typed overlay text "BabaCut Test"; no real user media.
# Usage: bash shots.sh <tag> <wm-size> <wm-density> <locales...>   e.g. bash shots.sh api35 1170x2532 480 en de fa ar
set -u
TAG=$1; SIZE=$2; DENS=$3; shift 3; LOCALES="$*"
PKG=de.codinix.babacut
OUT=shots; mkdir -p "$OUT"
log(){ echo "== $*"; }
rawdump(){ adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1 || : > /tmp/u.xml; }
# a system "isn't responding" dialog (seen on the small-screen run) blocks every tap: press "Wait" and dump again
dump(){ rawdump; local i; for i in 1 2 3; do grep -q 'android:id/aerr_wait' /tmp/u.xml || return 0
  tapb "$(bounds 'resource-id="android:id/aerr_wait"')"; log "dismissed ANR dialog"; sleep 3; rawdump; done; }
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
# positive sheet button = last clickable TextView (Sheet adds negative first, positive last); locale-independent
tappositive(){ dump; tapb "$(tr '>' '\n' < /tmp/u.xml | grep 'class="android.widget.TextView"' | grep 'clickable="true"' | tail -1 | grep -o 'bounds="[^"]*"' | tr -c '0-9' ' ')" && log "tap positive"; sleep 1; }
# n-th clickable node (1-based) of the current top window
tapclickable(){ dump; tapb "$(bounds 'clickable="true"' "$1")" && log "tap clickable $1"; sleep 1; }
back(){ adb shell input keyevent KEYCODE_BACK; sleep 1; }
closesheet(){ back; dump; grep -q 'design_bottom_sheet' /tmp/u.xml && back; true; }
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
start(){ adb shell am force-stop $PKG; adb shell am start -W -n $PKG/de.codinix.videoeditor.MainActivity >/dev/null; sleep 8; dump; }
waitboot(){ adb wait-for-device; until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done; sleep 10; }
setlocale(){ # $1 = BCP-47 tag
  if [ "$SDK" -ge 33 ]; then adb shell cmd locale set-app-locales $PKG --locales "$1"
  else # API < 33 has no per-app locale command: switch the system locale (google_apis image, adb root)
    adb root >/dev/null; sleep 3; adb shell "setprop persist.sys.locale $1; setprop ctl.restart zygote"; waitboot
    adb shell wm size $SIZE; adb shell wm density $DENS
  fi
}
tagof(){ case $1 in fr) echo fr-FR;; en) echo en-US;; de) echo de-DE;; fa) echo fa-IR;; ar) echo ar-EG;; *) echo "$1";; esac; }
# hold a long-press on the scrub bar and drag a little, screenshot while the finger is down (A3 drag hint)
dragshot(){ dump; local b; b=$(bounds "resource-id=\"$PKG:id/scrubBar\""); [ -z "$b" ] && { log "no scrubBar"; return 1; }
  read -r x1 y1 x2 y2 <<< "$b"; local x=$(( x1+(x2-x1)/4 )) y=$(( (y1+y2)/2 ))
  adb shell input motionevent DOWN $x $y; sleep 1.2; adb shell input motionevent MOVE $((x+30)) $((y-20)); sleep 0.5
  adb shell input motionevent MOVE $((x+60)) $((y-40)); sleep 0.5
  adb exec-out screencap -p > "$OUT/$TAG-$1.png"; log "shot $TAG-$1"
  adb shell input motionevent UP $((x+60)) $((y-40)); sleep 1; }
record_review(){ # one ~10 s segment, then review
  start; tapid recordButton || return 1; sleep 10; tapid recordButton; sleep 2; tapid finishButton || return 1; sleep 8; }
{
  echo "tag=$TAG sdk=$SDK release=$(adb shell getprop ro.build.version.release | tr -d '\r')"
  echo "model=$(adb shell getprop ro.product.model | tr -d '\r')"
} > "$OUT/$TAG-device.txt" 2>&1
adb shell wm size $SIZE; adb shell wm density $DENS; sleep 2
adb shell wm size >> "$OUT/$TAG-device.txt"; adb shell wm density >> "$OUT/$TAG-device.txt"
adb shell settings put global hide_error_dialogs 1; adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
adb install -r -g app/build/outputs/apk/debug/app-debug.apk || { echo "install failed"; exit 1; }

# part 1: font scale 1.0 - delete pill (A4), drag hint (A3), export progress (A5/A16)
adb shell settings put system font_scale 1.0
for L in $LOCALES; do
  setlocale "$(tagof $L)"; p=$L
  if [ "$L" = en ]; then # dumps for the 48dp measurement (A6)
    start; shot "$p-21-record-idle-dump" 1
    tapid settingsButton && { shot "$p-22-settings-dump"; closesheet; }
    start; tapid addTextButton && { adb shell input text "BabaCut%sTest"; sleep 2; tappositive; sleep 1; shot "$p-23-overlay-dump"; tapid removeOverlayButton; }
    start; tapid mosaicButton && { shot "$p-24-mosaic-dump"; back; }
  fi
  record_review || continue
  tapid reviewDeleteButton && shot "$p-12-delete-armed" 0.5
  sleep 4
  dragshot "$p-13-drag-hint"
  sleep 2
  tapid reviewSaveButton && { shot "$p-14-export-sheet" 1; tappositive; shot "$p-15-export-progress" 1; shot "$p-15b-export-progress" 4; }
  sleep 20; shot "$p-16-after-export" 1
done
# part 2: font scale 2.0 (A11 quality circle, A4 status pill, A10 scrub text)
adb shell settings put system font_scale 2.0
for L in en de; do
  setlocale "$(tagof $L)"; p=$L-fs200
  start; shot "$p-01-record-idle" 1
  tapid settingsButton && { shot "$p-02-settings-sheet"; closesheet; }
  record_review && { shot "$p-08-review" 1; tapid reviewDeleteButton && shot "$p-12-delete-armed" 0.5; }
done
adb shell settings put system font_scale 1.0
adb logcat -d > "$OUT/$TAG-logcat.txt" 2>&1 || true
exit 0
