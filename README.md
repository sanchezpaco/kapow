# Kapow

A native Android comic reader designed first for the **Samsung Z Fold** and
usable on any Android 11+ device. The reading experience follows the device
posture: full pages or a two-page spread unfolded, panel-by-panel **Guided
View** on the cover screen, and **enlarged speech bubbles** that keep the text
readable on the small screen. Bilingual UI (Spanish / English), black,
graphite and paper themes.

Kapow reads comic archives you already own (`.cbz`, `.cbr` and `.pdf`). It is
not a store and downloads nothing.

## Features

- Library from a folder you pick with the Android document picker: covers,
  series grouping, reading progress, search and sort, per-comic settings.
- Reading modes driven by posture (`docs/foldable.md`, `docs/reading-modes.md`):
  single page, two-page spread in landscape, tabletop mode.
- **Guided View**: panels are detected on-device and read one at a time
  (`docs/guided-view.md`).
- **Enlarged speech bubbles**: bubbles are detected on-device, redrawn larger
  and re-anchored so they stay legible on the cover screen
  (`docs/speech-bubbles.md`).
- Both detectors are YOLO models running on a minimal ONNX Runtime build,
  fully offline (`docs/ml-runtime.md`, `docs/training.md`).
- Onboarding on first launch, settings and themes, open-source licences
  screen, and a "Report a visual glitch" action that lets you mail the
  affected page (`docs/glitch-report.md`).
- No accounts, no analytics, no network: `docs/privacy.md`.

## Tech

Kotlin · Jetpack Compose (Material 3) · `androidx.window` · Coil · Room ·
DataStore · Hilt · ONNX Runtime · 7-Zip-JBinding · `PdfRenderer`.
AGP 9, Gradle 9.7, compileSdk 37, minSdk 30, `arm64-v8a` only.

## Build

Prerequisites:

- A JDK 17+ on `JAVA_HOME` to run Gradle (Android Studio's bundled JBR works;
  the Makefile points at it on macOS).
- The Android SDK with platform 37, found through `ANDROID_HOME` or a
  `local.properties` with `sdk.dir=`.
- An `arm64-v8a` device or emulator: the app ships only that ABI, so x86
  emulator images will not install it. The Pixel Fold AVD on Apple silicon
  is arm64 and works.

No keystore, service account or private file is needed for a debug build; the
release signing config is only picked up when `local.properties` provides it
(`docs/release.md`).

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # unit tests (~10 s)
./gradlew :app:installDebug         # install on the connected device
```

The debug build installs as `com.sanchezpaco.kapow.debug` next to the release
`com.sanchezpaco.kapow`.

### Makefile

`make` wraps the same Gradle targets for day-to-day work on macOS; run `make
help` for the list. The ones used most:

| Target | What it does |
| --- | --- |
| `make deploy` | build the debug APK, install it and launch the app |
| `make test` | unit tests |
| `make release` / `make deploy-release` | release APK signed with the upload key |
| `make bundle` / `make deploy-bundle` | signed AAB, or the AAB installed through `bundletool` |
| `make publish-internal` | upload the AAB to the Play internal track with fastlane |
| `make devices` | list connected devices |

With more than one device connected pass `DEVICE=<serial>`.

## Trying it

1. Launch a foldable AVD (for example `Pixel Fold`) or connect a device.
2. `make deploy` (or `./gradlew :app:installDebug`).
3. Follow the onboarding: pick the folder that holds your comics. A sample
   comic ships with the app so the reader works with an empty folder.
4. Fold the device or shrink the window to see Guided View; rotate to
   landscape unfolded for the two-page spread; toggle the bubble icon in the
   reader HUD for enlarged bubbles.

## Tests and tooling

- Unit tests live in `app/src/test`; `./gradlew :app:testDebugUnitTest` runs
  them, and CI (`.github/workflows/build.yml`) runs the same build.
- Some JVM tests are offline visualizers gated by environment variables
  (`KAPOW_PANEL_VIZ_DIR`, `KAPOW_RECTS_DUMP_DIR`): they draw the panel and
  bubble detections over a folder of pages without a device. They are skipped
  otherwise. See `docs/guided-view.md` and `docs/speech-bubbles.md`.
- `app/src/androidTest` holds on-device benchmarks and dumps for the
  detectors, not release gates.
- `tools/ort/` rebuilds the minimal ONNX Runtime AAR; `tools/training/`
  retrains and exports the two detectors (`docs/training.md`).

## Project layout

```
app/src/main/java/com/comicify/
├── core/         ui (theme, shared controls), window (posture), storage (Room), input, util
├── domain/model  posture-agnostic reading models
└── feature/      library · reader · settings · onboarding, each split ui → domain → data
docs/             one file per concern, indexed in docs/README.md
tools/            ort (runtime build) · training (model pipeline)
fastlane/         Play metadata and upload
```

The Kotlin package stays `com.comicify` (the project's original name) so that
installed data and the Room database keep working; the product, application
id and repository are Kapow.

Architecture details are in `docs/architecture.md`; the code rules every
change follows are in `CLAUDE.md`; how to contribute in `CONTRIBUTING.md`.

## Licence

Kapow is free software under the [GNU AGPL-3.0](LICENSE). The panel and
speech-bubble models were trained with Ultralytics YOLO (AGPL-3.0) on a
private corpus that is not distributed; the app ships only the resulting
weights and runs them with ONNX Runtime. Third-party attributions are listed
in Settings → Open-source licences.
