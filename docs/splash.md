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
   `KapowRoot` from the first frame. Its first frames repaint exactly the same
   icon, so the handover from the system splash is invisible. After three
   warm-up frames (the library composes underneath during them) a linear
   1 300 ms clock drives the sequence:
   - 0–140 ms: the icon grows and fades out.
   - 0–640 ms: the ten page panels draw their keylines in white, staggered
     35 ms apart (dash-phase stroke), then the fills come in in cascade and
     the gutter blue and vignette fade in; at 620 ms the keylines turn to ink.
   - 380–720 ms: the bubble pops with squash & stretch and overshoot.
   - 560–780 ms: the K slams from 4× to 1×, followed by a burst of impact
     lines.
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
the 1.3 s sequence. On the emulators the OS splash itself lingers for several
seconds before the process starts; that is the emulator, not the app.
