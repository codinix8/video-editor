# Decisions – BabaCut

Newest first. Entries before 2026-11-08 are reconstructed from HANDOVER.md, README and commit history.

## 2026-11-08 – Onboarding into AI Nation
- Agents work on `agent/…` branches with pull requests; no direct pushes to `main`, no self-merge.
  Previously Claude pushed straight to `main` (HANDOVER §2). Note: every push to `main` creates a public GitHub
  release with an APK, so merges should be deliberate.
- Project record: existing root `HANDOVER.md` stays (not renamed); new record files live in `docs/` and are
  excluded from the GitHub Pages site via `docs/_config.yml`.
- Team composition requested from the owner as decision `project.team`.

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
