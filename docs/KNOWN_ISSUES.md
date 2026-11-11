# Known issues and risks – BabaCut

## Open issues
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
- 2026-11-11 German words leaked into localised error messages (fallback labels "Bild", "Umrechnung", "Umwandlung",
  "Video", "Spracherkennung", "Entwurf", "Kachelvideo", "Overlay-Video" and German exception texts in
  `MainActivity.kt`, `WhisperEngine.kt`, `ModelManager.kt`, `Exporter.kt`, `OverlayAudioRenderer.kt`). Fixed in 1.1.5
  (task 6f0c1893): 18 new string keys in all 7 `strings.xml` (incl. Exporter's "Keine Segmente", not in the plan
  table); `UserFacingException` + `ErrorText` for classes without a Context. Still German by design (out of scope):
  GL/EGL texts (`GlUtil.kt`, `EglCore.kt`), `CrashLog.kt` labels (option D), the debug-only "Ton-Kontrolle" toast.
  System/Media3 exception messages are shown as delivered by Android. Phone check by the owner pending.
- 2026-11-11 Agent PRs kept returning with "Conflict with main" (11 returns in 24 h) and the record collected
  duplicate/stale entries. Fixed at the root by conflict-safe record rules (`docs/AI_NATION.md`) and union merge for
  `docs/DECISIONS.md` (task 9167d767). Still open: PR #7 and the `pr.yml` deletion need the owner's merge.
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
- **Every push to `main` publishes** a GitHub release with an APK (public repo). Docs-only merges no longer do
  (`paths-ignore: docs/**`, `**/*.md` in `build.yml`, task f075de7b, 2026-11-09; `build-102` came from docs PR #2; on 2026-11-11 also
  `build-109`, `build-110`, `build-112` from docs PRs #10, #9, #6).
  Open: verify on the next docs-only merge that no new `build-N` release appears. Mixed pushes (docs + code) still
  build and release, as intended.
- **Thin automated test coverage** – since 2026-11-10 `ci.yml` builds every push and PR, runs the JVM unit tests
  (`TimelineRemap`, transcode rule, `Loudness`, caption chunking, `EmojiDict`) and `lintDebug`, all blocking. UI,
  recording, export, drafts/session JSON and the native Whisper path are still only tested by hand on the phone, so a
  green `build-test-lint` does not prove that these work. `build-test-lint` is not yet a required check (owner step).
- **No automated tests; PR CI only builds** – since 2026-11-09 `pr.yml` checks that every PR compiles
  (`assembleDebug`) and runs `lintDebug`, but lint is non-blocking (no baseline yet) and there are no unit tests.
  Regressions in behaviour are still only found by manual phone testing; auto-merge is unsafe.
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
