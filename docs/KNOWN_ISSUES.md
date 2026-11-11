# Known issues and risks – BabaCut

## Open issues
- German words in error toasts regardless of app language (`MainActivity.kt`: "Bild", "Umrechnung", "Umwandlung",
  "Spracherkennung", "Entwurf", "Kachelvideo", "Overlay-Video"; `WhisperEngine.kt`: "Modell konnte nicht geladen
  werden") – still on `main` after `v1.1.4`; the owner wants it fixed before the Play re-application.
- German words leak into localised error messages (still on `main` after `v1.1.4`): fallback labels passed to
  `R.string.error` / `player_error` ("Bild", "Umrechnung", "Umwandlung", "Video", "Spracherkennung", "Entwurf",
  "Kachelvideo", "Overlay-Video"), German exception texts shown in toasts (`MainActivity.kt` 1324/1548/1870,
  `WhisperEngine.kt` 92, `ModelManager.kt` 33) and in the export error sheet (`Exporter.kt` 136/220/228/301,
  `OverlayAudioRenderer.kt` 239). Line numbers as of `40521ea`. Planned fix A in `docs/CLOSED_TEST_1_1_4_PLAN.md`
  (versionCode 83). Deferred: GL/EGL exception texts (`GlUtil.kt`, `EglCore.kt`; not verified to reach the UI),
  crash-report labels (`CrashLog.kt`). Verified: the 7 `strings.xml` files have the same 223 keys; this does not
  prove runtime translation coverage (hard-coded Kotlin strings are not counted).
- Caption drift on very long videos (audio-check message only in the debug build) – not resolved.
- Website imprint is a placeholder (imprint data missing) – legal risk once the app is in production.
- DKIM for babacut.io not configured; babacut.net redirect possibly missing.
- `TimelineRemap` quirks found by the unit tests (behaviour kept, not yet confirmed as bugs):
  - `map(t)` returns a value for t == old end of a segment only for the segment that is *last in the new order*
    (whichever old segment that is); for any other retained segment the old end time is the
    start of the next old segment: it maps to that segment's new position if it is retained, otherwise to null.
  - `captions()`: the 300 ms minimum duration wins over the segment-end clamp, so a caption that starts < 320 ms
    before a segment end can stick out into the next segment; a word can end before it starts after clamping.

## Resolved
- 2026-11-10 Website loaded Google Fonts (Vazirmatn) – German warning-letter risk. Fixed: font self-hosted under
  `docs/assets/fonts/` (task 6c203aca). 404.html links `style.css` relatively, so on deep missing URLs neither
  the stylesheet nor the font loads (unchanged, pre-existing).

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
- `MainActivity.kt` (3326 lines) concentrates most UI logic – change risk is high. Split proposal (12 move-only
  steps, not started): `docs/MAINACTIVITY_SPLIT.md`.
- Play production access not yet granted (rejected 2026-10-25); revenue depends on it. Re-application draft:
  `docs/PLAY_PRODUCTION_APPLICATION.md` (needs Console figures Z1–Z7 and owner confirmations); closed-track delivery
  of 1.1.2–1.1.4 is not provable from git.
- Translation tables from the old container are lost; new strings must be added to all 7 `strings.xml` by hand.
