# Library

The home of the collection: import comics, browse covers, resume reading.

## Import

- The user picks a **comics folder** via the Storage Access Framework
  (`ACTION_OPEN_DOCUMENT_TREE`). We `takePersistableUriPermission` and store the
  tree Uri in DataStore (`LibraryPreferences`), so the folder survives restarts.
- `ComicScanner` walks the tree recursively with `DocumentsContract` (no extra
  dependency), collecting `.cbz/.cbr/.pdf`. Subfolders name the series.
- Files stay where the user put them; only a durable document Uri is persisted,
  never a copy. A single file can still be opened directly (`OPEN_DOCUMENT`)
  without adding it to the library.
- Scanning stays responsive: each discovered comic is inserted immediately with
  `pageCount`/`coverPath` left null, and covers fill in afterwards (below).

## Persistence (Room)

`ComicifyDatabase` (`core/storage`) holds two tables, exposed as Flows through
`LibraryRepository` and combined into `LibraryComic` domain models for the UI.

```
Comic(
    id, documentUri, displayName,
    series, issueNumber, year,
    pageCount?, coverPath?, addedAt,
)

ReadingState(
    comicId, pageIndex, completed, updatedAt,
)
```

- `documentUri` is unique; re-scans ignore comics already registered.
- `ReadingState` powers "continue reading" and cross-session position restore
  (see `foldable.md` for the in-memory `ReadingPosition` this maps to). The
  reader seeds its initial page from it and, on each page turn, saves the index
  and marks `completed` at the last page.
- Detected panels are cached in-memory per session by the reader's `PageLoader`;
  a persistent panel cache is deferred to a later phase.

## Covers

- `CoverGenerator` decodes the first page through `ComicSourceFactory` at grid
  resolution and writes a JPEG to internal storage (`filesDir/covers/{id}.jpg`),
  recording `coverPath` and `pageCount`.
- Runs lazily and asynchronously after a scan, so titles appear instantly and
  thumbnails stream in. A comic whose cover cannot be decoded keeps a null cover
  and shows a placeholder.

## Home UI

- Cover grid with an adaptive column count (`GridCells.Adaptive`), so wider /
  unfolded windows show more columns.
- Each cover shows a reading-progress indicator ("n / total" + bar) and a
  "Completed" badge on finished comics.
- A "Continue reading" shelf surfaces the most recently read, unfinished comics.
- Affordances to choose/refresh the folder and to open a single file.
- Entries are naturally sorted by series, then issue number, then title.

## Metadata

- Baseline: `ComicNameParser` parses `series`, `issue number`, `year` from the
  filename with a tolerant, unit-tested pure function. No network needed. Series
  falls back to the containing folder name when the filename yields none.
- Optional enrichment: ComicVine API for descriptions/cover art. Deferred and
  behind a toggle — only if it adds value without noise. No copyrighted content
  is downloaded, only metadata.

## Bilingual

All library strings come from resources (`docs/i18n.md`). Dates and numbers are
formatted with locale-aware formatters, never hand-built strings.
