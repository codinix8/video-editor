# Knowledge – BabaCut

English digest; details in [HANDOVER.md](../HANDOVER.md) §2–3 (German).

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
- Bump `versionCode` (+1) for every build that goes to testers; current 83 / 1.1.4 (82 = owner build 1.1.4 on main, 83 from task b9d022fb, on `main` via PR #12); next is 84.

## Release
- Tag `vX.Y.Z` → `release.yml` builds AAB + APK signed with the upload key (secrets `UPLOAD_KEYSTORE_B64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`). Upload the AAB to the Play Console manually.
- The owner's phone has the Play (internal test) build. GitHub debug APKs have another signature and cannot be
  installed over it → test builds go as AAB to the internal test.
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
- `Drafts.kt`, `CrashLog.kt` – drafts and crash recovery; `Pro.kt` – billing.
- Package id `de.codinix.babacut`, Kotlin namespace stays `de.codinix.videoeditor`.

## Website
- `docs/` is GitHub Pages (CNAME babacut.io), static HTML with DE/EN/FA switch.
- Web font: Vazirmatn is self-hosted (`docs/assets/fonts/`, `@font-face` at the top of `style.css`). Do not add
  external font/CSS links (Google Fonts = German warning-letter risk). Upstream woff2 files:
  `raw.githubusercontent.com/rastikerdar/vazirmatn/v33.003/fonts/webfonts/`.
- Jekyll is active (no `.nojekyll`); Markdown in `docs/` would be published unless listed in `docs/_config.yml`.

## Working with the owner
- Read [FINETUNING.md](FINETUNING.md) before talking to the owner: plain German, no jargon, he dictates,
  "erst reden, dann bauen", a "Bitte prüfen" checklist per build, every string in all 7 languages.
- FINETUNING.md is a dated mirror of `hub_finetuning` (last sync 2026-11-11, 63 insights). Before relying on it,
  re-run `hub_finetuning` and compare count/dates; the hub wins on conflicts.
- Any new Markdown file in `docs/` must be added to `exclude` in `docs/_config.yml`, or it is published on babacut.io.

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

## Repo inventory facts (2026-11-09)
- No GitHub issues exist; the owner reports bugs via screenshots in chat.
- Branch `build-logs` is written by CI only (force-push of the last failing `build.log`); not a work branch.
- PR #2 was squash-merged from a branch stacked on PR #1, so PR #1's commits are not ancestors of `main` and its
  diff against `main` is now deletions only. Check `git diff main <branch>` before assuming an open PR still adds work.
