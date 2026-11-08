# Fine-tuning – how the owner works (BabaCut)

Harvested 2026-11-08 (task 9b8fbac3) from the pre-AI-Nation project history: `HANDOVER.md`, `README.md`,
commit history on `main`, PR #1. No `docs/OWNER_PROFILE.md` existed on any branch; no issues or owner PR comments.
All 19 insights were submitted to the fine-tuning hub (`hub_submit_owner_insights`). `_cross` = holds for all of
the owner's projects. Insights already known in the hub (e.g. "ask only about concept direction",
"docs after every task") were not repeated. Newest first within each area.

## Communication
- **[_cross, high]** He has no programming experience: report in plain German without jargon; ask him only for
  things he can see or do (screenshots, tapping through the app, web-UI settings).
  Evidence: HANDOVER §2 "der Nutzer hat keine Programmiererfahrung und testet auf dem Handy".
- **[_cross, medium]** He often dictates; read for intent and tolerate transcription errors.
  Evidence: HANDOVER §2 "Nutzer diktiert oft".

## Decisions
- **[medium]** Drops an ambitious feature that does not work well enough and ships a simpler robust variant,
  keeping the idea for later. Evidence: README step 3a "KI-Freistellung verworfen"; roadmap MediaPipe item.
- **[high]** "Erst reden, dann bauen": questions and a proposal before larger rebuilds.
  Evidence: HANDOVER §8.

## Quality
- **[high]** Every user-facing string in all 7 languages in the same change.
  Evidence: commit 2885c4d; HANDOVER §3 "in allen 7 strings.xml ergänzen".
- **[high]** Done = confirmed by him on his device; small numbered build steps.
  Evidence: README "Stand" table; HANDOVER §4 "alles vom Nutzer bestätigt".
- **[high]** Each build: versionCode +1, wait for CI, report download link + "Bitte prüfen" checklist.
  Evidence: HANDOVER §8.

## Feedback
- **[medium]** Rejects disliked UI changes outright and notices lost behaviour: keep redesigns as separate
  revertible commits; never drop features in refactors.
  Evidence: d1a366d reverts b9eb45d; 13e608a "entfernte Funktionen wiederhergestellt".

## Error handling
- **[medium]** Handle messy real-world input automatically (convert HEVC/AV1, HDR, >1080p, 60 fps on import).
  Evidence: commit 672cf06.
- **[high]** Diagnose from his screenshots, read code first, then make a targeted change.
  Evidence: HANDOVER §8.

## Design
- **[high]** Persian community is a first-class audience (Farsi UI/RTL site, Top/Ali/Baba, "Baba Regenbogen").
  Evidence: HANDOVER §1; commits 57bb3b4, 11974c1.
- **[high]** Follow familiar creator-app patterns (TikTok-style sheets, icon panels, platform safe zones).
  Evidence: commits 5c15af5, 5d5b054, b03167d.

## Security / legal
- **[_cross, high]** No secrets in repo or docs; he hands them over per session and sets GitHub secrets himself.
  Evidence: HANDOVER header and §8.
- **[medium]** Factual marketing (no superlatives); remove cheap legal risks such as Google Fonts.
  Evidence: HANDOVER §6, §8.
- **[high]** Privacy by design: no account, no upload, no ads, on-device processing; cloud/tracking only as a
  concept decision. Evidence: HANDOVER §1, §5.

## Priorities
- **[high]** Protect users from losing work: auto backups, crash recovery into drafts, undo for destructive actions.
  Evidence: commits d3aa7db, a7dc9aa, 5587cf2; README step 2e.

## Transparency
- **[medium]** Make failures visible (player errors, readable build logs); deep diagnostics in debug builds only.
  Evidence: commits 672cf06, 6f71f9e; HANDOVER §2 build-logs branch.

## Money
- **[high]** Subscription with visible-but-locked Pro features (crown, greyed rows, watermark, resolution cap)
  instead of ads. Evidence: commit 722efaa; HANDOVER §4.

## Thinking
- **[medium]** Anchor features in the differentiator "edit while recording, not afterwards"; use it for positioning.
  Evidence: HANDOVER §1, §6.
