# Decisions – BabaCut

Newest first. Entries before 2026-11-08 are reconstructed from HANDOVER.md, README and commit history.

## 2026-11-09 – First JVM unit tests (task b9d022fb)
- Pure decision of `Downscaler.transcodeReason` extracted into `TranscodePolicy.reason(mime, width, height,
  rotation, fps, colorTransfer)`; Downscaler still reads the `MediaFormat` and delegates. Same rule and order
  (codec → >1080p → >31 fps → HDR), same texts. Extracted into a new object instead of testing `Downscaler`
  directly, because `Downscaler` creates a main-thread `Handler` on class load, which fails on the JVM.
- `TranscodePolicy` copies `COLOR_TRANSFER_ST2084`(6)/`COLOR_TRANSFER_HLG`(7) to stay Android-free;
  `MediaFormatConstantsTest` guards the copy against android.jar.
- Plain JUnit 4.13.2, no Robolectric/mocking library: `TimelineRemap` only needs data classes. No
  `unitTests.isReturnDefaultValues`, so an accidental Android call in a test fails loudly instead of returning 0.
- `testDebugUnitTest` is **blocking** in `pr.yml` (lint stays non-blocking).
- `versionCode` 81 → 82: app code was touched, and the merge to `main` produces a tester build.
- Branch stacked on PR #5 (`pr.yml` exists only there); merge PR #5 first.
- Tests document current behaviour, including two quirks found while writing them (see KNOWN_ISSUES).

## 2026-11-09 – Pull-request CI (task 0b65e2ce)
- New `.github/workflows/pr.yml` instead of adding `pull_request` to `build.yml`, so PR runs can never reach the
  release/`build-logs` steps. Permissions `contents: read`.
- Lint runs but is non-blocking (`continue-on-error` on the step), because the code was never linted and no Android
  SDK was available in the agent container to create a baseline. Chosen over `lint { abortOnError = false }` in
  Gradle so that `lintVitalRelease` still guards store builds. Follow-up: baseline, then blocking.
- No `versionCode` bump: no app code changed and PR runs produce no tester build.

## 2026-11-09 – Deep-integration inventory (task 9299e3fd)
- Docs-only inventory; no code changed. Result: `docs/AI_NATION.md` "State and backlog (2026-11-09)".
- Backlog order (proposed via `hub_propose_tasks`, owner releases): PR CI first (no tests, no PR checks today),
  then first unit tests, no release for docs-only pushes, self-hosted font, Play re-application draft, 1.1.4 plan,
  `MainActivity.kt` split proposal.
- PR #1 left untouched although it is superseded by the merged PR #2; closing it is the owner's call.

## 2026-11-08 – Owner profile harvested (task 9b8fbac3)
- 19 owner insights (3 `_cross`, 16 project) distilled from HANDOVER, README and commits; submitted to the hub and
  recorded in `docs/FINETUNING.md`. The file is excluded from the public site via `docs/_config.yml`.
- This branch is stacked on PR #1 (onboarding) because the record files only exist there; merge PR #1 first.

## 2026-11-08 – Onboarding into AI Nation
- Agents work on `agent/…` branches with pull requests; no direct pushes to `main`, no self-merge.
  Previously Claude pushed straight to `main` (HANDOVER §2). Note: every push to `main` creates a public GitHub
  release with an APK, so merges should be deliberate.
- Project record: existing root `HANDOVER.md` stays (not renamed); new record files live in `docs/` and are
  excluded from the GitHub Pages site via `docs/_config.yml`.
- **Pending: `project.team`.** The request could not be filed: `hub_request_decision` returned 403 (forbidden)
  for role `video-editor-entwickler`. Prepared draft (to be filed by a role with permission; owner text in German):
  1. Schlank – Entwickler + Code-Reviewer – kits: core, qa (lowest cost; tests/stability only).
  2. Launch-Team – kits: core, qa, copy, security, growth (Play approval, imprint/privacy, store copy, tests + PR CI).
  3. Voll – kits: core, ui, qa, assets, copy, security, growth, sales (fastest growth, highest cost before launch).
  Recommendation: option 2 – no subscription revenue without Play production access; it lowers legal risk
  (imprint, Google Fonts) and prepares Pro conversion at moderate cost. Predicted owner choice: option 2.
  If no decision: option 1.

## Earlier (reconstructed)
- **Monetisation:** BabaCut Pro subscription (Play Billing 8): monthly 2.99 €, annual 19.99 € with 7-day trial;
  free tier limited to 1080p and watermark; crown marks Pro features.
- **Privacy by design:** no account, no upload, no ads; Play data-safety "no data collected".
- **On-device captions** with whisper.cpp (Tiny/Base/Small as "Top/Ali/Baba"; Base default).
- **AI background removal rejected** for tile mode (step 3a); camera shown as framed tile instead.
- **Gradle build cache off** (it produced stale intermediates).
- **No R8/minify.**
- **Insert-time transcoding** of HEVC/AV1, >1080p, >30 fps and HDR videos to H.264 1080p (1.1.2).
- **Captions re-recognised automatically after cut** (1.1.3).
