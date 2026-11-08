# AI Nation – project map for BabaCut (codinix8/video-editor)

Onboarded into AI Nation on 2026-11-08 (task 4e8e8c10). The project's own rules ([CLAUDE.md](../CLAUDE.md),
[HANDOVER.md](../HANDOVER.md) §8) stay in force and take precedence.

## Project record map

| Record part   | File | Notes |
|---------------|------|-------|
| Handover      | [`/HANDOVER.md`](../HANDOVER.md) | Existing, German, complete handbook (state 2026-11-03). Not renamed. |
| Concept       | [`/HANDOVER.md`](../HANDOVER.md) §1 + §4 | Purpose, target group, feature scope, Pro limits. No separate CONCEPT.md needed. |
| Roadmap       | [`docs/ROADMAP.md`](ROADMAP.md) | **New.** Next steps; history stays in the README "Stand" table. |
| Decisions     | [`docs/DECISIONS.md`](DECISIONS.md) | **New.** Seeded with decisions found in HANDOVER/README/commits. |
| Knowledge     | [`docs/KNOWLEDGE.md`](KNOWLEDGE.md) | **New.** Build/release facts and gotchas (English digest of HANDOVER §2–3). |
| Known issues  | [`docs/KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | **New.** Open bugs and risks. |
| Status table  | [`/README.md`](../README.md) "Stand" | Existing, German, per-step build history. |
| Repo rules    | [`/CLAUDE.md`](../CLAUDE.md) | **New.** Project rules + AI Nation section. |

`docs/` is also the public website (GitHub Pages, babacut.io). The record files above are excluded from the site
via [`docs/_config.yml`](_config.yml).

## State summary (2026-11-08)

- **Purpose:** BabaCut, an Android editor for short 9:16 videos – "edit while recording, not afterwards":
  segment recording, image/text/video overlays, tile mode, mosaic, on-device Whisper captions, cut in review,
  export up to 4K. No account, no upload, no ads.
- **Users:** creators; the Persian community is a key segment (Farsi UI, 7 languages: EN, DE, TR, ES, FR, FA, AR).
  The owner has no programming experience and tests on his phone (Xiaomi, Android 16) via the Play internal test.
- **Business:** BabaCut Pro subscription via Google Play Billing (`babacut_pro`: monthly 2.99 €, annual 19.99 € with
  7-day trial). Free tier limited to 1080p with watermark. No revenue yet – app is not in production.
- **Stack:** Kotlin, CameraX, Media3 (Transformer/ExoPlayer), OpenGL ES 2 compositor, whisper.cpp (JNI, submodule
  v1.9.4, NDK 27), Play Billing 8. compile/targetSdk 36, minSdk 26, AGP 8.7.3, Java 17. ~34 Kotlin files;
  `MainActivity.kt` ~3300 lines. Version 1.1.3 (versionCode 81).
- **Run/build:** `./gradlew assembleDebug` (needs Android SDK, NDK 27.2.12479018, CMake 3.22.1, submodules).
- **Deploy:** `build.yml` – every push to `main` builds a debug APK and a GitHub release `build-N`.
  `release.yml` – tag `v*` builds signed AAB + release APK (upload key from GitHub secrets); the AAB is uploaded
  manually to the Play Console. Website: GitHub Pages from `docs/` (babacut.io).
- **Play status:** internal test 1.1.3; closed test running; first production request rejected on 2026-10-25
  ("requires more testing"); re-apply from about **2026-11-09** with ≥12 testers for 14 days.
- **Open work:** see [ROADMAP.md](ROADMAP.md). **Risks:** see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## CI status

**There are no automated tests in this repository and none run in CI.** `build.yml` only compiles a debug APK on
pushes to `main`; it does not run on pull requests. `release.yml` only builds release artifacts. Auto-merge of
agent PRs therefore has no safety net. Recommended next step (ROADMAP): a PR workflow running
`./gradlew assembleDebug testDebugUnitTest lintDebug` plus first unit tests for pure logic (`TimelineRemap`,
`Captions` chunking, `Loudness`, `Downscaler.transcodeReason`).
