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
- **About** — version name and build label, "Show the introduction again"
  (`docs/onboarding.md`) and "Open-source licences", which opens
  `LicencesScreen`: a static list of attributions (`attributions` in
  `LicencesScreen.kt` — library or model, licence, URL; tapping a row opens
  the URL). Names and licence identifiers are data, not translated copy. Keep
  it in sync when a dependency or model changes.

## Backup

`android:fullBackupContent="@xml/backup_rules"` (API 30) and
`android:dataExtractionRules="@xml/data_extraction_rules"` (API 31+) back up
only the Room database (`comicify.db`: library, progress, per-comic settings,
detections) and the DataStore files (`files/datastore/`: folder Uri,
preferences, theme, onboarding flag). Covers, the sample comic, the copied
model files and the cache are left out; they are regenerated. After a restore
the document grants are gone, so the library reports `AccessLost` until the
folder is chosen again, and `generateMissingCovers` also regenerates covers
whose file no longer exists.

Everything is stored in `ReaderPreferencesRepository` (DataStore
`reader_preferences`); the folder Uri stays in `LibraryPreferences`.
`AppSettingsViewModel` combines them into one immutable `AppSettingsUiState`
and writes straight through, so every change is a live preview.

## Theme

`ThemeChoice(ground, accent)` (`core/ui/theme/ThemeChoice.kt`):

- **Ground**: `Black` (pure black, OLED), `Graphite` (dark grey) or `Paper`
  (warm cream, `light = true`). Each carries its background, surface and
  raised-surface colours. On Paper the palette swaps to dark inks and black
  translucent hairlines/tracks, every accent is darkened by 45 % so it still
  clears AA on cream, `MaterialTheme` switches to `lightColorScheme` and
  `MainActivity` re-applies `enableEdgeToEdge` with dark status-bar icons.
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

The hero and settings-header glows blend the cover's ambient with the
ground (not black), so they read on paper as well.

The reader keeps its page, ambient backdrop and black chrome regardless of the
theme; only the accent (active toggles, progress, guided stops) follows.
