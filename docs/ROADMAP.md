# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
- [x] 2026-11-10 Closed-test update planned (proposal only, no code) → `docs/CLOSED_TEST_1_1_4_PLAN.md`
      (task d444acae, revised after review of PR #10).
- [x] 2026-11-09 PR CI: `.github/workflows/pr.yml` runs `assembleDebug` + `lintDebug` on every PR to `main`,
      no release/tag (task 0b65e2ce). Lint is non-blocking until a baseline exists; unit tests follow.
- [x] 2026-11-10 Proposal for splitting `MainActivity.kt` → `docs/MAINACTIVITY_SPLIT.md` (12 move-only steps,
      no code; task de9795a4). Waiting for the owner's go on steps 1–5.
- [x] 2026-11-10 Vazirmatn web font self-hosted on babacut.io; no Google Fonts request left (task 6c203aca).
- [x] 2026-11-09 Decision `project.team` requested from the hub (task 4e8e8c10; waiting for Gremium/owner).
- [x] 2026-11-09 Deep-integration inventory and backlog → `docs/AI_NATION.md` "State and backlog"; 7 tasks proposed
      to the hub (waiting for owner release, task 9299e3fd).
- [x] 2026-11-08 Owner profile harvested → `docs/FINETUNING.md` + hub fine-tuning store (task 9b8fbac3).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09. Draft ready (2026-11-10, task 036afa99, revised):
      `docs/PLAY_PRODUCTION_APPLICATION.md`; waiting for owner figures Z1–Z7 and his `[BESTÄTIGEN]` answers.
      Owner choice: apply only after the 1.1.4 polish (error messages in 7 languages + release notes) is in the
      closed test.
- [ ] Localise the remaining German error labels (7 languages) + Play release notes – not in the tagged
      `v1.1.4` (versionCode 82); next build takes versionCode 83. Plan: `docs/CLOSED_TEST_1_1_4_PLAN.md` (open PR).
- [ ] Re-apply for Play production access from ~2026-11-09 (concrete answers with numbers/quotes).
- [ ] Ship error messages in all 7 languages to closed testers as 1.1.5 (versionCode 83; name to be confirmed).
      Owner chose A + B, then the production application (hub decision 2a5ce05a). Plan: `docs/CLOSED_TEST_1_1_4_PLAN.md`
      (A: ~15 German error texts incl. ModelManager download and Exporter/MediaStore errors; B: version + Play release
      notes in 7 languages; D crash-report labels optional, not approved; C caption clamp deferred). `v1.1.4` (82,
      2026-11-10) shipped other fixes. Which versions reached the closed track is unverified (owner: Play Console).
- [ ] Imprint data from the owner → finish website imprint/privacy (legal).

## Next
- [ ] Lint baseline (`app/lint-baseline.xml`), then make `lintDebug` in `pr.yml` blocking (remove `continue-on-error`).
- [ ] Add `testDebugUnitTest` to `pr.yml` together with the first unit tests (prerequisite for auto-merge).
- [ ] First unit tests for pure logic (TimelineRemap, Captions chunking, Loudness, transcodeReason).
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
