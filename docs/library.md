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
- A re-scan reconciles the library with the folder in **both** directions: new
  files are inserted, and rows whose document Uri is no longer present are
  removed (their cover and reading state deleted with them). Deleting a comic
  from the folder and refreshing makes it disappear instead of lingering as a
  broken entry. The folder Uri is taken with read **and** write permission so
  the app can also delete the underlying file itself (below).
- Scanning stays responsive: each discovered comic is inserted immediately with
  `pageCount`/`coverPath` left null, and covers fill in afterwards (below).

## Sample comic

- A short bundled comic (`assets/sample.cbz`) gives a freshly installed app
  something to open before the user picks a folder. On first launch
  `seedSampleIfNeeded` copies it into internal storage
  (`filesDir/sample/sample.cbz`) and inserts a library row whose `documentUri`
  is a `file://` Uri, titled from `sample_comic_title`. A `sampleSeeded` flag in
  `LibraryPreferences` guards it: the seed runs exactly once, so deleting the
  sample keeps it gone rather than resurrecting it on the next launch.
- Because its Uri is a locally-managed `file://` (not a SAF `content://`), two
  paths special-case it: a folder re-scan's prune step skips locally-managed
  comics (a SAF scan never lists them, so they must not be treated as missing),
  and **Delete comic** removes the backing file directly instead of through
  `DocumentsContract`. It reads and deletes like any other comic otherwise.

## Persistence (Room)

`ComicifyDatabase` (`core/storage`) holds two tables, exposed as Flows through
`LibraryRepository` and combined into `LibraryComic` domain models for the UI.

```
Comic(
    id, documentUri, displayName,
    series, issueNumber, year,
    pageCount?, coverPath?, coverAmbient?, addedAt, favorite,
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
- The long-press menu also offers **Delete comic**, which after a confirmation
  dialog deletes the file from the folder via
  `DocumentsContract.deleteDocument` and removes its library row, cover, and
  reading state. If the delete fails (e.g. the folder was granted read-only by
  an older pick), the file and its entry are left intact rather than lying about
  a deletion that did not happen; re-picking the folder grants write access.
- Detected panels are cached in-memory per session by the reader's `PageLoader`;
  a persistent panel cache is deferred to a later phase.

## Grouping by series

- A header toggle switches the grid between the flat view and a **grouped** view.
  Grouping is purely visual — no files move, nothing is renamed; it reorganises
  the same `LibraryComic` list. `LibraryCatalog.grouped` buckets comics by a
  normalized `series` key (trimmed, case-insensitive) preserving the sorted
  order. A bucket with **two or more** volumes becomes a `LibraryEntry.Group`
  (a folder card); a lone comic stays a `LibraryEntry.Single` and opens directly.
  Since `series` is the file name with the volume/issue number, year, and volume
  marker stripped (`ComicNameParser`), only names that match apart from the
  number collapse together.
- A group card shows the first volume's cover with a stacked-card backing, a
  count badge, and aggregate read progress. Tapping it drills into a **series
  sub-screen** (a back button plus that series' volumes as normal comic cards);
  the flat/grouped choice and reader navigation are otherwise unchanged.
- The toggle is remembered across restarts (`grouped` in `LibraryPreferences`,
  default off). Filters apply before grouping, so a filtered group that drops to
  one volume renders as a single card.

## Covers

- `CoverGenerator` decodes the first page through `ComicSourceFactory` at grid
  resolution and writes a JPEG to internal storage (`filesDir/covers/{id}.jpg`),
  recording `coverPath`, `pageCount` and `coverAmbient`: the cover's ambient
  colour (same Palette rule as the reader's `ambientColorInt`), stored as an
  ARGB int so the library can tint the hero without decoding the cover again.
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
a green "Completed" badge and favorites an amber star. The most recently read
unfinished comic becomes the **hero**: a wide card washed with the cover's
ambient colour, showing the cover, progress and an estimated time left
(`LibraryCatalog.minutesLeft`, a flat 45 s per page until real reading stats
exist); any other unfinished comics follow in a row of smaller resume cards.

## Home UI

- Cover grid with an adaptive column count (`GridCells.Adaptive`), so wider /
  unfolded windows show more columns.
- Each cover shows a progress ring, a "Completed" badge on finished comics, an
  amber star on favorites and the title (which already carries the series).
- A **filter bar** (All / Unread / Read / Favorites) narrows the grid; the choice
  lives in the ViewModel and resets on relaunch. "Continue reading" only shows
  under the "All" filter.
- **Long-pressing a cover** opens a menu to mark it read/unread and add/remove it
  from favorites.
- The "Continue reading" section: the hero card for the latest unfinished
  comic, then a row for the rest.
- Icon-only affordances to choose/refresh the folder, toggle grouping and open
  a single file; the empty state keeps the large "Choose folder" button.
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
