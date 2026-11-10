# Fine-tuning – how the owner works (BabaCut)

Mirror of the AI Nation fine-tuning hub (`hub_finetuning`, project `video-editor`) for every agent working on BabaCut.
Read it before writing to the owner or proposing work.

- **Last sync:** 2026-11-10 (task 9b560127). The hub returned 60 insights: 17 for `video-editor`, 43 `_cross`
  (hold for all of the owner's projects). All are listed below; nothing still valid was removed.
- **First harvest:** 2026-11-08 (task 9b8fbac3) from HANDOVER, README, commits and PR #1 (19 insights).
- **Format:** `[scope · confidence · date]` – scope `BabaCut` = project-specific, `_cross` = all projects; date =
  when the insight entered the hub. Grouped by area, newest first within each area. Evidence is quoted from the hub.
- **Superseded** items are struck through and point to the item that replaces them; they stay here for traceability.
- **Re-sync:** call `hub_finetuning` with `project_id: "video-editor"`, compare the item count and dates with the
  line above, and add new items at the top of their area.

## Communication
- **[_cross · high · 2026-11-10]** Always name the project in every decision or question, using the name he uses
  for it (for this repo: **BabaCut**), and keep his direct channel to the project agents open alongside the hub.
  Evidence: "bitte nenne immer den Projektnamen dazu, in diesem Fall \"Frontborn\". Kann ich mit dem Frontborn
  agenten auch weiterhin direkt kommunizieren?"
- **[_cross · medium · 2026-11-09]** Use unambiguous project names (BabaCut app vs. the media manager that
  distributes videos/tweets), not generic terms like "Video Editor".
  Evidence: "Mit Video Editor, meinst du Babacut (App) oder den Media Manager der Videos, tweets usw. verteilt?"
- **[_cross · medium · 2026-11-08]** He often dictates; read for intent and tolerate transcription errors instead
  of asking about typos. Evidence: HANDOVER §2 "Kommunikation auf Deutsch; Nutzer diktiert oft."
- **[_cross · high · 2026-11-08]** He has no programming experience: report in plain German without jargon; ask
  him only for things he can see or do (screenshots, tapping through the app, settings in a web UI).
  Evidence: HANDOVER §2 "der Nutzer hat keine Programmiererfahrung und testet auf dem Handy".
- **[_cross · high · 2026-11-08]** Reachable 24/7 without quiet hours, but sleeps about 0–8 h; bundle decisions
  needed overnight into the 23:00 evening round. Evidence: ai-nation HANDOVER 2026-11-07.
- **[_cross · high · 2026-11-08]** Technical artifacts, role profiles and prompts in English; always address him
  in German. Evidence: ai-nation HANDOVER 2026-11-08 "all technical documentation in English".
- **[_cross · medium · 2026-11-08]** Make every owner step as simple as possible: say exactly what to do now, in
  the fewest steps. Evidence: "was ist jetzt zu tun, so einfach wie möglich".
- **[_cross · high · 2026-11-08]** Anything he must do in a browser (accounts, tokens, settings) comes as a
  ready-made, copyable task for a ChatGPT browser agent with links, steps, safety rules and a reporting
  requirement. Evidence: ai-nation HANDOVER 2026-11-07 "geh immer so vor".
- **[_cross · high · 2026-11-08]** "weiter", "mach weiter", "ok" = permission to continue with the next roadmap
  item; do not stop to ask what to do next. Evidence: ai-nation HANDOVER requests 2026-11-07/08.
- **[_cross · medium · 2026-11-08]** He writes terse, command-style messages (single words, slash commands);
  keep responses and status output compact and scannable. Evidence: messages "hall", "/dashboard", "/import".

## Decisions
- **[_cross · medium · 2026-11-10]** He approves owner-gated merges in protected areas (CI workflows,
  `.env.example`, docker-compose) when CI is green; bundle such merge requests into one batch approval instead of
  separate interruptions. Evidence: 8 of 8 `merge.owner` decisions (periscope, video-editor, zamann) →
  "Mergen freigeben".
- **[_cross · high · 2026-11-10]** Before presenting a decision, check whether it was already resolved in his
  direct chat with a project agent and drop it if so; stale decisions annoy him.
  Evidence: "Bitte prüfe, welche Entscheidungen wirklich noch nötig sind oder nicht".
- **[BabaCut · medium · 2026-11-08]** Drops an ambitious feature that does not work well enough and ships a
  simpler robust variant, keeping the idea for later. Evidence: README step 3a "KI-Freistellung verworfen";
  ROADMAP later item "Personen-Freistellung on-device (MediaPipe)".
- **[BabaCut · high · 2026-11-08]** "Erst reden, dann bauen": questions and a proposal before larger rebuilds;
  only small, clear fixes go straight to code. Evidence: HANDOVER §8.
- **[_cross · high · 2026-11-08]** Ask him only about concept direction (design direction, milestones,
  architecture); the team decides implementation details. Evidence: ai-nation HANDOVER 2026-11-08; constitution
  Art. 5.
- **[_cross · high · 2026-11-08]** Decisions as short bullet templates (situation, problem, options, risks,
  economic benefit, recommendation), always with your own recommendation. Evidence: ai-nation HANDOVER v0.8;
  ROADMAP submission format.
- **[_cross · high · 2026-11-08]** Prefer the bold option with a rollback path over excessive caution.
  Evidence: ai-nation HANDOVER v0.8 "risk-taking with rollback".

## Feedback
- **[_cross · high · 2026-11-08]** He approves a recommendation with a short confirmation ("ok", "Alles klar,
  leg bitte los."); end with one concrete recommended next step he can accept in a word.
  Evidence: minab168 WORKLOG W003, W006.
- ~~**[_cross · medium · 2026-11-08]** He confirms with one word ("passt", "ok", "done", "alles hinterlegt"), so
  present concrete proposals instead of open questions.~~ **Superseded** by the item above (same insight, raised
  to high by the minab168 harvest).
- **[BabaCut · medium · 2026-11-08]** Rejects disliked UI changes outright and notices lost behaviour: keep
  redesigns as separate revertible commits; never drop features as a side effect of a refactor.
  Evidence: d1a366d reverts b9eb45d; 13e608a "entfernte Funktionen wiederhergestellt"; e414118.

## Quality
- **[_cross · medium · 2026-11-10]** CI (lint, tests, build on every push and PR) in every repository so that
  reviewed PRs can be merged automatically; bold cleanup of stale artifacts such as merged branches.
  Evidence: identical /auftrag CI setup for periscope and video-editor; zamann "Ja, alle 86 löschen".
  *BabaCut status:* PR CI is ROADMAP "Next" (hub task #5).
- **[_cross · medium · 2026-11-09]** Carry a task through without being asked: code review, CI, docs, fixing
  findings, a regular merge, then checking the result in production. Evidence: /auftrag PR #137.
  *BabaCut note:* agents do not merge here (CLAUDE.md); "production" = the owner's phone check of the build.
- **[_cross · high · 2026-11-09]** Scale process overhead to project size and revenue: small low-revenue projects
  (Minab, Mokisfood, Periscope) need no test environment and go straight to prod. Evidence: "no Test environment
  required"; "Bitte Periscope auch ohne eigene Testumgebung einstufen." *BabaCut is not on that list* – it has
  the Play internal/closed test track.
- **[BabaCut · high · 2026-11-08]** Every user-facing string in all 7 languages (EN default + DE/TR/ES/FR/FA/AR)
  in the same change. Evidence: commit 2885c4d; HANDOVER §3.
- **[BabaCut · high · 2026-11-08]** Done = confirmed by him on his device; build in small numbered steps he can
  test one at a time. Evidence: README "Stand" table; HANDOVER §4 "alles vom Nutzer bestätigt".
- **[BabaCut · high · 2026-11-08]** Each build: versionCode +1, wait for CI, report download link plus a
  "Bitte prüfen" checklist. Evidence: HANDOVER §8.
- **[_cross · high · 2026-11-08]** Update the project record after every task without being asked; work without a
  docs update is not finished and not merged. Evidence: ai-nation HANDOVER 2026-11-08 (merge gate on docs_path).
- **[_cross · high · 2026-11-08]** Green tests plus a clean interface are enough to merge; do not ask him to
  approve individual pushes. Evidence: ai-nation HANDOVER 2026-11-08; DECISIONS "Ja, das ist okay".

## Priorities
- **[BabaCut · medium · 2026-11-10]** For store progress he prefers a small polish release first (untranslated
  error messages fixed in all 7 languages + release notes) before the next store step, rather than the predicted
  default. Evidence: milestone decision "1.1.4 klein: Fehlermeldungen in allen 7 Sprachen + Neuerungen-Text,
  danach Antrag" (prediction "Option 1" missed). *Status:* 1.1.4 shipped (commit f7999a1); the preference stays.
- **[BabaCut · high · 2026-11-08]** Protect users from losing work: auto backups, crash recovery into drafts,
  undo for destructive actions. Evidence: commits d3aa7db, a7dc9aa, 5587cf2; README step 2e.
- **[_cross · high · 2026-11-08]** Continuity matters: memory, handovers between agent generations and context
  limits must be seamless. Evidence: ai-nation DECISIONS 2026-11-07 "sehr wichtig"; handover at 85 %.
- **[_cross · high · 2026-11-08]** Work autonomously; tell him precisely and only what he must do himself;
  budgets keep automation running without per-item approvals. Evidence: "Alles eigenständig erledigen".
- **[_cross · high · 2026-11-08]** Priority order when goals conflict: safety > law/constitution > owner wishes >
  quality > speed > cost. Evidence: constitution Art. 3.
- **[_cross · high · 2026-11-08]** Money focus: every proposal names the concrete economic benefit or says openly
  that there is none. Evidence: ai-nation HANDOVER v0.7.1.
- ~~**[_cross · low · 2026-11-08]** He wants the whole portfolio managed centrally and onboards it in bulk.~~
  **Superseded** by Thinking "portfolio-wide, reusable structures" (same observation, medium confidence).

## Thinking
- **[BabaCut · medium · 2026-11-08]** Anchor features in the differentiator "edit while recording, not afterwards";
  use it for positioning against established editors. Evidence: HANDOVER §1, §6.
- **[_cross · medium · 2026-11-08]** Keep each company's shop, hosting, mail and docs separate from his other
  company projects. Evidence: mokisfood AGENTS.md, README.
- **[_cross · high · 2026-11-08]** He regards AI agents as equal partners and values independent thinking; be
  honest and say no with reasons when a task is unclear or wrong. Evidence: ai-nation HANDOVER v0.7; "Recht auf Nein".
- **[_cross · high · 2026-11-08]** Every project must be carvable (sale/hand-off) at any time: every data item
  carries a project_id. Evidence: ai-nation DECISIONS 2026-11-07.
- **[_cross · medium · 2026-11-08]** Thinks in portfolio-wide reusable structures: roll out proven roles and
  mechanisms to every project, onboard projects in bulk. Evidence: ai-nation HANDOVER 2026-11-08.

## Analysis
- **[_cross · high · 2026-11-10]** When he notices an anomaly he asks "why" and wants a data-based investigation
  that names the cause and proposes a concrete limiting mechanism, reported briefly in German.
  Evidence: "Warum? Leite eine Untersuchung ein."; minab attention analysis.
- **[_cross · medium · 2026-11-08]** He expects advisor-style recommendations backed by sources, not a neutral
  list of options. Evidence: ai-nation HANDOVER 2026-11-07 (model per task).

## Transparency
- **[_cross · medium · 2026-11-10]** Show costs per project (today and 7 days) and mark figures "geschätzt" when
  cost tracking has gaps. Evidence: /auftrag Projektkarte "Kosten heute: X $ (7 Tage: Y $)".
- **[BabaCut · medium · 2026-11-08]** Make failures visible (player/conversion errors, readable build logs); deep
  diagnostics in debug builds only. Evidence: commits 672cf06, 6f71f9e; HANDOVER §2 `build-logs` branch.
- **[_cross · high · 2026-11-08]** He wants to see agents working live: dashboards show current activity per
  project in parallel views without him asking. Evidence: ai-nation DECISIONS 2026-11-07; HANDOVER 2026-11-08.
- ~~**[_cross · medium · 2026-11-08]** He checks the dashboard repeatedly, late at night too, so it must always
  show current state.~~ **Superseded** by the item above (raised to high).

## Error handling
- **[_cross · medium · 2026-11-10]** Never silently drop anything he sends (photos, screenshots, captions,
  unsupported formats); process it or tell him explicitly it could not be handled. Evidence: /auftrag Telegram
  "Der Owner merkt nicht, dass nichts angekommen ist."
- **[BabaCut · medium · 2026-11-08]** Handle messy real-world input automatically (convert HEVC/AV1, HDR, >1080p,
  >30 fps on import). Evidence: commit 672cf06; `Downscaler.transcodeReason()`.
- **[BabaCut · high · 2026-11-08]** Diagnose from his screenshots: read the code first, then a narrow targeted
  change. Evidence: HANDOVER §8.

## Security / legal
- **[_cross · low · 2026-11-09]** Tends to choose privacy-focused providers for his own infrastructure (Proton,
  Posteo). Evidence: "eigenes Mithra-Postfach (Proton über die Bridge oder Posteo)".
- **[_cross · high · 2026-11-08]** No secrets in repo or docs; he hands them over per session and sets GitHub
  secrets himself. Evidence: HANDOVER header and §8.
- **[BabaCut · medium · 2026-11-08]** Factual marketing (no superlatives); remove cheap legal risks such as Google
  Fonts. Evidence: HANDOVER §6, §8. *Status:* Google Fonts removed 2026-11-10 (PR #8).
- **[BabaCut · high · 2026-11-08]** Privacy by design: no account, no upload, no ads, on-device processing;
  cloud/tracking only as a concept decision. Evidence: HANDOVER §1, §5.
- **[_cross · high · 2026-11-08]** No provider-specific data restrictions: Anthropic, OpenAI and DeepSeek are rated
  equally; only the constitution and company-data confidentiality limit what goes to models.
  Evidence: ai-nation HANDOVER 2026-11-07.
- **[_cross · high · 2026-11-08]** Strictly legal but pragmatic checks: green = do it, yellow = 2–3 sentences plus
  a legal alternative, orange = his review; measure against the law, not authorities' opinions.
  Evidence: ai-nation HANDOVER v0.7.2/v0.7.3.

## Design
- **[_cross · medium · 2026-11-09]** Record each project's test strategy as an explicit tag whose name states the
  consequence (e.g. no separate test environment needed), not a vague label like "privat".
  Evidence: "Tags abändern von \"privat\" auf irgendwas, dass widerspiegelt, dass keine separate Testumgebung
  nötig ist".
- **[BabaCut · high · 2026-11-08]** Persian community is a first-class audience (Farsi UI, RTL site, Top/Ali/Baba,
  "Baba Regenbogen"). Evidence: HANDOVER §1; commits 57bb3b4, 11974c1.
- **[BabaCut · high · 2026-11-08]** Follow familiar creator-app patterns (TikTok-style bottom sheets, icon panels,
  platform safe-zone guides). Evidence: commits 5c15af5, 5d5b054, b03167d.

## Money
- **[BabaCut · high · 2026-11-08]** Subscription with visible-but-locked Pro features (crown, greyed rows,
  watermark, 1080p cap on free) instead of ads. Evidence: commit 722efaa; HANDOVER §4.
- **[_cross · medium · 2026-11-08]** Spends readily when it buys autonomy (a few hundred EUR/month) but wants
  spending bounded by budgets, limits and falling cost per task, not per-item approvals.
  Evidence: ai-nation DECISIONS 2026-11-07.
