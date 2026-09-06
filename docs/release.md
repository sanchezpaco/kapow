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

### Stages and the launcher aliases

The page grid comes in five stages — `blue` (the shipped one), `ink` (near-black
page, grey gutters), `red`, `violet` and `mix` (a fixed hue per panel over
blue/red/violet in two shades each, laid out so the tiles inside the launcher's
circle carry colour — the earlier blue/red/violet/ink cycle put the ink tiles
and the darkest shades exactly there and the icon read as grey) — all with the
same bubble, K, halftone and vignette. `STAGES` at the top of `icon.py` is the
whole definition; the script writes `ic_launcher_background_<stage>.xml` plus a
mipmap pair `ic_launcher_<stage>{,_round}.xml` sharing the one foreground and
monochrome. Blue keeps the plain names (`ic_launcher_background.xml`,
`ic_launcher{,_round}.xml`) so the manifest default does not move, and `--png`
still rasterises blue only: **the store icon stays blue** whatever the user
picks.

`MAIN`/`LAUNCHER` does not live on `.MainActivity` any more. Five
`<activity-alias>` elements target it, one per stage, each with its own
`android:icon`/`android:roundIcon` and the same `android:label`; `.LauncherBlue`
ships `android:enabled="true"`, the other four `false`. The launcher shows the
icon of whichever alias is enabled and redraws a moment after the switch (the
picker in Settings → Appearance, `docs/settings.md`). Because the alias carries
the `LAUNCHER` category, `adb shell monkey -p <package> -c
android.intent.category.LAUNCHER 1` keeps working through the enabled alias.
`.MainActivity` stays exported with the `VIEW` filter, so opening a comic from
a file manager is untouched. Manifest components survive R8 as they are: the
release merged manifest under
`app/build/intermediates/merged_manifests/release/` shows the five aliases.

One thing the aliases cannot do: `<activity-alias>` takes no `android:theme`,
so the cold-start splash still runs `Theme.Kapow.Starting` from `.MainActivity`
with `windowSplashScreenAnimatedIcon="@mipmap/ic_launcher"`, and the Compose
overlay repaints the same blue `LogoShapes`. **The splash stays blue** even
when the launcher icon is not; making it follow would mean per-stage colours in
`LogoShapes` as well, not just a manifest attribute (see `docs/splash.md`).

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
`planet-comics-011-gm-removed-cbpop` and `jumbo-comics-105-november-1947`) and
the bundled AURA sample. Never ship screenshots of copyrighted comics. Raw
captures live in `tools/store_assets/raw/<shot>-<phone|tablet>-<lang>.jpg`
(release build only — the debug build shows its build badge);
`tools/store_assets/screenshots.py` frames each one as a tilted ink-bordered
panel on the page-grid stage with a shout title and a caption box, sized
1080×1920 (phone) and 2560×1440 (seven inch: title and caption on the left, the
capture scaled to a fixed width on the right). Copy for every shot is the
`SHOTS` table; the order of the table is the store order, chosen in the
2026-09-06 conversion review (differentiators first: bubbles, Guided View,
shelf, page look, search, details, look; tablets: spread, bubbles on the
spread, Guided View on the spread, wall of covers).

Shots listed in `COMPARE_SHOTS` are stacked pairs with two tagged panels
(BEFORE/AFTER, PAGE/PANEL, ORIGINAL/MORE CONTRAST): `<shot>-off-…jpg` is the top
panel, `<shot>-…jpg` the bottom. Both are cropped to the same top strip of the
page (`page_bounds` finds the page inside the dark reader background); when the
bottom capture is not the same page — Guided View's zoomed panel — the top
panel is the whole page, scaled down to leave the zoomed panel its full width.
The strip shrinks so two panels always fit the canvas. The tablet pair crops the
right-hand page of the spread.

Two raws are pre-cropped before framing: the settings capture to the Appearance
section (background, accents, app icon) and the tablet library to the cover
area, because the tablet layout leaves the right third empty. A phone capture
wider than 960 px after scaling is clamped to that width and centred.

Recapture recipe: `cmd locale set-app-locales com.sanchezpaco.kapow --user 0
--locales es-ES` switches the app language without touching the system; the
`Medium_Phone_API_36.0` AVD (1080×2400) gives the phone captures and the
`Tablet_API_36` AVD (Pixel Tablet, 2560×1600, landscape) the spread, both with
the `/sdcard/Showcase` folder chosen as the library. The AURA sample in that
folder is a copy of `assets/sample.cbz` with a `ComicInfo.xml` (series, number,
year, title, summary) so the shelf and Details show metadata; delete the seeded
sample so it does not appear twice. `settings put global sysui_demo_allowed 1`
plus the `com.android.systemui.demo` broadcasts give the clean 10:00 status
bar. Tapping the page centre toggles the HUD (on the tablet, tap the page, not
the empty half); captures with the HUD hidden carry no locale, so the tablet
reader raws are the same file for both languages. Page look cycles Original →
Brighter → More contrast → Paper from the gear panel; the pair uses Planet
Comics #69 page 3, the yellowest scan in the set, with More contrast — the one
preset that still reads at store-card size (Brighter and Paper were tried and
look like the same image twice).
