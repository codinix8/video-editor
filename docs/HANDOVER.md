# Handover: video-editor

Full handbook (German): [`/HANDOVER.md`](../HANDOVER.md); §9 there holds the same state in German. Project map: [AI_NATION.md](AI_NATION.md).

## Current state

_As of 2026-11-22 (task d04f27db). `git ls-remote --tags origin` was re-checked on this date._

- BabaCut: Android editor for 9:16 videos ("edit while recording"). `app/build.gradle.kts` holds versionCode 84 / versionName 1.1.5.
- The newest tag on the remote is still `v1.1.4` (82). `v1.1.5` is not tagged, so `app/` stays frozen. The owner decided on
  2026-11-14 (decision d44fe515, option B) that the agent tags `v1.1.5` on the checked `main` state; this has not happened yet.
  Cause is unknown (open question; the tag starts `release.yml` and creates a public GitHub release).
- 1.1.6 is planned and merged as plans only ([plans/](plans/)): PR-1, (PR-2), PR-3, PR-4, PR-5, PR-6; 1.1.7 = cancellable export.
- Open question: two PR-6 plans (full-screen scrim vs XML scrim); the planner must choose one (see DECISIONS.md).
- CI: `ci.yml` runs build, JVM unit tests and lint on every push and PR. UI, recording, export and the caption path are tested by hand only.
- Play: first production request rejected 2026-10-25; the second request waits for 1.1.5 in the closed test (draft: PLAY_PRODUCTION_APPLICATION.md).

## Next steps

1. Tag `v1.1.5` on the checked `main` state (owner decision d44fe515), then internal and closed test.
2. Read off Z1-Z7 and submit the production request (owner).
3. Choose a PR-6 plan, then implement 1.1.6 (versionCode 85 = first 1.1.6 store build).
4. Owner-side: imprint data for the website, `MainActivity.kt` split proposal ([MAINACTIVITY_SPLIT.md](MAINACTIVITY_SPLIT.md)), `build-test-lint` as required check.

Open questions (not invented): why the `v1.1.5` tag is missing since 2026-11-14; no separate CONCEPT.md exists, the concept lives in HANDOVER §1 and §4.

## Log

The log is in monthly files under `docs/handover/` (newest first; not part of the start-up reading).
New entries: a change fragment in docs/changes/ – the hub folds it into the month file.

- [2026-11](handover/2026-11.md)
