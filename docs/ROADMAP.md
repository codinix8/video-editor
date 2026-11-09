# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
- [x] 2026-11-09 No public APK release for docs-only pushes: `paths-ignore` (`docs/**`, `**/*.md`) in `build.yml`
      (task f075de7b). Verify on the next docs-only merge to `main`.
- [x] 2026-11-09 Deep-integration inventory and backlog → `docs/AI_NATION.md` "State and backlog"; 7 tasks proposed
      to the hub (waiting for owner release, task 9299e3fd).
- [x] 2026-11-08 Owner profile harvested → `docs/FINETUNING.md` + hub fine-tuning store (task 9b8fbac3).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09 (concrete answers with numbers/quotes).
- [ ] Ship an update to closed testers between test day 7 and 10 (e.g. 1.1.4 with small fixes).
- [ ] Imprint data from the owner → finish website imprint/privacy (legal).

## Next
- [ ] CI for pull requests: build + unit tests + lint on every PR (prerequisite for auto-merge).
- [ ] First unit tests for pure logic (TimelineRemap, Captions chunking, Loudness, transcodeReason).
- [ ] Proposal (no code) for splitting `MainActivity.kt`. *(proposed 2026-11-09)*
- [ ] Self-host the Farsi web font instead of Google Fonts (legal risk).
- [ ] Email: DKIM for babacut.io; babacut.net redirect.
- [ ] Owner proofreads FA/AR texts; check screenshots per language for clipped buttons.
- [ ] Clarify caption drift on very long videos.

## Later
- [ ] Website relaunch ("live instead of post-editing"), comparison page, blog, glossary, press page,
      structured data / llms.txt.
- [ ] Whistle (Cactus) as fast free recognition (no FA/TR/AR).
- [ ] On-device person segmentation (MediaPipe) with temporal smoothing.
- [ ] iOS port (estimated 60–70 % of the original effort).
- [ ] Split `MainActivity.kt` (~3300 lines) into smaller units.
