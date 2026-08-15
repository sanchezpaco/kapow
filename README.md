# Comicify

A native Android comic reader built for personal use, designed first for the
**Samsung Z Fold**. The reading experience adapts to device posture: full pages
unfolded, a two-page spread in landscape, and panel-by-panel guided reading on
the cover screen. Bilingual UI (Spanish / English), pure-black OLED theme.

Comicify reads comic archives you already own (`.cbz` today; `.cbr` and `.pdf`
planned). It is not a store and downloads no copyrighted content.

## Status

Phase 0 (skeleton) and the Phase 1 MVP are working: open a `.cbz`, read it
full-page with pinch-zoom, and it already switches to a two-page spread when the
device is unfolded to landscape. See `ROADMAP.md` for what is next and `docs/`
for how each part works.

## Tech

Kotlin · Jetpack Compose (Material 3) · `androidx.window` · Coil · Room ·
DataStore · Hilt. AGP 9, Gradle 9.7, compileSdk 37, minSdk 30.

## Build & run

Requires the Android SDK and a JDK 17+ to run Gradle (Android Studio's bundled
JBR works). A JDK 17 toolchain is resolved automatically for compilation.

```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Run the unit tests
./gradlew :app:testDebugUnitTest

# Install on a connected device or emulator
./gradlew :app:installDebug
```

The debug build uses the `com.comicify.debug` application id.

### Trying it on a foldable emulator

1. Launch a foldable AVD (e.g. `Pixel_10_Pro_Fold`).
2. `./gradlew :app:installDebug`
3. Open the app, tap **Open comic**, and pick a `.cbz` from storage.
4. Rotate to landscape (unfolded) to see the two-page spread.

## Project layout

```
app/src/main/java/com/comicify/
├── core/ui/theme      Design system (theme, colors)
├── core/window        Foldable posture tracking + derivation
├── core/util          Shared helpers (natural sort)
├── domain/model       Posture-agnostic reading models
├── feature/reader     Reader surfaces, ViewModel, CBZ source, page loader
└── feature/library    Home screen and comic import (SAF)
```

See `CLAUDE.md` for the code rules this project follows.
