# Decisions – BabaCut

Newest first. Entries before 2026-11-08 are reconstructed from HANDOVER.md, README and commit history.

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
