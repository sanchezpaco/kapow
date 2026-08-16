# Library

The home of the collection: import comics, browse covers, resume reading.

## Import

- The user picks a **comics folder** via the Storage Access Framework
  (`ACTION_OPEN_DOCUMENT_TREE`). We `takePersistableUriPermission` and store the
  tree Uri in DataStore (`LibraryPreferences`), so the folder survives restarts.
- `ComicScanner` walks the tree recursively with `DocumentsContract` (no extra
  dependency), collecting `.cbz/.cbr/.pdf` by extension, or by comic MIME type
  when a provider hides the extension. Extension detection is tolerant of trailing
  suffixes providers append to duplicate downloads (e.g. `Comic.cbr (1)`).
  Subfolders name the series.
- A scan that throws (revoked permission, unreadable provider) is caught in the
  ViewModel: it is logged, surfaced as an error message in the UI, and always
  clears the scanning state instead of leaving an empty shelf and a stuck
  spinner.
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
    pageCount?, coverPath?, addedAt, favorite,
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
- `completed` is also settable by hand from the library: marking a comic read
  writes a `ReadingState` at the last page; marking it unread deletes the row
  (clearing its resume position). `favorite` lives on the comic itself and is
  toggled the same way. Both are reached by long-pressing a cover. The `favorite`
  column was added in schema **v2** (migration `1→2`).
- Detected panels are cached in-memory per session by the reader's `PageLoader`;
  a persistent panel cache is deferred to a later phase.

## Covers

- `CoverGenerator` decodes the first page through `ComicSourceFactory` at grid
  resolution and writes a JPEG to internal storage (`filesDir/covers/{id}.jpg`),
  recording `coverPath` and `pageCount`.
- Runs lazily and asynchronously after a scan, so titles appear instantly and
  thumbnails stream in. A comic with no decoded cover yet (or one that cannot be
  decoded) shows a **procedural cover** instead of an empty box: a gradient keyed
  to the series name, a large faded monogram, a comic halftone-dot overlay and the
  title — so PDFs and freshly-scanned comics still read as cover art.

## Visual identity

The shelf uses a "Comic Red & Ink" palette local to the library UI: pure-black
OLED ground, a comic-red accent (matching the reader theme's `primary`) with an
amber highlight and green for completed. Progress shows as a red ring on the cover
corner (percent) and a red→amber bar under in-progress comics; finished comics get
a green "Completed" badge and favorites an amber star. A "Continue reading" row
surfaces recent unfinished comics with a resume affordance and progress.

## Home UI

- Cover grid with an adaptive column count (`GridCells.Adaptive`), so wider /
  unfolded windows show more columns.
- Each cover shows a reading-progress indicator ("n / total" + bar), a
  "Completed" badge on finished comics, and an amber star on favorites.
- A **filter bar** (All / Unread / Read / Favorites) narrows the grid; the choice
  lives in the ViewModel and resets on relaunch. "Continue reading" only shows
  under the "All" filter.
- **Long-pressing a cover** opens a menu to mark it read/unread and add/remove it
  from favorites.
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
