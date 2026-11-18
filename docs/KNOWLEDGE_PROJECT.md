# Project knowledge – BabaCut (curated)

English digest; details in [HANDOVER.md](../HANDOVER.md) §2–3 (German). Moved here verbatim from `docs/KNOWLEDGE.md`
on 2026-11-18 (task 70e3e60d) because `KNOWLEDGE.md` became the hub-generated extract (line budget 120).
New durable project facts go here (newest section at the end, with tags) and are reported to the hub.

## Build
- Needs Android SDK (platform 36, build-tools 36.0.0), NDK 27.2.12479018, CMake 3.22.1, Java 17.
- `git submodule update --init --recursive` (whisper.cpp v1.9.4) before building.
- `./gradlew assembleDebug`; CI build takes ~4.5 min. `android.suppressUnsupportedCompileSdk=36` with AGP 8.7.3.
- CI (`.github/workflows/ci.yml`, workflow `CI`, job/check name `build-test-lint`; since 2026-11-10, was `pr.yml`):
  on every push (except branch `build-logs`), every `pull_request` to `main` and manually → `assembleDebug`, then
  `testDebugUnitTest` and `lintDebug` (both run after a successful build, both blocking; artifacts
  `unit-test-report`, `lint-report`; outcomes in the job summary). Permissions `contents: read`; only runs on `main`
  write the Gradle cache; no release/tag/`build-logs` push. Lint blocks on errors not listed in `app/lint-baseline.xml`
  (15 pre-existing errors, 2026-11-10). A green step with `continue-on-error` says nothing about the result: PR #6's
  lint looked green but had 15 errors. A baseline can be built from the CI artifact `lint-report`
  (`lint-results-debug.xml`: keep Error/Fatal issues, make paths relative to `app/`). Renaming the job changes the check name: update the branch-protection rule too.
- The runner token can read check-runs and job steps (`gh api repos/codinix8/video-editor/commits/<sha>/check-runs`,
  `.../actions/runs/<id>/jobs`), so an agent can verify its own CI run after pushing.
- Unit tests: `./gradlew testDebugUnitTest` (JUnit 4, `app/src/test/java/de/codinix/videoeditor/`). Keep tested
  logic Android-free (pattern: `TranscodePolicy` decides, `Downscaler` reads `MediaFormat`). The android.jar stub
  throws on any call (incl. `org.json`), so tests must not touch Android classes beyond compile-time constants.
  `ci.yml` runs them blocking after `assembleDebug`; report artifact `unit-test-report`. Contributor guide with
  local commands: `CONTRIBUTING.md` (repo root). Classes that only reference `org.json` inside methods (e.g.
  `Caption`) are testable as long as the test does not call those methods.
- Without an Android SDK (agent container) the pure tests can still run: download Temurin JDK 17 + kotlinc 2.0.21
  + junit/hamcrest/json jars, stub `android.graphics.Bitmap` and `overlay.TextRenderer` (needed by
  `OverlayModel.kt`), compile the sources with the tests and run `org.junit.runner.JUnitCore`.
- `ci.yml` is the only PR check (legacy `pr.yml` deleted 2026-11-11, task cea0d561). `lintVitalRelease` in `release.yml` blocks store builds.
- 16 KB page-size linker flags are set (Play requirement).
- Bump `versionCode` (+1) for every build that goes to testers; current 84 / 1.1.5 (task 6f0c1893, error-text localisation; 82 = tag `v1.1.4`, 83 = PR #12); next is 85.

## Release
- Tag `vX.Y.Z` → `release.yml` builds AAB + APK signed with the upload key (secrets `UPLOAD_KEYSTORE_B64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`). Upload the AAB to the Play Console manually.
- The owner's phone has the Play (internal test) build. GitHub debug APKs have another signature and cannot be
  installed over it → test builds go as AAB to the internal test.
- `build.yml` skips docs-only pushes (`paths-ignore: docs/**`, `**/*.md`). To get a debug APK anyway, start
  "Build APK" manually (Actions → Run workflow).
- Failed build logs are force-pushed to branch `build-logs` (`build.log`).

## Code map (app/src/main/java/de/codinix/videoeditor/)
- `MainActivity.kt` – recording, review, dialogs, Pro gates, persistence (3326 lines; line-range map and split plan
  in `docs/MAINACTIVITY_SPLIT.md`). Shared state (`segments`, `overlayStore`, `mosaic`, `captions`, `audioHistory`)
  couples all areas; `persistSession()` has 32 call sites. Private `radioList()` there is dead code.
- `gl/CompositorProcessor.kt` – OpenGL ES2 CameraX effect; preview equals recording.
- `overlay/` – overlay models, gestures, mosaic, text rendering.
- `whisper/` – Whisper engine (JNI in `app/src/main/cpp`), model manager, captions, styles, emoji dictionary.
- `Exporter.kt`, `VideoConcat.kt`, `OverlayAudioRenderer.kt`, `Loudness.kt` – export and audio mix.
- `Downscaler.kt` – insert-time transcoding (rule in `TranscodePolicy.kt`, unit-tested); `TimelineRemap.kt` – remap captions/audio after cuts.
- `UserFacingException.kt` – error texts for the UI: throw `UserFacingException(R.string.x, args)` where no Context
  exists, show via `ErrorText.of` (`errorText(e, fallbackRes)` in MainActivity/Exporter). Never put German literals
  into toasts/sheets; every new text goes into all 7 `strings.xml`.
- `Drafts.kt`, `CrashLog.kt` – drafts and crash recovery; `Pro.kt` – billing.
- Package id `de.codinix.babacut`, Kotlin namespace stays `de.codinix.videoeditor`.

## Website
- `docs/` is GitHub Pages (CNAME babacut.io), static HTML with DE/EN/FA switch.
- Web font: Vazirmatn is self-hosted (`docs/assets/fonts/`, `@font-face` at the top of `style.css`). Do not add
  external font/CSS links (Google Fonts = German warning-letter risk). Upstream woff2 files:
  `raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/webfonts/`.
- Jekyll is active (no `.nojekyll`); Markdown in `docs/` would be published, so `docs/_config.yml` excludes every
  `*.md` by one glob (since 2026-11-13, task dd98d806; before: a per-file list).

## Working with the owner
- Read [FINETUNING.md](FINETUNING.md) before talking to the owner: plain German, no jargon, he dictates,
  "erst reden, dann bauen", a "Bitte prüfen" checklist per build, every string in all 7 languages.
- FINETUNING.md is a dated mirror of `hub_finetuning` (last sync 2026-11-11, 63 insights). Before relying on it,
  re-run `hub_finetuning` and compare count/dates; the hub wins on conflicts.
- New Markdown files in `docs/` need no `_config.yml` edit: the glob `"*.md"` keeps them off babacut.io (checked by
  `pages-check.yml`). A new public page must be HTML (task dd98d806).

## Record merge conflicts (2026-11-11, task 9167d767)
Tags: `scope/project` `area/merge` `area/review` `type/incident` `tech/git`
- Symptom: 11 merges/reviews returned in 24 h, mostly "Conflict with main" plus "protected area (`build.yml`,
  `pr.yml`): merge only by the owner".
- Cause: every agent PR edits the same record files (HANDOVER, AI_NATION, ROADMAP, DECISIONS, KNOWLEDGE,
  KNOWN_ISSUES) and rewrites shared lines (state summaries, "next versionCode", CI paragraphs). Each merge
  conflicts every other open PR; "keep both" or union resolutions leave duplicates and stale paragraphs. Workflow
  PRs wait for the owner, so they conflict again by the time he merges.
- Fix: rules in `docs/AI_NATION.md` "Writing the record without conflicts" (add don't rewrite, one home per
  volatile fact, short-lived branches, workflows in their own small PR, check for duplicates after a resolution);
  `docs/DECISIONS.md merge=union` in `.gitattributes`. `merge=union` is safe only for append-only files and only
  applies to local git merges; whether GitHub's merge check honours it is unverified.
- Platform counterpart: hub incident R-163 (same pattern in mokisfood-website).

## Record merge conflicts, round 2: same insertion point (2026-11-12, task 5d8b5cdc)
Tags: `scope/project` `area/merge` `area/docs` `area/review` `type/incident` `tech/git`
- Symptom: 10 merges/reviews returned in 24 h after the first fix (#14): "Conflict with main", "protected area,
  merge only by the owner" and re-reviews after a base merge.
- Cause: the #14 rule told every PR to add its entry *at the top* of ROADMAP "Done" and KNOWN_ISSUES "Resolved".
  Two branches that insert at the same line always conflict in git, however small the entries are (PR #16 vs #15).
  `merge=union` covered only DECISIONS.
- Fix: append-only `docs/DONE.md` (new bullets at the end, never edit old ones) with `merge=union`; ROADMAP and
  KNOWN_ISSUES keep only state that is edited in place; rule 6 "smallest record diff" in `docs/AI_NATION.md`.
  Verified with `git merge-tree`: 1 conflict without the attribute, 0 with it.
- Limits: `merge=union` works for local merges (the merger and agents run `git merge origin/main` locally); whether
  GitHub's own mergeability check honours it is unverified. Union is only safe for files whose lines are never
  edited. Workflow PRs (owner-only merge) and re-review after a base merge (hub R-130) are expected returns, not bugs.

## Repo inventory facts (2026-11-09)
- No GitHub issues exist; the owner reports bugs via screenshots in chat.
- Branch `build-logs` is written by CI only (force-push of the last failing `build.log`); not a work branch.
- PR #2 was squash-merged from a branch stacked on PR #1, so PR #1's commits are not ancestors of `main` and its
  diff against `main` is now deletions only. Check `git diff main <branch>` before assuming an open PR still adds work.

## Design skills and UI audits (2026-11-12, task 9defaf40)
Tags: `scope/project` `area/ui` `area/review` `type/howto`
- Project profile `app` in `codinix8/ai-nation` `spec/skills.yaml`; the runner places only `apple-design` and
  `ui-cheatsheet` for the developer role (and only for UI tasks). The full set can be read from
  `spec/skills/emilkowalski/<skill>/` in the hub repo with `gh api`.
- Agent containers have no Android SDK/emulator/Java. App screenshots work on GitHub Actions instead:
  `reactivecircus/android-emulator-runner@v2`, `google_apis` x86_64 API 35 (has ARM64 translation for the
  arm64-only APK), KVM udev rule, `-camera-back emulated`; `adb shell wm size 1170x2532` + `wm density 480` gives
  390×844 dp; per-app locale with `cmd locale set-app-locales de.codinix.babacut --locales fa-IR`; navigate by
  `uiautomator dump` + resource-id bounds. Use API 35+, API 34 hides edge-to-edge problems. Back key on a sheet
  with a focused EditText only closes the keyboard. Website screenshots work
  with the global Playwright (`/opt/tools/npm/lib/node_modules/playwright`, Chromium under `/opt/tools/pw-browsers/`).
- Android translations of web rules used in the audit: touch target 48dp, pressed state = ripple/state list,
  text in sp, motion via `ValueAnimator` (respects animator scale), insets via `ViewCompat.setOnApplyWindowInsetsListener`.

## Record, hub and tooling facts (2026-11-13, folded from change fragments)
Tags: `scope/project` `type/howto` `type/gotcha`
- **Incident, returned merges round 3:** symptom "Conflict with main" although all record entries go into fragments; cause a list in a shared config file that every task extends (`docs/_config.yml` `exclude:`) enforced by a CI check wanting one exact line per file; fix a glob plus a check on the built output (task dd98d806). Detect early: `git log --since=<24h> --format=%h -- <file>` per file over merged PRs; a non-record file touched by most PRs is the next hotspot. Rounds 1 and 2: hub items 2259, 2378. #type/incident #area/merge #area/docs #area/ci
- `git merge-tree --write-tree origin/main HEAD` (git ≥ 2.38) exits 1 and prints `CONFLICT` lines when the branch would conflict with `main`; no working tree touched, cheap pre-PR check. #area/merge
- Jekyll `exclude:` entries are `File.fnmatch` patterns without `FNM_PATHNAME`; `"*.md"` (quoted) also excludes Markdown in subfolders. #tech/jekyll
- Re-assigned tasks reuse the branch name derived from task id and title: check `git ls-remote origin` and `gh pr list --head <branch>` before the first push, pick a new name instead of force-pushing (`docs/TROUBLESHOOTING.md`). #area/process
- Knowledge sync: `hub_knowledge_sync` only reads entries outside the generated section, so send the hand-written part plus empty `BEGIN`/`END` markers; the returned `content_md` (~160 KB) lands in a tool-result file. The cloud agent container has `node` but no `python3`/`jq` (checked 2026-11-13): script with `node`. #area/docs #area/integration
- Hub-derived incident drafts for setup tasks (CI, first tests) carry the task title as "symptom" and "not stated" elsewhere; rebuild them from the DECISIONS entry of the source task. #area/docs
- Emulator screenshots in CI: API 30 `google_apis` x86_64 with ARM translation is ~3× slower than API 35 (hit the 60-min limit after 3 locales; give ≥ 90 min); API < 33 has no `cmd locale set-app-locales` (switch system locale via `adb root`, `persist.sys.locale`, zygote restart). A "Pixel Launcher isn't responding" dialog can swallow every tap of a run: `pm disable-user --user 0 com.google.android.apps.nexuslauncher` and `settings put global hide_error_dialogs 1` before installing, or press `android:id/aerr_wait`. Sheet buttons are clickable `TextView`s, not `Button`s: tap the last clickable TextView for the positive action. #area/tooling
- `input motionevent DOWN … MOVE … UP` scrubs instead of lifting a segment, so the drag-hint state (A3) cannot be scripted that way. Touch targets can be measured without code: `uiautomator dump` bounds divided by density/160. #area/tooling #area/ui
- `Exporter` has no public `cancel()`; only `release()` (`Exporter.kt:321-325`) cancels the Media3 `Transformer` and shuts the worker down. Cancellable export needs a new `Exporter.cancel()` (correction to audit A5). #area/export
- Playwright mockups in the cloud container: use the preinstalled `/opt/pw-browsers/chromium_headless_shell-1243` (`executablePath`); load local images via a written HTML file and `page.goto('file://…')`, not `setContent`. #area/tooling
- Answers already in the record for the production application: app id/category/contact, rejection 2026-10-25, tester sources and invite link, Console declarations (18+, no data collected, no ads), 7-language store listing, subscription prices, ~300-character field limit, version 1.1.5/84. Only Z1–Z7, K1–K8 and the language choice (DE/EN) remain open. #area/release

## Plans, review position and runner facts (2026-11-14, folded from change fragments)
Tags: `scope/project` `type/gotcha` `type/howto`
- Review position drift (A23, "0:13 / 0:12"): stored segment durations come from CameraX `recordedDurationNanos` (`MainActivity.kt:621`), while ExoPlayer positions run on the MP4 file timeline, which is slightly longer; `updateReviewPosition` (`:2722-2737`) adds them, `fmt()` (`:3270-3273`) truncates. The real-duration fix would touch drafts, export remap and overlay offsets: follow-up candidate after 1.1.6. #area/ui #type/knowledge
- The 52 × 25 dp "preview-sound" target in the round-3 dump is the 52 dp rail button half-hidden at the bottom of the scrolling rail (A21), not a size bug: scroll the rail before measuring. #area/ui #type/gotcha
- Views with a ripple draw it only when pressed and clickable; changing `bg_pill.xml` to a ripple leaves the non-clickable status pills unchanged. #area/ui
- `MainActivity.kt:3263-3264`: the comment "delete key becomes overlay play/pause while recording" is stale (the code always shows the backspace icon; `onDeletePressed` returns while recording); the plan has PR-4 remove it with the unused `vo` line. #area/ui
- Long-press on the scrub bar (A13) can be scripted with `input motionevent DOWN`, wait > 450 ms without moving (12 px slop), then `MOVE`, `screencap`, `UP`; moving early scrubs. **Unverified:** this is only proposed in the 1.1.6 design-polish plan and contradicts the earlier "A3 drag hint cannot be scripted" note above; PR-5 must verify it on a device. #area/tooling #area/mobile
- Runner: `gh pr create --head <branch>` (after `git push -u` the upstream is not always recorded in the agent container; hub item 2532). For a rework round do not assume `origin/<branch>` exists: fetch the branch or PR head explicitly and push `HEAD:refs/heads/<branch>` (hub item 2564). #area/process #type/gotcha
- Knowledge sync result (~170 KB) arrives as a tool-result file; extract `content_md` with `node` (no `jq`/`python3`). Procedure: "Record, hub and tooling facts" above. #area/docs

## CI screenshots, export and scrim facts (2026-11-15, folded from change fragments)
- A workflow with only `workflow_dispatch` cannot be run from a PR branch before it is on `main`; a temporary `pull_request` path trigger on its own file gives a run on the PR (remove it before the owner merge when the brief fixes the trigger list; afterwards `gh workflow run <file> --ref <branch>`). #area/ci #type/gotcha
- Node.js 20 deprecation annotation for the pinned v4 actions (forced onto Node 24, works); move pins to newer majors in a later chore together with `ci.yml`. #area/ci #type/chore
- Android emulator in GitHub Actions needs no third-party action: KVM via udev rule, `sdkmanager --install emulator "system-images;android-35;google_apis;x86_64"`, `avdmanager create avd -d pixel_6`, start `emulator -no-window -gpu swiftshader_indirect -no-snapshot` in the background, poll `getprop sys.boot_completed` (10-minute wall-clock deadline). Set `ANDROID_AVD_HOME` explicitly (e.g. `$HOME/.android/avd` via `$GITHUB_ENV`), otherwise `avdmanager` and `emulator` use different AVD folders and the emulator exits with "Unknown AVD name". Owner-path workflows are approved faster without third-party actions. #area/ci #type/howto
- `adb shell input text` can drop keys on a slow emulator IME: check the uiautomator dump for the full text, clear and retype, drop the picture if it still fails. #area/ci #type/gotcha
- `Exporter` route C (fast MediaMuxer concat) is reachable only for Pro users: free exports always carry the watermark post overlay, which forces the Media3 Transformer route. #area/export
- `Exporter.export` falls back to re-encoding on any `Exception` from the fast concat; `java.util.concurrent.CancellationException` is an `IllegalStateException` and would trigger that fallback unless caught first. #area/export
- BabaCut layouts: on record and review only `segmentBar` and `reviewBar` are anchored to the parent top; other top controls chain from them. A full-bleed change needs the top inset only on those two bars, but `gestureView`/`safeZone` map overlays by `frameAspect` relative to their own size, so a taller preview needs an overlay-placement check. #area/ui
- A UI scrim over the camera must sit directly above `PreviewView`/`PlayerView` in the XML, below `safeZone`, `gestureView` and `captionView`, so user overlays and captions (which are in the export) are not darkened. #area/ui
- `GradientDrawable(Orientation, IntArray)` spaces any number of colours evenly on all API levels; custom offsets (`setColors(int[], float[])`) need API 29 (minSdk 26): use more evenly spaced stops with eased alpha. #area/ui #tech/android
- versionCode: bump only for uploads to Play / tester builds; merges into `main` do not bump (rule 2026-11-14). #area/release
- The brief's fold list names a HANDOVER "log"; `HANDOVER.md` is a German handbook without a log, so the state goes into its §9 "Stand" lines and log entries into `docs/DONE.md`. A brief may name a fragment date that differs from the task date; the repo convention (`docs/changes/README.md`) wins. #area/docs

