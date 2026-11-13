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
