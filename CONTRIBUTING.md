# Contributing – BabaCut

Project rules: [CLAUDE.md](CLAUDE.md) and [HANDOVER.md](HANDOVER.md) §8 (German). This file covers CI and the
checks you can run locally.

## CI pipeline (`.github/workflows/ci.yml`)

Workflow **CI**, one job with the check name **`build-test-lint`**.

| Trigger | What runs |
|---|---|
| every push to any branch (except `build-logs`) | build, unit tests, lint |
| every pull request to `main` | build, unit tests, lint |
| manual (`workflow_dispatch`) | build, unit tests, lint |

Steps (all blocking, a red step turns the check red):

1. `./gradlew assembleDebug` – debug APK incl. the native whisper.cpp build (NDK 27.2.12479018, CMake 3.22.1,
   SDK platform 36, Java 17).
2. `./gradlew testDebugUnitTest` – JVM unit tests in `app/src/test` (JUnit 4). Report: artifact `unit-test-report`.
3. `./gradlew lintDebug` – Android lint; errors fail the check (AGP default `abortOnError`), warnings only show up
   in the report. Report: artifact `lint-report`. Exception: the 15 errors that existed on 2026-11-10 are frozen in
   `app/lint-baseline.xml` (see `docs/KNOWN_ISSUES.md`). When you fix one, delete its `<issue>` entry; never add new
   errors to the baseline to make CI green.

Tests and lint both run after a successful build, so one run shows both results. The job summary lists the outcome
of each step. `ci.yml` has read-only permissions and **publishes nothing** (no release, no tag, no `build-logs`
push). Publishing stays in `build.yml` (push to `main` → public debug APK release) and `release.yml` (tag `v*` →
signed AAB/APK for Play).

### Required check for auto-merge

The repository owner sets this once in the GitHub web UI: *Settings → Branches → Branch protection rule for `main`
→ Require status checks to pass → add `build-test-lint`*. With that rule, a pull request can only be merged (also
via auto-merge) when the CI job is green.

## Running the checks locally

Requirements: JDK 17, Android SDK with `platforms;android-36`, `build-tools;36.0.0`, `ndk;27.2.12479018`,
`cmake;3.22.1`, and the submodules (`git submodule update --init --recursive`).

```bash
./gradlew assembleDebug        # build (same as CI step 1)
./gradlew testDebugUnitTest    # unit tests  → app/build/reports/tests/testDebugUnitTest/index.html
./gradlew lintDebug            # lint        → app/build/reports/lint-results-debug.html
# all three in one go:
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The app module declares a native CMake build (whisper.cpp); install the NDK even if you only want the unit tests
(not verified whether a pure test run can skip it).

## Writing unit tests

- Location: `app/src/test/java/de/codinix/videoeditor/…`, plain JUnit 4 (`junit:junit:4.13.2`), no Robolectric or
  mocking library.
- Test pure Kotlin logic only. Android classes (`Log`, `Handler`, `org.json` from android.jar, …) throw on the JVM
  on purpose (`unitTests.isReturnDefaultValues` is off). If logic is tangled with Android calls, extract the pure
  part into its own object first (example: `TranscodePolicy` extracted from `Downscaler`).
- Tests describe current behaviour. If a test exposes a quirk, keep the behaviour, write the quirk into
  `docs/KNOWN_ISSUES.md` and fix it in a separate change.

Covered so far: `TimelineRemap`, `TranscodePolicy`, `Loudness`, `Caption.chunk`/`chunkSentences`, `EmojiDict`.
