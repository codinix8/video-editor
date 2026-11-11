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
| Owner profile | [`docs/FINETUNING.md`](FINETUNING.md) | **New (2026-11-08).** How the owner thinks, decides and gives feedback; mirrored in the hub fine-tuning store. |
| Proposals     | [`docs/MAINACTIVITY_SPLIT.md`](MAINACTIVITY_SPLIT.md) | **New (2026-11-10).** Plan for splitting `MainActivity.kt` in 12 move-only steps; waiting for the owner. |
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
  `MainActivity.kt` ~3300 lines. Version 1.1.4 (versionCode 82, tag `v1.1.4`, 2026-11-10).
- **Run/build:** `./gradlew assembleDebug` (needs Android SDK, NDK 27.2.12479018, CMake 3.22.1, submodules).
- **Deploy:** `build.yml` – every push to `main` builds a debug APK and a GitHub release `build-N`.
  `release.yml` – tag `v*` builds signed AAB + release APK (upload key from GitHub secrets); the AAB is uploaded
  manually to the Play Console. Website: GitHub Pages from `docs/` (babacut.io).
- **Play status:** internal test 1.1.3; closed test running; first production request rejected on 2026-10-25
  ("requires more testing"); re-apply from about **2026-11-09** with ≥12 testers for 14 days.
- **Open work:** see [ROADMAP.md](ROADMAP.md). **Risks:** see [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Team decision

`project.team` was requested on 2026-11-09 (hub id `7f90c967`, decider: Gremium; details in
[DECISIONS.md](DECISIONS.md)). Recommendation: lean team (kits: core, qa) until Play production access, then add
copy/security/ui. Until decided, the developer and code-reviewer roles work on released tasks.

## CI status

**There are no automated tests in this repository.** Since 2026-11-09 `pr.yml` builds every pull request to `main`
(`assembleDebug`) and runs `lintDebug` (non-blocking, no release). `build.yml` compiles and publishes a debug APK on
pushes to `main`. `release.yml` only builds release artifacts. Auto-merge of
agent PRs is still unsafe (build-only check). Recommended next step (ROADMAP): a lint baseline and
`testDebugUnitTest` in `pr.yml` plus first unit tests for pure logic (`TimelineRemap`,
`Captions` chunking, `Loudness`, `Downscaler.transcodeReason`).

## State and backlog (2026-11-09)

Inventory by task 9299e3fd (docs only, no code changed). Sources: `gh pr list`, `gh issue list`, `git ls-remote`,
`gh run list`, `gh release list`, the workflows, a grep for TODO/FIXME and the record files in `docs/`.

### What works
- As of 2026-11-09 the record said 1.1.3 (81) was on the Play internal and closed test (owner statement; `v1.1.4`/82
  tagged 2026-11-10, closed-track delivery unverified); all features in HANDOVER §4 are
  confirmed by the owner on his phone. 34 Kotlin files, ~8.7k lines (`MainActivity.kt` 3292 lines).
- CI builds are green: the last `Build APK` runs on `main` (2026-11-03, 2026-11-08) succeeded in ~2 min; latest
  debug release is `build-102`. The last store build `release-1.1.3-8` (tag `v1.1.3`, 2026-10-26) succeeded.
- GitHub Pages deploys babacut.io from `docs/` on every push to `main` (runs green).
- Code hygiene: no `TODO`/`FIXME`/`HACK` markers in `app/src`. All 7 `values*` string folders present.

### Open pull requests, issues and branches
| Item | State | Note |
|------|-------|------|
| [PR #1](https://github.com/codinix8/video-editor/pull/1) "Onboard BabaCut into AI Nation" (`agent/4e8e8c10-…`) | open | **Superseded:** its content reached `main` via the stacked [PR #2](https://github.com/codinix8/video-editor/pull/2) (merged 2026-11-08). Its diff against `main` now only *removes* lines (FINETUNING.md etc.). Do not merge; the owner can close it. Not touched by this task. |
| [PR #2](https://github.com/codinix8/video-editor/pull/2) "Harvest owner profile" | merged | – |
| Issues | none | No GitHub issues (open or closed). Bugs are reported via screenshots in chat. |
| Branch `build-logs` | CI-owned | Force-pushed by the workflows with the last failing `build.log` (last: build 94, 2026-10-07). Not a work branch. |
| Owner branches | none | No other branches from the owner's earlier single chats exist on the remote. |

### What is open (product)
See [ROADMAP.md](ROADMAP.md) "Now": Play production re-application (possible from **today, 2026-11-09**, ≥12 testers
for 14 days), an update for closed testers between test day 7 and 10, imprint data from the owner for the website.

### Risks
- **No tests, no PR CI** (details below) – regressions are found only on the owner's phone.
- ~~Website loads Google Fonts~~ – resolved 2026-11-10: Vazirmatn is self-hosted in `docs/assets/fonts/`
  (task 6c203aca; it was loaded by index, 404, support, imprint and privacy).
- **Imprint is a placeholder** – legal risk once the app is in production.
- **Every push to `main` publishes a public GitHub release with an APK** (`build.yml`), also for docs-only merges
  (e.g. `build-102` from PR #2). Merges should be deliberate; a `paths-ignore` for `docs/**` would avoid this.
- **Debug keystore with plaintext passwords committed** (`keystore/videoeditor.jks`, `app/build.gradle.kts`) –
  debug signature only; Play uses the upload key from GitHub secrets.
- **`MainActivity.kt` concentration** (3292 lines) – high change risk.
- No revenue until Play production access is granted.

### CI status
- `build.yml`: push to `main` + manual → `assembleDebug`, uploads APK, creates release `build-N`; on failure pushes
  `build.log` to branch `build-logs`. Does not run on pull requests.
- `pr.yml` (added 2026-11-09, task 0b65e2ce): `pull_request` to `main` → `assembleDebug` + `lintDebug`
  (lint `continue-on-error`, report artifact `lint-report`). Read-only permissions; no release, tag or `build-logs` push.
  Next: lint baseline → blocking lint; `testDebugUnitTest` once tests exist.
- `release.yml`: tag `v*` + manual → signed `bundleRelease assembleRelease`, release `release-<version>-N`.
- **No unit/instrumentation tests exist and none run.** Therefore agent PRs must not be auto-merged. The PR build
  check (backlog item 1) is done; unit tests are the next item.

### Proposed next tasks (submitted with `hub_propose_tasks`, waiting for owner release)
1. Add a pull-request CI workflow (build + lint) without publishing a release.
2. Add the first JVM unit tests for pure logic (`TimelineRemap`, `Downscaler.transcodeReason`) and run them in PR CI.
3. Skip the public APK release for docs-only pushes to `main` (`paths-ignore: docs/**`, `*.md`).
4. ~~Self-host the Vazirmatn web font on babacut.io (remove Google Fonts).~~ Done 2026-11-10 (task 6c203aca).
5. Prepare the Play production re-application text (German draft for the owner, with numbers from the closed test).
6. Plan the 1.1.4 closed-test update: collect small fixes from KNOWN_ISSUES into a "Bitte prüfen" list (proposal only).
   *Done 2026-11-10 (task d444acae): `docs/CLOSED_TEST_1_1_4_PLAN.md`. Owner chose A + B, then apply (hub decision
   2a5ce05a). Since the owner's `v1.1.4` (82) shipped other fixes, A + B go into the next version (83, proposed 1.1.5).*
7. Proposal (no code) for splitting `MainActivity.kt` into smaller units, for the owner to approve ("erst reden").
