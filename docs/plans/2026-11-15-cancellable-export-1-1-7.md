# Implementation plan: BabaCut 1.1.7 – cancellable export (A5/A16)

Task `b06437d2` (2026-11-14), written for the Projekt-Planer. Plan only: no file under `app/`, `.github/` or
`scripts/` is changed by this task.

Concept approved: owner decision `plan.question` 2026-11-13T11:59, **option A** (hub knowledge #2753):
"1.1.6 = improvements 1 + 2 + 3, 1.1.7 = improvement 4". Concept:
[`docs/concepts/2026-11-13-design-polish-1-1-6.md`](../concepts/2026-11-13-design-polish-1-1-6.md), section
"4 – Export abbrechen, mit Fortschrittsbalken". Findings:
[`docs/design/DESIGN-AUDIT-2026-11.md`](../design/DESIGN-AUDIT-2026-11.md) **A5** (export cannot be cancelled,
progress only as text) and **A16** (export progress is a stock Material dialog beside the custom sheets; second
accent red). Round 2 of the audit confirmed both from the real export dialog
(`api35-{en,de,fr,fa}-15-export-progress.png`, `api35-en-15b-export-progress.png`), including the fa finding: the
stock dialog's title is left-aligned while the body is right-aligned (`supportsRtl="false"`).

Earlier plans this one builds on:

- PR-1 (A1 + version 85) and PR-2 (A19): [`2026-11-14-bugfixes-1-1-6.md`](2026-11-14-bugfixes-1-1-6.md).
- PR-3 (A22/A23): [`2026-11-14-a22-a23-bugfix-plan.md`](2026-11-14-a22-a23-bugfix-plan.md).
- PR-4 (A2 press feedback, A6 48 dp, A7) and PR-5 (long translations):
  [`2026-11-14-1-1-6-design-polish.md`](2026-11-14-1-1-6-design-polish.md).
- PR-6 (A20 top scrim): [`2026-11-15-a20-top-scrim-pr6.md`](2026-11-15-a20-top-scrim-pr6.md).

## Start condition and scope

- **The `app/` freeze holds until the owner tags `v1.1.5`** (hub #1789, #2918). Only docs/plans work is allowed now
  (#2484, #2478). This plan changes nothing in the app.
- **PR-7a and PR-7b start strictly after PR-6 is merged and after the 1.1.6 store build exists** (tag `v1.1.6`,
  `release.yml`). 1.1.6 must be in testing on its own, so a 1.1.7 regression cannot be mixed up with a 1.1.6 one.
- **In scope:** cancel for the save/export flow (`runExport` → `Exporter`), for both export paths and the overlay
  sound pre-render; cleanup on cancel; the export sheet replacing the stock dialog (A5 + the A16 dialog part).
- **Out of scope (named, with the consequence):**
  - The **insert-time conversion dialog** (`Downscaler.run`, stock dialog in `MainActivity.kt` near `:1554` today,
    and the tile copy dialog near `:1315`). The concept mentions it, but option A approved "1.1.7 = cancellable
    export", and `Downscaler` has its own Transformer and its own temp files. The owner will see: in 1.1.7 the
    "convert inserted video" window still has no cancel button. Recommendation: a follow-up task after 1.1.7 that
    reuses `ExportRun` and `ExportProgressSheet` from this plan (small, because the building blocks then exist).
  - The second accent red `#FF3B4E` in `bg_round_button_accent.xml` (A16 colour part). The new sheet uses
    `Sheet.ACCENT` (`#FE2C55`), so it adds no third red; unifying the button drawable is a colour change on the
    record screen and needs its own screenshot round.
  - No remaining-time estimate (concept: "Keine Restzeit-Schätzung").
- Line numbers below are from `main` at `b06047e`. PR-1 to PR-5 edit `MainActivity.kt` first, so **all
  `MainActivity.kt` lines will have moved when PR-7b starts** – find every place by its code snippet, not by number.

## 1. How export runs today, and where the cancel hook goes

Entry point: `MainActivity.runExport(targetHeight, micGain)` (`MainActivity.kt:2808-2867`). It opens a
`MaterialAlertDialogBuilder` dialog with `setCancelable(false)` (`:2809-2812`), disables the controls, **releases the
review players** (`:2815-2818`), creates a fresh `Exporter` (single use) and calls `Exporter.export(...)`
(`:2830`). `Exporter.export` (`Exporter.kt:116-164`) picks one of three routes:

| Route | When | Runs on | Code |
|-------|------|---------|------|
| **A. Overlay-sound pre-render, then Transformer** | `audioMix` not empty | `worker` thread, then main | `Exporter.kt:129-143` → `renderMixWav` (`:79-87`) → `OverlayAudioRenderer.decode` + `renderMix` → `main.post { transform(...) }` |
| **B. Media3 Transformer** | mic gain ≠ 1, captions, post overlays (watermark for free users!), scaling, or fast path not possible / failed | main (Transformer uses its own threads; progress polled every 400 ms on main) | `transform()` `Exporter.kt:166-249` |
| **C. Fast MediaMuxer concat** | none of the above and `VideoConcat.canFastConcat` | `worker` thread | `Exporter.kt:149-160` → `VideoConcat.concat` (`VideoConcat.kt:84-147`, loop in `copyTrack` `:150-177`) |

All successful routes end in `saveToGallery(outFile)` (`Exporter.kt:292-319`) on `worker`, then
`listener.onDone(uri)` on main. Note: route C is only reachable for Pro users (free exports always carry the
watermark post overlay, `MainActivity.kt:2803-2807`, which forces route B).

Today there is **no public cancel**. `Exporter.release()` (`:321-325`) cancels the Transformer and shuts the worker
down; it is called from `onDone`/`onError` and from `onDestroy` (`MainActivity.kt:3315`).

### Cancel hooks (PR-7a)

One run token per `Exporter`: a new pure class `ExportRun` (section 4) holds the phase and the cancelled flag
(thread-safe, `AtomicReference`). `Exporter` gets:

```kotlin
/** Stops the running export. Returns false when it is too late (saving to the gallery has started). */
fun cancel(): Boolean   // must be called on the main thread
```

`cancel()` calls `run.requestCancel()`; if accepted it cancels the Transformer (if any), stops the progress poll and
starts the cleanup (section 2) on `worker`. **After an accepted `cancel()`, the listener gets no further
`onProgress`/`onDone`/`onError` for this run** (every `main.post { listener… }` goes through a small
`deliver { }` helper that checks `run.isCancelled`). The caller resets its UI right after `cancel()` returns true.

| Route | Hook | Granularity |
|-------|------|-------------|
| A – pre-render | `renderMixWav(..., isCancelled: () -> Boolean = { false })` passes the check into `OverlayAudioRenderer.decodeFresh` (inside `while (!outputDone)`, `OverlayAudioRenderer.kt:80`) and `renderMix` (inside `while (frame < totalFrames)`, `:167`, one chunk = 100 ms of audio). On cancel they throw `java.util.concurrent.CancellationException`; their existing `finally` blocks already release codec/extractor and close files. After the pre-render, `main.post { if (run.enter(TRANSCODING)) transform(...) }` – a cancel between pre-render and Transformer start never starts the Transformer. The review player's call `Exporter.renderMixWav(this, mixes, totalMs, wav)` (`MainActivity.kt:2632`) keeps the default `{ false }`, unchanged behaviour. | ≤ 100 ms audio per check (render), one codec buffer (decode) |
| B – Transformer | `transformer?.cancel()` on main (Media3 1.5.1 `Transformer.cancel()`, must be called on the thread the Transformer was built on = main here). `poll` already stops when `transformer == null` (`:242`). `onCompleted`/`onError` that race with the cancel are dropped by `deliver { }`; `onCompleted` additionally checks `run.enter(SAVING)` before `saveToGallery`, so a run cancelled in the last ms deletes its file instead of saving. | immediate |
| C – fast concat | `VideoConcat.concat(files, output, onProgress, isCancelled: () -> Boolean = { false })`; `copyTrack` checks it every sample (`:159-174`; a flag read per sample is negligible next to `readSampleData`) and throws `CancellationException`. The existing `finally { muxer.stop(); muxer.release() }` (`:142-145`) runs; `stop()` may throw on a half-written file and is already caught. **The fallback `catch (e: Exception)` at `Exporter.kt:156-158` must not re-encode after a cancel:** add `catch (e: CancellationException)` before it (CancellationException is an IllegalStateException, so the generic catch would otherwise start route B). | per sample |
| Saving | `saveToGallery` is **not** cancellable: `run.enter(SAVING)` closes the cancel window, the sheet disables the button (section 3). It copies a finished file and takes seconds. | – |

`release()` becomes `cancel(); worker.shutdown()` (still single-use, still called from `onDestroy`), so leaving the
app during an export also cleans up.

**Rejected alternatives:**

- *`Thread.interrupt()` / `worker.shutdownNow()` for routes A and C:* `MediaExtractor`/`MediaCodec` calls do not
  react to interrupts, so the loop would keep running; and shutting the worker down mid-run would skip the cleanup
  that has to run on it. A flag checked inside the loops is deterministic and testable.
- *Kotlin coroutines `Job.cancel()`:* the project has no coroutines in the export path; adding them only for this
  is a larger rebuild than a flag.
- *Letting the cancel also stop `saveToGallery` mid-copy:* would need to delete a half-written `IS_PENDING`
  MediaStore row; it saves at most a few seconds. Not worth the risk of a broken gallery entry.

## 2. Cleanup on cancel (and two existing leaks)

| Artefact | Today | On cancel (PR-7a) |
|----------|-------|-------------------|
| Partial output `cacheDir/export_<ts>.mp4` | deleted only after a successful save; the startup sweep deletes `export_*` (`MainActivity.kt:286-287`) | deleted on `worker` after the route stopped (route C: after `muxer.release()`; route B: right after `Transformer.cancel()` returned – Media3 does not promise to delete it, so we always do). |
| Overlay mix WAV `cacheDir/ovl_mix_<ts>.wav` | **never deleted** – not on success, not on error, and the startup sweep does not match `ovl_mix_` (existing leak: one WAV per export with overlay sound, 48 kHz stereo = ~11 MB per minute) | deleted on cancel; **also deleted after `onCompleted`/`onError` of the Transformer** (fixes the leak). The sweep at app start also gets `ovl_mix_` (PR-7b, it is in `MainActivity.kt`). |
| Decode temp `cacheDir/ovl_pcm_<ts>.raw` (`OverlayAudioRenderer.kt:72`) | renamed to the `pcm_<key>.raw` cache on success | deleted when the decode is cancelled (it is incomplete and must not become the cache). The finished `pcm_<key>.raw`/`.meta` cache stays – it is a valid cache for the next export and the review player. |
| Pending MediaStore row | on a save failure the `IS_PENDING=1` row stays (existing gap, `Exporter.kt:304-309`) | not reached by cancel (saving is not cancellable); PR-7a still wraps the copy in `try { … } catch { resolver.delete(uri, null, null); throw }` because it is the same function. |
| Auto draft backup (`Drafts.save(auto = true)` via `saveProjectAsDraft(auto = true)`, `MainActivity.kt:2837-2839`; `Drafts.pruneAuto`, `Drafts.kt:43`) | written only in `onDone` | **nothing is written, nothing is pruned.** A cancelled export is not an export. The `else` branch of `onDone` that deletes segments, sounds, captions and texts when no backup is kept (`:2841-2850`) must never run on cancel – cancel has its own path in `MainActivity` (section 3) and `Exporter` never calls `onDone` after a cancel. |
| Session file (`persistSession`, `MainActivity.kt:2916`) | untouched by export | untouched – crash recovery keeps working during and after a cancel. |
| Segments, overlays, captions, texts | only read by the export | unchanged (concept: "Deine Aufnahme bleibt erhalten"). |
| Review players | released before the export (`:2815-2818`) | rebuilt exactly as in the `onError` path: `if (inReview) { buildPlayer(); main.post(playbackTicker) }` (`buildPlayer()` already rebuilds the overlay-sound player, `MainActivity.kt:2612`). PR-7b moves these lines into one `restoreAfterExport()` helper shared by `onError` and cancel. Risk named in the concept ("Vorschau nach dem Abbrechen schwarz") is checked by screenshot V4. |

Which files a run created is recorded in `ExportRun` (`run.track(file)`), and the cleanup deletes exactly those
(`ExportCleanup.toDelete`, section 4). Rejected: *sweeping `cacheDir` by prefix on cancel* – a second export started
right after the cancel would lose its own fresh files; the prefix sweep is only safe at app start.

## 3. UI: export sheet with progress bar and cancel (PR-7b)

New file `app/src/main/java/de/codinix/videoeditor/ExportProgressSheet.kt` (keeps the `runExport` diff small):

```kotlin
class ExportProgressSheet(ctx: Context, @StringRes progressRes: Int, onCancel: () -> Unit) {
    fun setProgress(percent: Int)      // bar + "%1$d %%" text
    fun setIndeterminate()             // pre-render phase (route A) until the first Transformer progress
    fun setSaving()                    // bar 100 %, cancel button disabled
    fun dismiss()
}
```

Built on `Sheet` (same dark, rounded surface with handle as every other BabaCut sheet → A16):

- Title `R.string.export_title` (existing). Below: Material `LinearProgressIndicator` (material 1.12.0 is already a
  dependency), determinate, `indicatorColor = Sheet.ACCENT`, track `Sheet.CARD`, 6 dp thick, rounded ends. Under it
  the existing text `export_running` / `export_running_mix` ("Assembling video… 42 %") with tabular figures
  (`fontFeatureSettings = "tnum"`, ui-cheatsheet "numbers jitter") so the line does not shift every 400 ms.
- Hint `export_cancel_hint` in the secondary text colour (`0xFFDDDDE2` as in `Sheet`), one line, wraps at large font.
- One full-width button **"Cancel"** using the existing string `R.string.cancel` (all 7 languages already exist).
  It is the same action button `Sheet.btn()` builds, **after PR-4**: ripple `#33FFFFFF` on the `CARD` background
  and `minHeight = 48 dp` (PR-4, `Sheet.kt:119-126` today). To reuse it instead of copying it, PR-7b moves `btn()`
  into a `Sheet.actionButton(ctx, label, primary)` companion function (the only `Sheet.kt` change; `Sheet` keeps
  calling it). Secondary (not accent) style: cancel is the non-primary action and must not compete with the red bar.
- `setSaving()`: button `isEnabled = false`, `alpha = 0.4f`; percentage shows 100 %. No new string.
- Dismissal: `setCancelable(true)` + `setCanceledOnTouchOutside(false)` + `behavior.isDraggable = false`.
  **Back key = cancel** (concept) through `setOnCancelListener { onCancel() }`; a tap outside or a swipe does
  nothing, so a stray touch cannot stop a long export. In the saving phase Back does nothing (`setCancelable(false)`).
- Motion: no custom animation. The progress bar animates with the platform animator, so with "remove animations"
  (animator duration scale 0) it jumps without motion (apple-design §14 reduced motion). The sheet slide-in is the
  existing `BottomSheetDialog` one.
- RTL: `supportsRtl="false"` stays; the sheet uses the same gravity for title and body (both from `Sheet`), so the fa
  mismatch of the stock dialog (audit round 2) disappears. Check in V3.

`MainActivity.runExport` (PR-7b) replaces the `MaterialAlertDialogBuilder` block with `ExportProgressSheet`, routes
`onProgress` to `sheet.setProgress`, and adds the cancel path:

```kotlin
onCancel = {
    if (ex.cancel()) {                 // false = saving already started; the button is disabled then anyway
        sheet.dismiss(); ex.release(); exporter = null
        setControlsEnabled(true)
        restoreAfterExport()           // shared with onError: players + ticker
        Toast.makeText(this, R.string.export_cancelled, Toast.LENGTH_SHORT).show()
    }
}
```

No confirmation dialog for cancel: nothing is lost (apple-design §16 Agency: confirm only destructive,
irreversible actions). The "Export cancelled" note is a short `Toast` (status feedback, §16 "four kinds of
feedback"). Rejected: showing it in the review status pill – the playback ticker overwrites that pill every frame,
so it would need a hold timer in `MainActivity.kt`; and the record screen has no equivalent place.

### New strings (all 7 `strings.xml`, #2532)

`StringsConsistencyTest` already fails the build when a key is missing in one of the seven files. Two keys; the
button reuses `cancel`. Tone follows the existing impersonal wording (`backup_kept`).

| Key | values (EN) | de | tr | es | fr | fa | ar |
|-----|-------------|----|----|----|----|----|----|
| `export_cancel_hint` | Your recording and edits stay as they are. | Aufnahme und Bearbeitung bleiben erhalten. | Kayıt ve düzenlemeler olduğu gibi kalır. | La grabación y la edición se conservan. | L\'enregistrement et les modifications sont conservés. | ضبط و ویرایش‌ها حفظ می‌شوند. | يُحتفظ بالتسجيل والتعديلات. |
| `export_cancelled` | Export cancelled | Export abgebrochen | Dışa aktarma iptal edildi | Exportación cancelada | Export annulé | خروجی گرفتن لغو شد | تم إلغاء التصدير |

fa and ar wording: owner check in V3 (the owner reads fa).

## 4. JVM-testable pure logic (PR-7a)

Pattern from `TranscodePolicy` (`TranscodePolicy.kt` + `TranscodePolicyTest.kt`): Android-free objects next to the
Android code, unit tests in `app/src/test/java/de/codinix/videoeditor/`, no Robolectric.

New `ExportRun.kt` (no Android imports):

```kotlin
class ExportRun {
    enum class Phase { STARTING, PRE_RENDER, CONCAT, TRANSCODING, SAVING, DONE, FAILED, CANCELLED }
    val phase: Phase
    val isCancelled: Boolean
    /** Move to [next]; false if the run was cancelled or already ended (the caller stops). */
    fun enter(next: Phase): Boolean
    /** True if the cancel was accepted (any phase before SAVING). */
    fun requestCancel(): Boolean
    fun track(file: java.io.File)           // temp files this run created
    fun tracked(): List<java.io.File>
}
object ExportCleanup {
    /** Files to delete after a cancel/error/success: tracked temps, never a segment or a pcm_ cache file. */
    fun toDelete(run: ExportRun, outcome: ExportRun.Phase): List<java.io.File>
    /** Prefixes the startup sweep in MainActivity deletes: export_, ovl_mix_, ovl_pcm_, overlay_video_. */
    fun isStaleTemp(fileName: String): Boolean
}
```

Phase/cancel state lives in one `AtomicReference<Phase>` with `compareAndSet`, so `requestCancel()` on main and
`enter(SAVING)` on `worker` cannot both win.

New tests `ExportRunTest.kt`, `ExportCleanupTest.kt`:

- cancel accepted in STARTING, PRE_RENDER, CONCAT, TRANSCODING; refused in SAVING, DONE, FAILED, CANCELLED;
- `enter()` returns false after a cancel (pre-render → transcoding hand-off, onCompleted → saving);
- a second `requestCancel()` returns false (idempotent; Back + button both fire);
- race: 1 000 iterations of `requestCancel()` and `enter(SAVING)` on two threads – exactly one wins every time;
- `toDelete` after cancel = output + WAV + partial `ovl_pcm_`, after success = WAV only (output is deleted by the
  existing save path), never a file outside the tracked list;
- `isStaleTemp`: true for `export_1.mp4`, `ovl_mix_1.wav`, `ovl_pcm_1.raw`, `overlay_video_1.mp4`; false for
  `pcm_abc.raw`, `pcm_abc.meta`, `review_ovl_3.wav`, segment names.

The loop hooks themselves (`MediaCodec`, `MediaMuxer`, Transformer) cannot run on the JVM; they are covered by the
device/emulator checks V1–V6.

## 5. PR cut, order and files

| PR | Content | Files | Starts | Parallel with |
|----|---------|-------|--------|---------------|
| **PR-7a** | Engine: `ExportRun`, `ExportCleanup`, `Exporter.cancel()`, hooks in routes A/B/C, cleanup incl. `ovl_mix_` leak and pending-row delete; no visible change | `Exporter.kt`, `VideoConcat.kt`, `OverlayAudioRenderer.kt`, new `ExportRun.kt`, new `ExportCleanup.kt`, new `ExportRunTest.kt`, new `ExportCleanupTest.kt` | after PR-6 is merged **and** the 1.1.6 store build (tag `v1.1.6`) exists | none (nothing else open on these files) |
| **PR-7b** | UI: export sheet, cancel path, `restoreAfterExport()`, startup sweep via `ExportCleanup.isStaleTemp`, strings, versionCode for 1.1.7 | new `ExportProgressSheet.kt`, `MainActivity.kt` (`runExport`, `onError` restore, startup sweep in `onCreate`), `Sheet.kt` (extract `actionButton`, no visual change), 7 × `strings.xml`, `app/build.gradle.kts` | after PR-7a is merged | none |

- **No overlap with the serial `MainActivity.kt` chain PR-1 → PR-3 → PR-4 → PR-5 (PR-6 does not touch the file):**
  PR-7a does not touch `MainActivity.kt` at all. PR-7b touches it, but only after PR-6 and the 1.1.6 store build,
  i.e. when every PR of the chain is merged. It is cut from a `main` that contains all of them. The PR-6 plan's
  remark that 1.1.7 "may start on `MainActivity.kt` in parallel with" PR-6 is not used: this brief requires the
  strict order, and the app freeze plus the 1.1.6 test round make the parallel start worthless anyway.
- **`Sheet.kt`** is edited by PR-1 (conditional) and PR-4 (close X, `btn()` ripple + 48 dp). PR-7b starts after both
  and only moves the PR-4 version of `btn()` into a companion function.
- **`strings.xml`** (7 files) is edited by PR-4 and possibly PR-5; PR-7b appends two keys after them.
- Why two PRs: PR-7a is the risky part (threads, files) and is reviewable without UI; PR-7b is the visible part with
  the screenshot round. Rejected: one PR (one large diff mixing threading and UI); three PRs with the strings
  separate (a string without its UI fails nothing but leaves unused keys in a release).

## 6. versionCode

Rule settled in task `bff4da85` (hub #2917, #2914; supersedes "1.1.7 / versionCode 86" in #2753 and in the
design-polish plan): one code per Play upload. 1.1.6 starts at 85 (PR-1); every further 1.1.6 tester build is +1.
**1.1.7 takes the next free code after the last 1.1.6 tester build, not a fixed 86.**

- **PR-7b** sets `versionName = "1.1.7"` and `versionCode = <last uploaded 1.1.6 code> + 1` in
  `app/build.gradle.kts`. PR-7a does not bump (no tester build is cut from it).
- How the developer finds the number: the `versionCode` on `main` when PR-7b starts (it holds the last 1.1.6 bump),
  cross-checked with the `v1.1.6*` tags. If an emergency 1.1.6 hotfix is uploaded while PR-7b is open, rebase and
  take the next code again – Play rejects a code that was already uploaded.

## 7. Screenshot verification (PR-7b, emulator API 35 as in the audit, plus the owner's phone)

Same device setup and naming as the audit (`api35-<lang>-NN-<name>.png`). Files go to the PR, not to
`docs/design/screenshots/` (that folder belongs to the audit).

- **V1 – route B (Transformer), en:** record 3 segments, Save → 1080p. Screenshot at ~30 %
  (`api35-en-15-export-sheet.png`): BabaCut sheet, red bar, "… 30 %", hint, 48 dp Cancel button. Press-and-hold
  Cancel: ripple visible (PR-4 parity). Release → sheet gone, controls enabled, Toast "Export cancelled"
  (`api35-en-15c-export-cancelled.png`).
- **V2 – files:** after V1, `adb shell run-as de.codinix.babacut ls cache` shows no `export_*`, no `ovl_mix_*`;
  `adb shell content query --uri content://media/external/video/media --projection _display_name` shows no new
  `VideoEditor_*` entry; Drafts has no new auto backup.
- **V3 – languages and large font:** V1 screenshot in de, fa, ar, fr and en at font scale 1.3. fa/ar: title, bar
  and hint aligned the same way (audit round-2 mismatch gone), hint not cut off; owner checks the fa wording.
- **V4 – review after cancel:** cancel from the review screen; review video plays again (not black), with overlay
  sound if a video overlay with sound exists (route A). Screenshot `api35-en-09b-review-after-cancel.png`.
- **V5 – route A + C:** add an overlay video with sound → Save → cancel during "Rendering video (with overlay
  sound)…" (indeterminate bar). Pro build (route C, "Original"): cancel during the fast concat – check in logcat
  that no "Schnelles Zusammenfügen fehlgeschlagen, versuche Neukodierung" line appears (no fallback after cancel).
- **V6 – Back key and late cancel:** Back during export = cancel (same result as V1). Tap outside / swipe down:
  nothing happens. A full export to the end still saves, shows the existing "saved" sheet and the auto backup
  (regression check of `onDone`); the Cancel button is greyed out in the last seconds (saving).
- Design check (Design-Abnahme) against apple-design §1 (press feedback on touch-down), §14 (reduced motion: run V1
  with "remove animations" on), §16 (never trap the user; four kinds of feedback) and ui-cheatsheet (tabular
  numbers, button press scale/ripple).

## Acceptance criteria

- Cancel stops all three routes within about one second on the emulator; no error sheet appears after a cancel.
- No `export_*`, `ovl_mix_*` or partial `ovl_pcm_*` file and no gallery entry remains after a cancel; the overlay mix
  WAV is also gone after a successful export.
- Segments, overlays, captions, texts, session and drafts are unchanged after a cancel; no auto backup is written.
- The export window is a BabaCut sheet with a determinate red bar, the 48 dp Cancel button has PR-4 press feedback,
  Back cancels, outside tap/swipe does not.
- `ExportRunTest`, `ExportCleanupTest`, `StringsConsistencyTest` and all existing tests green; CI green.
- `versionName "1.1.7"`, `versionCode` = next free code after the last 1.1.6 tester build.

## Effort and risk

M (concept: 1–2 days): PR-7a ~1 day incl. tests, PR-7b ~0.5 day + screenshot round. Main risk: a cancel racing with
`onCompleted` (covered by `ExportRun` compare-and-set and the race test) and a black review after cancel (V4).
