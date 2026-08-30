# Release

How a signed build reaches Google Play. Application id: `com.sanchezpaco.kapow`
(debug installs as `com.sanchezpaco.kapow.debug` next to it).

## Signing

Play App Signing holds the app signing key; we only hold the **upload key**.
It lives outside the repo at `~/.android/kapow-upload.jks` (alias `upload`,
RSA 2048, generated with `keytool -genkeypair`). Losing it is recoverable: Play
Console can register a new upload key.

`app/build.gradle.kts` reads the key from `local.properties` (git-ignored):

```
kapow.keystore.path=/Users/<you>/.android/kapow-upload.jks
kapow.keystore.storePassword=...
kapow.keystore.keyAlias=upload
kapow.keystore.keyPassword=...
```

Each entry falls back to an environment variable (`KAPOW_KEYSTORE_PATH`,
`KAPOW_KEYSTORE_STOREPASSWORD`, `KAPOW_KEYSTORE_KEYALIAS`,
`KAPOW_KEYSTORE_KEYPASSWORD`). Without a path the release build stays
unsigned; nothing falls back to the debug key.

## Targets

- `make release` / `make deploy-release` — release APK signed with the upload
  key (R8 + resource shrinking), installed with `adb install`.
- `make bundle` — `bundleRelease`, the `.aab` to upload
  (`app/build/outputs/bundle/release/app-release.aab`).
- `make deploy-bundle` — derives a universal APK from the bundle with
  `bundletool` (`~/.android/tools/bundletool.jar`, downloaded from the GitHub
  releases) and installs it. This is the closest local approximation to what
  Play delivers; bundletool re-signs the derived APK with the debug keystore,
  which only matters for installation.
- `make publish-internal` — `fastlane supply` to the internal testing track.
  Needs a Play service account JSON referenced from `local.properties` as
  `kapow.play.serviceAccountJson=/path/to/key.json`, and the app already
  created in Play Console with its first bundle uploaded by hand (the API
  cannot create apps or make the first upload).

## Closing a version

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, add the
version's section to `CHANGELOG.md` (user-facing changes only, Keep a
Changelog headings) and the Play "What's new" text as
`fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` in both
locales, commit as "Close version X.Y.Z" and tag `vX.Y.Z`.

## Checks after a release build

- `apksigner verify --print-certs` on the APK shows `CN=Kapow`; the bundle
  passes `jarsigner -verify`.
- `unzip -l app-release.aab | grep base/lib/` lists only `arm64-v8a`
  (the `abiFilters` survive bundling).
- `app/build/outputs/mapping/release/mapping.txt` maps every
  `net.sf.sevenzipjbinding.*` and `ai.onnxruntime.*` class to itself: both
  native libraries resolve those names in `JNI_OnLoad` and crash otherwise
  (`app/proguard-rules.pro`).
- Open a CBZ, a CBR and a PDF on the installed bundle-derived APK, with
  Guided View and enlarged bubbles toggled.

## Launcher icon and store icon

The icon is a white speech bubble with a Luckiest Guy "K" (yellow `#FFC107`
→ red `#FF3D45`, ink contour and offset) over a tilted comic-page grid in
blue, chosen 2026-08-29 after several playground rounds (shout balloon, panel
gutters, halftone K were rejected). `tools/store_assets/icon.py` is the single
source: it bakes the K glyph into path data (font in `tools/store_assets/fonts/`,
Apache-2.0) and writes `res/drawable/ic_launcher_{background,foreground,monochrome}.xml`;
`--png` also rasterises the 512 px store icon (with the per-panel halftone the
vector omits, invisible at launcher sizes) to
`fastlane/metadata/android/{en-US,es-ES}/images/icon.png` through headless
Chrome. Edit the constants at the top of the script, never the XML. Two VectorDrawable
facts the script encodes: a `<path>` whose fill is an `<aapt:attr>` gradient
drops its `strokeColor`, so the K is a gradient-fill path plus a separate
stroke-only path; and launchers show only the inner 72 of the 108 dp, so the
foreground mark is scaled by 72/90 to look like the 512 px store icon.

## Feature graphic and screenshots

`tools/store_assets/feature_graphic.py` renders the 1024×500 feature graphic
for `en-US` and `es-ES`: the icon's stage, the bubble mark and a "KAPOW!"
wordmark as a lockup on the left, the slogan ("Your collection, always with
you" / "Tu colección, siempre contigo", chosen 2026-08-29) and the format list
as comic caption boxes (Archivo Bold, OFL, `tools/store_assets/fonts/`), and a page of
the bundled sample (`SAMPLE_PAGE` inside `assets/sample.cbz`) as a tilted
ink-bordered panel on the right. Copy lives in the script's `OUTPUTS` table —
both languages change together. The listing never claims the app is "built
for the Fold": Kapow is sold as a reader for any Android device that adapts
on foldables.

Screenshots use public-domain Golden Age comics (Fiction House's *Planet
Comics* and *Jumbo Comics*, 1940s, copyright not renewed; archive.org item
`planet-comics-011-gm-removed-cbpop` and `jumbo-comics-105-november-1947`).
Never ship screenshots of copyrighted comics. Raw captures live in
`tools/store_assets/raw/<shot>-<phone|tablet>-<lang>.jpg` (release build only —
the debug build shows its build badge); `tools/store_assets/screenshots.py`
frames each one as a tilted ink-bordered panel on the page-grid stage with a
shout title and a caption box, sized 1080×1920 (phone) and 2560×1440 (seven
inch: title and caption on the left, the capture bleeding off the bottom-right
edge). Copy for every shot is the `SHOTS` table. The "bigger bubbles" shot is a
before/after pair: `bubbles-off-phone-<lang>.jpg` (page 4 of the sample, HUD
hidden, bubbles off) and `bubbles-phone-<lang>.jpg` (same page at 1.9×) are
cropped to the same top strip of the page (`COMPARE_STRIP`) and stacked as two
panels with BEFORE/AFTER tags, so the same bubbles appear in both. The
settings capture is pre-cropped to the Screen → Appearance sections so the
About line stays out.

Recapture recipe: `cmd locale set-app-locales com.sanchezpaco.kapow --user 0
--locales es-ES` switches the app language without touching the system; the
Medium Phone AVD gives the 9:20 phone captures, the Fold AVD in landscape
(`settings put system user_rotation 1`) the spread. Tapping the page centre
toggles the HUD; the bubble-size slider under the bubble button goes to 1.9×.
