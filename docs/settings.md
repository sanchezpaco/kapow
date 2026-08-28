# App settings

The gear in the library header opens `AppSettingsScreen`
(`feature/settings/ui`), a full-screen page in the same visual family as the
per-comic settings (`docs/library.md`). It is the only home of the global
preferences; the reader HUD still toggles some of them in place.

## Sections

- **Reading (defaults)** — reading direction, enlarged bubbles on open, bubble
  size, Guided View on open, volume keys turn pages. These are the values a
  comic falls back to when its own setting is "Default"; the per-comic screen
  spells them out ("Default (Off)"). The reader reads them once on open
  (`ReaderViewModel.applyOpenDefaults`) and the bubble scale/direction live
  through `combine` with the per-comic override.
- **Screen** — night tint and keep the screen on while reading
  (`FLAG_KEEP_SCREEN_ON` is now conditional, default on).
- **Library** — the comics folder (its display name), "Choose folder" and
  "Refresh". Scans still run in `LibraryViewModel` so the library shows the
  scanning/error state; the settings screen only shows the spinner on the
  Refresh button while a scan runs.
- **Appearance** — theme picker, below.
- **About** — version name and build label.

Everything is stored in `ReaderPreferencesRepository` (DataStore
`reader_preferences`); the folder Uri stays in `LibraryPreferences`.
`AppSettingsViewModel` combines them into one immutable `AppSettingsUiState`
and writes straight through, so every change is a live preview.

## Theme

`ThemeChoice(ground, accent)` (`core/ui/theme/ThemeChoice.kt`):

- **Ground**: `Black` (pure black, OLED) or `Graphite` (dark grey). Each
  carries its background, surface and raised-surface colours.
- **Accent**: seven curated presets (red, amber, orange, green, cyan, violet,
  pink), all ≥ 4.5:1 against both grounds and the raised surface, plus
  `Dynamic` = Material You `dynamicDarkColorScheme(context).primary`
  (falls back to red below API 31). The default red is `FF3D45`, brighter than
  the old `E62429` so it clears AA on graphite; the old red survives as the
  fixed `danger` colour for destructive actions.

`ComicifyTheme(choice)` derives a `ComicifyPalette` (accent, deep/pale
variants for gradients, accent-tinted inks and raised surface, fixed
secondary amber / good / danger / hairlines) and exposes it as
`ComicifyTheme.palette` (`LocalComicifyPalette`); it also fills
`MaterialTheme.colorScheme` (primary = accent, background = ground,
error = danger) for the Material components and the reader HUD, which only
uses `colorScheme.primary`. `LibraryPalette.kt` keeps its old names
(`Accent`, `Surface2`, `InkDim`, …) as composable getters over the palette so
the library code reads as before. `MainActivity` collects
`ReaderPreferencesRepository.theme` and re-themes the whole tree; the system
bars are transparent so the ground shows behind them.

The swatches are split-diagonal circles: the current ground on the upper-left
half, the accent on the lower-right, a hairline outline so the black half
reads on a black page, and a ring on the selected one. The Material You
swatch shows the resolved wallpaper colour with a sparkle glyph.

The reader keeps its page, ambient backdrop and black chrome regardless of the
theme; only the accent (active toggles, progress, guided stops) follows.
