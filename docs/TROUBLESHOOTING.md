# Troubleshooting – BabaCut (video-editor)

Incidents and how to recognise and fix them. One section per entry: symptom, cause, check, fix, prevention.
Entries derived by the hub from finished incident tasks are checked against the source task and the project
record before they land here; the source line keeps the hub knowledge item id.

## 2026-11-13 – Onboarding blocked: `hub_request_decision` 403 and a branch name held by an open PR

- **Area:** AI Nation process (hub permissions, git branches); no app code.
- **Symptom:** The onboarding task (integrate video-editor into AI Nation, CONCEPT 9.5) could not finish its last
  DoD item: filing the `project.team` decision with `hub_request_decision` returned HTTP 403 (forbidden) for the
  role `video-editor-entwickler` (2026-11-08). When the task was re-assigned and resumed (2026-11-09), its original
  branch `agent/4e8e8c10-onboarding-integrate-video-editor-into-a` was still held by the open, superseded
  [PR #1](https://github.com/codinix8/video-editor/pull/1).
- **Cause:** 403 – a missing permission for the developer role on the hub side (platform, not this repo); it was
  gone when the task resumed. Branch – re-assigned tasks derive the same branch name from the task id and title,
  and PR #1 (content already on `main` via PR #2) was never closed.
- **Check:** for the 403, retry `hub_request_decision` once and look in the hub knowledge (type/incident) for a
  platform-wide permission issue before working around it. For the branch, run
  `git ls-remote origin | grep <task-id-prefix>` and `gh pr list --head <branch>` before the first push.
- **Fix:** the request was filed once the 403 was gone (hub decision id `7f90c967`, decider Gremium;
  recommendation: lean team, kits core + qa, until Play production access – see `docs/DECISIONS.md`
  2026-11-09). The resumed work went to a new branch `agent/4e8e8c10-project-team-decision` (PR #4, merged)
  instead of force-pushing over PR #1's branch. All other DoD items (state summary, AI Nation section in
  `CLAUDE.md`, `docs/AI_NATION.md` map, CI status) had already been delivered via PR #2.
- **Prevention:** check `git ls-remote` before pushing a re-assigned task and pick a new branch name rather than
  force-pushing; never rewrite a branch another open PR points to. Report hub 403s as platform issues instead of
  rebuilding workarounds in the repo. Closing the superseded PR #1 is still the owner's call.
- **Watch:** Projektmanager.
- **Source:** hub task `4e8e8c10-2efb-40bb-ad2f-54c0970fd156` (video-editor), knowledge item 1797.
  Corrections to the hub draft: the symptom was the task title only and the cause was "not stated"; both were
  filled in from `docs/DECISIONS.md`, `docs/AI_NATION.md` and the PR history (PR #1, #4).

## 2026-11-10 – CI ran no tests and lint was green only because errors were swallowed

- **Area:** CI (`.github/workflows/ci.yml`), lint, unit tests; no app behaviour.
- **Symptom:** before 2026-11-10 only pull requests to `main` were checked (`pr.yml`: build + lint), lint ran
  with `continue-on-error`, so its green step said nothing, and pushes to work branches had no check at all. When
  lint was made blocking, the first run (CI run 42887488201) failed on pre-existing errors.
  (Hub draft: the symptom was the task title "Set up GitHub Actions CI (lint, tests, build)"; this was a setup
  task, not a production incident.)
- **Cause:** lint had never been blocking, so errors piled up unnoticed; the PR-only trigger meant branches were
  first checked when a PR was opened.
- **Check:** `gh run list --workflow ci.yml --branch <branch>` shows a `build-test-lint` run for every push; in
  `ci.yml` no step may carry `continue-on-error: true`. `app/lint-baseline.xml` should only shrink.
- **Fix (task 40c40718, PR stacked on #6/#5, merged):** `pr.yml` renamed to `ci.yml` (workflow `CI`, job
  `build-test-lint`): push to every branch except `build-logs`, `pull_request` to `main`, manual; `assembleDebug`,
  `testDebugUnitTest` and `lintDebug` all blocking, `contents: read`, publishes nothing. Pre-existing lint errors
  frozen in `app/lint-baseline.xml`; new errors fail CI. Correction to the hub draft: the "15" errors were an
  undercount – the baseline held 16, task 0eecba17 cut it to 9 (see `docs/KNOWN_ISSUES.md`). The legacy `pr.yml`
  was deleted later (task cea0d561), so `ci.yml` is the only PR check.
- **Prevention:** keep every CI step blocking; when a check is introduced non-blocking, add a dated task to make
  it blocking. Fix baseline errors in their own tasks instead of growing the baseline. Making `build-test-lint` a
  required check in the GitHub branch settings is an owner step (`CONTRIBUTING.md`).
- **Watch:** Projektmanager.
- **Source:** hub task `40c40718-d677-4da8-a1ea-61af9cc8fe11` (video-editor), knowledge item 2255. Record:
  `docs/DECISIONS.md` 2026-11-10 "CI on every push and pull request".

## 2026-11-09 – No unit tests in the repo; first tests landed via a different PR than planned

- **Area:** tests (`app/src/test`), CI, PR flow.
- **Symptom:** the repo had zero automated tests; every change was only checked on the owner's phone. Later the
  PR with the first tests (#6) was sent back by the owner, although its code was already on `main`.
  (Hub draft: the symptom was the task title; this was a setup task, not a production incident.)
- **Cause:** no test setup existed. `Downscaler` creates a main-thread `Handler` when the class loads, so it
  cannot be tested on the JVM directly. PR #6 was stacked on PR #5 and was itself stacked under PR #12, so its
  commits reached `main` with the squash merge of PR #12 (1eb2c6c) and PR #6 became redundant.
- **Check:** `./gradlew testDebugUnitTest` locally, or the `unit-test-report` artifact of the `build-test-lint`
  run on `main`. Before reworking a returned PR: `git log origin/main --oneline -- <files of the PR>` to see
  whether its code is already on `main`.
- **Fix (task b9d022fb):** pure rule extracted into `TranscodePolicy.reason(…)` (Downscaler delegates); plain
  JUnit 4 tests `TimelineRemapTest` (16), `TranscodePolicyTest` (16), `MediaFormatConstantsTest` (1);
  `testDebugUnitTest` blocking in CI. Close-out: no new code, PR #6 reduced to a docs close-out; `main` CI run
  43002550851: 54 tests (incl. the tests added by task 40c40718), 0 failures.
- **Prevention:** keep logic that needs no Android classes in small objects that JVM tests can reach; add a test
  with every bug fix in such logic. Do not stack a new task on another open PR (`docs/AI_NATION.md`, record rule 3).
- **Watch:** Projektmanager.
- **Source:** hub task `b9d022fb-d012-4004-9bbe-ee0b6ca3ac2e` (video-editor), knowledge item 2257. Record:
  `docs/DECISIONS.md` 2026-11-09 "First JVM unit tests" and 2026-11-11 "Task b9d022fb closed out".

## 2026-11-11 – Many agent PRs returned with "Conflict with main"

- **Area:** merge, docs (project record), CI/protected paths.
- **Symptom:** 11 merges/reviews came back within 24 h, mostly "Conflict with main" on the same record files,
  plus workflow PRs (#5, #7 on `.github/workflows/`) that waited for the owner's merge and went stale.
- **Cause:** every agent PR rewrote the same shared lines of about six record files (state summaries, "next
  versionCode", CI paragraphs), so each merge put all other open PRs into conflict. "Keep both" resolutions left
  duplicates and stale paragraphs. Same pattern as platform incident R-163 (mokisfood).
- **Check:** `gh pr list --state open --json number,mergeable` – several `CONFLICTING` PRs that touch the same
  record lines are the signal; after a resolution, `git diff origin/main -- docs/ HANDOVER.md` shows duplicates.
  Watch how long PRs on `.github/workflows/*` wait for the owner merge.
- **Fix (task 9167d767):** record rules in `docs/AI_NATION.md` "Writing the record without conflicts";
  `merge=union` in `.gitattributes` only for the append-only `docs/DECISIONS.md` (tested with `git merge-tree`);
  duplicate/stale entries removed; root cause in `docs/KNOWLEDGE.md`. PR #7 (`build.yml`) was later merged by the
  owner and `pr.yml` deleted (task cea0d561). A second round (inserting at the same top line) was fixed by task
  5d8b5cdc (append at the end of `docs/DONE.md`, also `merge=union`); since pipeline v18 tasks write their own
  change fragment in `docs/changes/`.
- **Prevention:** follow the record rules: append, do not rewrite; one home per volatile fact; short-lived
  branches; workflow changes in their own small PR; check for duplicates after every conflict resolution.
  Correction to the hub draft: "prevention" repeated the check text; this is the actual rule set.
- **Watch:** Projektmanager.
- **Source:** hub task `9167d767-420f-4af2-a05f-4cfb9ac7ed74` (video-editor), knowledge item 2259. Record:
  `docs/DECISIONS.md` 2026-11-11 "Conflict-safe project record", `docs/KNOWLEDGE.md` "Record merge conflicts".

## 2026-11-11 – German error texts in every app language (BabaCut 1.1.4)

- **Area:** UI strings, localisation (all 7 `strings.xml`), error handling.
- **Symptom:** error messages showed German words in every app language, e.g. the fallback labels "Bild",
  "Umrechnung", "Umwandlung", "Video", "Spracherkennung", "Entwurf", "Kachelvideo", "Overlay-Video", "Keine
  Segmente" and German exception texts. (Hub draft: the symptom was the task title "localise remaining German error
  labels"; filled in from the record.)
- **Cause:** hard-coded German strings in `MainActivity.kt`, `WhisperEngine.kt`, `ModelManager.kt`, `Exporter.kt`
  and `OverlayAudioRenderer.kt`; the classes without a `Context` had no way to read string resources.
- **Check:** `StringsConsistencyTest` (added by task 6e3ee84a) fails when a key of `values/strings.xml` is
  missing in a locale or a listed German word appears in a non-German `strings.xml`. It does not scan Kotlin
  sources; for those, run `grep -rnE '"[^"]*(Fehler|fehlgeschlagen|Keine )' app/src/main/java | grep -v 'Log\.'`
  before a release. Expected hits on 2026-11-13: only the GL/EGL texts in `gl/` and one code comment.
- **Fix (task 6f0c1893, PR #15, versionCode 84 / 1.1.5):** 18 new string keys in all 7 `strings.xml`;
  `UserFacingException(R.string.x, args)` thrown by classes without a `Context`, turned into text by `ErrorText.of`
  with the activity context (in-app language applies); `ErrorTextTest`. Crash report labels followed in task
  a0f44077 (option D). Still German by design: GL/EGL texts (`GlUtil.kt`, `EglCore.kt`) and the debug-only
  "Ton-Kontrolle" toast. Phone check by the owner pending.
- **Prevention:** no user-visible literal strings in Kotlin; new texts go into all 7 `strings.xml` (project
  rule). Extend the word list of `StringsConsistencyTest` whenever a new German leak is fixed.
- **Watch:** Projektmanager.
- **Source:** hub task `6f0c1893-6e11-4dd9-bc4e-05365c9ed4a0` (video-editor), knowledge item 2261. Record:
  `docs/DECISIONS.md` 2026-11-11 "Error texts from resources via UserFacingException", `docs/DONE.md`.
