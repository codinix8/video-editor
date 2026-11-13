# Design audit 2026-11 – BabaCut (app + babacut.io)

Task `9defaf40` (2026-11-12), read-only: no code was changed. Auditor: `video-editor-entwickler` (g39), reassigned
from the designer role (R-254).

**Rework 2026-11-12 (g41, review on PR #22):** app screenshots added from an Android emulator (see "Screenshots").
A1 is now confirmed on a device image, three new app findings (A19–A21) come from the images.

**Round 2 2026-11-13 (g47/g48, task `fa0e1afc`):** all main views in en/de/fa, device matrix API 30/35/36 + 360×640 dp;
status of the device-dependent findings in "Screenshot round 2 and device verification"; new findings A22, A23.

## Basis

- **Rulebook:** the vendored design skills of the hub, profile `app` for `video-editor` (`spec/skills.yaml` in
  `codinix8/ai-nation`, source `emilkowalski` pinned at 4061b67): `apple-design`, `emil-design-eng`, `ui-cheatsheet`,
  `review-animations` (+ `STANDARDS.md`), `break-ui` (+ `CATALOG.md`), `mobile-native`. They were not placed in the
  clone for the developer role, so they were read directly from the hub repo. Web-oriented rules were translated to
  Android (48dp touch targets, ripple/pressed state, `sp` text, animator scale, insets).
- **Owner design decisions respected** (no finding asks to change them):
  - `supportsRtl=false` on purpose – the app layout is not mirrored for fa/ar (`HANDOVER.md:77-78`).
  - TikTok-style bottom sheets and mosaic panel, crown on locked rows (`HANDOVER.md:74`, `:87`).
  - Progress bar green → orange → red near the limit (`HANDOVER.md:83`); watermark on free export (`:94-95`).
  - Dark theme, portrait only (`themes.xml:2`, `AndroidManifest.xml:33`).
  - Self-hosted Vazirmatn for fa on the website (`docs/DECISIONS.md`, website fonts).
  - Every new string in all 7 `strings.xml`; user-facing errors via `UserFacingException`.
  - `hub_design_preferences` is designer-only (403 for this role); the decisions above come from the project record.

## Screenshots and their limits

- **Website (babacut.io, `docs/`):** Playwright/Chromium, full page, desktop 1440×900 and mobile 390×844
  (touch emulation). The site holds no customer data. Files in [`screenshots/`](screenshots/):
  `web-index-{en,de,fa}-{desktop,mobile}.png`, `web-support-en-{desktop,mobile}.png`,
  `web-privacy-de-{desktop,mobile}.png`.
- **App (rework):** debug APK (versionCode 84, `main` at 5ea5e0a) on the Android emulator of GitHub Actions,
  run [43325272690](https://github.com/codinix8/video-editor/actions/runs/43325272690): system image
  `google_apis` x86_64 **API 35 (Android 15)** with ARM64 translation (the APK is arm64-only), profile `pixel_6`
  with `wm size 1170x2532` + `wm density 480` = **390×844 dp**, gesture navigation. Locales via
  `cmd locale set-app-locales`: **en** at font scale 1.0, **de** at font scale **1.3**, **fa** at 1.0.
  Test data only: the emulator's synthetic camera scene, no person, no customer data; recorded segment ~6 s.
  Device facts in [`screenshots/app/device-api35.txt`](screenshots/app/device-api35.txt). A first run on API 34
  ([43324012441](https://github.com/codinix8/video-editor/actions/runs/43324012441)) is kept for two comparison
  images (`api34-*`), because edge-to-edge is only enforced from API 35.
  Files in [`screenshots/app/`](screenshots/app/), `api35-<locale>-<nn>-<view>.png`: record idle (01), settings
  sheet + scrolled (02/02b), quality (03), filter (04), text sheet (05), drafts (06), recording (07), one segment
  (08), review (09), review text sheet (10); fa: 01 and 02.
  *Not captured:* captions, sound and export sheets (the scripted back key only closed the keyboard, so the text
  sheet stayed open), and 200 % font. The temporary workflow ran on the scratch branch
  `agent/9defaf40-app-screenshots`, was never part of this PR and the branch was deleted afterwards; the script
  is the starting point for improvement 5.
- Desktop 1440×900 does not apply to the app (portrait-only phone app); it is covered for the website.

## Screenshot round 2 and device verification (task `fa0e1afc`, 2026-11-13)

All main views in en, de and fa, plus a device matrix to check the device-dependent findings. No app code was
changed (1.1.5 is tagged from `main`).

**Capture note (reproducible).** Debug APK built from `main` at c84b3f5 (versionCode 84) inside a temporary
workflow on the scratch branch `agent/fa0e1afc-app-shots` (never merged; copies of the script and workflow are kept
as [`shots.sh.txt`](screenshots/app/2026-11-13/shots.sh.txt) and
[`design-shots.yml.txt`](screenshots/app/2026-11-13/design-shots.yml.txt)). Emulator: GitHub Actions,
`reactivecircus/android-emulator-runner`, image `google_apis` x86_64 with ARM64 translation, AVD profile `pixel_6`,
gesture navigation on API 35/36 (`navigation_mode` 2), font scale 1.0, app locale via `cmd locale set-app-locales`
(API ≥ 33) or the system locale (API 30). Device facts per run in `<tag>-device.txt`.

| Tag | Android | Screen | Run | Views |
|-----|---------|--------|-----|-------|
| `api35` | 15 (API 35) | 1170×2532 @ 480 dpi = 390×844 dp | [43347814682](https://github.com/codinix8/video-editor/actions/runs/43347814682) | 01–11 in en, de, fa (+ ar 02, 08) |
| `api36` | 16 (API 36) | 390×844 dp | 43347814682 | 01, 08, 09 en; 02, 08 fa; 02 ar |
| `api30` | 11 (API 30) | 390×844 dp, 3-button nav | [43341857246](https://github.com/codinix8/video-editor/actions/runs/43341857246) (job hit the 60-min limit after view 09 of fa) | 01, 08 en; 08 de; 02, 08 fa |
| `api35small` | 15 (API 35) | 720×1280 @ 320 dpi = **360×640 dp** | 43347814682 | 01, 08 en; 02 fa; 11 de |

**Test data:** the emulator's synthetic camera scene (no person, no user media), the typed overlay text
"BabaCut Test", a recorded segment of about 12–30 s. Nothing leaves the run except the artifact.

Two round-1 images (run 43341857246) are kept as `run1-*` because round 2 did not show the same state.

**Files** in [`screenshots/app/2026-11-13/`](screenshots/app/2026-11-13/), `<tag>-<locale>-<nn>-<view>.png`:
01 record idle · 02 settings sheet · 03 overlay editing (text overlay selected with its edit column) · 04 mosaic
layout popup · 05 tile mode (2×2, top-left tile selected) · 06 recording · 07 paused with one segment (segment
bar) · 08 review · 09 review/cut (scrubbed, paused) · 10 caption sheet · 11 export sheet. The round-1 files in
`screenshots/app/` stay unchanged because the 1.1.6 concept links them.

**Not covered:** 04/05 on the small screen (the mosaic button sits below the visible part of the tool rail there,
see A21), font scales other than 1.0 (A11, 200 %), the delete confirmation pill (A4) and the drag hint (A3).
Still images cannot show motion, so the review-animations rules (A2, A14, A15) are not judged here.

### Device-dependent findings: status

| Finding | Status | Evidence (all under `screenshots/app/2026-11-13/`) |
|---------|--------|-----------------------------------------------------|
| **A1** edge-to-edge | **Device-specific: confirmed on API 35 and 36, not reproduced on API 30.** On API 35/36 the app draws under the status bar: the review progress line (red) runs through the clock, the segment bar (`segmentBar` at y 36–54 px) lies inside the reported cutout inset of 135 px, and on the record screen the white status icons vanish completely on the bright scene. Same on the small screen. On API 30 the status bar is opaque black and the 3-button nav bar stays clear (edge-to-edge is only enforced from API 35). | `api35-en-08-review.png`, `api36-en-08-review.png`, `api35-en-01-record-idle.png`, `api35small-en-08-review.png`; contrast `api30-en-08-review.png`, `api30-en-01-record-idle.png` |
| **A19** fa/ar label spacing | **Confirmed on all tested devices (API 30, 35, 36, small screen), fa and ar.** Besides the settings rows the same run-together happens in the caption sheet ("تشخیص خودکارزبان") and the text sheet ("100 %شفافیت متن"), and the switch row label touches the switch. | `api35-fa-02-settings-sheet.png`, `api36-fa-02-settings-sheet.png`, `api30-fa-02-settings-sheet.png`, `api35small-fa-02-settings-sheet.png`, `api35-ar-02-settings-sheet.png`, `api36-ar-02-settings-sheet.png`, `api35-fa-10-caption-sheet.png`, `run1-api35-fa-03-text-sheet.png` |
| A18 fa/ar numbers in mixed text | **Confirmed (was plausible).** The review status "pos / total · 1 segment" is built by string concatenation; in fa/ar the bidi algorithm reorders the parts: the segment count fuses with the total ("۰:۰۸ / ۱۰:۱۴ بخش" instead of position / total · 1 segment). | `api35-fa-08-review.png`, `api35-fa-07-segment-bar.png`, `api30-fa-08-review.png`, `api35-ar-08-review.png` |
| A20 no scrim | **Confirmed on every device.** Review hint and grey pills are hard to read on the bright scene. | `api35-de-08-review.png`, `api35small-en-08-review.png` |
| A21 tool rail below the fold | **Confirmed, worse on the small screen.** At 360×640 dp only 7 rail buttons are visible (settings to text); portrait, mosaic, mic and the rest are off-screen; so 04/05 could not be scripted. | `api35small-en-01-record-idle.png`, `api35-en-01-record-idle.png` |
| A4 status pill vs. buttons | Not reproduced at font scale 1.0 in en/de/fa (pill fits); `tap_again_delete` not triggered – stays plausible. | `api35-de-09-review-cut.png` |
| A22 *(new)* segment bar invisible on bright footage | **Confirmed.** `SegmentProgressView` paints recorded segments white on a 33 % white track; on the bright scene the whole bar disappears after recording (pixels at y 45 are pure white), only the live green segment shows. Same rule as A20; fix together with A1/A20 (scrim or dark outline). | `api35-en-07-segment-bar.png`; code `SegmentProgressView.kt:20-21` |
| A23 *(new)* "0:13 / 0:12" | **Confirmed (low).** After scrubbing to the end the review status shows a position larger than the total, because `fmt()` truncates seconds and the player position can pass the summed duration by a few hundred ms. | `run1-api35-en-09-review-cut.png` (round 2 caught the loop restart instead); `MainActivity.kt:2736`, `:3270-3273` |

## Findings

Effort: S ≤ ½ day, M ≈ 1–2 days, L > 2 days (agent time incl. CI). "A" = app, "W" = website.

### High

| # | Finding | Rule (skill) | Evidence | Effort |
|---|---------|--------------|----------|--------|
| A1 | **No window-inset handling.** `targetSdk = 36` makes Android 15+ draw edge-to-edge (status-bar colour in the theme is ignored), but nothing in `app/src/main` handles insets. Segment bar (12dp from top) and review back button can sit under the status bar/camera cutout; the record button (44dp from bottom) and save button can collide with the 3-button nav bar. **Confirmed on API 35:** the red segment bar is drawn under the status-bar clock and icons, white status icons vanish on bright footage (`api35-en-10-review-text.png`, `api35-en-09-review.png`, `api35-en-01-record-idle.png`; API 34 still shows a black status bar, `api34-en-01-record-idle.png`). The owner's phone is Android 16. **Round 2 (fa0e1afc): also on API 36 and 360×640 dp; not on API 30 → device-specific (API 35+).** | mobile-native #7 "content stops at the notch", #10 status bar | `app/build.gradle.kts:14`; `res/values/themes.xml:3-4`; `activity_main.xml:31`, `:390`; `view_review.xml:186`; no `WindowInsets`/`fitsSystemWindows` anywhere; screenshots `app/api35-en-09-review.png`, `app/api35-en-10-review-text.png` | M |
| A2 | **No pressed state on buttons.** Round rail buttons, pills, sheet rows, Pro cards and mosaic items are plain shapes without ripple/state list: a tap shows nothing until the sheet opens, the app feels laggy. | apple-design §1 "respond on pointer-down", §10; review-animations "button press feedback 100–160 ms" | `res/drawable/bg_round_button.xml`, `bg_round_button_accent.xml`, `bg_pill.xml`; `Sheet.kt:124`; `SheetRows.kt:47-50`; `MainActivity.kt:911-918`, `:1198-1206` | S–M |
| A3 | **Single-line canvas text clips long translations.** The reorder hint is drawn with one `drawText`, centred, no width limit: fr `drag_hint` (67 chars, 12px·density) is far wider than the ~328dp bar; both ends are cut. de/tr similar. | break-ui CATALOG "labels from data", "container at 320px" | `ScrubBarView.kt:161-162`; `res/values-fr/strings.xml:162` | S |
| A4 | **Review status pill can cover its neighbour buttons** *(plausible)*. `wrap_content` anchored to parent, no `constrainedWidth`/`maxLines`; fr/de `tap_again_delete` exceeds the ~232dp between back and save-draft and blocks their taps. Emulator: the de status "0:06 / 0:15 · 1 Segment" at 130 % font still fits (`app/api35-de130-09-review.png`); `tap_again_delete` was not triggered, so the item stays plausible. | break-ui "long translated strings" | `view_review.xml:121-133`; `MainActivity.kt:2736` | S |
| A19 | *(new, screenshot)* **fa settings rows run label and value together** ("فارسیزبان برنامه", "تاپ (سریع)تشخیص"): with `supportsRtl=false` the Persian label aligns to the right edge of its weighted box and the value view has no start margin, so both words touch and read as one; a switch row label touches the switch. **Round 2 (fa0e1afc): confirmed in fa and ar on API 30/35/36 and 360×640 dp, also in the caption and text sheets.** Fix stays text-level (alignment + margin), `supportsRtl=false` is kept. | break-ui RTL/bidi; ui-cheatsheet spacing | `SheetRows.kt:53`, `:83-85`; `app/api35-fa-02-settings-sheet.png` | S |

### Medium

| # | Finding | Rule (skill) | Evidence | Effort |
|---|---------|--------------|----------|--------|
| A5 | **Export and conversion cannot be cancelled**; progress only as a percentage in text. A multi-minute modal traps the user, although `Exporter` already has `cancel()`. | apple-design §16 "never trap the user"; break-ui States | `MainActivity.kt:2808-2812`, `:1315-1319`, `:1554`; `Exporter.kt:322` | M |
| A6 | **Touch targets under 48dp:** sheet close X 36dp; settings, tile and overlay buttons 44dp; mosaic line item 44dp high. | mobile-native baseline (48dp Android) | `Sheet.kt:83`; `activity_main.xml:84`, `:227`, `:256-371`; `MainActivity.kt:1212` | S |
| A7 | **Dimmed buttons still take taps and do nothing silently** while recording (alpha 0.35, not disabled; handlers return without a hint). Seen while recording: 720p, delete and finish dimmed only (`app/api35-en-07-recording.png`). | break-ui States "disabled actions" | `MainActivity.kt:3259-3262`, `:641`, `:684` | S |
| A8 | **Raw error text reaches users:** camera bind `e.message`, `"Code ${event.error}"`, hard-coded "Overlay" and " %" in a localised status. | break-ui States "raw error message"; project rule `UserFacingException` | `MainActivity.kt:494`, `:630`, `:2735` | S |
| A9 | **No haptics on record start/stop and on "segment over trash"** (only on long-press and snapping). | apple-design §13 "haptics for meaningful moments" | `MainActivity.kt:553-600`; `ScrubBarView.kt:153-154` | S |
| A10 | **Scrub-bar text ignores the system font size** (`12f * density` instead of sp). | apple-design §15 "respect text-size setting" | `ScrubBarView.kt:74` | S |
| A11 | **Quality label in a fixed 52dp circle** may wrap/clip at 150–200 % font size *(plausible)*. At 130 % "720p" still fits (`app/api35-de130-01-record-idle.png`); 200 % not tested. | break-ui "large text / fixed heights clip" | `activity_main.xml:126-136` | S |
| A20 | *(new, screenshot)* **Text on video has no scrim:** the review hint (white 80 %, 12sp) and grey status pills become hard to read on bright footage; together with A1 the status-bar icons disappear. | apple-design legibility/contrast; ui-cheatsheet text on images | `view_review.xml:158-167`; `app/api35-en-09-review.png`, `app/api35-de130-09-review.png` | S |
| W1 | **Mobile header wraps into 2–3 rows; in fa the brand "BabaCut" touches the nav link "خصوصی"** – the header has no `gap`, nav links only `margin-left`, and the header is not `dir=rtl` while its content is Persian. | ui-cheatsheet spacing; break-ui "container at 320px", RTL | `docs/style.css:9`, `:12`, `:32`, `:39`; `screenshots/web-index-fa-mobile.png`, `web-index-en-mobile.png` | S |
| W2 | **Website touch targets too small:** nav links 17px high, language flags 39×25px, footer links 16px. | apple-design 44pt minimum; mobile-native touch targets | `docs/style.css:12`, `:29`, `:36`; measured with Playwright at 390×844 | S |
| W3 | **CTA buttons stack with zero gap on mobile** ("Get it on Google Play" touches "See features"); `.btn` has no `:active`/`:focus-visible` state and no transition. | ui-cheatsheet `scale(0.97)` on `:active`; emil "focus states" | `docs/style.css:19-20`; `screenshots/web-index-en-mobile.png` | S |

### Low

| # | Finding | Rule (skill) | Evidence | Effort |
|---|---------|--------------|----------|--------|
| A12 | Section header top padding rounds density down first (`18 * dp.toInt()` → 2.625 becomes 2). | ui-cheatsheet spacing | `SheetRows.kt:28` | S |
| A13 | Long-press slop is 12 raw px (~4dp); finger jitter cancels reorder. Use `scaledTouchSlop`. | apple-design §10 hysteresis | `ScrubBarView.kt:197` | S |
| A14 | Things snap instead of moving: trash scales 1.0→1.2 instantly, segments jump after a drop, record button only swaps colour. | emil "preventing jarring changes" | `ScrubBarView.kt:154`, `:218`; `bg_record_button.xml` | M |
| A15 | Timings outside the rules: tip card fade-out 400 ms (> 300 ms); play/pause fade driven by a 40 ms redraw clock ignores the animator duration scale (reduced motion). | review-animations STANDARDS "UI < 300 ms", reduced motion | `MainActivity.kt:1293`; `OverlayGestureView.kt:290-306` | S |
| A16 | Two accent reds (`#FE2C55` vs `#FF3B4E`); export progress is a stock Material dialog beside custom sheets. | emil "cohesion" | `Sheet.kt:153`; `res/values/colors.xml:2` | S |
| A17 | `Paint`/`Path` allocated in `onDraw`. | apple-design §11 frame smoothness | `ScrubBarView.kt:154`, `:166`; `OverlayGestureView.kt:299` | S |
| A18 | fa/ar strings mixed with numbers may render in confusing order; time formatted with `Locale.GERMANY` *(plausible)*. Text-level fix (`BidiFormatter`) keeps `supportsRtl=false`. | break-ui RTL/bidi | `MainActivity.kt:2736`; `ScrubBarView.kt:180` | S |
| A21 | *(new, screenshot)* Right tool rail at 390×844 dp: the last tools sit below the fold behind a fading edge only (first-run tip says "it scrolls"); in review the save button covers part of the "Made with BabaCut" watermark preview. | apple-design clarity; break-ui "container at 320px" | `activity_main.xml:95-104`; `app/api35-en-01-record-idle.png`, `app/api35-en-09-review.png` | S |
| W4 | Headings use default tracking; body text on legal pages runs ~1000px wide (> 65ch). | ui-cheatsheet `-0.02em`, `max-width: 65ch` | `docs/style.css:16`, `:30`; `screenshots/web-privacy-de-desktop.png` | S |
| W5 | Language switch is flag-emoji only (no `aria-pressed`, no language name visible); site has en/de/fa while the app has 7 languages; footer "Support/GitHub" untranslated in fa and footer margins not mirrored in RTL. | break-ui RTL; apple-design clarity | `docs/index.html:22-25`; `docs/lang.js:9`; `docs/style.css:29`; `screenshots/web-index-fa-mobile.png` | S–M |

### What is already good

- `sp` text sizes on views, plurals via `getQuantityString`, `contentDescription` on icon buttons (record/stop
  switches, `MainActivity.kt:3243`).
- Haptics only where causal: snapping overlays/captions and long-press (`OverlayGestureView.kt:146`,
  `CaptionView.kt:202`, `ScrubBarView.kt:43`).
- Forgiving destructive actions: double-tap delete, 15 s undo, drag-to-trash, confirm on back with unsaved segments.
- Direct scrubbing (1:1 playhead, fine-scrub after a pause), consistent sheet system (handle, 24dp radius,
  54dp rows, crown on locked rows) matching the owner's TikTok-style decision.
- Website: no horizontal overflow at 390px in en/de/fa; self-hosted fa font; cards use colour, not borders+shadows.

## Top 5 improvements (proposed as tasks, waiting for the owner's release)

| Rank | Improvement | Covers | Expected effect | Effort |
|------|-------------|--------|-----------------|--------|
| 1 | **Edge-to-edge insets** on recording, review and sheets | A1 (confirmed on API 35), A20 | Nothing under status bar, cutout or nav bar on Android 15/16 – the owner's own phone class and the Play reviewers' devices. | M |
| 2 | **Pressed feedback + 48dp targets** (ripple on all round/pill buttons and rows, enlarge close X etc., disable instead of dim) | A2, A6, A7 | Every tap answers immediately; fewer missed taps; the app feels faster without any engine change. | S–M |
| 3 | **Long-translation and RTL text fixes** (fa/ar label-value spacing in sheet rows, scrub-bar hint with width limit, constrained status pill, sp text, auto-size quality label) | A3, A4, A10, A11, A19 | No clipped or tap-blocking text in fr/de/tr/fa/ar; matches the open ROADMAP item "check screenshots per language for clipped buttons". | S |
| 4 | **Cancellable export with a progress bar** (also for insert-time conversion) | A5, A16 | Users are never trapped in a multi-minute modal; a wrong export setting costs seconds, not minutes. | M |
| 5 | **App screenshots in CI** (JVM screenshot tests, e.g. Roborazzi, for recording, review and main sheets in 7 languages and at 200 % font) | audit gap, A3/A4/A11 | Every later design task can be checked from images instead of the owner's phone; this audit's *plausible* items become verifiable. | M (the emulator script of this audit is a working prototype: API 35, 390×844 dp, locale + font scale) |

The website findings (W1–W5, all S) are a good batch for the planned "website relaunch" (ROADMAP "Later") or a
small separate task; they are not in the top 5 because the store app is where users and revenue are.

## What the skills change for this project

- They give the design checks a concrete, citeable rulebook. Before, design came from the owner's screenshots only;
  now press feedback, touch targets, insets, motion timing and long-content overflow are checked rule by rule.
- Screenshots on an emulator turned the most important code finding (A1, edge-to-edge) from "plausible" into
  "confirmed" and surfaced a Persian layout defect (A19) that the static audit missed.
- The biggest shift is **states and edge cases** (break-ui, mobile-native): the app's look is consistent, but
  feedback on touch, system insets and long translations in 7 languages were never checked systematically.
- Many web rules (CSS easing, `:active`) need translation to Android; a small Android cheat-sheet in the record
  would make future audits faster.

## Proposed tasks (hub, 2026-11-12, waiting for the owner's release)

1. `d3bf1d65` Edge-to-Edge insets · 2. `6e53b97d` press feedback + 48dp · 3. `5b1362db` long-translation overflow ·
4. `067dc3c3` cancellable export · 5. `5cf6cd1c` app screenshots in CI.

**Rework note (g41):** A19 (fa/ar label-value spacing) belongs to task `5b1362db` (improvement 3) and A20 to
`d3bf1d65` (improvement 1); the proposals are unchanged in the hub and still wait for the owner's release.
