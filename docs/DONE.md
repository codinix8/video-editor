# Done log – BabaCut

Append-only log of finished work and resolved issues (project record, not published on babacut.io).
Since 2026-11-12 (task 5d8b5cdc) this file replaces the "Done" section of `docs/ROADMAP.md` and the "Resolved"
section of `docs/KNOWN_ISSUES.md`, which every PR used to edit at the same line.

**How to write here** (see `docs/AI_NATION.md`, "Writing the record without conflicts"):
- Add one new bullet **at the end of the file**: `- YYYY-MM-DD [done|fixed] … (task <id8>)`.
- Never edit, reorder or delete an existing bullet; to correct one, append a new bullet that names it.
- The file merges with `merge=union` (`.gitattributes`): on a local `git merge origin/main`, two PRs that both
  appended an entry keep both entries without a conflict.

## Done until 2026-11-11 (moved from ROADMAP "Done", newest first)
- [x] 2026-11-11 Fix D: crash report labels in `CrashLog.kt` localised in all 7 languages (4 keys `crash_label_*`;
      neutral keys in the file, labels applied at display time by `CrashReportText` + `CrashReportTextTest`); still
      versionCode 84 / 1.1.5, not tagged (task a0f44077).
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

## Resolved until 2026-11-11 (moved from KNOWN_ISSUES "Resolved", newest first)
- 2026-11-11 Crash report labels ("Zeit", "Gerät", …) were German in every app language. Fixed in 1.1.5 (task
  a0f44077, option D): `CrashLog.kt` now stores neutral keys (`time`, `thread`, `version`, `device`) and
  `CrashReportText` labels them with the activity context when shown (4 new keys `crash_label_*` in all 7
  `strings.xml`); old German reports are mapped too. Stack traces stay as delivered. Phone check by the owner pending.
- 2026-11-11 Two CI workflows ran on every PR to `main` (legacy `pr.yml` next to `ci.yml`, doubled CI minutes).
  Fixed: `.github/workflows/pr.yml` deleted (task cea0d561); `ci.yml` (`build-test-lint`) is the only PR check.
- 2026-11-11 German words leaked into localised error messages (fallback labels "Bild", "Umrechnung", "Umwandlung",
  "Video", "Spracherkennung", "Entwurf", "Kachelvideo", "Overlay-Video" and German exception texts in
  `MainActivity.kt`, `WhisperEngine.kt`, `ModelManager.kt`, `Exporter.kt`, `OverlayAudioRenderer.kt`). Fixed in 1.1.5
  (task 6f0c1893): 18 new string keys in all 7 `strings.xml` (incl. Exporter's "Keine Segmente", not in the plan
  table); `UserFacingException` + `ErrorText` for classes without a Context. `CrashLog.kt` labels (option D) followed
  in task a0f44077, see the entry above. Still German by design (out of scope):
  GL/EGL texts (`GlUtil.kt`, `EglCore.kt`) and the debug-only "Ton-Kontrolle" toast.
  System/Media3 exception messages are shown as delivered by Android. Phone check by the owner pending.
- 2026-11-11 Agent PRs kept returning with "Conflict with main" (11 returns in 24 h) and the record collected
  duplicate/stale entries. Fixed at the root by conflict-safe record rules (`docs/AI_NATION.md`) and union merge for
  `docs/DECISIONS.md` (task 9167d767). Still open: PR #7 needs the owner's merge.
- 2026-11-10 Website loaded Google Fonts (Vazirmatn) – German warning-letter risk. Fixed: font self-hosted under
  `docs/assets/fonts/` (task 6c203aca). 404.html links `style.css` relatively, so on deep missing URLs neither
  the stylesheet nor the font loads (unchanged, pre-existing).

## Log (oldest first – append new entries below this line)
- 2026-11-12 [fixed] Returned merges caused by record conflicts, second round (task 5d8b5cdc): the rule "add a new
  entry at the top of its section" made every two parallel PRs insert at the same line of ROADMAP "Done" /
  KNOWN_ISSUES "Resolved" (PR #16 vs #15). Both logs moved into this append-only file with `merge=union`; misplaced
  lint-baseline item moved back to KNOWN_ISSUES "Open issues"; knowledge entry in `docs/KNOWLEDGE.md`.
- 2026-11-12 [done] Knowledge sync (task 0fe9cac9): `docs/KNOWLEDGE.md` got the hub-generated section "Aus dem
  Wissens-Hub" (403 items, 2 confidential excluded by the hub) via `hub_knowledge_sync`; the hand-written part above
  it is unchanged. The hub is master of that section: do not edit it by hand, re-run the sync instead.
- 2026-11-12 [done] Design audit with the hub design skills (task 9defaf40, read-only): `docs/design/DESIGN-AUDIT-2026-11.md`
  (18 app + 5 website findings, ranked, with rule and `file:line`), 10 website screenshots (1440×900, 390×844) in
  `docs/design/screenshots/`; app audited statically (no Android SDK in the container). Top 5 proposed as owner tasks.
  `docs/design/` excluded from the website in `docs/_config.yml`.
- 2026-11-12 [done] Design audit rework (task 9defaf40, review on PR #22): 26 app screenshots from a GitHub Actions
  emulator (API 35, 390×844 dp, en / de at 130 % font / fa; test scene only) in `docs/design/screenshots/app/`;
  A1 edge-to-edge confirmed, new findings A19 (fa label-value spacing), A20 (no scrim), A21 (rail/watermark).
