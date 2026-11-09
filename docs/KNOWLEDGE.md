# Knowledge – BabaCut

English digest; details in [HANDOVER.md](../HANDOVER.md) §2–3 (German).

## Build
- Needs Android SDK (platform 36, build-tools 36.0.0), NDK 27.2.12479018, CMake 3.22.1, Java 17.
- `git submodule update --init --recursive` (whisper.cpp v1.9.4) before building.
- `./gradlew assembleDebug`; CI build takes ~4.5 min. `android.suppressUnsupportedCompileSdk=36` with AGP 8.7.3.
- PR CI (`.github/workflows/pr.yml`, since 2026-11-09): on `pull_request` to `main` → `assembleDebug`, then
  `lintDebug` with `continue-on-error` (report artifact `lint-report`, outcome in the job summary). Permissions
  `contents: read`, Gradle cache read-only, no release/tag/`build-logs` push. Lint is non-blocking at workflow level
  (not via `abortOnError=false` in Gradle), so `lintVitalRelease` in `release.yml` keeps blocking store builds.
- 16 KB page-size linker flags are set (Play requirement).
- Bump `versionCode` (+1) for every build that goes to testers; current 81 / 1.1.3.

## Release
- Tag `vX.Y.Z` → `release.yml` builds AAB + APK signed with the upload key (secrets `UPLOAD_KEYSTORE_B64`,
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`). Upload the AAB to the Play Console manually.
- The owner's phone has the Play (internal test) build. GitHub debug APKs have another signature and cannot be
  installed over it → test builds go as AAB to the internal test.
- Failed build logs are force-pushed to branch `build-logs` (`build.log`).

## Code map (app/src/main/java/de/codinix/videoeditor/)
- `MainActivity.kt` – recording, review, dialogs, Pro gates, persistence (very large).
- `gl/CompositorProcessor.kt` – OpenGL ES2 CameraX effect; preview equals recording.
- `overlay/` – overlay models, gestures, mosaic, text rendering.
- `whisper/` – Whisper engine (JNI in `app/src/main/cpp`), model manager, captions, styles, emoji dictionary.
- `Exporter.kt`, `VideoConcat.kt`, `OverlayAudioRenderer.kt`, `Loudness.kt` – export and audio mix.
- `Downscaler.kt` – insert-time transcoding; `TimelineRemap.kt` – remap captions/audio after cuts.
- `Drafts.kt`, `CrashLog.kt` – drafts and crash recovery; `Pro.kt` – billing.
- Package id `de.codinix.babacut`, Kotlin namespace stays `de.codinix.videoeditor`.

## Website
- `docs/` is GitHub Pages (CNAME babacut.io), static HTML with DE/EN/FA switch.
- Jekyll is active (no `.nojekyll`); Markdown in `docs/` would be published unless listed in `docs/_config.yml`.

## Working with the owner
- Read [FINETUNING.md](FINETUNING.md) before talking to the owner: plain German, no jargon, he dictates,
  "erst reden, dann bauen", a "Bitte prüfen" checklist per build, every string in all 7 languages.
- Any new Markdown file in `docs/` must be added to `exclude` in `docs/_config.yml`, or it is published on babacut.io.

## Repo inventory facts (2026-11-09)
- No GitHub issues exist; the owner reports bugs via screenshots in chat.
- Branch `build-logs` is written by CI only (force-push of the last failing `build.log`); not a work branch.
- PR #2 was squash-merged from a branch stacked on PR #1, so PR #1's commits are not ancestors of `main` and its
  diff against `main` is now deletions only. Check `git diff main <branch>` before assuming an open PR still adds work.
