# Onboarding

`OnboardingScreen` (`feature/onboarding/ui`) is the first thing a fresh
install shows. `KapowRoot` renders it whenever the `onboarding_seen` flag in
`ReaderPreferencesRepository` (DataStore `reader_preferences`) is false and no
comic is being opened; a `VIEW` intent (opening a file from another app) skips
it and goes straight to the reader. The root draws nothing until the flag has
been read, so there is no library-then-onboarding flash.

Three steps in a `HorizontalPager`, each drawn with Compose primitives (no
image assets):

1. **Folder** — "Choose folder" launches the same `OpenDocumentTree` picker as
   the library and feeds `LibraryViewModel.onFolderPicked`; the note says the
   sample comic is already on the shelf and the folder can be chosen later.
2. **Gestures** — a page with the two tap-zone bands (20 % each side, the
   values from `TapZones`), chevrons, and the centre tap; the copy also covers
   pinch / double-tap zoom and swipe.
3. **Modes** — Guided View and enlarged bubbles, with the same icons as the
   reader HUD toggles, and the "Start reading" button.

"Skip" (top right) and "Start reading" both call `OnboardingViewModel.finish()`,
which sets the flag; the screen leaves through the root's normal fade.
Back moves to the previous step and does nothing on the first one.

**Show it again**: Settings → About → "Show the introduction again" only resets
the flag (`AppSettingsViewModel.onReplayOnboarding`); the root reacts and shows
the onboarding, and finishing it returns to the library. There is no separate
navigation path.

Strings live under `onboarding_*` in `values/` and `values-es/`.
