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

## 2026-11-12 – Returned merges, round 2: parallel PRs inserted record entries at the same line

- **Area:** docs (project record), merge, review; no app code.
- **Symptom:** after the first conflict fix (task 9167d767, PR #14), 10 of the project's merges/reviews still came
  back within 24 h. Reasons: "Conflict with main", "protected area, merge only by the owner" and a required
  re-review after a base merge. Docs-only PRs could not be merged without a manual conflict resolution.
- **Cause:** the rule added by PR #14 told every PR to put its entry *at the top* of ROADMAP "Done" and
  KNOWN_ISSUES "Resolved". Two branches that insert at the same line always conflict in git, however small the
  entries are (verified: PR #16 head `e8e6f13` vs `main` `3fc36eb` → conflict in `KNOWN_ISSUES.md`; PR #16 vs
  #15). `merge=union` covered only `docs/DECISIONS.md` at that time. The other two return reasons are not
  repo-side: PRs touching protected paths (`.github/workflows/`) need the owner's merge, and a base merge after
  approval triggers a re-review by design (hub R-130).
- **Check:** before opening a PR, run `git fetch origin && git merge-tree --write-tree origin/main HEAD` (git ≥ 2.38;
  exit code 1 and `CONFLICT` lines mean a conflict). Flag any record rule that makes every task write at the same
  line. Count returned merges/reviews by reason: many "Conflict with main" returns on the same files point to a
  systemic cause, not to single PRs.
- **Fix (task 5d8b5cdc, PR #19, merged):** finished work and resolved issues moved into the append-only
  `docs/DONE.md` (new bullets at the end, old ones never edited) with `merge=union` in `.gitattributes`; ROADMAP
  keeps only Now/Next/Later and KNOWN_ISSUES only Open issues/Risks, changed in place; rule 6 "smallest record
  diff" in `docs/AI_NATION.md`. Verified with `git merge-tree`: two branches appending to `DONE.md` gave 1 conflict
  without the attribute, 0 with it. Since pipeline v18 each task writes its own change fragment in
  `docs/changes/`, so tasks no longer touch shared record lines at all.
- **Prevention:** write the record as a new fragment file (`docs/changes/`) or an append at the end of a
  `merge=union` file; never make a rule that inserts at a fixed line. Use `merge=union` only for files whose lines
  are never edited (not ROADMAP, KNOWN_ISSUES, KNOWLEDGE, HANDOVER – union would silently keep both versions).
  Keep workflow changes in their own small PR so the owner-only merge does not block other work. Whether GitHub's
  own mergeability check honours `merge=union` is unverified; the merger merges locally.
- **Watch:** Projektmanager.
- **Source:** hub task `5d8b5cdc-3e5a-42a4-ab9b-a51e6a1f9e9a` (video-editor), knowledge item 2378. Record:
  `docs/DECISIONS.md` 2026-11-12 "Done log split out of ROADMAP/KNOWN_ISSUES", `docs/KNOWLEDGE.md` "Record merge
  conflicts, round 2". Corrections to the hub draft: the incident date is 2026-11-12 (hub: 2026-11-13, the
  derivation date); "rule #14" is the rule added by PR #14; "prevention" repeated the check text and was rewritten;
  the owner-only merge and the re-review after a base merge are expected returns, not part of the cause; the
  pipeline v18 change fragments and the limits of `merge=union` were added. Commit `e8e6f13` (PR #16's old head)
  is not on `main`; the reference is taken from `docs/DECISIONS.md`.

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

## 2026-11-12 – Knowledge sync: `hub_knowledge_sync` result too large for the tool output

- **Area:** docs (project record), integration (AI Nation hub tools); no app code.
- **Symptom:** during the first knowledge sync (task 0fe9cac9), `hub_knowledge_sync` for `docs/KNOWLEDGE.md`
  returned about 146 KB (403 items). The MCP client cut the response off at its output limit, so `content_md`
  could not be copied into the file reliably from the visible tool output.
- **Cause:** the generated section "Aus dem Wissens-Hub" holds every non-confidential hub item for the project,
  so the payload exceeds the MCP tool output limit; the client saves the full result to a tool-result file
  instead. The agent container has no `python3` and no `jq` for scripting the payload, only `node`.
- **Check:** a tool result that ends with a "saved to …" / "exceeds maximum allowed tokens" notice, or a payload
  far larger than usual. Read the saved result file, not the visible output, and compare the item count and size
  with the hub's summary before writing anything.
- **Fix:** parse the saved tool-result file with `node`, extract `content_md` and write it to
  `docs/KNOWLEDGE.md` directly instead of retyping it; review `git diff --stat` (only the generated section may
  change), then commit, add the log bullet in `docs/DONE.md` (PR #20) – or, since pipeline v18, a change
  fragment in `docs/changes/` – and open the PR.
- **Prevention:** keep the request small: the sync only reads entries outside the generated section, so send the
  hand-written part plus empty `BEGIN hub-knowledge`/`END hub-knowledge` markers instead of the full file (found
  in task 228be7ef, PR #32). Expect the response to land in a file and plan the `node` extraction from the start;
  never edit the generated section by hand – re-run the sync.
- **Watch:** Projektmanager.
- **Source:** hub task `0fe9cac9-f116-4a40-8d6e-28bc0b0f8138` (video-editor), knowledge item 2391. Record:
  `docs/DONE.md` 2026-11-12 "Knowledge sync (task 0fe9cac9)", PR #20 (commit `5ea5e0a`); follow-up sync
  `docs/changes/2026-11-13-228be7ef.md` (PR #32). Corrections to the hub draft: the incident date is 2026-11-12
  (hub: 2026-11-13, the derivation date); the log bullet went to `docs/DONE.md`, not HANDOVER; "prevention"
  repeated the check text and was rewritten with the smaller-request approach from task 228be7ef. The 146 KB size
  is taken from the hub draft; the record states the item count (403, 2 confidential excluded).
