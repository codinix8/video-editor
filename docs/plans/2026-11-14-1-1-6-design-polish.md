# Implementation plan: BabaCut 1.1.6 design polish – press feedback + 48 dp, long translations

Task `30d90d59` (2026-11-13), written for the Projekt-Planer. Plan only: no file under `app/` or `.github/` is
changed by this task. Target: **1.1.6 / versionCode 85** (set by PR-1 only).

Concept approved: owner decision `plan.question` 2026-11-13T11:59, **option A** – 1.1.6 = insets + press feedback +
long texts, 1.1.7 = cancellable export. Concept:
[`docs/concepts/2026-11-13-design-polish-1-1-6.md`](../concepts/2026-11-13-design-polish-1-1-6.md), improvements 2
and 3. Findings: [`docs/design/DESIGN-AUDIT-2026-11.md`](../design/DESIGN-AUDIT-2026-11.md) (A2, A3, A4, A6, A7, A10,
A11, A18, A20b; measurements of screenshot round 3).

This plan adds **PR-4** and **PR-5** after the already planned PRs:

- PR-1 (A1 insets + version bump) and PR-2 (A19, `SheetRows.kt`):
  [`2026-11-14-bugfixes-1-1-6.md`](2026-11-14-bugfixes-1-1-6.md) (task `d392d839`).
- PR-3 (A22 + A23 after PR-1): [`2026-11-14-a22-a23-bugfix-plan.md`](2026-11-14-a22-a23-bugfix-plan.md)
  (task `e99e35a9`).

## Start condition and scope

- **Start:** only after the owner has tagged `v1.1.5` (app freeze, hub knowledge #1789, #1791, #2478, #2484).
  Check with `git ls-remote --tags origin v1.1.5`. **Check on 2026-11-13 (this task): the tag `v1.1.5` does not
  exist**; the newest tags are `v1.1.4` and `release-1.1.4-9` (both `f7999a1`), `build-123` is the newest build tag.
- **In scope:**
  - **Item 2 of the concept – press feedback + 48 dp targets:** A2 (no pressed state), A6 (targets < 48 dp, measured
    44/36/25 dp, sheet buttons 46 dp), A7 (dimmed buttons swallow taps silently while recording; part of the same
    concept item and of the approved option A).
  - **Item 3 of the concept beyond A19 – long translations / large font:** A4 (review status pill over its neighbour
    buttons), A11 (quality label at 200 %), A20b (review hint cut off at 200 %), A18 (fa/ar bidi order in the status
    texts), plus A3 (drag hint clipped) and A10 (scrub-bar text ignores font size), which the concept bundles into the
    same item and no other plan carries.
- **Out of scope:**
  - **Cancellable export (concept item 4, A5/A16) is 1.1.7 / versionCode 86 and is not planned here.** No change to
    `runExport`, `Exporter.kt` or the conversion dialog in PR-4/PR-5.
  - A1 insets (PR-1), A19 row spacing (PR-2), A22/A23 (PR-3).
  - The **top scrim** behind status bar / segment bar (A20, concept item 1). It is approved with option A, but neither
    the A1 plan nor this plan carries it. Open point for the planner: give it its own PR-6 (`activity_main.xml`,
    `view_review.xml`, a new gradient drawable; after PR-5, because of the shared layouts) or a follow-up task.
    Only the **review-hint background** (the A20 part on `reviewHint`) rides in PR-5, because A20b edits the same
    view and the same lines.
  - The screenshot CI (PR #31, open owner decision on the workflow): not touched. Screenshots come from the
    scratch-branch tooling, as in the other plans.
- Line numbers below are from `main` at `e41300d`. **PR-1 and PR-3 edit `MainActivity.kt` first, so all
  `MainActivity.kt` lines will have moved when PR-4/PR-5 start** – re-check every reference by its code snippet, not
  by number.

## PR order (no two PRs on `MainActivity.kt` or `SheetRows.kt` at the same time)

| PR | Findings | Files | Starts | Parallel with |
|----|----------|-------|--------|---------------|
| PR-1 (A1/A19 plan) | A1 + version 85 / "1.1.6" | `MainActivity.kt`, `Sheet.kt` (conditional), `app/build.gradle.kts` | after the `v1.1.5` tag | PR-2 |
| PR-2 (A1/A19 plan) | A19 | `SheetRows.kt` | after the `v1.1.5` tag | PR-1, PR-3 |
| PR-3 (A22/A23 plan) | A22 + A23 | `SegmentProgressView.kt`, `MainActivity.kt`, new `ReviewTime.kt`, new `ReviewTimeTest.kt` | after PR-1 is merged | PR-2 |
| **PR-4** (this plan) | A2 + A6 + A7 | `MainActivity.kt`, `SheetRows.kt`, `Sheet.kt`, `res/drawable/bg_round_button*.xml`, `bg_pill.xml`, new `bg_round_button_48.xml`, new `bg_round_button_accent_48.xml`, new `res/animator/press_scale.xml`, `res/layout/activity_main.xml`, 7 × `strings.xml`, new `TouchTargetLayoutTest.kt` | after **PR-2 and PR-3** are merged | none |
| **PR-5** (this plan) | A3, A4, A10, A11, A18, A20b (+ review-hint background of A20) | `ScrubBarView.kt`, `MainActivity.kt`, new `StatusText.kt`, new `StatusTextTest.kt`, `res/layout/view_review.xml`, `res/layout/activity_main.xml`, new `res/drawable/bg_hint.xml` | after **PR-4** is merged | none |

Why this order:

- **`MainActivity.kt`** is edited by PR-1, PR-3, PR-4 and PR-5. They run strictly one after the other:
  PR-1 → PR-3 → PR-4 → PR-5. Each PR is cut from a `main` that already contains the previous one.
- **`SheetRows.kt`** is edited by PR-2 (A19 margins in `rowBase`/`choice`/`switch`/`slider`) and PR-4 (ripple on
  `rowBase`). PR-4 starts after PR-2 is merged, so the ripple lands on the A19 version of `rowBase`.
- **`Sheet.kt`** is edited conditionally by PR-1 (bottom inset) and by PR-4 (close X, buttons). PR-4 is after PR-1.
- **`activity_main.xml`** is edited by PR-4 (44 → 48 dp targets) and PR-5 (quality label, status pill). Serial order
  keeps them apart. PR-1 changes insets in code, not in this layout (A1 plan), but if PR-1 ends up editing it, the
  serial order still holds.
- **PR-5 after PR-4, not before:** PR-4 is the larger visible change and needs the most screenshot review; PR-5 is
  text-level and can be checked on the same views afterwards. The reverse order is equally safe file-wise; the
  planner may swap them, but never run them in parallel.
- **No PR touches `app/build.gradle.kts` except PR-1.** PR-4 and PR-5 do not bump the versionCode (open point from
  the other plans unchanged: one versionCode 85 for the whole 1.1.6 cycle vs. "every build +1").
- **1.1.7 (cancellable export)** edits `MainActivity.kt` (`runExport`, conversion dialog) and `Sheet.kt`; it starts
  only after PR-5 is merged and the 1.1.6 store build exists.

---

## PR-4 – press feedback (A2), 48 dp targets (A6), locked buttons while recording (A7)

### A2 – pressed state on buttons, pills, rows, cards, mosaic items

**Bug/design gap:** all round buttons and pills are plain `<shape>` drawables; sheet rows, sheet buttons, Pro cards and
mosaic items are `GradientDrawable`s without a state. A tap shows nothing until the next sheet opens.

**Files and lines:**

- `app/src/main/res/drawable/bg_round_button.xml` (whole file, oval `#66000000`),
  `bg_round_button_accent.xml` (oval `#FF3B4E`), `bg_pill.xml` (rect, 20 dp corners): wrap the existing shape in a
  `<ripple android:color="#33FFFFFF">` with the shape as the content item (the content doubles as the mask, so the
  ripple stays inside the oval/pill). minSdk 26 (`app/build.gradle.kts:13`), so `<ripple>` needs no fallback.
  `bg_pill` is also the background of the non-clickable `statusText` (`activity_main.xml:41`), `tipCard` (`:57`) and
  `reviewStatus` (`view_review.xml:125`); a ripple only draws for a pressed, clickable view, so they look unchanged.
- New `app/src/main/res/animator/press_scale.xml`: `<selector>` with `state_pressed` → `scaleX/scaleY` 0.96 in
  100 ms (`@android:interpolator/decelerate_quad`), default → 1.0 in 160 ms. Animators follow the system
  "animator duration scale", so with animations off the scale change is instant (reduced motion).
- `MainActivity.kt` `onCreate`, after the click listeners (`:394-396` today): one loop
  `listOf(binding.settingsButton, binding.flipButton, binding.qualityButton, …, binding.finishButton)` that sets
  `stateListAnimator = AnimatorInflater.loadStateListAnimator(this, R.animator.press_scale)` on every round button of
  `activity_main.xml` (ids at `:83`, `:117-207`, `:226`, `:239`, `:255-365`, `:375`, `:398`) and the review buttons
  in `view_review.xml` (`backButton` `:50`, `captionsButton` `:62`, `captionsEditButton` `:74`, `reviewTextButton` `:87`,
  `reviewSoundButton` `:99`, `saveDraftButton` `:111`,
  `reviewDeleteButton` `:170`, `reviewSaveButton` `:182`). Not on `recordButton` (`:387`): it has its own record
  animation; it gets only the ripple through its drawable if it uses one of the three files (it uses
  `bg_record_button.xml`, which stays unchanged).
- `SheetRows.kt` `rowBase` (`:47-56` today; after PR-2 with the A19 margins): add
  `foreground = selectableItemBackground` (resolve `android.R.attr.selectableItemBackground` once per `SheetRows`
  via `ctx.obtainStyledAttributes`). Covers `switch`, `choice`, `lockedRow` and the slider head.
- `Sheet.kt:119-126` `btn()`: background becomes
  `RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), <existing GradientDrawable>, null)`.
- `Sheet.kt:79-83` close X: same `RippleDrawable` around the existing oval.
- `MainActivity.kt:908-919` Pro cards (`card()` inside the paywall): `foreground = RippleDrawable(…, null,
  <copy of the card GradientDrawable as mask>)`, so the ripple keeps the 16 dp corners and the accent stroke stays
  visible.
- `MainActivity.kt:1193-1206` mosaic layout items and `:1209-1215` the line-options item: `foreground` =
  `selectableItemBackgroundBorderless` (the popup has no card shape per item; `bg_panel_selected` stays the selected
  marker).

**Rejected alternative:** Material `MaterialButton` / `ShapeableImageView` for all buttons. It brings ripple and
states for free but changes paddings, tint and insets of ~25 views; too wide for a polish release.

### A6 – touch targets at least 48 × 48 dp, visible size unchanged

**Measured (round 3, `docs/design/screenshots/app/2026-11-13-r3/api35-en-targets-under-48dp.txt`, px/3 = dp):**
settings 44 × 44, overlay remove / edit text 44 × 44, sheet close X 36 × 36, mosaic popup line item 56 × 44, sheet
buttons 175 × 46, preview-sound 52 × 25. Tile buttons 44 × 44 by layout (`activity_main.xml:283-337`), not dumped.

**Files and lines:**

- New `res/drawable/bg_round_button_48.xml` = `<inset android:drawable="@drawable/bg_round_button"
  android:inset="2dp"/>` and `bg_round_button_accent_48.xml` (same for the accent). The view grows to 48 dp, the
  visible circle stays 44 dp, and the ripple from A2 stays inside the visible circle.
- `activity_main.xml`: `layout_width`/`layout_height` 44dp → 48dp and background → `bg_round_button_48` for
  `settingsButton` (`:84-85`, `:88`), `soundButton` (`:227-228`, `:230`), `shapeButton` (`:256-257`, `:260`),
  `borderButton` (`:269-270`, `:273`), `tileMediaButton` (`:284-285`, `:288`), `tileCameraButton` (`:297-298`,
  `:301`), `tileFillButton` (`:310-311`, `:314`), `tileVideoButton` (`:323-324`, `:327`), `tileSoundButton`
  (`:336-337`, `:340`), `editTextButton` (`:349-350`, `:352`); `removeOverlayButton` (`:362-363`, `:365`) →
  `bg_round_button_accent_48`. Where such a button has a margin ≥ 2 dp towards the screen edge or a neighbour,
  reduce it by 2 dp so the visible circle keeps its position; where two 48 dp views would now overlap, keep the
  margin and accept a 2 dp shift (check in the screenshot).
- `settingsButton` sits above the right rail (`rightBar` is `constraintTop_toBottomOf="@id/settingsButton"`,
  `:95-108`). Reduce `rightBar`'s `layout_marginTop` (`:99`, 6 dp) by 2 dp so the rail does not move down and A21
  (tools below the fold) does not get worse. The rail buttons are already 52 dp.
- `Sheet.kt:79-83` close X: `FrameLayout.LayoutParams` 36 → 48 dp; padding 8 → 14 dp so the icon keeps its size;
  background = `InsetDrawable(<ripple oval>, 6 dp)` so the visible circle stays 36 dp. Title padding at `:77`
  (40 dp left/right) → 52 dp so a long title does not run under the larger target.
- `Sheet.kt:119-126` `btn()`: add `minHeight = (48 * dp).toInt()` (today 13 dp + 13 dp padding + 15 sp text ≈ 46 dp).
- `MainActivity.kt:1212` mosaic line item: `(56 * dp, 44 * dp)` → `(56 * dp, 48 * dp)`.
- **preview-sound 52 × 25 dp is not a size bug:** the button is 52 × 52 dp (`activity_main.xml:202-207`); the dump
  caught it half-hidden at the bottom of the scrolling rail (A21). No change; the check below scrolls the rail first.
- Exempt from the 48 dp rule (not tap targets of the app): the bottom-sheet drag handle (52 × 4 dp, `Sheet.kt:69-72`)
  and the sheet's touch-outside strip (326 × 12 dp) from the same dump.

### A7 – locked buttons while recording give a hint instead of silence

**Bug:** `MainActivity.kt:3259-3262` only sets `alpha 0.35` on quality, delete and finish while recording;
`onDeletePressed` (`:641`) and `onFinishPressed` (`:684`) return silently, and `showQualityDialog` (`:394`, `:513`)
has no recording check at all in the click path.

**Files and lines:**

- `MainActivity.kt:3259-3262`: keep `alpha 0.35` as the locked look; additionally set
  `ViewCompat.setStateDescription(view, if (recording) getString(R.string.not_while_recording) else null)` so
  TalkBack announces the state. Keep the views enabled: a disabled view would get no click and could not show the
  hint.
- `:641` and `:684`: `if (activeRecording != null) { hintLocked(); return }`; `:394`: wrap
  `showQualityDialog()` in the same check. `hintLocked()` = one short `Toast` with `R.string.not_while_recording`,
  at most once per 2 s (no toast stacking on repeated taps).
- The comment at `:3263` ("while recording the delete key becomes the overlay play/pause button") is stale: `:3264`
  reads the video overlay but never uses it, `:3265-3266` always set the backspace icon and "delete last", and the
  click goes straight to `onDeletePressed` (`:395`), which returns while recording. So the hint blocks no other
  function. PR-4 removes the stale comment and the unused `vo` line, nothing else there.
- New string `not_while_recording` in all 7 files (rule: every UI string in all 7 `strings.xml`):

  | File | Text |
  |------|------|
  | `res/values/strings.xml` | Not available while recording |
  | `res/values-de/strings.xml` | Während der Aufnahme nicht verfügbar |
  | `res/values-tr/strings.xml` | Kayıt sırasında kullanılamaz |
  | `res/values-es/strings.xml` | No disponible durante la grabación |
  | `res/values-fr/strings.xml` | Indisponible pendant l\'enregistrement |
  | `res/values-fa/strings.xml` | در حین ضبط در دسترس نیست |
  | `res/values-ar/strings.xml` | غير متاح أثناء التسجيل |

  fa and ar go onto the owner's proofreading list (as in 1.1.5).

### PR-4 tests

- **New JVM test `app/src/test/java/de/codinix/videoeditor/TouchTargetLayoutTest.kt`** (JUnit 4.13.2, reads the
  layout XML as text like `StringsConsistencyTest` does, no Android classes): for every element in
  `res/layout/activity_main.xml` and `view_review.xml` whose `android:background` is one of `bg_round_button*`,
  assert that `layout_width` and `layout_height` are ≥ 48dp. Fails today on the 11 views listed under A6; passes after
  PR-4. Guards against new 44 dp buttons.
- `StringsConsistencyTest` must stay green with the new `not_while_recording` key in all 7 files.
- Existing tests (`TimelineRemapTest`, `ReviewTimeTest` from PR-3, …) unchanged and green.
- No JVM test for ripple/scale (drawing state, no Robolectric in the project).

### PR-4 screenshot verification

Tooling: the audit's emulator scripts as template (`docs/design/screenshots/app/2026-11-13-r3/shots3.sh.txt`,
`design-shots-r3.yml.txt`) on a scratch branch that is never merged. Emulator `google_apis` x86_64 API 35, `pixel_6`,
1170 × 2532 @ 480 dpi = 390 × 844 dp. Gotchas (hub knowledge #2277): disable
`com.google.android.apps.nexuslauncher` and set `hide_error_dialogs=1` before installing; API 30 is ~3× slower.

| Check | How | Before (source) | Language |
|-------|-----|-----------------|----------|
| **Target sizes** | `uiautomator dump` on record idle, overlay-text sheet, mosaic popup, settings sheet, export sheet, tile mode; list clickable bounds; dp = px ÷ (density ÷ 160) = px ÷ 3 on this device. Expected: **no clickable view under 48 × 48 dp** except the two exemptions above. Scroll the rail to the end before dumping record idle. | `2026-11-13-r3/api35-en-targets-under-48dp.txt` | en |
| Visible size unchanged | Compare circles pixel-wise | `2026-11-13-r3/api35-en-21-record-idle.png`, `api35-en-23-overlay-text-sheet.png`, `api35-en-24-mosaic-popup.png`, `2026-11-13/api35-en-05-tile-mode.png` | en |
| Rail not pushed down (A21) | First and last visible rail button at the same y as before | `2026-11-13-r3/api35-en-21-record-idle.png`, `2026-11-13/api35small-en-01-record-idle.png` (360 × 640 dp) | en |
| **Pressed state** | `input motionevent DOWN x y`, wait 150 ms, `screencap`, `input motionevent UP x y` on: settings, a rail button, a sheet row, a sheet button, the close X, a Pro card | new (no before image; `api35-en-02-settings-sheet.png` as unpressed reference) | en |
| Sheet close X / buttons | Close X 48 dp target, 36 dp circle; buttons ≥ 48 dp high | `2026-11-13-r3/api35-en-14-export-sheet.png`, `api35-fa-14-export-sheet.png` | en, fa |
| Locked while recording | Start recording, tap 720p / delete / finish, `screencap` with the toast | `../api35-en-07-recording.png`, `2026-11-13/api35-de-06-recording.png` | en, de, fa |

Store the new images under `docs/design/screenshots/app/<date>-1-1-6-pr4/` with the same file names as the sources.

### PR-4 acceptance criteria

1. Every round button, pill button, sheet row, sheet button, Pro card and mosaic item shows a visible pressed state
   within 100 ms of touch-down (ripple; round buttons also scale to 0.96) and returns within 160 ms after release.
2. With "Animator duration scale = off" the scale is instant, the ripple colour still appears.
3. The uiautomator dump lists no clickable target below 48 × 48 dp on the six views above (exemptions: sheet drag
   handle, touch-outside strip).
4. Visible circle sizes and positions are unchanged within 2 dp; the right rail shows the same tools above the fold as
   before on 390 × 844 and 360 × 640 dp.
5. Tapping quality, delete or finish while recording shows "Not available while recording" (in the app language);
   at most one toast per 2 s on repeated taps; camera flip still works while recording.
6. `build-test-lint` green (`assembleDebug`, `testDebugUnitTest` incl. `TouchTargetLayoutTest` and
   `StringsConsistencyTest`, `lintDebug`).

---

## PR-5 – long translations and large font (A3, A4, A10, A11, A18, A20b)

### A3 – drag hint clipped at both ends (fr/de/tr)

**Files and lines:** `ScrubBarView.kt:73-76` (`textPaint`), `:161-162` (`drawText` of `drop_delete` /
`drag_hint`, centred, no width limit).

**Fix:** turn `textPaint` into a `TextPaint`; draw the hint with a cached `StaticLayout`
(`StaticLayout.Builder.obtain(hint, 0, hint.length, textPaint, width − 32dp)`, `ALIGN_CENTER`, `setMaxLines(2)`,
`setEllipsize(TextUtils.TruncateAt.END)`), translated so its bottom line sits at `barTop − 10dp` as today. Rebuild the
layout only when the text or the width changes (no allocation per frame; `onDraw` runs on every drag move). The trash
circle sits above the bar: check that two lines at 200 % do not overlap it; if they do, place the hint below the
bar instead of above.

### A10 – scrub-bar text ignores the system font size

**Files and lines:** `ScrubBarView.kt:74` `textSize = 12f * resources.displayMetrics.density`.

**Fix:** `TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)`. This also scales the
time labels (`:96`, `:127-129`) and the drag hint. Check at 200 % that the time labels at both ends of the bar do not
overlap each other or the playhead label; if they do, cap the size at 18 dp
(`min(sp, 18 * density)`) and record the cap in the PR body.

### A4 – review status pill covers back and save-draft buttons

**Measured (round 3):** `tap_again_delete` spans 49–1121 px (fr) and 156–1014 px (de) at font 1.0, across back
(36–192 px) and save-draft (978–1134 px); at 200 % even the normal status covers both (de). The pill is not
clickable, so taps still pass; the defect is hidden icons and unreadable text.

**Files and lines:** `view_review.xml:121-133` (`reviewStatus`).

**Fix:** keep `layout_width="wrap_content"`; constrain start to `@id/backButton` end and end to
`@id/saveDraftButton` start (instead of `parent`), `android:layout_marginHorizontal="8dp"`,
`app:layout_constrainedWidth="true"`, `android:gravity="center"`, `android:maxLines="2"`,
`android:ellipsize="end"`. The pill stays centred between the two buttons and never overlaps them.

**Deviation from the concept:** the concept says `maxLines=1`. One line would cut `tap_again_delete` to
"Tap again to delete the la…" in fr/de, losing the warning; two lines keep it readable and still fit the 52 dp button
row at font 1.0. At 200 % the pill may grow above the button height; that is acceptable (nothing is covered).

### A11 – quality label "720p" breaks and clips at 200 %

**Files and lines:** `activity_main.xml:125-136` (`qualityButton`, fixed 52 dp circle, `textSize 13sp`); record
status pill `statusText` `:36-49` (wraps to two lines at 200 %).

**Fix:**

- `qualityButton`: `android:maxLines="1"`, `android:autoSizeTextType="uniform"`, `android:autoSizeMinTextSize="8sp"`,
  `android:autoSizeMaxTextSize="13sp"`, `android:autoSizeStepGranularity="1sp"` (platform attributes, minSdk 26),
  `android:paddingHorizontal="4dp"`. At 1.0 the label looks as today (13 sp fits); at 200 % it shrinks instead of
  breaking into "720 / p". Check "1080p" and "480p" as well (labels from `MainActivity.kt:3276-3279`: "4K", "1080p", "720p", "480p").
- `statusText`: `app:layout_constrainedWidth="true"`, `android:layout_marginHorizontal="16dp"`,
  `android:maxLines="2"`, `android:ellipsize="end"`, `android:gravity="center"`. Two lines at 200 % are fine; the pill
  must not run under the settings button.

### A20b – review hint wraps and is cut off at the left edge at 200 %

**Files and lines:** `view_review.xml:157-167` (`reviewHint`, `wrap_content`, centred, no width limit).

**Fix:** keep `layout_width="wrap_content"` and the parent start/end constraints, add
`app:layout_constrainedWidth="true"`, `android:layout_marginHorizontal="24dp"`, `android:gravity="center"`,
`android:maxLines="2"`, `android:ellipsize="end"`. Plus the approved A20 hint background: new
`res/drawable/bg_hint.xml` (rect `#99000000`, 12 dp corners) as `android:background`,
`android:paddingHorizontal="10dp"`, `android:paddingVertical="4dp"`. With `wrap_content` + `constrainedWidth` the
background hugs the text and never leaves the 24 dp margins. The top scrim (rest of A20) is not part of PR-5.

### A18 – fa/ar status texts read in the wrong order (bidi)

**Bug (confirmed, round 2):** the review status is concatenated (`MainActivity.kt:2736`:
`fmt(pos) + " / " + fmt(total) + " · " + plural + vol`); in fa/ar the paragraph direction comes from the first strong
character (the Persian/Arabic word in the plural), so the parts are reordered and the count fuses with the total
("۰:۰۸ / ۱۰:۱۴ بخش"). The record status (`:3246-3250`, `paused_label + " · " + plural + " · " + fmt(total)`) is built
the same way (`api35-fa-07-segment-bar.png`).

**Files and lines:**

- New `app/src/main/java/de/codinix/videoeditor/StatusText.kt` (pure Kotlin, no Android imports):

  ```kotlin
  object StatusText {
      private const val FSI = '⁨'; private const val PDI = '⁩'
      /** Wraps one part in a first-strong isolate, so its own direction cannot reorder its neighbours. */
      fun isolate(s: String): String = "$FSI$s$PDI"
      /** "pos / total" with each time isolated. */
      fun range(pos: String, total: String): String = isolate(pos) + " / " + isolate(total)
      /** Joins non-empty parts with " · ", each part isolated; the order of the parts is kept on screen. */
      fun join(vararg parts: String): String = parts.filter { it.isNotEmpty() }.joinToString(" · ") { isolate(it) }
  }
  ```

- `MainActivity.kt` review status (`:2736` today; after PR-3 it uses the clamped position):
  `StatusText.join(StatusText.range(fmt(shown), fmt(total)), plural) + vol`, where `vol` is isolated as well.
- `MainActivity.kt:3246-3250` record status: `paused_label`, plural and `fmt(total)` through `StatusText.join`.
  `recording` (`getString(R.string.recording, fmt(total))`) uses `StatusText.isolate(fmt(total))` as the argument.
- `view_review.xml:121-133` `reviewStatus` and `activity_main.xml:36-49` `statusText`:
  `android:textDirection="ltr"`, so the part order is the app's LTR order (the app stays LTR, `supportsRtl=false`,
  owner decision), while each isolated part keeps its own direction.
- `ScrubBarView.kt:178-181` `fmt` uses `Locale.GERMANY`, the status uses `Locale.getDefault()`: in fa the pill shows
  Persian digits, the scrub bar Latin digits. Use `Locale.getDefault()` in `ScrubBarView.fmt` too, so both read the
  same; the literal `.` separator for tenths stays.
- Seen while planning, not fixed here: the overlay volume suffix `" · Overlay NN %"` (`:2735`) is a hard-coded
  English word. A new string would be needed; candidate for a later task, not in PR-5.

**Rejected alternative:** `BidiFormatter.unicodeWrap` (androidx) – same effect but needs the Android/androidx runtime
in the unit test; explicit isolates are testable on the JVM and cover API 26+ (`TextView` supports U+2066–U+2069
since API 23).

### PR-5 tests

- **New `app/src/test/java/de/codinix/videoeditor/StatusTextTest.kt`** (JUnit 4.13.2):
  - `isolate("x") == "⁨x⁩"`
  - `range("0:08", "0:15") == "⁨0:08⁩ / ⁨0:15⁩"`
  - `join(range("۰:۰۸", "۱۰:۱۴"), "۱ بخش")` starts with the isolated range and ends with the isolated plural
    (order kept, exactly one " · ")
  - `join("a", "", "b") == "⁨a⁩ · ⁨b⁩"` (empty parts dropped)
  - every FSI has a matching PDI (balanced isolates, no leak into the following text)
- `ReviewTimeTest` (PR-3) unchanged and green; `StringsConsistencyTest` green (PR-5 adds no strings).
- No JVM test for `StaticLayout`, sp sizes and layouts (Android classes); covered by the screenshots.

### PR-5 screenshot verification

Same emulator and tooling as PR-4. Font scale via `settings put system font_scale 2.0` (`fs200`), locale via
`cmd locale set-app-locales de.codinix.videoeditor --locales <tag>`.

| Finding | Before (under `docs/design/screenshots/app/`) | Views / languages after | Check |
|---------|-----------------------------------------------|-------------------------|-------|
| A3 | `2026-11-13-r3/api35-fr-13-drag-hint.png` (shows scrubbing, not the hint) | review with ≥ 2 segments, fr, de, tr, en | Capture with `input motionevent DOWN`, wait 700 ms **without moving** (450 ms long-press, 12 px slop, A13), `MOVE` by 40 px, `screencap`, `UP`. Hint fully visible, at most 2 lines, ends with "…" if longer. |
| A10 | `2026-11-13-r3/api35-de-fs200-08-review.png` vs `api35-de-12-delete-armed.png` | review, de, fs200 and 1.0 | Time labels twice as large at 200 %, no overlap. |
| A4 | `2026-11-13-r3/api35-fr-12-delete-armed.png`, `api35-de-12-delete-armed.png`, `api35-de-fs200-08-review.png`, `api35-de-fs200-12-delete-armed.png` | review + first delete tap, fr, de, en, fa; de fs200 | Back and save-draft icons fully visible; text not clipped mid-word (ellipsis only beyond 2 lines). Pill bounds from `uiautomator dump` lie between the two buttons. |
| A11 | `2026-11-13-r3/api35-de-fs200-01-record-idle.png`, `api35-en-fs200-01-record-idle.png`, `api35-de130-01-record-idle.png` | record idle, en, de at fs200 and 1.3; quality set to 720p and 1080p | Label on one line inside the circle; status pill ≤ 2 lines, not under the settings button. |
| A20b | `2026-11-13-r3/api35-de-fs200-08-review.png`, `api35-en-fs200-08-review.png` | review, de, en at fs200 and 1.0 | No letter cut at either edge; dark hint background readable on the bright scene. |
| A18 | `2026-11-13/api35-fa-08-review.png`, `api35-ar-08-review.png`, `api30-fa-08-review.png`, `api35-fa-07-segment-bar.png` | review and record (paused, 1 segment), fa, ar on API 35, fa on API 30 | Order reads position / total · count, like en; the count does not fuse with the total; scrub-bar digits match the status digits. |
| Regression | `2026-11-13/api35-en-08-review.png`, `api35-en-01-record-idle.png` | en at 1.0 | Looks as before except the hint background. |

Store the new images under `docs/design/screenshots/app/<date>-1-1-6-pr5/` with the same file names as the sources.

### PR-5 acceptance criteria

1. fr, de, tr and en drag hints are fully readable (≤ 2 lines, ellipsis only beyond that).
2. Scrub-bar texts follow the system font size (or the documented 18 dp cap).
3. The review status pill never overlaps the back or save-draft buttons in any of the 7 languages at 1.0 and 200 %.
4. "720p"/"1080p" stay on one line inside the 52 dp circle at 200 %; the record status pill wraps to at most 2 lines.
5. The review hint is never cut at a screen edge at 200 % and has the dark background.
6. In fa and ar the review and record status read in the same part order as in en, and digits in the status and the
   scrub bar use the same digit set.
7. `build-test-lint` green (`StatusTextTest`, `ReviewTimeTest`, `StringsConsistencyTest`, lint).

---

## After PR-5

- The 1.1.6 debug APK (from `build.yml`) goes to the owner for the Xiaomi (Android 16) check: record screen, a tap on
  each round button, settings sheet in Persian, review at large system font. Store build via tag `v1.1.6`
  (`release.yml`) by the owner.
- The record (`KNOWN_ISSUES.md` lines for A2, A3, A4, A6, A7, A10, A11, A18, A20b) is closed by the documentation fold
  from each PR's change fragment.
- 1.1.7: cancellable export (concept item 4) gets its own plan/task after the 1.1.6 store build.

## Design rules applied (from `.claude/skills/`, not part of the repo)

- **apple-design §1 "Respond on pointer-down, not on release"** → ripple + scale on `state_pressed` (A2), not on
  click.
- **apple-design §10 Tap "highlight on touch-down, commit on touch-up … add hit padding around the target"** → 48 dp
  targets via inset backgrounds; visible size unchanged (A6).
- **apple-design §14 Reduced motion** → scale via `StateListAnimator`, which follows the system animator duration
  scale; the ripple colour remains as non-motion feedback.
- **apple-design §15 / concept "respect the text-size setting"** → sp in the scrub bar (A10), auto-size quality label
  instead of a fixed size (A11).
- **ui-cheatsheet "Button feels unresponsive → scale(0.97) on :active"** → Android equivalent scale 0.96, 100 ms in,
  160 ms out.
- **ui-cheatsheet "Dropdown feels sluggish → ease-out under 300 ms"** → decelerate interpolator, all press
  transitions ≤ 160 ms.
- **ui-cheatsheet "Cap the line … max-width"** → width limits instead of unbounded single lines for the drag hint,
  status pill and review hint (A3, A4, A20b).
- `review-animations`, `break-ui` and `mobile-native` are not in this clone; their rules are cited from the audit
  (button press feedback 100–160 ms; long translated strings; RTL/bidi; 48 dp Android baseline).
