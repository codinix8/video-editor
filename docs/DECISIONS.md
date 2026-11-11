# Decisions – BabaCut

Newest first. Entries before 2026-11-08 are reconstructed from HANDOVER.md, README and commit history.

## 2026-11-11 – Task b9d022fb closed out (docs only)
- The owner sent PR #6 back (merge decision); its commits then reached `main` inside PR #12 (1eb2c6c, stacked). No
  new code: re-implementing would have duplicated what is on `main`. PR #6's branch was then merged with `-s ours` onto `main` (no force push), so PR #6 now carries only this
  docs close-out.
- Evidence: `main` CI run 43002550851 (`build-test-lint`), `unit-test-report`: 54 tests, 0 failures.
- Consequence for the 1.1.5 polish (scope A + B): versionCode 83 is taken, it gets **84**.
- Found: `pr.yml` still exists next to `ci.yml` (see KNOWN_ISSUES); not removed here to keep this PR docs-only.

## 2026-11-10 – CI on every push and pull request (task 40c40718)
- Built on the open PRs instead of a competing workflow: branch stacked on PR #6 (which already contains PR #5's
  commit), `main` merged in. Merging this PR makes PR #5 and #6 redundant (close them instead of merging).
- `pr.yml` renamed to `ci.yml` (workflow `CI`, job name `build-test-lint` = the status check to require).
  Triggers: `push` on all branches except `build-logs` (CI log branch, no code), `pull_request` to `main`,
  `workflow_dispatch`. Pushes to a branch with an open PR therefore run twice (push + PR); accepted for the "every
  push" requirement, cancelled per event/ref by `concurrency`.
- Still a separate workflow with `contents: read`; publishing stays in `build.yml`/`release.yml`. The push run on
  `main` duplicates the debug build of `build.yml` (~5 min of runner time per merge); accepted so `main` has a
  green/red CI status of its own.
- `lintDebug` made blocking **with a baseline** (`app/lint-baseline.xml`, wired via `lint { baseline = … }` in
  `app/build.gradle.kts`). The first blocking run (CI run 42887488201) failed with 15 errors, 85 warnings: the green
  lint step on PR #6 only meant `continue-on-error` had swallowed them. The baseline holds exactly those 15 errors
  (generated from that run's XML report, warnings excluded so they stay visible); any new error fails CI. The
  errors themselves are not fixed here (app code + 7 `strings.xml`, own task) – listed in KNOWN_ISSUES.
  `lintVitalRelease` (store builds) only checks fatal issues and was never failing on these, so nothing that
  blocked before is weakened.
- Tests and lint run only after a successful build, but independently of each other (`!cancelled()`), so a failing
  test does not hide lint results.
- New JVM tests without touching app code: `Loudness.gain`, `Caption.chunk`/`chunkSentences`, `EmojiDict.forWord`.
  No new frameworks (JUnit 4 as before). No `versionCode` bump beyond PR #6's 83: no app code changed here.
- Docs: `CONTRIBUTING.md` (English, technical) + a short German pointer in `README.md`.
- Evidence: CI run 42888492366 on 6a19a11 green (push event), 54 tests / 0 failures / 0 ignored in the report.
- Open owner step: mark `build-test-lint` as required status check for `main` (not possible with the agent token).
## 2026-11-10 – Play production re-application draft (task 036afa99, revised after review of PR #9)
- Draft in German in `docs/PLAY_PRODUCTION_APPLICATION.md` (excluded from the site), answers kept near the
  ~300-character field limit noted in HANDOVER §5. Console figures are placeholders Z1–Z7; facts only the owner
  knows (which features testers used, email feedback, device checks, Console completeness, whether fixes came from
  testers) are explicit `[BESTÄTIGEN: …]` placeholders – numeric Console totals do not establish them.
- Versions are listed as built/tagged in git (1.1.1/79, 1.1.2/80, 1.1.3/81, 1.1.4/82 tagged 2026-11-10), separately
  from closed-track delivery, which is unknown until the owner reads Z7 (internal vs. closed track). The C1 answer
  only keeps versions Z7 confirms on the closed track.
- The stale "apply now with 1.1.3 vs. ship 1.1.4 first" choice is removed. Recorded owner choice (hub decision
  2a5ce05a): small 1.1.4 with error messages in all 7 languages + release notes, then apply.
- Finding: the owner's `v1.1.4` (f7999a1) shipped other fixes (silence-hallucination filter, permission help, etc.)
  but not the error-label localisation – German labels remain on `main` (`MainActivity.kt` "Bild", "Umrechnung",
  "Umwandlung", "Spracherkennung", "Entwurf", "Kachelvideo"; `WhisperEngine.kt` "Modell konnte nicht geladen werden").
  Per the owner's choice that polish is still due before applying (next versionCode 83; separate task).
- No app code changed, no versionCode bump (docs-only).

## 2026-11-10 – Closed-test update planned (task d444acae, revised after review of PR #10)
- Proposal only, no code ("erst reden, dann bauen"): `docs/CLOSED_TEST_1_1_4_PLAN.md` (German, for the owner).
- Owner choice recorded, not re-asked (hub decision 2a5ce05a): "1.1.4 klein: Fehlermeldungen in allen 7 Sprachen +
  Neuerungen-Text, danach Antrag" = scope A + B, then the production application. D (English crash-report labels)
  stays optional and unapproved; C (TimelineRemap caption 300 ms clamp) deferred until PR #6 tests are on `main`.
- The owner's own `v1.1.4` (82, f7999a1, 2026-11-10) shipped other fixes but not A, so A + B target the next
  version: versionCode 83, proposed versionName 1.1.5 (owner to confirm). PR #6 also sets 82 and must be bumped.
- Scope A extended per review: `ModelManager.kt` download error, `Exporter.kt` save/MediaStore/re-encode/overlay-audio
  errors and `OverlayAudioRenderer.kt` "Kein Ton im Overlay" (all reach the toast or the export error sheet).
  Explicitly deferred: GL/EGL exception texts (not verified to reach the UI), system `e.message`, log-only texts.
- Claims narrowed: verified result is "the 7 `strings.xml` have the same 223 keys", not full runtime translation.
- Closed-track rollout history is not inferable from versionCode; marked `[BESTÄTIGEN]` for the owner (Play Console).
  "Changes were shipped during the test" may only be used in the application if the Console confirms it.

## 2026-11-09 – No release for docs-only pushes (task f075de7b)
- `build.yml` push trigger gets `paths-ignore: docs/**` and `**/*.md`; `workflow_dispatch` stays. A push is skipped
  only when *every* changed file matches; mixed pushes still build and publish `build-N`.
- `docs/**` covers the website too (HTML/CSS/JS); website changes never affect the APK, so no build is needed.
- Workflow files are not ignored: a change to `build.yml` itself (including this one) still triggers a build.
- No `versionCode` bump in this PR: the app is unchanged (CI config only, same as PR #5). `main` is at 82 (owner
  build 1.1.4); 83 is planned for the next app update (see the 2026-11-10 closed-test entry), so a bump here would
  only create a conflict. The merge build of this PR (it changes `build.yml`, so it is not skipped) republishes
  versionCode 82 as a new `build-N`. Updated 2026-11-10 when merging `main` (1.1.4) into this branch; before that
  the text said 81. Updated 2026-11-11 when merging `main` again (PR #9/#10): stale "PR #6 already claims 83" removed.
  Updated again 2026-11-11 (afternoon) when merging `main` (#12, #6): 3 of the last 5 releases (`build-109`,
  `build-110`, `build-112`) came from docs-only merges (#10, #9, #6), which this change would have skipped.
- Skipped pushes do not consume a run number, so `build-N` numbers stay consecutive for real builds.

## 2026-11-09 – Pull-request CI (task 0b65e2ce)
- New `.github/workflows/pr.yml` instead of adding `pull_request` to `build.yml`, so PR runs can never reach the
  release/`build-logs` steps. Permissions `contents: read`.
- Lint runs but is non-blocking (`continue-on-error` on the step), because the code was never linted and no Android
  SDK was available in the agent container to create a baseline. Chosen over `lint { abortOnError = false }` in
  Gradle so that `lintVitalRelease` still guards store builds. Follow-up: baseline, then blocking.
- No `versionCode` bump: no app code changed and PR runs produce no tester build.

## 2026-11-10 – Proposal for splitting MainActivity.kt (task de9795a4)
- Proposal only, no code: `docs/MAINACTIVITY_SPLIT.md`. Recommended: plain Kotlin controllers sharing one
  `ProjectState` + a small `Host` interface; no ViewModel/Compose/DI migration (that would be a rebuild).
- Order by risk: pure logic with JVM tests first (caption timing, gain ranges, session JSON), wide-coupled UI
  (review, mosaic, overlays, recording) last. One move-only PR per step, `versionCode` +1, phone checklist per step.
- Session/draft JSON keys must not change (user work). Prerequisite: PR CI (#5) and JVM tests (#6) merged.
- Status: waiting for the owner (3 questions in §6 of the proposal). No versionCode bump: docs-only.

## 2026-11-10 – Self-hosted Vazirmatn web font (task 6c203aca)
- Google Fonts removed from all five pages (index, 404, support, imprint, privacy – the brief named three).
- Vazirmatn v33.003 from the upstream release (github.com/rastikerdar/vazirmatn), static Regular (400) and Bold (700)
  woff2 only – the weights the old Google link requested; ~100 KB total, no variable font, no subsetting.
  SIL OFL 1.1 licence shipped next to the files (`docs/assets/fonts/OFL.txt`).
- Font stack in `style.css` unchanged (`"Vazirmatn","Noto Sans Arabic",Tahoma,…` for `lang="fa"`); `font-display:swap`.
- No versionCode bump: website-only change, no app build.

## 2026-11-09 – First JVM unit tests (task b9d022fb)
- Pure decision of `Downscaler.transcodeReason` extracted into `TranscodePolicy.reason(mime, width, height,
  rotation, fps, colorTransfer)`; Downscaler still reads the `MediaFormat` and delegates. Same rule and order
  (codec → >1080p → >31 fps → HDR), same texts. Extracted into a new object instead of testing `Downscaler`
  directly, because `Downscaler` creates a main-thread `Handler` on class load, which fails on the JVM.
- `TranscodePolicy` copies `COLOR_TRANSFER_ST2084`(6)/`COLOR_TRANSFER_HLG`(7) to stay Android-free;
  `MediaFormatConstantsTest` guards the copy against android.jar.
- Plain JUnit 4.13.2, no Robolectric/mocking library: `TimelineRemap` only needs data classes. No
  `unitTests.isReturnDefaultValues`, so an accidental Android call in a test fails loudly instead of returning 0.
- `testDebugUnitTest` is **blocking** in `pr.yml` (lint stays non-blocking).
- `versionCode` 81 → 82, after merging `main` (owner build 1.1.4 = 82) → **83**: app code was touched, and the merge to
  `main` produces a tester build. `versionName` stays 1.1.4 (no user-visible change).
- Branch stacked on PR #5 (`pr.yml` exists only there); merge PR #5 first.
- Tests document current behaviour, including two quirks found while writing them (see KNOWN_ISSUES).

## 2026-11-09 – Pull-request CI (task 0b65e2ce)
- New `.github/workflows/pr.yml` instead of adding `pull_request` to `build.yml`, so PR runs can never reach the
  release/`build-logs` steps. Permissions `contents: read`.
- Lint runs but is non-blocking (`continue-on-error` on the step), because the code was never linted and no Android
  SDK was available in the agent container to create a baseline. Chosen over `lint { abortOnError = false }` in
  Gradle so that `lintVitalRelease` still guards store builds. Follow-up: baseline, then blocking.
- No `versionCode` bump: no app code changed and PR runs produce no tester build.

## 2026-11-09 – `project.team` requested (task 4e8e8c10, resumed)
- The earlier 403 on `hub_request_decision` is gone; the request was filed (hub decision id `7f90c967`, decider:
  Gremium). Options (German for the owner): 1. Schlank – Entwickler + Code-Reviewer + QA-Tester (kits: core, qa);
  2. Ausgewogen – plus Texter + Security-Review (kits: core, ui, qa, copy, security); 3. Wachstum – plus Growth +
  Asset-Ersteller (kits: core, ui, qa, assets, copy, security, growth).
- Recommendation: option 1 now, extend to option 2 after Play production access. This differs from the 2026-11-08
  draft (which recommended a launch team): no revenue is possible before production access, the owner keeps
  overhead small for projects without revenue, and the website legal items (fonts, imprint) are small enough to be
  single tasks without a dedicated role. Predicted owner choice: option 1 (option 2 possible). If no decision:
  current state (developer + code reviewer).

## 2026-11-09 – Deep-integration inventory (task 9299e3fd)
- Docs-only inventory; no code changed. Result: `docs/AI_NATION.md` "State and backlog (2026-11-09)".
- Backlog order (proposed via `hub_propose_tasks`, owner releases): PR CI first (no tests, no PR checks today),
  then first unit tests, no release for docs-only pushes, self-hosted font, Play re-application draft, 1.1.4 plan,
  `MainActivity.kt` split proposal.
- PR #1 left untouched although it is superseded by the merged PR #2; closing it is the owner's call.

## 2026-11-08 – Owner profile harvested (task 9b8fbac3)
- 19 owner insights (3 `_cross`, 16 project) distilled from HANDOVER, README and commits; submitted to the hub and
  recorded in `docs/FINETUNING.md`. The file is excluded from the public site via `docs/_config.yml`.
- This branch is stacked on PR #1 (onboarding) because the record files only exist there; merge PR #1 first.

## 2026-11-08 – Onboarding into AI Nation
- Agents work on `agent/…` branches with pull requests; no direct pushes to `main`, no self-merge.
  Previously Claude pushed straight to `main` (HANDOVER §2). Note: every push to `main` creates a public GitHub
  release with an APK, so merges should be deliberate.
- Project record: existing root `HANDOVER.md` stays (not renamed); new record files live in `docs/` and are
  excluded from the GitHub Pages site via `docs/_config.yml`.
- ~~Pending: `project.team`~~ (filed 2026-11-09, see above). The request could not be filed at first: `hub_request_decision` returned 403 (forbidden)
  for role `video-editor-entwickler`. Prepared draft (to be filed by a role with permission; owner text in German):
  1. Schlank – Entwickler + Code-Reviewer – kits: core, qa (lowest cost; tests/stability only).
  2. Launch-Team – kits: core, qa, copy, security, growth (Play approval, imprint/privacy, store copy, tests + PR CI).
  3. Voll – kits: core, ui, qa, assets, copy, security, growth, sales (fastest growth, highest cost before launch).
  Recommendation: option 2 – no subscription revenue without Play production access; it lowers legal risk
  (imprint, Google Fonts) and prepares Pro conversion at moderate cost. Predicted owner choice: option 2.
  If no decision: option 1.

## Earlier (reconstructed)
- **Monetisation:** BabaCut Pro subscription (Play Billing 8): monthly 2.99 €, annual 19.99 € with 7-day trial;
  free tier limited to 1080p and watermark; crown marks Pro features.
- **Privacy by design:** no account, no upload, no ads; Play data-safety "no data collected".
- **On-device captions** with whisper.cpp (Tiny/Base/Small as "Top/Ali/Baba"; Base default).
- **AI background removal rejected** for tile mode (step 3a); camera shown as framed tile instead.
- **Gradle build cache off** (it produced stale intermediates).
- **No R8/minify.**
- **Insert-time transcoding** of HEVC/AV1, >1080p, >30 fps and HDR videos to H.264 1080p (1.1.2).
- **Captions re-recognised automatically after cut** (1.1.3).
