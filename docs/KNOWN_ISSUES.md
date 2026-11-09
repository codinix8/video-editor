# Known issues and risks – BabaCut

## Open issues
- Caption drift on very long videos (audio-check message only in the debug build) – not resolved.
- Website imprint is a placeholder (imprint data missing) – legal risk once the app is in production.
- Website uses Google Fonts for Farsi – known German warning-letter risk; self-host planned.
- DKIM for babacut.io not configured; babacut.net redirect possibly missing.

## Risks
- **No automated tests and no PR CI** – regressions are only found by manual phone testing; auto-merge is unsafe.
- **Every push to `main` publishes** a GitHub release with an APK (public repo). Docs-only merges no longer do
  (`paths-ignore: docs/**`, `**/*.md` in `build.yml`, task f075de7b, 2026-11-09; `build-102` came from docs PR #2).
  Open: verify on the next docs-only merge that no new `build-N` release appears. Mixed pushes (docs + code) still
  build and release, as intended.
- **PR #1 is superseded** by merged PR #2; its remaining diff only deletes record lines – must not be merged.
- **Debug keystore with plaintext passwords** is committed (`keystore/`, `app/build.gradle.kts`). Only the debug
  signature is affected (Play uses the upload key + Play App Signing), but anyone can sign APKs that look like
  the GitHub debug builds.
- `MainActivity.kt` (~3300 lines) concentrates most UI logic – change risk is high.
- Play production access not yet granted (rejected 2026-10-25); revenue depends on it.
- Translation tables from the old container are lost; new strings must be added to all 7 `strings.xml` by hand.
