# Design audit 2026-11 – BabaCut (app + babacut.io)

Task `9defaf40` (2026-11-12), read-only: no code was changed. Auditor: `video-editor-entwickler` (g39), reassigned
from the designer role (R-254).

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
- **App:** **no screenshots.** The container has no Android SDK, no emulator and no Java; the app could not be
  built or rendered. App findings come from a static code audit with verified `file:line` references. Items that
  need a device to confirm are marked *plausible*. Closing this gap is improvement 5 below.

## Findings

Effort: S ≤ ½ day, M ≈ 1–2 days, L > 2 days (agent time incl. CI). "A" = app, "W" = website.

### High

| # | Finding | Rule (skill) | Evidence | Effort |
|---|---------|--------------|----------|--------|
| A1 | **No window-inset handling.** `targetSdk = 36` makes Android 15+ draw edge-to-edge (status-bar colour in the theme is ignored), but nothing in `app/src/main` handles insets. Segment bar (12dp from top) and review back button can sit under the status bar/camera cutout; the record button (44dp from bottom) and save button can collide with the 3-button nav bar. *Plausible until seen on a device* – the owner's phone is Android 16. | mobile-native #7 "content stops at the notch", #10 status bar | `app/build.gradle.kts:14`; `res/values/themes.xml:3-4`; `activity_main.xml:31`, `:390`; `view_review.xml:186`; no `WindowInsets`/`fitsSystemWindows` anywhere | M |
| A2 | **No pressed state on buttons.** Round rail buttons, pills, sheet rows, Pro cards and mosaic items are plain shapes without ripple/state list: a tap shows nothing until the sheet opens, the app feels laggy. | apple-design §1 "respond on pointer-down", §10; review-animations "button press feedback 100–160 ms" | `res/drawable/bg_round_button.xml`, `bg_round_button_accent.xml`, `bg_pill.xml`; `Sheet.kt:124`; `SheetRows.kt:47-50`; `MainActivity.kt:911-918`, `:1198-1206` | S–M |
| A3 | **Single-line canvas text clips long translations.** The reorder hint is drawn with one `drawText`, centred, no width limit: fr `drag_hint` (67 chars, 12px·density) is far wider than the ~328dp bar; both ends are cut. de/tr similar. | break-ui CATALOG "labels from data", "container at 320px" | `ScrubBarView.kt:161-162`; `res/values-fr/strings.xml:162` | S |
| A4 | **Review status pill can cover its neighbour buttons** *(plausible)*. `wrap_content` anchored to parent, no `constrainedWidth`/`maxLines`; fr/de `tap_again_delete` exceeds the ~232dp between back and save-draft and blocks their taps. | break-ui "long translated strings" | `view_review.xml:121-133`; `MainActivity.kt:2736` | S |

### Medium

| # | Finding | Rule (skill) | Evidence | Effort |
|---|---------|--------------|----------|--------|
| A5 | **Export and conversion cannot be cancelled**; progress only as a percentage in text. A multi-minute modal traps the user, although `Exporter` already has `cancel()`. | apple-design §16 "never trap the user"; break-ui States | `MainActivity.kt:2808-2812`, `:1315-1319`, `:1554`; `Exporter.kt:322` | M |
| A6 | **Touch targets under 48dp:** sheet close X 36dp; settings, tile and overlay buttons 44dp; mosaic line item 44dp high. | mobile-native baseline (48dp Android) | `Sheet.kt:83`; `activity_main.xml:84`, `:227`, `:256-371`; `MainActivity.kt:1212` | S |
| A7 | **Dimmed buttons still take taps and do nothing silently** while recording (alpha 0.35, not disabled; handlers return without a hint). | break-ui States "disabled actions" | `MainActivity.kt:3259-3262`, `:641`, `:684` | S |
| A8 | **Raw error text reaches users:** camera bind `e.message`, `"Code ${event.error}"`, hard-coded "Overlay" and " %" in a localised status. | break-ui States "raw error message"; project rule `UserFacingException` | `MainActivity.kt:494`, `:630`, `:2735` | S |
| A9 | **No haptics on record start/stop and on "segment over trash"** (only on long-press and snapping). | apple-design §13 "haptics for meaningful moments" | `MainActivity.kt:553-600`; `ScrubBarView.kt:153-154` | S |
| A10 | **Scrub-bar text ignores the system font size** (`12f * density` instead of sp). | apple-design §15 "respect text-size setting" | `ScrubBarView.kt:74` | S |
| A11 | **Quality label in a fixed 52dp circle** may wrap/clip at 150–200 % font size *(plausible)*. | break-ui "large text / fixed heights clip" | `activity_main.xml:126-136` | S |
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
| 1 | **Edge-to-edge insets** on recording, review and sheets | A1 | Nothing under status bar, cutout or nav bar on Android 15/16 – the owner's own phone class and the Play reviewers' devices. | M |
| 2 | **Pressed feedback + 48dp targets** (ripple on all round/pill buttons and rows, enlarge close X etc., disable instead of dim) | A2, A6, A7 | Every tap answers immediately; fewer missed taps; the app feels faster without any engine change. | S–M |
| 3 | **Long-translation overflow fixes** (scrub-bar hint with width limit, constrained status pill, sp text, auto-size quality label) | A3, A4, A10, A11 | No clipped or tap-blocking text in fr/de/tr/fa/ar; matches the open ROADMAP item "check screenshots per language for clipped buttons". | S |
| 4 | **Cancellable export with a progress bar** (also for insert-time conversion) | A5, A16 | Users are never trapped in a multi-minute modal; a wrong export setting costs seconds, not minutes. | M |
| 5 | **App screenshots in CI** (JVM screenshot tests, e.g. Roborazzi, for recording, review and main sheets in 7 languages and at 200 % font) | audit gap, A3/A4/A11 | Every later design task can be checked from images instead of the owner's phone; this audit's *plausible* items become verifiable. | M |

The website findings (W1–W5, all S) are a good batch for the planned "website relaunch" (ROADMAP "Later") or a
small separate task; they are not in the top 5 because the store app is where users and revenue are.

## What the skills change for this project

- They give the design checks a concrete, citeable rulebook. Before, design came from the owner's screenshots only;
  now press feedback, touch targets, insets, motion timing and long-content overflow are checked rule by rule.
- The biggest shift is **states and edge cases** (break-ui, mobile-native): the app's look is consistent, but
  feedback on touch, system insets and long translations in 7 languages were never checked systematically.
- Many web rules (CSS easing, `:active`) need translation to Android; a small Android cheat-sheet in the record
  would make future audits faster.
