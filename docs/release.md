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
