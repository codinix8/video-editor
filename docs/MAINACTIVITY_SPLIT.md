# Proposal: splitting `MainActivity.kt` (no code yet)

Status: **proposal, waiting for the owner's go** (task de9795a4, 2026-11-10). Nothing in this file has been built.
Owner rules that shape it: "erst reden, dann bauen"; never drop a feature in a refactor; nothing that can lose a
user's work; every build `versionCode` +1 with a "Bitte prüfen" list.

## 1. Inventory (main at `2339516`, app 1.1.4)

`app/src/main/java/de/codinix/videoeditor/MainActivity.kt` has **3326 of 8781** Kotlin lines (38 %). One class
holds all state and all UI flows. Map by line range (approximate, for orientation):

| Lines | Area | ~Lines |
|---|---|---|
| 60–272 | Fields: camera, segments, overlays, mosaic, captions, review texts, audio history, players, launchers | 210 |
| 273–444 | `onCreate`: binding, button wiring, back handling | 170 |
| 445–693 | Camera + recording: bind, flip, quality dialog, record/stop, free space, delete-last, finish | 250 |
| 694–844 | Text overlay sheet (`showTextDialog`, colour/alpha/background) | 150 |
| 845–945 | Pro gates: crowns, `requirePro`, Pro sheet | 100 |
| 946–1073 | Settings, licences, first-run tips | 130 |
| 1074–1138 | Filter picker | 65 |
| 1139–1450 | Mosaic (tiles, tile images/videos, tile players, line options) | 310 |
| 1451–1661 | Greenscreen, overlay buttons, video overlay import + players | 210 |
| 1662–1849 | Mic/volume/slider sheets, overlay playback sync, play/pause on tap | 190 |
| 1850–1887 | `loadBitmap` (EXIF-aware decode) | 40 |
| 1888–2250 | Captions: sheet, caption editor, timing math, Whisper transcription | 360 |
| 2251–2365 | Review: reorder segments, delete with 15 s undo | 115 |
| 2366–2541 | Review: sound sheet (gain ranges), review text sheet | 175 |
| 2542–2735 | Review mode: enter/exit, player, overlay audio sync, position | 195 |
| 2736–2904 | Export sheet, `runExport`, share, discard | 170 |
| 2905–3016 | Session persistence (`session.json`), crash recovery/report | 110 |
| 3017–3226 | Drafts: save, list, load | 210 |
| 3227–3326 | `refreshUi`, formatting, lifecycle (`onStop/onStart/onDestroy`) | 100 |

Facts that matter for the split:
- **Shared mutable project state is the real coupling**, not the UI: `segments`, `overlayStore`, `mosaic`,
  `captions`, `reviewTexts`, `audioHistory`, `liveDurationMs`, `inReview`. Almost every area reads or writes them.
- `persistSession()` is called from **32 places**; `refreshUi()` and `currentTotalMs()` likewise everywhere. These
  three are the "host" calls every extracted unit will need.
- `registerForActivityResult` launchers (7) must be created in the Activity before `onStart`; they stay there and
  pass results on.
- Pure logic hidden inside the Activity (good first test targets): `shiftStart`, `shiftEnd`, `retext` (caption
  timing), `applyGainRange`, `gainAt` (review sound), `fmt`, quality `label`, the JSON shape of `persistSession` /
  `recoverSessionIfAny`.
- Dead code: private `radioList()` (lines 528–552) is unused; both callers use `Sections.radioList` in `SheetRows.kt`.
- `currentTotalMs()` exists, but three places still inline `segments.sumOf { it.durationMs } + liveDurationMs`
  (`installVideoOverlay`, `syncOverlayPlayer`, `refreshUi`).

## 2. Target shape

Not a framework switch (no ViewModel/Compose/DI migration – that would be a rebuild, not a split). Plain Kotlin
classes, each owning one area, sharing one state object:

```
MainActivity            lifecycle, binding, launchers, wiring (~600 lines target)
ProjectState            segments, captions, reviewTexts, audioHistory, mosaic, liveDurationMs, totalMs()
SessionStore            session.json read/write (pure codec + file I/O), crash report
DraftsController        save/list/load drafts (uses SessionStore codec where it overlaps)
RecordingController     CameraX bind/flip/quality, record/stop, free space, delete-last
OverlayController       text/image/video overlay import, greenscreen, overlay players, volume sheets
MosaicController        tiles, tile media, tile players
CaptionsController      caption sheet, editor, Whisper transcription; CaptionEdits (pure timing math)
ReviewController        review mode, player, reorder/undo, sound + text sheets; GainEdits (pure)
ExportController        export sheet, runExport, share, discard
SettingsSheets          settings, licences, first-run tips, filter picker
ProGate                 crowns, requirePro, Pro sheet
```

Each controller gets the Activity's `binding`, the shared `ProjectState` and a small `Host` interface
(`persist()`, `refreshUi()`, `showProSheet()`, `toast()`, `context`). No controller talks to another directly;
cross-area calls go through the Host or the Activity. This keeps every step a move, not a redesign.

## 3. Order of extraction (one PR per step)

Ordered by risk: pure and well-tested first, wide-coupled UI last. Each step is a **move-only** change
(cut, paste, qualify references); no renamed strings, no layout change, no behaviour change.

| # | Step | Risk | Phone check ("Bitte prüfen") |
|---|---|---|---|
| 0 | Prerequisite: PR CI (#5) and JVM unit tests (#6) merged | – | – |
| 1 | Remove dead `radioList()`, use `currentTotalMs()` everywhere, move `fmt`/`label`/`loadBitmap` into `Util.kt` | very low | record 2 clips, status time correct; add an image overlay (portrait photo stays upright) |
| 2 | Extract pure `CaptionEdits` (`shiftStart`, `shiftEnd`, `retext`) and `GainEdits` (`applyGainRange`, `gainAt`) + JVM tests that pin today's behaviour | low | caption editor: move start/end ±, edit text; review sound: mute one segment |
| 3 | Introduce `ProjectState` (fields only move, Activity delegates) | low–medium | full flow: record, overlay, review, export, draft save/load |
| 4 | `SessionStore`: JSON codec as pure functions + round-trip JVM test, then file I/O | medium (user work!) | record, kill app from recents, reopen → recovered as before; overlays/captions intact |
| 5 | `DraftsController` | medium | save draft, open draft, delete draft, auto-backup after export |
| 6 | `ProGate` + `SettingsSheets` | low | crowns visible without Pro; settings: language, model, quality, safe zones, backups |
| 7 | `ExportController` | medium | export 720p/1080p, watermark without Pro, share, discard with confirm |
| 8 | `CaptionsController` | medium | generate captions (FA + DE), style templates, editor |
| 9 | `ReviewController` | high | review: play/seek, reorder, delete + undo within 15 s, sound and text sheets |
| 10 | `MosaicController` | high | each mosaic layout, tile image, tile video with sound, line options |
| 11 | `OverlayController` | high | text/image/video overlay, greenscreen background, volume, play/pause on tap during recording |
| 12 | `RecordingController` | high | record/stop, flip while recording, quality dialog (4K locked), app to background while recording |

Steps 1–5 alone bring the file under ~2500 lines and put the riskiest data path (session recovery) under tests.
Steps 6–12 can be paused at any point; every intermediate state is a working app.

## 4. How each step stays behaviour-identical

1. **Move-only diffs.** A step never mixes moving and changing. Bug fixes found on the way go into a separate PR
   (or KNOWN_ISSUES), not into the move.
2. **Pin before moving.** Pure functions get JVM tests on the *current* code first (step 2/4), including quirks;
   the move must keep them green.
3. **Feature checklist per step.** The "Bitte prüfen" column above is the owner's thumb test; HANDOVER §4 is the
   full feature list that must still work after the last step.
4. **Same strings, same IDs.** No new UI strings are expected; if one is ever needed, it goes into all 7 `strings.xml`.
5. **One build per step**, `versionCode` +1, GitHub APK only (no `vX.Y.Z` store tag) until a block of
   steps is confirmed on the phone.
6. **Git revert is the rollback.** Because each step is one PR with one purpose, a regression is reverted, not
   debugged under pressure.

## 5. Risks

- **Session/draft persistence (steps 4–5) can lose user work** if the JSON shape changes. Mitigation: round-trip
  test against a `session.json` written by today's code; never rename a key.
- **Player lifecycle**: ExoPlayer instances (`player`, `overlayPlayer`, `reviewOverlayPlayer`, `tilePlayers`) are
  released in `onDestroy` and paused in `onStop`. A moved player that is not wired into lifecycle leaks or keeps
  playing in the background. Mitigation: each controller exposes `onStart/onStop/onDestroy`, called from the
  Activity; check "app to background" in every step from 9 on.
- **Main-thread handlers** (`main.post`, `playbackTicker`, `undoExpire`, `disarmRunnable`, `hideTip`) must be
  removed on destroy wherever they move.
- **Executors** (`bgExecutor`, `whisperExecutor`) stay owned by the Activity and are passed in, so shutdown stays in
  one place.
- **Orientation/config change**: today the Activity is recreated and `recoverSessionIfAny()` restores state. The
  split must not add state that survives only in a controller.
- **Open PRs touching the file**: none of #1/#5/#6/#7/#9/#10 change `MainActivity.kt` today; re-check before each
  step to avoid conflicts the owner cannot resolve on the phone.
- **Effort**: roughly one agent session per step; steps 9–12 may need two. No direct revenue; the gain is lower
  change risk for every later feature (Pro, captions, store fixes).

## 6. Questions for the owner (German summary)

> **BabaCut – Vorschlag: MainActivity aufteilen.** Die Datei hat 3326 Zeilen; fast jede Änderung berührt sie.
> Vorschlag: in 12 kleinen Schritten aufteilen, jeder Schritt verschiebt nur Code (keine Funktion fällt weg) und
> kommt mit einer kurzen "Bitte prüfen"-Liste fürs Handy. Zuerst die sicheren Teile mit Tests (Schritte 1–5,
> inkl. Wiederherstellung nach Absturz), danach die Bildschirme.
> 1. Einverstanden mit Schritten 1–5 zuerst (danach neu entscheiden)?
> 2. Builds der Zwischenschritte nur an Tester (kein Store-Tag), bis ein Block bestätigt ist?
> 3. Erst nach dem 1.1.4-Testerupdate und dem Produktionsantrag beginnen?
