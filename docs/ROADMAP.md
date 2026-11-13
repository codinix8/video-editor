# Roadmap – BabaCut

Build history: README "Stand" table. Source of the items below: HANDOVER.md §5–7 (2026-11-03) and onboarding.

## Done
Finished items: [`docs/DONE.md`](DONE.md) (append-only log; do not add "Done" items here).

## Now
- [ ] Re-apply for Play production access from ~2026-11-09. Draft updated to 1.1.5 / versionCode 84 (2026-11-13,
      task f16621ce): `docs/PLAY_PRODUCTION_APPLICATION.md` with a German owner runbook (Console paths for Z1–Z7,
      confirmations K1–K8, answers already known from the record). Owner reads the values only after 1.1.5 is in
      the closed test "Closed Beta" (owner choice 2a5ce05a); nothing submitted.
- [ ] Ship 1.1.5 to closed testers (owner scope "A + B + D", 2026-11-11): A = localised error texts (task 6f0c1893,
      merged), B = release notes (Play "What's new" draft in 7 languages: `docs/RELEASE_NOTES_1_1_5.md`,
      task 9c582082, PR #21; FA/AR wait for the owner's proofreading; nothing uploaded), D = localised crash report labels (task a0f44077, merged via PR #18 / 2babaec;
      PR #17 superseded). Owner tags `v1.1.5`, internal test on
      his phone (checklist: `docs/TEST_CHECKLIST_1_1_5.md`, task 6e3ee84a), then the closed test "Closed Beta" with the release notes from `docs/CLOSED_TEST_1_1_4_PLAN.md` (B).
      Which versions reached the closed track is unverified (owner: Play Console).
- [ ] Owner decision `concept.approval` on the 1.1.6 design polish ([concept 2026-11-13](concepts/2026-11-13-design-polish-1-1-6.md), task 277c2965; planner sends the decision). Proposal: improvement 5 (screenshots in CI) right away without a version bump; 1.1.6 / versionCode 85 = insets + press feedback + long texts/fa spacing; 1.1.7 / versionCode 86 = cancellable export. Bug parts A1 and A19/A3 may start without approval (owner rule: visible bugs are fixed directly). 1.1.5 (84) stays untouched.
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
      tasks (hub ids `d3bf1d65`, `6e53b97d`, `5b1362db`, `067dc3c3`, `5cf6cd1c`), waiting for the owner's release (concept with mockups 2026-11-13, see "Now"; screenshots of all main views incl. export progress done by tasks fa0e1afc, a8ef1268; only A3 drag hint still open in the audit) – edge-to-edge insets, pressed feedback + 48dp targets, long-translation
      overflow (incl. fa/ar label-value spacing, A19), cancellable export, app screenshots in CI. Website findings W1–W5 fit the website relaunch.

## Later
- [ ] Website relaunch ("live instead of post-editing"), comparison page, blog, glossary, press page,
      structured data / llms.txt.
- [ ] Whistle (Cactus) as fast free recognition (no FA/TR/AR).
- [ ] On-device person segmentation (MediaPipe) with temporal smoothing.
- [ ] iOS port (estimated 60–70 % of the original effort).
- [ ] Split `MainActivity.kt` (3326 lines) per `docs/MAINACTIVITY_SPLIT.md`: steps 1–5 (dead code, pure caption/gain
      math + tests, `ProjectState`, `SessionStore`, drafts) (prerequisite met 2026-11-11: CI and unit tests on `main`); steps 6–12 later.
