# Knowledge – BabaCut

English digest; details in [HANDOVER.md](../HANDOVER.md) §2–3 (German).

## Build
- Needs Android SDK (platform 36, build-tools 36.0.0), NDK 27.2.12479018, CMake 3.22.1, Java 17.
- `git submodule update --init --recursive` (whisper.cpp v1.9.4) before building.
- `./gradlew assembleDebug`; CI build takes ~4.5 min. `android.suppressUnsupportedCompileSdk=36` with AGP 8.7.3.
- 16 KB page-size linker flags are set (Play requirement).
- Bump `versionCode` (+1) for every build that goes to testers; current 81 / 1.1.3.

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
- `Downscaler.kt` – insert-time transcoding; `TimelineRemap.kt` – remap captions/audio after cuts.
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
- Any new Markdown file in `docs/` must be added to `exclude` in `docs/_config.yml`, or it is published on babacut.io.

## Repo inventory facts (2026-11-09)
- No GitHub issues exist; the owner reports bugs via screenshots in chat.
- Branch `build-logs` is written by CI only (force-push of the last failing `build.log`); not a work branch.
- PR #2 was squash-merged from a branch stacked on PR #1, so PR #1's commits are not ancestors of `main` and its
  diff against `main` is now deletions only. Check `git diff main <branch>` before assuming an open PR still adds work.
