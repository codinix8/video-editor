# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
- [x] 2026-11-11 Fix A: remaining German error labels and exception texts localised in all 7 languages (18 new keys,
      `UserFacingException`/`ErrorText` + `ErrorTextTest`); version 1.1.5 (task 6f0c1893). Not tagged.
- [x] 2026-11-11 Cause of the returned merges found and fixed in the repo (task 9167d767): every PR rewrote the same
      record lines → conflicts and duplicate entries. Conflict-safe record rules in `docs/AI_NATION.md`, union merge
      for `docs/DECISIONS.md`, duplicates/stale entries removed (ROADMAP, KNOWLEDGE, KNOWN_ISSUES, AI_NATION).
- [x] 2026-11-10 `docs/FINETUNING.md` synced with the fine-tuning hub: 63 insights (17 BabaCut, 46 `_cross`; re-synced 2026-11-11),
      grouped by area, newest first, 3 superseded items marked (task 9b560127).

- [x] 2026-11-11 Task b9d022fb closed out: its code (`TranscodePolicy`, `TimelineRemapTest`, `TranscodePolicyTest`,
      `MediaFormatConstantsTest`) reached `main` via PR #12 (1eb2c6c), not via PR #6. `main` CI run 43002550851:
      54 tests, 0 failures. PR #6 now carries only this docs close-out (branch re-based on `main` by a no-force `-s ours` merge).
- [x] 2026-11-10 CI on every push and PR (task 40c40718): `pr.yml` → `ci.yml` (workflow `CI`, check
      `build-test-lint`), build + unit tests + lint all blocking; new tests for `Loudness`, caption chunking and
      `EmojiDict`; `CONTRIBUTING.md` with local commands; lint baseline for 15 pre-existing errors. 54 tests, 0 failures
      (CI run 42888492366). Includes PR #5 and #6 (stacked).
- [x] 2026-11-09 No public APK release for docs-only pushes: `paths-ignore` (`docs/**`, `**/*.md`) in `build.yml`
      (task f075de7b; PR #7 brought up to date with `main` again on 2026-11-11). Verify on the next docs-only
      merge to `main` that no new `build-N` release appears.
- [x] 2026-11-10 Closed-test update planned (proposal only, no code) → `docs/CLOSED_TEST_1_1_4_PLAN.md`
      (task d444acae, revised after review of PR #10).
- [x] 2026-11-09 PR CI: `.github/workflows/pr.yml` runs `assembleDebug` + `lintDebug` on every PR to `main`,
      no release/tag (task 0b65e2ce). Lint is non-blocking until a baseline exists; unit tests follow.
- [x] 2026-11-10 Proposal for splitting `MainActivity.kt` → `docs/MAINACTIVITY_SPLIT.md` (12 move-only steps,
      no code; task de9795a4). Waiting for the owner's go on steps 1–5.
- [x] 2026-11-10 Vazirmatn web font self-hosted on babacut.io; no Google Fonts request left (task 6c203aca).
- [x] 2026-11-09 First JVM unit tests (task b9d022fb): 33 JUnit tests in `app/src/test` for `TimelineRemap` and
      `TranscodePolicy` (rule behind `Downscaler.transcodeReason`); `pr.yml` runs `testDebugUnitTest` (blocking).
- [x] 2026-11-09 Decision `project.team` requested from the hub (task 4e8e8c10; waiting for Gremium/owner).
- [x] 2026-11-09 Deep-integration inventory and backlog → `docs/AI_NATION.md` "State and backlog"; 7 tasks proposed
      to the hub (waiting for owner release, task 9299e3fd).
- [x] 2026-11-08 Owner profile harvested → `docs/FINETUNING.md` + hub fine-tuning store (task 9b8fbac3).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09. Draft ready (2026-11-10, task 036afa99, revised):
      `docs/PLAY_PRODUCTION_APPLICATION.md`; waiting for owner figures Z1–Z7 and his `[BESTÄTIGEN]` answers.
      Owner choice: apply only after the 1.1.4 polish (error messages in 7 languages + release notes) is in the
      closed test.
- [ ] Ship 1.1.5 to closed testers: owner merges the 1.1.5 PR (task 6f0c1893), tags `v1.1.5`, internal test on
      his phone, then the closed test "Closed Beta" with the release notes from `docs/CLOSED_TEST_1_1_4_PLAN.md` (B).
      Which versions reached the closed track is unverified (owner: Play Console).
- [ ] Imprint data from the owner → finish website imprint/privacy (legal).

## Next
- [ ] Owner: make `build-test-lint` a required status check for `main` (GitHub settings, see `CONTRIBUTING.md`).
- [ ] More unit tests for pure logic: `Caption`/session JSON round-trip (needs a real `org.json` test dependency,
      the android.jar stub throws), Whisper hallucination filter (extract from `WhisperEngine` first, its companion
      loads the native library).
- [ ] Skip the public APK release for docs-only pushes to `main` (`paths-ignore`): open PR #7, protected path
      (`build.yml`), waits for the owner's merge.
- [ ] Delete the legacy `.github/workflows/pr.yml` (duplicate of `ci.yml`; protected path, owner merge).
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
      math + tests, `ProjectState`, `SessionStore`, drafts) (prerequisite met 2026-11-11: CI and unit tests on `main`); steps 6–12 later.
