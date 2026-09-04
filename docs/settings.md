# App settings

The gear in the library header opens `AppSettingsScreen`
(`feature/settings/ui`), a full-screen page in the same visual family as the
per-comic settings (`docs/library.md`). It is the only home of the global
preferences; the reader HUD still toggles some of them in place.

## Shape of the page

Every section is a **grouped surface**: the accent eyebrow + title + hairline
(`core/ui/SectionHeader.kt`) sits above a raised card (`palette.raised`, 20 dp
radius, no shadow) whose rows are separated by hairlines. The row vocabulary
lives in `core/ui/SettingsControls.kt` and is shared with the per-comic screen:

- `SettingsRow` / `SettingsStackedRow` — 56 dp minimum height, 16 dp horizontal
  padding, label (`bodyLarge`) with optional muted supporting text
  (`bodySmall`); the control sits in the trailing slot, or under the label when
  it is a cluster of buttons or tiles.
- `SettingsSwitchRow` — every boolean. The whole row is `toggleable`; the
  `Switch` is decorative (`onCheckedChange = null`) with the accent as the
  checked track. On/Off chip pairs are gone: a selected "Off" in red read as a
  warning.
- `SettingsChoiceRow` — mutually exclusive values as chips. Label and chips sit
  in a `FlowRow` of at most two items, so the chips ride in the trailing slot
  when they fit and wrap to their own line when they do not. The selected chip
  is accent at 16 % **plus a 1 dp accent border** and an accent semibold label,
  which is what makes it readable on the Paper ground; unselected chips use
  `palette.track` so they still separate from the raised card.
- `SettingsActionRow` — a navigation row with a trailing chevron.
- `BubbleScaleRow` — label left, current value as a small accent pill with
  tabular figures, the slider on its own line under it with the two extremes as
  muted `labelSmall` captions.

**Width.** Below 840 dp the sections are one centred column capped at 640 dp.
From 840 dp (the unfolded Fold) they split into two columns of at most 480 dp
each — Reading + Screen on the left, Library + Appearance + About on the right —
centred as a block, so the unfolded screen is no longer 40 % empty.

The scroll position is hoisted into `KapowRoot` (`appSettingsScroll`) because
`AnimatedContent` drops the screen from composition when the Licences page
opens; coming back now lands where you left.

## Sections

- **Reading (defaults)** — reading direction (choice), enlarged bubbles on open
  (switch), bubble size (slider), Guided View on open (switch), volume keys turn
  pages (switch). These are the values a comic falls back to when its own
  setting is "Default"; the per-comic screen spells them out ("Default (Off)",
  "Default (Pages)"). The reader reads them once on open
  (`ReaderViewModel.applyOpenDefaults`) and the bubble scale/direction live
  through `combine` with the per-comic override.
- **Screen** — night tint and keep the screen on while reading
  (`FLAG_KEEP_SCREEN_ON` is now conditional, default on).
- **Library** — one row: the comics folder with its display name as supporting
  text, and under it "Choose folder" as a tonal accent button and "Refresh" as a
  quiet text button. Solid red stays reserved for destructive actions. Scans
  still run in `LibraryViewModel` so the library shows the scanning/error state;
  the settings screen only shows the spinner on the Refresh button while a scan
  runs.
- **Appearance** — theme picker, below.
- **About** — version name and build label as a muted line at the top of the
  surface, then chevron rows: "Show the introduction again"
  (`docs/onboarding.md`), "Open-source licences" and "Source code on GitHub"
  (opens the repository in the browser). Licences opens
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

`KapowTheme(choice)` derives a `KapowPalette` (accent, deep/pale
variants for gradients, accent-tinted inks and raised surface, fixed
secondary amber / good / danger / hairlines) and exposes it as
`KapowTheme.palette` (`LocalKapowPalette`); it also fills
`MaterialTheme.colorScheme` (primary = accent, background = ground,
error = danger) for the Material components and the reader HUD, which only
uses `colorScheme.primary`. `LibraryPalette.kt` keeps its old names
(`Accent`, `Surface2`, `InkDim`, …) as composable getters over the palette so
the library code reads as before. `MainActivity` collects
`ReaderPreferencesRepository.theme` and re-themes the whole tree; the system
bars are transparent so the ground shows behind them.

The ground is picked from three **mini-preview tiles**: a rounded rectangle
painted in that ground's own background with a dot in the current accent, the
name under it, and an accent ring on the selected one — you see the page you are
choosing instead of reading a word for it.

The accent swatches are split-diagonal circles: the current ground on the
upper-left half, the accent on the lower-right, a hairline outline so the black
half reads on a black page, and a ring on the selected one. The Material You
swatch shows the resolved wallpaper colour with a sparkle glyph.

The hero and settings-header glows blend the cover's ambient with the
ground (not black), so they read on paper as well.

The reader keeps its page, ambient backdrop and black chrome regardless of the
theme; only the accent (active toggles, progress, guided stops) follows.
