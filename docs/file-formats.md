# File formats

Kapow reads comic archives the user owns. A single abstraction hides the
container format from the reader.

## Abstraction

```
interface ComicSource {
    val pageCount: Int
    suspend fun pageNames(): List<String>
    suspend fun decodePage(index: Int, targetWidth: Int): Bitmap
}
```

- Pages are always returned in correct reading order (entries sorted
  naturally: `page2 < page10`).
- `decodePage` decodes at a target width to control memory; the reader passes the
  surface width. Full-resolution decode happens only for zoom.
- Decoding is off the main thread (`Dispatchers.Default`/`IO`), invoked via use
  cases from the `data` layer.

## CBZ (Phase 1)

- A ZIP of images. Read with `java.util.zip.ZipFile`.
- Filter entries to image types (`jpg`, `jpeg`, `png`, `webp`), sort naturally.
- Decode with Coil / `BitmapFactory` using `inSampleSize` for the target width.

## Format detection by content

Extensions lie: many `.cbr` files are actually ZIP archives. `ComicSourceFactory`
opens the picked document as a read-only `ParcelFileDescriptor`, reads the
leading magic bytes through a positional `FileChannel` read, and routes on
content, not extension. Only ZIP is copied to a cache file (`java.util.zip.ZipFile`
needs a path, and reopening the descriptor through `/proc/self/fd` is refused
with `EACCES` by scoped storage); RAR and PDF read the descriptor directly, so
opening no longer scales with the archive size (Blacksad #1, 165 MB RAR, on
the Fold: first page 509 → 361 ms):

- `Rar!…` → RAR (both RAR4 and RAR5) → `CbrComicSource`.
- `%PDF…` → PDF → `PdfComicSource`.
- Anything else → ZIP → `CbzComicSource` (default).

The pure magic-byte matcher (`detectComicFileFormat`) lives in `ComicFileFormat.kt`
and is unit-tested independently of any file IO.

## CBR (RAR4 and RAR5)

- Read with **7-Zip-JBinding** (`com.github.omicronapps:7-Zip-JBinding-4Android`,
  via JitPack), which ships native `.so` per ABI and handles both RAR4 and RAR5.
  `junrar` was dropped because it cannot read RAR5.
- The archive is opened once over `DescriptorInStream` (an `IInStream` doing
  positional reads on the document's descriptor) and then **extracted once,
  sequentially, in the background** into a per-comic temp directory in the
  cache (`comic_pages*/<itemIndex>`), through `extract(indices, …)` calls whose
  `IArchiveExtractCallback` streams each item to its own file. The caller
  passes the page the reader will show first: for non-solid archives the items
  from that page to the end are extracted first and the head afterwards, so
  resuming a 62-page RAR at page 19 shows it after 222 ms instead of ~800 ms;
  solid archives (`PropID.SOLID`) keep the single ascending pass, since 7-Zip
  has to decompress the block from the start anyway.
- Why: real `.cbr` files are usually *solid* RAR archives, so extracting a
  single item makes 7-Zip decompress the whole solid block from the start up to
  that item. Per-page extraction therefore costs O(N) for page N and page turns
  get progressively slower through the comic (O(N²) overall, multiplied by the
  thumbnail strip). One sequential pass is O(total).
- `decodePage(index)` awaits a per-item `CompletableDeferred` that is completed
  when that item's file is fully written, then reads the file and decodes it.
  Items come out in archive order, so the first pages become readable almost
  immediately while the rest is still extracting. Extraction failures complete
  the pending deferreds with `ComicSourceException.ReadFailure`.
- The extraction directory is wiped before extracting and deleted on `close()`.
  Closing while extraction is still running aborts it at the next item, and the
  archive, descriptor and directory are released from the extraction thread.
- Same image filtering/natural sorting as CBZ, behind the same `ComicSource`.
- The bundled native library loads on 16 KB-page devices (verified on the
  API 37.1 foldable emulator).

## Plain image folder

- A SAF tree/folder `Uri` (from `ACTION_OPEN_DOCUMENT_TREE`) pointing at an
  uncompressed folder of images. Opened via `ComicSourceFactory.openFolder`,
  which returns a `FolderComicSource`.
- Children are listed with `DocumentsContract` (`buildChildDocumentsUriUsingTree`),
  filtered to image types (`jpg`, `jpeg`, `png`, `webp`, `gif`, `bmp`) and sorted
  naturally, behind the same `ComicSource` abstraction.
- Each page is decoded on demand from its document `Uri` via the shared
  `decodeSampled` helper; there is no archive to extract or cache file to clean up.

## PDF (Phase 5)

- Rendered with the framework `android.graphics.pdf.PdfRenderer` in
  `PdfComicSource`, behind the same `ComicSource` interface as CBZ/CBR.
- The renderer is created straight on the document's `ParcelFileDescriptor`
  (no cache copy); renderer and descriptor are closed in `close()`.
- Each page is a `PdfRenderer.Page` rasterized to an `ARGB_8888` bitmap at the
  reader's target width, with height derived from the page aspect ratio. The
  bitmap is pre-filled white before `render` so transparent PDF backgrounds show
  as paper white. Rendering runs on `Dispatchers.IO`.
- `PdfRenderer` is not thread-safe: page rendering is serialized behind a
  `Mutex` per source.

## Open failures

`ComicSourceFactory.open` never leaks raw exceptions to the UI. Failures are
classified into a sealed `ComicOpenError` (`feature/reader/domain`) with three
cases, each carried to the reader as a distinct localized message:

- `UnsupportedFormat` — the magic bytes are not ZIP, RAR or PDF.
- `EmptyArchive` — the archive opened but contains zero readable image pages.
- `ReadFailure` — the stream could not be read, or the archive is corrupted.

`ComicSourceFactory` throws a matching `ComicSourceException` (`feature/reader/data`)
that wraps the `ComicOpenError`; `ReaderViewModel` catches it and stores the
error case in `ReaderUiState` for `ReaderScreen` to render.

## Natural sorting

Archive entry order is not guaranteed. Sort filenames with a natural comparator
so numeric runs order numerically (`p1, p2, … p10`, not `p1, p10, p2`). This
lives in `core` and is unit-tested.

## Margin auto-crop

Some pages carry a wide solid border (near-white or near-black) that shrinks the
art. After decoding, `MarginCrop` inspects the page: if the four corners agree on
a uniform colour, it scans inward from each edge for full lines of that colour and
returns the tight content rectangle as a normalized `Rect`. Guards keep it safe —
a page with no meaningful border, disagreeing corners, or a crop that would remove
more than half of either axis is left whole. The crop is computed once per page in
the data layer (`PageLoader.decode`, behind a default-on flag) and the page is
cropped there, so ambient colour, panel detection and display all operate on the
same content. Validated offline against real pages with a `BufferedImage` harness
and unit-tested on synthetic bordered pages.

## Dominant color

For letterbox fill (`reading-modes.md`), the decoder also samples the page's edge
pixels to compute a dominant color, cached per page. Cheap: downscale to a tiny
bitmap and average/quantize edge rows/columns.

## Memory strategy

- Decode to target width (2160 px: power-of-two subsampling, then a density
  scale inside `BitmapFactory` down to exactly 2160 px, so a 2953 px scan
  no longer stays full-res at 53 MB because halving would undershoot), never
  full-res for paging; an `LruCache` of 8 pages plus a thumbnail cache in `PageLoader`.
- The displayed page is a **hardware bitmap** (`Bitmap.Config.HARDWARE`,
  2026-08-28). Software bitmaps of 1988 × 3056 (24 MB each, three resident
  around the pager) blew HWUI's 72 MB texture cache on the Z Fold, so every
  frame of a page turn re-uploaded a page: 78 % janky frames, 73 ms median,
  93 slow bitmap uploads in 121 frames on Doctor Doom #1. GPU-resident pages
  need no upload: 1.8 % janky, 5 ms median, 0 slow uploads on the same turns.
- Hardware bitmaps cannot be read, so `PageArt` also keeps a software
  `analysis` copy at the detector's size (shorter side 1000 px, what
  `PanelDetector` already rescaled to): margin crop runs before the copy,
  ambient colour, panel/bubble detection and the paper-colour sampling of
  enlarged bubbles read the analysis bitmap; drawing (page, bubble copies,
  Guided View) uses the hardware one.
