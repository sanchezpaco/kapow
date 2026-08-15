# Library

The home of the collection: import comics, browse covers, resume reading.

## Import

- Use the Storage Access Framework (SAF) so the user picks files/folders without
  broad storage permissions.
- On import we register the comic in the library DB and generate a cover
  thumbnail (first page decoded at grid size).
- Files stay where the user put them; we persist a durable URI
  (`takePersistableUriPermission`) rather than copying, unless the user opts to
  import into internal storage.

## Persistence (Room)

```
Comic(
    id, title, sourceUri, format,
    pageCount, coverPath, addedAt,
)

ReadingState(
    comicId, pageIndex, panelIndex, normalizedFocusX, normalizedFocusY,
    updatedAt, completed,
)

PanelCache(
    comicId, pageIndex, rectsJson, isManualOverride,
)
```

- `ReadingState` powers "continue reading" and cross-session position restore
  (see `foldable.md` for the in-memory `ReadingPosition` this maps to).
- `PanelCache` stores detected/edited panels so detection runs once per page.

## Home UI

- Cover grid, adaptive column count from window size class (more columns
  unfolded).
- Each cover shows a reading-progress indicator.
- A "Continue reading" shelf surfaces the most recently read, unfinished comics.
- Sort/filter by series, recently added, recently read.

## Metadata

- Baseline: parse `series`, `issue number`, `year` from the filename with a
  tolerant regex. No network needed.
- Optional enrichment: ComicVine API for descriptions/cover art. Deferred and
  behind a toggle — only if it adds value without noise. No copyrighted content
  is downloaded, only metadata.

## Bilingual

All library strings come from resources (`docs/i18n.md`). Dates and numbers are
formatted with locale-aware formatters, never hand-built strings.
