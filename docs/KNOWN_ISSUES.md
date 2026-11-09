# Known issues and risks – BabaCut

## Open issues
- Caption drift on very long videos (audio-check message only in the debug build) – not resolved.
- Website imprint is a placeholder (imprint data missing) – legal risk once the app is in production.
- Website uses Google Fonts for Farsi – known German warning-letter risk; self-host planned.
- DKIM for babacut.io not configured; babacut.net redirect possibly missing.
- `TimelineRemap` quirks found by the unit tests (behaviour kept, not yet confirmed as bugs):
  - `map(t)` returns a value for t == end of the *last old* segment only if that segment is also last in the new
    order; after a reorder, the old end time maps to null.
  - `captions()`: the 300 ms minimum duration wins over the segment-end clamp, so a caption that starts < 320 ms
    before a segment end can stick out into the next segment; a word can end before it starts after clamping.

## Risks
- **Thin automated test coverage** – since 2026-11-09 `pr.yml` builds every PR (`assembleDebug`), runs 33 JVM unit
  tests (`testDebugUnitTest`, blocking; only `TimelineRemap` and the transcode rule) and `lintDebug` (non-blocking,
  no baseline). UI, recording, export and captions are still only tested by hand on the phone; auto-merge is unsafe.
- **Every push to `main` publishes** a GitHub release with an APK (public repo) – also for docs-only merges
  (`build-102` came from docs PR #2). Fix proposed 2026-11-09 (`paths-ignore`).
- **PR #1 is superseded** by merged PR #2; its remaining diff only deletes record lines – must not be merged.
- **Debug keystore with plaintext passwords** is committed (`keystore/`, `app/build.gradle.kts`). Only the debug
  signature is affected (Play uses the upload key + Play App Signing), but anyone can sign APKs that look like
  the GitHub debug builds.
- `MainActivity.kt` (~3300 lines) concentrates most UI logic – change risk is high.
- Play production access not yet granted (rejected 2026-10-25); revenue depends on it.
- Translation tables from the old container are lost; new strings must be added to all 7 `strings.xml` by hand.
