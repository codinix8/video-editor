# Known issues and risks – BabaCut

## Open issues
- Caption drift on very long videos (audio-check message only in the debug build) – not resolved.
- Website imprint is a placeholder (imprint data missing) – legal risk once the app is in production.
- DKIM for babacut.io not configured; babacut.net redirect possibly missing.
- `TimelineRemap` quirks found by the unit tests (behaviour kept, not yet confirmed as bugs):
  - `map(t)` returns a value for t == end of the *last old* segment only if that segment is also last in the new
    order; after a reorder, the old end time maps to null.
  - `captions()`: the 300 ms minimum duration wins over the segment-end clamp, so a caption that starts < 320 ms
    before a segment end can stick out into the next segment; a word can end before it starts after clamping.
- **Edge-to-edge overlap on Android 15/16** (design audit 2026-11, finding A1; device-specific: confirmed on API 35 and 36 emulators incl. 360×640 dp, not reproduced on API 30, task fa0e1afc):
  `targetSdk = 36` but no window-inset handling; top/bottom controls may sit under the status bar, cutout or nav bar.
  Emulator: segment bar under the status-bar clock, white status icons vanish on bright footage
  (`docs/design/screenshots/app/api35-en-10-review-text.png`). Other UI findings: `docs/design/DESIGN-AUDIT-2026-11.md`.
- **fa/ar settings rows: label and value touch** (design audit A19; confirmed in fa and ar on API 30/35/36, also caption and text sheets, task fa0e1afc; screenshot
  `docs/design/screenshots/app/api35-fa-02-settings-sheet.png`): e.g. "فارسیزبان برنامه"; `SheetRows.kt:53`, `:83-85`.
  Text-level fix, `supportsRtl=false` stays. Part of proposed task `5b1362db`.
- **New design-audit findings** (part of the proposed design tasks, tasks fa0e1afc, a8ef1268; details in `docs/design/DESIGN-AUDIT-2026-11.md`): A22 white segment bar invisible on bright footage (`SegmentProgressView.kt:20-21`, with A1/A20 in `d3bf1d65`); A23 review status can show position > total ("0:13 / 0:12", `MainActivity.kt:3270-3273`); A18 (fa/ar bidi in the review status) confirmed; A20b review hint cut at the left edge at 200 % font; A4 confirmed in fr/de and at 200 % font (pill not clickable, so no lost taps); A11 confirmed at 200 % ("720p" breaks and clips); A10 confirmed; A6 measured (44/36/25 dp targets, sheet buttons 46 dp); A5/A16 confirmed from the real export dialog. A3 (drag hint) stays plausible: cannot be scripted with `input motionevent`. #area/ui #type/bug

- **9 lint errors frozen in `app/lint-baseline.xml`** (2026-11-10, task 40c40718; 16→9 by task 0eecba17; the old "15" undercounted) – CI ignores them, new ones block.
  Likely real problems, each needs its own fix:
  - `MainActivity.kt:3200–3201`: `SuspiciousIndentation` – `publishMosaic(); updateTileButtons()` is indented as if
    inside the previous statement; check whether it should be in a block.
  - `MainActivity.kt:1618–1623`: Media3 `UnstableApi` used without `@OptIn` (`UnsafeOptInUsageError`, 7×).

## Resolved
Resolved issues: [`docs/DONE.md`](DONE.md) (append-only log; do not add "Resolved" entries here).

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
- **PR #1 is superseded** by merged PR #2; its remaining diff only deletes record lines – must not be merged.
- **Debug keystore with plaintext passwords** is committed (`keystore/`, `app/build.gradle.kts`). Only the debug
  signature is affected (Play uses the upload key + Play App Signing), but anyone can sign APKs that look like
  the GitHub debug builds.
- `MainActivity.kt` (3326 lines) concentrates most UI logic – change risk is high. Split proposal (12 move-only
  steps, not started): `docs/MAINACTIVITY_SPLIT.md`.
- Play production access not yet granted (rejected 2026-10-25); revenue depends on it. Re-application draft:
  `docs/PLAY_PRODUCTION_APPLICATION.md` (needs Console figures Z1–Z7 and owner confirmations); closed-track delivery
  of 1.1.2–1.1.5 is only provable from the Console (Z7). Draft is now for 1.1.5 with an owner runbook (task f16621ce).
- Translation tables from the old container are lost; new strings must be added to all 7 `strings.xml` by hand.
