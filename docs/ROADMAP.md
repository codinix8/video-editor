# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
Finished items: [`docs/DONE.md`](DONE.md) (append-only log; do not add "Done" items here).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09. Draft ready (2026-11-10, task 036afa99, revised):
      `docs/PLAY_PRODUCTION_APPLICATION.md`; waiting for owner figures Z1–Z7 and his `[BESTÄTIGEN]` answers.
      Owner choice: apply only after the 1.1.4 polish (error messages in 7 languages + release notes) is in the
      closed test.
- [ ] Ship 1.1.5 to closed testers (owner scope "A + B + D", 2026-11-11): A = localised error texts (task 6f0c1893,
      merged), B = release notes (Play "What's new" draft in 7 languages: `docs/RELEASE_NOTES_1_1_5.md`,
      task 9c582082, PR #21; FA/AR wait for the owner's proofreading; nothing uploaded), D = localised crash report labels (task a0f44077, merged via PR #18 / 2babaec;
      PR #17 superseded). Owner tags `v1.1.5`, internal test on
      his phone (checklist: `docs/TEST_CHECKLIST_1_1_5.md`, task 6e3ee84a), then the closed test "Closed Beta" with the release notes from `docs/CLOSED_TEST_1_1_4_PLAN.md` (B).
      Which versions reached the closed track is unverified (owner: Play Console).
- [ ] Imprint data from the owner → finish website imprint/privacy (legal).

## Next
- [ ] Owner: make `build-test-lint` a required status check for `main` (GitHub settings, see `CONTRIBUTING.md`).
- [ ] More unit tests for pure logic: `Caption`/session JSON round-trip (needs a real `org.json` test dependency,
      the android.jar stub throws), Whisper hallucination filter (extract from `WhisperEngine` first, its companion
      loads the native library).
- [ ] Skip the public APK release for docs-only pushes to `main` (`paths-ignore`): open PR #7, protected path
      (`build.yml`), waits for the owner's merge.
- [x] 2026-11-11 Delete the legacy `.github/workflows/pr.yml` (duplicate of `ci.yml`; task cea0d561, owner merge).
- [ ] Email: DKIM for babacut.io; babacut.net redirect.
- [ ] Owner proofreads FA/AR texts; check screenshots per language for clipped buttons.
- [ ] Clarify caption drift on very long videos.
- [ ] Design audit 2026-11 follow-ups (task 9defaf40, `docs/design/DESIGN-AUDIT-2026-11.md`): top 5 proposed as
      tasks (hub ids `d3bf1d65`, `6e53b97d`, `5b1362db`, `067dc3c3`, `5cf6cd1c`), waiting for the owner's release – edge-to-edge insets, pressed feedback + 48dp targets, long-translation
      overflow (incl. fa/ar label-value spacing, A19), cancellable export, app screenshots in CI. Website findings W1–W5 fit the website relaunch.

## Later
- [ ] Website relaunch ("live instead of post-editing"), comparison page, blog, glossary, press page,
      structured data / llms.txt.
- [ ] Whistle (Cactus) as fast free recognition (no FA/TR/AR).
- [ ] On-device person segmentation (MediaPipe) with temporal smoothing.
- [ ] iOS port (estimated 60–70 % of the original effort).
- [ ] Split `MainActivity.kt` (3326 lines) per `docs/MAINACTIVITY_SPLIT.md`: steps 1–5 (dead code, pure caption/gain
      math + tests, `ProjectState`, `SessionStore`, drafts) (prerequisite met 2026-11-11: CI and unit tests on `main`); steps 6–12 later.
