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
| Done log      | [`docs/DONE.md`](DONE.md) | **New (2026-11-12).** Append-only log of finished work and resolved issues (was ROADMAP "Done" + KNOWN_ISSUES "Resolved"); new bullets at the end; `merge=union`. |
| Owner profile | [`docs/FINETUNING.md`](FINETUNING.md) | **New (2026-11-08), synced 2026-11-11.** How the owner thinks, decides and gives feedback; dated mirror of the hub fine-tuning store (project + `_cross`). |
| Proposals     | [`docs/MAINACTIVITY_SPLIT.md`](MAINACTIVITY_SPLIT.md) | **New (2026-11-10).** Plan for splitting `MainActivity.kt` in 12 move-only steps; waiting for the owner. |
| Play application | [`docs/PLAY_PRODUCTION_APPLICATION.md`](PLAY_PRODUCTION_APPLICATION.md) | **New (2026-11-10, revised).** German draft of the 2nd production-access request: Play Console figures Z1–Z7 + `[BESTÄTIGEN]` items the owner fills in; apply after the 1.1.4 polish. |
| Design audit  | [`docs/design/DESIGN-AUDIT-2026-11.md`](design/DESIGN-AUDIT-2026-11.md) | **New (2026-11-12).** Ranked UI findings (app + website) against the hub design skills, screenshots, top 5 proposals. |
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
- **Deploy:** `build.yml` – every push to `main` builds a debug APK and a GitHub release `build-N`, except
  docs-only pushes (`paths-ignore: docs/**`, `**/*.md`; since 2026-11-09, task f075de7b).
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

Since 2026-11-10 `ci.yml` (workflow `CI`, check `build-test-lint`; replaces `pr.yml` from 2026-11-09) runs on every
push (all branches except `build-logs`) and every pull request to `main`: `assembleDebug`, the JVM unit tests
(`testDebugUnitTest`; `TimelineRemap`, `TranscodePolicy`, `Loudness`, caption chunking, `EmojiDict`) and `lintDebug`,
all blocking, no release. How to run the checks locally: `CONTRIBUTING.md`. `build.yml` compiles and publishes a debug APK on
pushes to `main`. `release.yml` only builds release artifacts. `build-test-lint` can be
made a required check for auto-merge (owner step in the GitHub settings, see `CONTRIBUTING.md`). Coverage is still
thin: UI, recording, export and the native caption path are only tested by hand.

## Writing the record without conflicts (since 2026-11-11, task 9167d767)

Every agent PR edits the same record files, so each merge put the other open PRs into conflict with `main`
(11 returned merges/reviews in 24 h). Resolving with "keep both sides" then left duplicates and stale paragraphs
behind. Rules for every record change:

1. **Add, do not rewrite – at the end of an append-only file.** Finished work and resolved issues go into
   [`docs/DONE.md`](DONE.md) as one new bullet **at the end of the file**; decisions go into `docs/DECISIONS.md`
   as a new dated entry. Both files merge with `merge=union` (`.gitattributes`), so two PRs that both appended an
   entry merge without a conflict on a local `git merge origin/main`. Do not reword, re-date, reorder or delete
   another task's entry; to correct one, append a new entry that names the old one. (Until 2026-11-12 the rule was
   "add at the top of ROADMAP 'Done' / KNOWN_ISSUES 'Resolved'": two parallel PRs then always inserted at the same
   line and conflicted, e.g. PR #16 vs #15 – task 5d8b5cdc.)
2. **One home per volatile fact.** `versionCode`/`versionName` live in `app/build.gradle.kts` and the one line in
   `KNOWLEDGE.md` "Build"; the CI state lives in "CI status" above. Other files link there instead of restating
   numbers ("next build takes 84" was copied into six files and had to be edited in all of them on every bump).
3. **Short-lived branches.** Merge `origin/main` right before pushing and again before asking for a re-review; do
   not stack a new task on another open PR.
4. **Protected paths (`.github/workflows/*`) go in their own small PR**, with only the workflow and one DECISIONS
   entry, and the PR body names the owner merge. These PRs wait for the owner and go stale; a small diff keeps the
   re-merge trivial.
5. **After resolving a conflict, check for duplicates** (`git diff origin/main -- docs/ HANDOVER.md`): a union or
   "keep both" resolution of a rewritten line leaves both versions in the file.
6. **Smallest record diff.** A task's record change is normally one bullet at the end of `docs/DONE.md` (plus a
   DECISIONS entry if something was decided, and a KNOWLEDGE entry for a durable fact). Edit ROADMAP
   ("Now/Next/Later"), KNOWN_ISSUES ("Open issues", "Risks") or HANDOVER only when the task changes that state, and
   then only the lines of your own item. Every extra edit of a shared line is a future conflict.

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
- ~~No tests, no PR CI~~ – since 2026-11-10 `ci.yml` runs build, 54 JVM unit tests and lint on every push/PR;
  UI, recording and export are still only tested on the owner's phone.
- ~~Website loads Google Fonts~~ – resolved 2026-11-10: Vazirmatn is self-hosted in `docs/assets/fonts/`
  (task 6c203aca; it was loaded by index, 404, support, imprint and privacy).
- **Imprint is a placeholder** – legal risk once the app is in production.
- **Every push to `main` publishes a public GitHub release with an APK** (`build.yml`), also for docs-only merges
  (e.g. `build-102` from PR #2). Merges should be deliberate; a `paths-ignore` for `docs/**` would avoid this.
  *Update 2026-11-09:* fixed by `paths-ignore` in `build.yml` (task f075de7b); still to verify on the next docs-only merge.
- **Debug keystore with plaintext passwords committed** (`keystore/videoeditor.jks`, `app/build.gradle.kts`) –
  debug signature only; Play uses the upload key from GitHub secrets.
- **`MainActivity.kt` concentration** (3292 lines) – high change risk.
- No revenue until Play production access is granted.

### CI status
- `build.yml`: push to `main` (not for docs-only pushes: `paths-ignore` `docs/**`, `**/*.md`) + manual → `assembleDebug`, uploads APK, creates release `build-N`; on failure pushes
  `build.log` to branch `build-logs`. **Does not run on pull requests.**
- `build.yml`: push to `main` + manual → `assembleDebug`, uploads APK, creates release `build-N`; on failure pushes
  `build.log` to branch `build-logs`. Does not run on pull requests.
- `ci.yml` (task 40c40718, 2026-11-10; was `pr.yml` from tasks 0b65e2ce/b9d022fb): push to any branch except
  `build-logs` + `pull_request` to `main` + manual → `assembleDebug`, `testDebugUnitTest`, `lintDebug`, all blocking.
  Read-only permissions; no release, tag or `build-logs` push.
- `release.yml`: tag `v*` + manual → signed `bundleRelease assembleRelease`, release `release-<version>-N`.
- (Historical, 2026-11-09: no tests existed.) Since tasks b9d022fb/40c40718, JVM unit tests run in `ci.yml`; no
  instrumentation tests exist.

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
