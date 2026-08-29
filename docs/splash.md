# Cold-start splash

Kapow plays a short animation in the logo's language the first time the
process is launched. It never plays when the app resumes from the background,
when the activity is recreated, or when a comic is opened through a `VIEW`
intent.

## How it plays

1. **System splash** (`Theme.Kapow.Starting`, `core-splashscreen`): the
   launcher icon inside its 192 dp circle on black, drawn by the OS while the
   process starts. `MainActivity` calls `installSplashScreen()` and hands
   over to `Theme.Kapow`.
2. **Compose overlay** (`core/ui/splash/SplashOverlay.kt`), drawn on top of
   `KapowRoot` from the first frame. Its first frame repaints exactly the same
   icon at the same place and size, so the handover from the system splash is
   invisible, and the icon never disappears: it becomes the animation. After
   three warm-up frames (the library composes underneath during them) a
   linear 1 300 ms clock drives the sequence:
   - 0–620 ms: the icon's circle opens until it covers the screen while the
     page inside grows to full size; the panels that were hidden outside the
     circle draw their keylines in white (dash-phase stroke, staggered 30 ms)
     and fill in cascade, turning to ink at 700 ms. The four panels already
     visible in the icon stay as they are.
   - 300–760 ms: the bubble and the K grow from icon size to hero size with
     squash & stretch and overshoot, followed by a burst of impact lines.
   - 1 000–1 300 ms: a diagonal wipe with a white edge reveals the app.

Every shape and colour comes from `LogoShapes.kt`, the same values as
`tools/store_assets/icon.py`, so the splash and the icon are one object.
The timing lives in `SplashTimeline.kt` as pure functions of the clock.

## Gating

`ColdStartSplash.claim(context)` returns `true` once per process and only
when the system animator scale is above zero, so "Remove animations" skips
straight to the app. `MainActivity` also skips it when it was started with a
`VIEW` intent.

## Verifying

Record a cold start and inspect the frames:

```
adb -s <device> shell am force-stop com.sanchezpaco.kapow.debug
adb -s <device> shell "screenrecord --time-limit 12 /sdcard/splash.mp4" &
adb -s <device> shell monkey -p com.sanchezpaco.kapow.debug -c android.intent.category.LAUNCHER 1
```

Then `ffmpeg -i splash.mp4 -vf fps=30 %03d.png` and drop the frames identical
to the previous one: a healthy run shows twenty or more distinct frames for
the 1.3 s sequence (the Z Fold cover screen shows about thirty). On the emulators the OS splash itself lingers for several
seconds before the process starts; that is the emulator, not the app.
