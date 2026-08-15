# File formats

Comicify reads comic archives the user owns. A single abstraction hides the
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
copies the picked document to a cache file, reads the leading magic bytes, and
routes on content, not extension:

- `PK…` → ZIP → `CbzComicSource`.
- `Rar!…` → RAR (both RAR4 and RAR5) → `CbrComicSource`.

## CBR (RAR4 and RAR5)

- Read with **7-Zip-JBinding** (`com.github.omicronapps:7-Zip-JBinding-4Android`,
  via JitPack), which ships native `.so` per ABI and handles both RAR4 and RAR5.
  `junrar` was dropped because it cannot read RAR5.
- The archive is opened once over a `RandomAccessFileInStream`; each page is
  extracted on demand by item index through an `IArchiveExtractCallback`.
- 7-Zip-JBinding is not thread-safe, so extraction is serialized behind a
  `Mutex`.
- Same image filtering/natural sorting as CBZ, behind the same `ComicSource`.
- The bundled native library loads on 16 KB-page devices (verified on the
  API 37.1 foldable emulator).

## PDF (Phase 5)

- Rendered with the framework `android.graphics.pdf.PdfRenderer`.
- Each page is a `PdfRenderer.Page` rasterized to a bitmap at target width.
- `PdfRenderer` is not thread-safe: serialize access behind a mutex per source.

## Natural sorting

Archive entry order is not guaranteed. Sort filenames with a natural comparator
so numeric runs order numerically (`p1, p2, … p10`, not `p1, p10, p2`). This
lives in `core` and is unit-tested.

## Dominant color

For letterbox fill (`reading-modes.md`), the decoder also samples the page's edge
pixels to compute a dominant color, cached per page. Cheap: downscale to a tiny
bitmap and average/quantize edge rows/columns.

## Memory strategy

- Decode to target width, never full-res for paging.
- Coil memory + disk cache handles reuse and preloading.
- On low memory, reduce preload count and evict non-visible full-res zoom
  bitmaps first.
