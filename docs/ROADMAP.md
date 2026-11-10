# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
- [x] 2026-11-10 CI on every push and PR (task 40c40718): `pr.yml` → `ci.yml` (workflow `CI`, check
      `build-test-lint`), build + unit tests + lint all blocking; new tests for `Loudness`, caption chunking and
      `EmojiDict`; `CONTRIBUTING.md` with local commands; lint baseline for 15 pre-existing errors. 54 tests, 0 failures
      (CI run 42888492366). Includes PR #5 and #6 (stacked).
- [x] 2026-11-10 Proposal for splitting `MainActivity.kt` → `docs/MAINACTIVITY_SPLIT.md` (12 move-only steps,
      no code; task de9795a4). Waiting for the owner's go on steps 1–5.
- [x] 2026-11-10 Vazirmatn web font self-hosted on babacut.io; no Google Fonts request left (task 6c203aca).
- [x] 2026-11-09 First JVM unit tests (task b9d022fb): 33 JUnit tests in `app/src/test` for `TimelineRemap` and
      `TranscodePolicy` (rule behind `Downscaler.transcodeReason`); `pr.yml` runs `testDebugUnitTest` (blocking).
- [x] 2026-11-09 PR CI: `.github/workflows/pr.yml` runs `assembleDebug` + `lintDebug` on every PR to `main`,
      no release/tag (task 0b65e2ce). Lint is non-blocking until a baseline exists; unit tests follow.
- [x] 2026-11-09 Decision `project.team` requested from the hub (task 4e8e8c10; waiting for Gremium/owner).
- [x] 2026-11-09 Deep-integration inventory and backlog → `docs/AI_NATION.md` "State and backlog"; 7 tasks proposed
      to the hub (waiting for owner release, task 9299e3fd).
- [x] 2026-11-08 Owner profile harvested → `docs/FINETUNING.md` + hub fine-tuning store (task 9b8fbac3).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09 (concrete answers with numbers/quotes).
- [ ] Ship an update to closed testers between test day 7 and 10 (e.g. 1.1.4 with small fixes).
- [ ] Imprint data from the owner → finish website imprint/privacy (legal).

## Next
- [ ] Owner: make `build-test-lint` a required status check for `main` (GitHub settings, see `CONTRIBUTING.md`).
- [ ] More unit tests for pure logic: `Caption`/session JSON round-trip (needs a real `org.json` test dependency,
      the android.jar stub throws), Whisper hallucination filter (extract from `WhisperEngine` first, its companion
      loads the native library).
- [ ] Skip the public APK release for docs-only pushes to `main` (`paths-ignore`). *(proposed 2026-11-09)*
- [ ] Email: DKIM for babacut.io; babacut.net redirect.
- [ ] Owner proofreads FA/AR texts; check screenshots per language for clipped buttons.
- [ ] Clarify caption drift on very long videos.

## Later
- [ ] Website relaunch ("live instead of post-editing"), comparison page, blog, glossary, press page,
      structured data / llms.txt.
- [ ] Whistle (Cactus) as fast free recognition (no FA/TR/AR).
- [ ] On-device person segmentation (MediaPipe) with temporal smoothing.
- [ ] iOS port (estimated 60–70 % of the original effort).
- [ ] Split `MainActivity.kt` (3326 lines) per `docs/MAINACTIVITY_SPLIT.md`: steps 1–5 (dead code, pure caption/gain
      math + tests, `ProjectState`, `SessionStore`, drafts) after PR CI (#5) and unit tests (#6); steps 6–12 later.
