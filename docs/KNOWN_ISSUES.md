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
  (versionCode 84; 83 is taken on `main` since PR #12). Deferred: GL/EGL exception texts (`GlUtil.kt`, `EglCore.kt`; not verified to reach the UI),
  crash-report labels (`CrashLog.kt`). Verified: the 7 `strings.xml` files have the same 223 keys; this does not
  prove runtime translation coverage (hard-coded Kotlin strings are not counted).
- Two CI workflows run on every PR to `main` (2026-11-11): the old `.github/workflows/pr.yml` ("PR check (build +
  lint)", non-blocking lint, no tests) is still on `main` next to `ci.yml`, although PR #12 described it as renamed.
  It duplicates build + lint and doubles CI minutes. Follow-up: delete `pr.yml` (workflow change = owner merge).
- Caption drift on very long videos (audio-check message only in the debug build) – not resolved.
- Website imprint is a placeholder (imprint data missing) – legal risk once the app is in production.
- DKIM for babacut.io not configured; babacut.net redirect possibly missing.
- `TimelineRemap` quirks found by the unit tests (behaviour kept, not yet confirmed as bugs):
  - `map(t)` returns a value for t == end of the *last old* segment only if that segment is also last in the new
    order; after a reorder, the old end time maps to null.
  - `captions()`: the 300 ms minimum duration wins over the segment-end clamp, so a caption that starts < 320 ms
    before a segment end can stick out into the next segment; a word can end before it starts after clamping.

## Resolved
- 2026-11-10 Website loaded Google Fonts (Vazirmatn) – German warning-letter risk. Fixed: font self-hosted under
  `docs/assets/fonts/` (task 6c203aca). 404.html links `style.css` relatively, so on deep missing URLs neither
  the stylesheet nor the font loads (unchanged, pre-existing).

- **15 lint errors frozen in `app/lint-baseline.xml`** (2026-11-10, task 40c40718) – CI ignores them, new ones block.
  Likely real problems, each needs its own fix:
  - `values/strings.xml:59` `mic_hint`: `% s` read as a broken format string (`StringFormatInvalid`, 6 locales) –
    probably a literal percent sign that should be `%%` or not marked as format.
  - `values-ar/strings.xml:193`: Arabic plural lacks `zero`/`two`/`few`/`many` (`MissingQuantity`).
  - `MainActivity.kt:3200–3201`: `SuspiciousIndentation` – `publishMosaic(); updateTileButtons()` is indented as if
    inside the previous statement; check whether it should be in a block.
  - `MainActivity.kt:1618–1623`: Media3 `UnstableApi` used without `@OptIn` (`UnsafeOptInUsageError`, 7×).

## Risks
- **Thin automated test coverage** – since 2026-11-10 `ci.yml` builds every push and PR, runs the JVM unit tests
  (`TimelineRemap`, transcode rule, `Loudness`, caption chunking, `EmojiDict`) and `lintDebug`, all blocking. UI,
  recording, export, drafts/session JSON and the native Whisper path are still only tested by hand on the phone, so a
  green `build-test-lint` does not prove that these work. `build-test-lint` is not yet a required check (owner step).
- **No automated tests; PR CI only builds** – since 2026-11-09 `pr.yml` checks that every PR compiles
  (`assembleDebug`) and runs `lintDebug`, but lint is non-blocking (no baseline yet) and there are no unit tests.
  Regressions in behaviour are still only found by manual phone testing; auto-merge is unsafe.
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
