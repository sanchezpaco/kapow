# Library

The home of the collection: import comics, browse covers, resume reading.

## Import

- The user picks a **comics folder** via the Storage Access Framework
  (`ACTION_OPEN_DOCUMENT_TREE`). We `takePersistableUriPermission` and store the
  tree Uri in DataStore (`LibraryPreferences`), so the folder survives restarts.
- `ComicScanner` walks the tree recursively with `DocumentsContract` (no extra
  dependency), collecting `.cbz/.cbr/.cb7/.cbt/.7z/.tar/.pdf` by extension, or by comic MIME type
  when a provider hides the extension. Extension detection is tolerant of trailing
  suffixes providers append to duplicate downloads (e.g. `Comic.cbr (1)`).
  Subfolders name the series.
- A scan that throws is caught in the ViewModel, logged, surfaced as a
  `LibraryScanError` in the UI (`AccessLost` for `SecurityException`, i.e. the
  tree grant vanished after a reinstall; `ReadFailure` for anything else), and
  always clears the scanning state instead of leaving an empty shelf and a
  stuck spinner.
- Files stay where the user put them; only a durable document Uri is persisted,
  never a copy. A single file can still be opened directly (`OPEN_DOCUMENT`)
  without adding it to the library.
- A re-scan reconciles the library with the folder in **both** directions: new
  files are inserted, and rows whose document Uri is no longer present are
  removed (their cover and reading state deleted with them). Deleting a comic
  from the folder and refreshing makes it disappear instead of lingering as a
  broken entry. The folder Uri is taken with read **and** write permission so
  the app can also delete the underlying file itself (below). A file that only
  moved or was renamed is **relinked**, not re-added: see *Identity* below.
- Scanning stays responsive: each discovered comic is inserted immediately with
  `pageCount`/`coverPath` left null, and covers fill in afterwards (below).

### When the library rescans

- **Automatically, whenever the app comes to the foreground.** `KapowRoot`
  hooks `Lifecycle.Event.ON_START` (`LifecycleEventEffect`) and calls
  `LibraryViewModel.onForeground()`, so a file dropped into the folder while the
  app sat in the background is on the shelf by the time the user looks at it,
  without touching refresh. The trigger sits in `KapowRoot`, not in
  `LibraryScreen`: the root stays composed across internal navigation, so
  closing the reader or leaving the settings screen does **not** rescan and
  going back from a page turn stays instant. Because the activity declares
  `configChanges` for orientation/size, folding and unfolding does not restart
  it either.
- The rescan is silent: it never blanks the grid — the shelf keeps rendering the
  comics already in Room while the scan runs, and the only feedback is the
  header's small "Scanning" spinner row.
- It is skipped when no folder is chosen (the folder Uri is read first, so a
  folder-less install does not even flip the scanning flag) and when a scan is
  already running, so overlapping scans are impossible.
- **Manually**, from Settings → Comics folder → Rescan (`onRefresh`), and
  implicitly when a folder is picked (`setFolder` scans the new tree).

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

`KapowDatabase` (`core/storage`) holds the library tables, exposed as Flows
through `LibraryRepository` and combined into `LibraryComic` domain models for
the UI. Per-comic reading settings live in their own table keyed by document
Uri (below); page detections are documented in `docs/ml-runtime.md`.

```
Comic(
    id, documentUri, displayName,
    series, issueNumber, year,
    pageCount?, coverPath?, coverPage, coverAmbient?, addedAt, favorite,
    storyTitle?, publisher?, writer?, penciller?, inker?, colorist?,
    summary?, readingType?, metadataVersion,
    rating, metadataEdited,
)

ReadingState(
    comicId, pageIndex, completed, updatedAt, shelved,
)

ComicSettings(
    documentUri, readingType?, coverAlone, bubblesEnlarged?, guided?,
    bubbleScale?, splitWidePages, splitSuggested, verticalScroll,
)

ReadingSession(
    id, comicId, startedAt, endedAt, pages, mode, finished,
)

Bookmark(
    comicId, pageIndex, createdAt,
)

ReadingList(
    id, name, createdAt,
)

ReadingListEntry(
    listId, comicId, ordering,
)
```

- `documentUri` is unique; re-scans ignore comics already registered.
- `contentHash` is the comic's real identity (below). It was added in schema
  **v13** (migration `12→13`), nullable and indexed, so an upgraded library
  keeps every row and fills the hashes in lazily.
- `readingType` on both `comics` and `comic_settings` replaced the older
  `readsRightToLeft` / `rightToLeft` booleans in schema **v19** (migration
  `18→19`, which rebuilds both tables). The migration preserves the setting —
  `1` becomes `'Manga'`, `0` becomes `'Comic'`, `NULL` stays `NULL` — so an
  upgraded library keeps reading manga right-to-left.
- `ReadingList` and its join table `ReadingListEntry` hold the reading lists
  (below). They arrived in schema **v17** (migration `16→17`). The entry is keyed
  by the **comic row id**, not by `documentUri`, so a relinked file (renamed or
  moved) keeps its place in every list for free. Both foreign keys cascade:
  deleting a list drops its entries, and pruning a comic drops it from every
  list without any code in the prune path.
- `ReadingSession` records one row per reading session and feeds the stats
  screen and the hero's estimate. It cascades on the comic and is pruned after
  400 days; see `docs/stats.md`.
- `Bookmark` holds the pages the reader marked, one row per page, primary key
  `(comicId, pageIndex)`, added in schema **v15**. It is keyed by the comic row
  id, not the document Uri, so bookmarks survive a rename or move, and it
  cascades on the comic so deleting a comic takes its bookmarks with it. There is
  no cap and nothing global: bookmarks belong to one comic. Toggling **split wide
  pages** rewrites them through the same logical page → source page → first half
  conversion the reader uses to keep the reading position, so two halves of a
  split page merge back into one bookmark. The reader shows them on the counter
  row and in the thumbnail scrubber (`docs/reading-modes.md`); the per-comic
  screen shows the count as a detail row.
- `ReadingState` powers "continue reading" and cross-session position restore
  (see `foldable.md` for the in-memory `ReadingPosition` this maps to). The
  reader seeds its initial page from it and, on each page turn, saves the index
  and marks `completed` at the last page. `completed` is sticky: once set it stays
  true on later saves even if the reader pages back into the comic, until the
  comic is explicitly marked unread.
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
- The `ComicInfo.xml` columns (`storyTitle` … `readingType`) and
  `metadataVersion` were added in schema **v11** (migration `10→11`). They are
  all nullable except `metadataVersion`, which defaults to 0 so every existing
  row is re-read once by the cover pass (below). Nothing else is touched, so a
  library upgraded in place keeps its covers and reading positions.
- `rating` (0 = unrated, 1..5) and `metadataEdited` were added in schema **v14**
  (migration `13→14`), both `NOT NULL DEFAULT 0`, so an upgraded library keeps
  every row unrated and unedited. `rating` is set from the Details section's
  star row — tapping the star that is already the rating clears it back to 0 —
  and is per comic: a series settings screen hides the Details section, so it
  never rates a whole stack. "Highly rated" means `rating >= 4`
  (`LibraryCatalog.highlyRated`).
- Detected panels are cached in-memory per session by the reader's `PageLoader`;
  a persistent panel cache is deferred to a later phase.

## Identity

Every table keys a comic by its `documentUri` (`comics`, `comic_settings`,
`page_detections`; `reading_states`, `reading_session` and `reading_list_entry`
hang off the comic `id`). A document Uri encodes the file's path, so renaming or moving a file —
or re-picking the folder after a lost grant — hands the scan a Uri it has never
seen and would strand progress, favourites, per-comic settings, cached
detections and the cover on a row whose file is gone.

The **content hash** is what actually identifies a comic, the same rule
YACReader uses: `SHA-1(first 512 KB of the file + the file size)`
(`ComicIdentity.hash`, pure; `ComicHasher` does the reading). The prefix keeps
it cheap on a 70 MB archive, and the size makes two files that share an opening
block distinct.

### Relinking

`documentUri` stays the row key; the scan reconciles by hash instead
(`LibraryReconciler.reconcile`, pure domain, given the arrivals and the rows
whose file is gone):

- Only Uris **not already in the database** are hashed, on `Dispatchers.IO`. A
  rescan of a library with no new files reads no bytes at all, so the automatic
  foreground rescan stays as cheap as it was.
- An arrival whose hash matches a missing row is that comic, moved or renamed:
  `LibraryRepositoryImpl.relinkComic` rewrites the Uri on the comic, its
  settings and its detections in one Room transaction, and re-derives the
  name-based fields (`displayName`, `series`, `issueNumber`, `year`) from the
  new file name, resetting `metadataVersion` so the next pass re-reads
  `ComicInfo.xml` over them. The row id never changes, so progress, favourite,
  sessions and the cover file follow for free.
- Duplicates are handled one for one: two identical files consume two missing
  rows, and a third copy is added as a new comic.
- Only what is left after that is removed. Ordering the scan
  *relink → add → remove* is what makes a rename survive: pruning first would
  delete the row before its file could be recognised under the new name.
- A scan that throws (`AccessLost`, i.e. the grant vanished) still prunes
  nothing, so rows survive until a folder is picked again and the hashes can
  match the files back to them. Only a **successful** scan that lists the folder
  and finds no file with a row's hash removes it.
- Rows that predate the feature, or whose hash could not be read, simply do not
  relink; they are added and removed by Uri exactly as before.

Hashes are backfilled lazily by `fillMissingDetails` (the cover pass), which
already walks every comic after a scan: a row with no hash gets one there, while
its file is still where the library expects it. Nothing hashes a comic twice.

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

## Reading lists

An ordered, cross-series list — a crossover, a re-read, anything the file names
cannot express. Lists are hand-ordered, never re-sorted.

- **Where they live in the UI:** the shelf title is a **switcher**, not a label.
  Tapping "All comics ▾" opens a dropdown with "All comics", every list (with
  its comic count) and "New list…". There is no toolbar icon and no tab — a
  folded 360 dp row is already full. With no lists at all, the dropdown holds
  only "New list…".
- While a list is open the eyebrow reads `LIST · 3/12 read` (the same
  `library_group_read` the series header uses, over the list's comics) and the
  trailing slot swaps the Recent sort toggle for the list "⋮" (rename, delete):
  a hand-ordered list has no sort. The grid shows the list in `ordering`, filter
  chips and search still narrow it, "Continue reading" collapses
  (`continueReadingVisible` also requires `openedList == null`) and grouping is
  ignored — a crossover is exactly what you do not want folded back into series
  stacks. The Layers toggle stays enabled and applies again on All comics.
- **Adding** is `Add to list…`, and it always works on a *set* of comics: the
  selection row's first square (one comic or twenty — see "Selection mode"), or
  the series menu, which passes the whole group in its shelf order. The dialog
  is headed by the comic's title (one comic), by the series name with the
  selected count under it (from a series), or by the count itself (from the
  selection row), and holds a **`TriStateCheckbox`** per list plus a "New list…"
  row that swaps the body for one text field — and while that field is up the
  dialog retitles itself *New list*, with the count moving to the subtitle, so
  it never reads "2 selected" over a name box. Creating a list from that dialog creates it *and*
  adds every comic in order. The box is `On` when all of the payload is already
  in that list, `Indeterminate` when only some of it is and `Off` when none is
  (`ReadingListMembership.of`); tapping `Off` or `Indeterminate` adds the ones
  that are missing (`missing`, in the given order), tapping `On` removes them
  all — a plain checkbox would have silently misreported a mixed selection.
  There is no snackbar: the box is the confirmation and unticking is the undo.
  Names are free text, trimmed, duplicates allowed; an empty name disables
  Create. New members append at `max(ordering) + 1`.
- **Reordering** is `Move up` / `Move down` in the selection "⋮" with exactly
  one comic ticked (a set has no place to move to), hidden at the ends
  — no drag handles inside a `LazyVerticalGrid`. `ReadingListOrder` is pure and
  works on the ordered comic ids; the repository writes the resulting order back
  as `ordering` 0…n-1.
- **Removing** comics from a list and **deleting** a list both show the shelf's
  undo snackbar. Undoing a removal re-inserts every entry at its own `ordering`
  (removal leaves the gap, so the comics land back where they were); undoing a
  delete re-creates the list with its id, name, date and members. Deleting needs
  no confirmation dialog — no comic is lost — and the shelf falls back to All
  comics immediately.
- **The reader follows the list.** `KapowRoot` passes
  `LibraryCatalog.nextInList(…) ?: LibraryCatalog.nextInSeries(…)`, so a comic
  opened while a list is on the shelf continues into the next issue *of the
  list* and otherwise into the next issue of its series. Nothing in the reader
  changes.
- The open list is ViewModel state (`openedList`), like `openedSeries`, so
  returning from settings or the reader lands back in the list. It is **not**
  persisted: the shelf opens on All comics.
- An open list with no comics shows `library_list_empty` in the grid area.

## Covers

- `CoverGenerator` decodes the comic's `coverPage` through `ComicSourceFactory` at
  grid resolution and writes a JPEG to internal storage
  (`filesDir/covers/{id}_{coverPage}.jpg`), recording `coverPath`, `coverPage`,
  `pageCount` and `coverAmbient`: the cover's ambient
  colour (same Palette rule as the reader's `ambientColorInt`), stored as an
  ARGB int so the library can tint the hero without decoding the cover again.
- The same pass reads `ComicInfo.xml` (below): the archive is already open, so
  metadata costs one extra entry read rather than a second pass over the
  library. It runs for every comic whose cover is missing **or** whose
  `metadataVersion` is behind `METADATA_VERSION`.
- Runs lazily and asynchronously after a scan, so titles appear instantly and
  thumbnails stream in. A comic with no decoded cover yet (or one that cannot be
  decoded) shows a **procedural cover** instead of an empty box: a gradient keyed
  to the series name, a large faded monogram, a comic halftone-dot overlay and the
  title — so PDFs and freshly-scanned comics still read as cover art.
- **Any page can be the cover.** "Choose cover" in the long-press menu opens
  `CoverPickerScreen`, a grid of every page (thumbnails decoded lazily at half the
  cover width through `PageThumbnails`, which keeps one `ComicSource` open for the
  screen's lifetime). Tapping a page writes `coverPage`, regenerates the cover
  through the same `CoverGenerator` and returns to the library; "Use first page"
  resets to page 0 and stays. `coverPage` is a **pre-split** page index, so
  splitting wide pages never shifts it, and it is clamped to the page count on
  every generation (`ComicCover.page`).
- The file name carries the page (`{id}_{page}.jpg`) so a new choice is a new
  `coverPath`: that is what invalidates Coil, which keys its memory cache on the
  model. The previous file is deleted after the new one is written, and deleting a
  comic removes every `covers/{id}_*.jpg` (`CoverGenerator.deleteCovers`).
  Regeneration recomputes `coverAmbient`, so the hero wash, the settings header and
  the reader's `initialAmbient` follow the new cover for free — and a series stack,
  which renders its first issue's cover, follows that issue's choice.
- The menu row is hidden while `pageCount` is still null (the cover pass has not
  run yet); a page that cannot be decoded leaves `coverPath`/`coverPage` untouched
  and reports it in a snackbar rather than falling back to page 0.

## Visual identity

The shelf uses a "Comic Red & Ink" palette local to the library UI: pure-black
OLED ground, a comic-red accent (matching the reader theme's `primary`) with an
amber highlight and green for completed. Progress shows as a red ring on the cover
corner (percent) and a red→amber bar under in-progress comics; finished comics get
a green "Completed" badge and favorites an amber star. The most recently read
unfinished comic becomes the **hero**: a wide card washed with the cover's
ambient colour, showing the cover, progress and an estimated time left
(`LibraryCatalog.minutesLeft`, fed by the pace measured from the reader's own
sessions and falling back to 45 s per page — see `docs/stats.md`); any other
unfinished comics follow in a row of smaller resume cards.
Both carry a "×" that hides the comic from "Continue reading" without touching
its progress (`ReadingState.shelved = false`); the next `saveProgress` upsert
re-shelves it, so a comic reappears as soon as it is read again.

## Home UI

- Cover grid with an adaptive column count (`GridCells.Adaptive`): the minimum
  cell is 112 dp on compact widths (folded, 3 columns) and 158 dp otherwise
  (unfolded, 5 columns). Only the cover is clipped to the card shape; the title
  and count sit outside so rounded corners never cut glyphs.
- Each cover shows a thin progress ring (no percentage), a "Completed" badge on
  finished comics, an amber star on favorites and the title (which already
  carries the series). Series stacks put the issue count bottom-left, away from
  the cover's logo.
- The **filter bar** (All / Unread / Read / Favorites) sits directly under the
  toolbar, **above** "Continue reading", in a **sticky header**: it pins to the
  top of the grid once it scrolls past, painted with the screen background so
  covers slide underneath. Putting it above the hero is what keeps it still —
  when a filter hides "Continue reading" the section collapses *below* the
  chips, so the row never moves under the finger (it used to jump ~185 dp). The
  shelf title row ("Shelf / All comics") comes after the hero, right above the
  grid. The chip row still scrolls horizontally if a translation makes it wider
  than the screen.
- The **Recent** sort toggle (`LibrarySort`) is not a fifth chip — a clock icon
  at the trailing end of the shelf title row, accent-tinted while on. It sat in
  the chip row before and clipped off the edge of a folded 360 dp screen, which
  also read as if "Recent" were a filter.
- "Continue reading" only shows under the "All" filter and while no search is
  active (`continueReadingVisible`; the list itself is always computed, so it
  survives the animation). Instead of vanishing, it **collapses** — fade plus
  `shrinkVertically` — and it shares a single grid item with the header, so
  switching filters slides the shelf header down rather than yanking the chip
  row ~185 dp up from under the user's finger.
- **Search**: the magnifier in the toolbar reveals a field that filters the shelf
  (`LibraryCatalog.search` → `SearchQuery`). See "Search syntax" below.
- **Long-pressing a cover** starts **selection mode** with that comic ticked
  (see "Selection mode" below); every per-comic action moved into the selection
  row that takes over the filter chips, so a cover has one menu instead of two. **Long-pressing a series stack**
  opens the series menu: series settings, add the whole series to a list, mark
  the whole series read/unread,
  add/remove every issue from favorites and delete the series (confirmation
  dialog, deletes every file); the same menu sits behind "⋮" in the series
  screen header. Inside a series
  screen each card is labelled by its issue number only.
- The "Continue reading" section: the hero card for the latest unfinished comic
  (compact cover and title on folded widths), then a row for the rest. The "×"
  or a horizontal swipe on the hero removes it from the shelf and shows a
  snackbar with **Undo** (`LibraryRepository.reshelve`); progress is never lost.
  The swipe state is keyed by comic so the next hero starts settled, and the
  row scrolls back to its start whenever the hero changes (a `LazyRow` would
  otherwise keep the old first item anchored and hide a reinserted one).
- Toolbar: search, group-by-series toggle, open-a-file and reading stats
  (`docs/stats.md`) are visible; choose
  folder and refresh live behind "⋮". The empty state keeps the large
  "Choose folder" button. The group toggle keeps the same `Layers` glyph the
  series count badge uses in both states and signals grouping only with the
  accent colour, like the search button does while open — a folder icon would
  collide with "choose folder" in Settings and onboarding.
- The opened series is ViewModel state (`openedSeries`), so returning from the
  settings screen lands back inside the series.
- Entries are naturally sorted by series, then issue number, then title.

### Selection mode

Anything you can do to one comic you can do to a handful, because there is only
one code path: the batch one.

- **In:** long-press any cover, on the shelf or inside a series screen — the
  platform gesture (Photos, Files, Drive), and it costs no extra tap for the
  batch the user actually wanted. While selection is on, a plain tap toggles a
  cover instead of opening it; outside selection a tap still opens the comic.
  **Out:** the "✕" square, deselecting the last comic, or system BACK.
- **Selection has no chrome of its own. It takes over the filter row.** The
  pinned `stickyHeader` that normally holds *Todos / Sin leer / Leídos /
  Favoritos* crossfades (150 ms) to the selection row and back. Nothing else on
  the screen moves: the toolbar keeps scrolling, the shelf title stays put, the
  grid's `contentPadding` is exactly what it is without selection, and no bar is
  inserted above or below anything. Losing the filter pills for the duration
  costs nothing — changing the filter would only have narrowed the selection
  anyway (see below). The series screen has no filter row, so there the same row
  is added as its own `stickyHeader` while a selection is live.
- **The row** is built from parts the shelf already uses, so it reads as the
  same app: a `GhostAction` "✕" identical to the search/layers/stats squares one
  line above, then the count as a *selected* `FilterPill` — `Accent` ground,
  white `Check` glyph and a tabular-figures number, `✓ 3` folded and
  `✓ 3 seleccionados` from 600 dp, not tappable — then `weight(1f)`, then the
  actions as `GhostAction` squares: add to list, remove from list (only inside
  an open list), read/unread, favourite, delete, "⋮". Delete is the same square
  in the destructive key (`Danger` ground at 15 %, `Danger` glyph), the exact
  mirror of `GhostAction(active = true)`, so no red word idles in the row.
- **Overflow is measured, not guessed.** `BoxWithConstraints` gives the row its
  width and `inlineActionCount` spends it on 48 dp squares in the order above;
  whatever does not fit falls into "⋮", which is always present. On the folded
  475 dp screen four actions fit, so inside an open list *delete* is the one
  that overflows — the safe failure. Unfolded, all five fit.
- **The "⋮"** leads with select-all / clear-selection (as a labelled row, not a
  dashed-square icon that exists nowhere else in this app), then *Details*,
  *Choose cover*, *Reading settings* and *Move up / Move down* when exactly one
  comic is ticked, then whatever actions overflowed. The comic detail sheet
  stays three taps from the shelf: long-press, "⋮", *Details*.
- **The grid states selectability in two tokens.** While selecting, every
  unselected cover takes a 1 dp `CardLine` hairline on `CardShape` — almost
  invisible, but the whole grid becomes outlined tiles that say "these are all
  pickable" with no dimming, no glyphs and no Material checkbox. A selected
  cover keeps 2 dp `Accent`, springs to 0.92 on a `graphicsLayer` **inside** the
  `sharedCover` node (so shared-element bounds never animate and neighbours
  never move), and takes an `Accent` check badge top-start with `FavoriteBadge`'s
  geometry — the one collision, so a selected card's favourite badge yields. The
  progress ring, the completed tick and `#01` are untouched. Hairline =
  selectable, 2 dp `Accent` = selected.
- **What clears the selection and what does not.** *Add to list* (when its
  dialog closes), *Remove from list*, *Delete* and the "⋮" navigations exit the
  mode; **mark read/unread and favourite keep it**, because read-then-favourite
  is a natural chain and both are undoable. Scrolling never clears it. Opening a
  list, a series, or toggling grouping does — the visible set changes wholesale.
  Changing the **filter, sort or search** does *not* wipe it: on every emission
  `LibrarySelection.reconcile` intersects the selection with the shelf, so
  comics the new filter hides simply drop out of it instead of being silently
  batched, and a delete, a rescan that drops a file or a relink can never leave
  a stale id behind.
- **What "select all" means** is `LibraryCatalog.selectable(entries, comics,
  grouped)` — exactly the covers the grid is drawing. Grouped, that is the loose
  covers only, never issues hidden inside a stack; ungrouped, it is every comic
  on the shelf. Getting this wrong once made the mode enterable but invisible:
  the shelf renders `state.comics` when grouping is off while `entries` is
  always the *grouped* structure, so long-pressing an issue of a multi-issue
  series selected a comic that the row then could not find. Pinned by
  `LibrarySelectionTest`.
- **BACK** lives in `LibraryContent`, gated on `state.selection` and registered
  before the series and open-list handlers (which stay gated on `!selecting`) —
  never inside the row it protects, or a row that fails to render takes the
  escape hatch down with it and BACK leaves the app.
- **Undo where it is honest**, through the shelf's existing
  `SnackbarHostState.undoable`: batch mark read/unread and batch
  favourite/unfavourite restore each comic's previous flag, and remove-from-list
  re-inserts every entry at its own `ordering`. **Delete gets none** —
  `deleteComic` removes the file from the device, so an "Undo" would be a lie;
  the one confirmation dialog quoting the count
  (`library_selection_delete_title/_body`, plurals; a single comic keeps its old
  by-name wording) is the guard.
- **Series stacks do not take part.** A stack is a container, not an item: tap
  opens it, long-press opens the series menu, and *Add series to list…* there
  adds every issue in the group's order in one gesture — cheaper than ticking
  twelve covers. While a selection is active, group cards and the whole
  "Continue reading" shelf go to 40 % and stop responding
  (`Modifier.inertWhile`, consuming pointer events at `PointerEventPass.Initial`)
  — they stay laid out, so nothing jumps, but a stray tap can no longer open the
  reader and throw away a selection being built. Inside a series screen the
  header's "⋮" hides for the same reason: "Delete series" must not sit one tap
  from a three-issue selection.
- **State.** `LibraryUiState.selection` is a `Set<Long>` of comic ids owned by
  `LibraryViewModel`; the UI carries it around as one `SelectionUi` value the
  way reading lists travel as `ReadingListsUi`. `LibrarySelection` and
  `ReadingListMembership` are pure and unit tested.

### Search syntax

`SearchQuery.parse` turns the field's text into a list of terms; every term must
match (they are ANDed). Parsing and matching are pure and unit-tested
(`SearchQueryTest`) — there is no Room `LIKE` query, because the library list is
already in memory. Tokens split on whitespace, and double quotes keep a phrase
together (`writer:"al ewing"`).

A **bare word** matches, case- and accent-insensitively (NFD, combining marks
stripped, so `pena` finds `Peña`), a substring of the title, series, story
title, writer, penciller, inker, colorist, publisher or file name. The summary is
deliberately not searched: it is prose, and every common word would match half
the library.

A token with a recognised `key:` prefix narrows to one field. Keys have Spanish
aliases, because the keys are the one piece of syntax a Spanish user has to
guess; accents are folded, so `año` and `ano` are the same key.

| term | alias | matches |
| --- | --- | --- |
| `series:hulk` | `serie:` | the series |
| `title:descent` | `titulo:` | the ComicInfo story title |
| `writer:ewing` | `guionista:` | the writer |
| `penciller:bennett` | `dibujante:` | the penciller |
| `inker:jose` | `entintador:` | the inker |
| `colorist:mounts` | `color:` | the colorist |
| `publisher:marvel` | `editorial:` | the publisher |
| `year:2018` | `año:` | the year exactly |
| `year>2015`, `year>=2015`, `year<2000`, `year<=2000` | `año>` | year comparisons |
| `rating:4+` | `valoración:` | rated at least N stars (`+` is sugar for `>=`) |
| `rating:3`, `rating>=4`, `rating<2` | `valoración>` | rating exactly, or compared; unrated is 0 |
| `added:30d` | — | added within the last N days (`d` only) |
| `is:reading` | — | started and unfinished (`pageIndex > 0 && !completed`) |
| `is:unread`, `is:read` | — | accepted, but the filter chips already own them |

Rules that keep the field forgiving — it never shows a syntax error:

- An **unrecognised key** (`foo:bar`) is not an error. The whole token becomes a
  bare word, so a colon inside a title still searches. So does a recognised key
  with a value that will not parse (`year:soon`).
- An **empty value** (`writer:`) is dropped.
- A comic with a **null field never matches** a field term.

Under the empty field sit three preset chips — **In progress** (`is:reading`),
**Highly rated** (`rating:4+`) and **Recently added** (`added:30d`) — and one
hint line. A preset types its literal
query into the field, so the syntax is learned by seeing it, and resets the
filter chip to **All** (`LibraryViewModel.onPresetQuery`) so a chip and a query
can never contradict each other. Otherwise chip and query AND together. Presets
and the hint render only while the field is blank and collapse with the same
fade + `shrinkVertically` "Continue reading" uses, so the grid does not jump.

When a query is active and nothing matches, the grid area shows
`library_search_empty` instead of the plain `library_filter_empty`.

## Per-comic reading settings

Tapping a cover opens the reader directly; there is no intermediate screen.
"Reading settings" in a card's long-press menu opens `ComicSettingsScreen` for
that comic. "Series settings" (stack long-press or the series header "⋮") opens
the same screen for the whole series (eyebrow "This series", "Applies to all N
comics"): the chosen values are written to every issue, and each issue can be
tweaked individually from inside the series screen.

- Header washed with the cover's ambient colour (cover shared with the card).
- The settings themselves sit in one grouped surface built from the shared
  vocabulary in `core/ui/SettingsControls.kt` (`SettingsSection`,
  `SettingsChoiceRow`, `SettingsSwitchRow`, `BubbleScaleRow`), so this screen and
  the app settings read as the same page. See `docs/settings.md` for the row
  anatomy.
- **Mode on open** — Default (…) · Pages · Guided view · Vertical scroll. The
  reader's three layouts are mutually exclusive, so they are one choice here
  rather than two independent toggles. It maps onto the unchanged
  `ComicSettings` fields through the pure pair in
  `feature/library/domain/ComicOpenMode.kt`:

  | Choice | `guided` | `verticalScroll` |
  | --- | --- | --- |
  | Default (…) | `null` | `false` |
  | Pages | `false` | `false` |
  | Guided view | `true` | `false` |
  | Vertical scroll | untouched | `true` |

  Reading it back, `verticalScroll` wins over `guided` — the same precedence
  `ReaderViewMode.of` applies — so a comic left in the strip by the HUD reads as
  "Vertical scroll" here. The "Default (…)" label spells out the global mode
  ("Default (Pages)" / "Default (Guided view)") from `OpenDefaults.guidedOnOpen`
  and the effective reading type — a webcomic reads "Default (Vertical scroll)".
- Reading type (Default · Comic · Manga · Webcomic), cover alone in
  the spread (switch, "Cover on its own page" / "Pairs 2-3, 4-5…"), split wide
  pages (switch, see `reading-modes.md#split-wide-pages`) and enlarged bubbles on
  open (Default · On · Off). The "Default" chip spells out the global value it
  falls back to ("Default (Comic)", "Default (Off)"). Turning bubbles on
  reveals the scale slider (same `BUBBLE_SCALE_RANGE` and steps as the reader's HUD stepper)
  stored as the comic's `bubbleScale`; a series-wide change writes it to every
  issue. Page look (Original · Brighter · More contrast · Paper) and page fit
  (Screen · Width) follow, carrying `ComicSettings.pageLook` and
  `ComicSettings.fitWidth`; neither has a global default, so neither offers a
  "Default" chip, and both stay in step with the reader's gear and eye panels
  because they read and write the same `comic_settings` row.
  Saved in `comic_settings` through `LibraryRepository.saveSettings`; a
  row equal to `ComicSettings.Default` is deleted rather than stored.
  `docs/reading-modes.md` describes how the reader consumes them.
- Header and rows share one centred column capped at 640 dp, so nothing is left
  aligned under a wider header on the unfolded screen; the screen stays
  full-screen (not a bottom sheet) because of the shared cover transition and
  the slider.
- Debug builds only: "Clear panel and bubble detections" drops the comic's
  `page_detections` rows so the next open re-runs the models.

`ComicSettingsViewModel` observes the first target's settings and fans writes
out to all targets; it also exposes the global reading type for the
"Default (…)" label.

### Navigation and transitions

`KapowRoot` derives a typed `Screen` (Library / Settings / CoverPicker / AppSettings /
Reader) from its state slots and renders it inside `SharedTransitionLayout` + `AnimatedContent`.
The cover picker keeps only the comic id in that slot and resolves the row on every
composition, so the picker sees the `coverPage` it has just written.
Screens cross-fade; the reader additionally scales in from 94 %. The cover is a
shared element between the grid card (or series stack) and the settings header:
`Modifier.sharedCover(comicId)` (`SharedCover.kt`) reads the transition and
visibility scopes from composition locals, so a cover outside the animated root
just renders normally. The reader receives the cover's ambient colour as
`initialAmbient`, so its backdrop already glows in the comic's colour while the
first page decodes instead of starting from the neutral default.

## Metadata

- Baseline: `ComicNameParser` parses `series`, `issue number`, `year` from the
  filename with a tolerant, unit-tested pure function. No network needed. Series
  falls back to the containing folder name when the filename yields none.
- On top of that, a comic that carries a **`ComicInfo.xml`** (the de-facto
  standard written by ComicRack, Komga, Kavita, Mylar…) is read from the archive
  itself. See `file-formats.md#comicinfoxml` for where the file is looked for
  and how each container reaches it.

### What is read

`ComicInfoParser` (`feature/library/domain`) is a pure function over the XML
text — `DocumentBuilder`, no IO, unit-tested — returning a `ComicInfo`. It reads
`Series`, `Number`, `Year`, `Title`, `Publisher`, `Writer`, `Penciller`,
`Inker`, `Colorist`, `Summary` and `Manga`, and ignores everything else
(`Volume`, `Count`, `Genre`, `Characters`, `Web`, `Notes`, `PageCount`,
`Pages`). A file that is not well-formed XML, or whose root is not
`<ComicInfo>`, yields `null` and the comic simply keeps its filename metadata.

### Precedence

`mergeComicMetadata(info, parsed, edited)` resolves the sources per field:

| Field | Winner |
| --- | --- |
| `series` | the hand edit, else the XML when non-blank, else `ComicNameParser` |
| `issueNumber` | the hand edit, else the XML **when it parses to an Int**, else `ComicNameParser` |
| `storyTitle` | the hand edit, else the XML |
| `year` | the XML when non-blank, else `ComicNameParser` |
| `publisher`, `writer`, `penciller`, `inker`, `colorist`, `summary` | the XML only |
| `readingType` | the XML only (`Manga` = `YesAndRightToLeft` → `Manga`, any other `Manga` value → `Comic`) |

### Hand edits

The Details section's **Edit details** row opens a dialog over `series`,
`issueNumber` and `storyTitle` (Series is required; Number is parsed leniently
by `parseEditedIssueNumber`, so `#012` and `12` are the same issue). Saving
writes the three columns and sets `metadataEdited`, which is what
`EditedComicMetadata` is built from: while it is set the metadata pass never
overwrites those three, whatever `METADATA_VERSION` says, and a relink keeps
them through a rename too — but `publisher`, the credits, `summary` and
`readingType` keep improving from a better ComicInfo parse. `displayName`
is never editable.

**Reset to file** (shown in the dialog only while the comic is edited) clears
`metadataEdited`, sets `metadataVersion` back to 0 and re-reads that one comic,
re-parsing its file name, so the values come back as a fresh scan would produce
them. Downstream nothing knows an edit happened: the title, the hero, the cards
and the series grouping are all recomputed from the edited columns.

The display title stays `LibraryCatalog.title` — `"$series #$issueNumber"` —
so it silently becomes the real one. `displayName` always keeps the file name
and is never overwritten: it is the row shown last in the Details section, so
the user can always see what the metadata replaced. Every column here is
searchable, by bare word or by `key:value` — see "Search syntax".

### When it is read

Not at scan time. The scan stays instant; the cover pass
(`fillMissingDetails`) already opens every archive once, and reads the XML
through that same `ComicSource`, so metadata lands a moment after the covers.
`METADATA_VERSION` (next to the parser, same idea as `DETECTIONS_VERSION` in
`ml-runtime.md`) is stored per comic: bumping the constant makes the next pass
re-read every comic, which is also how a library that predates this feature
gets enriched without a re-scan. It is at **2** since the reading type replaced
the `readsRightToLeft` flag (schema v18).

### Where it shows

- Cover cards and the reader's resume hero read `"$series #$issueNumber"`; when
  a story title exists both headline it instead (the hero keeps the series line
  above; the card's `#12` badge still carries the number).
- Inside a series screen each card keeps its `#12` label and gains the story
  title underneath.
- The series header line becomes `"Marvel Comics · 1/12 read"` (the publisher is
  omitted when unknown).
- **Details** — first item of a cover's long-press menu, opening
  `ComicSettingsScreen` scrolled to a new "Details" section: a star rating row,
  the summary, then Writer / Pencils / Inks / Colors / Publisher for the fields
  that have a value, then the file name, and last an **Edit details** row. A
  comic with no `ComicInfo.xml` shows one line saying so, plus its file name.
  The section is per comic, so it is hidden when the screen was opened for a
  whole series.
- `Manga` seeds the comic's **default** reading type: `YesAndRightToLeft` →
  `Manga`, any other value → `Comic`, missing or `Unknown` → nothing. The reader
  resolves the type as per-comic user setting → the comic's own default → the
  global default. There is no UI for it; a user who picks a type for that comic
  still wins. `ComicInfo.xml` has no webcomic flag, so `Webcomic` is only ever
  chosen by hand.

### Limits and follow-ups

- `Number` is often not an integer (`0`, `1.MU`, `Annual 1`). v1 keeps `Int?`
  and falls back to the filename parse for those; the XML's other fields are
  still used.
- `<Pages>` entries carrying `DoublePage` / `FrontCover` could later drive
  `splitWidePages` and `coverAlone` automatically. Out of scope for now.
- Optional enrichment: ComicVine API for descriptions/cover art. Deferred and
  behind a toggle — only if it adds value without noise. No copyrighted content
  is downloaded, only metadata.

## Bilingual

All library strings come from resources (`docs/i18n.md`). Dates and numbers are
formatted with locale-aware formatters, never hand-built strings.

## App settings

The gear in the library header opens the app-wide settings screen (global
reading defaults, screen, comics folder + rescan, theme, about). See
`docs/settings.md`. "Choose folder" is also the primary action of the empty
library.
