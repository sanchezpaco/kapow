# Reading modes

The reader chooses a **surface** from the current `ReadingPosture` (see
`foldable.md`). Each surface is a Compose strategy fed the same posture-agnostic
`ReadingPosition`.

## Full page (single)

Default on `UnfoldedSingle` and `CompactSingle`.

- Horizontal `Pager`, one page per screen.
- Content fits the screen; the unfolded screen shows a page at near-physical size
  so no zoom is needed for normal reading.
- Gestures: single-finger horizontal swipe changes page; a tap on the outer 20 %
  of the width turns the page (`TapZones` in `PageOrder.kt`: the left zone goes
  back and the right one forward, mirrored for right-to-left; in a spread each
  half exposes its outer 40 %, i.e. 20 % of the screen per side, leaving a wide
  centre so a tap for the chrome does not turn the page) and a tap
  in the middle toggles the chrome; while zoomed every tap is a centre tap.
  Pinch (two fingers) zooms; when zoomed, one finger pans (clamped to the page) with an inertial
  fling on release (velocity-tracked, `exponentialDecay` clamped to the pan
  bounds) and the pager is locked; double-tap toggles a 2.5× zoom centred on the
  tapped point. The
  zoom/pan detector only consumes events when two fingers are down or the page
  is already zoomed, so single-finger swipes always reach the
  pager (a custom `awaitEachGesture`, not `detectTransformGestures`).
  While zoomed, a one-finger pan only starts once the finger has travelled past
  `viewConfiguration.touchSlop` (`PanSlop` in `reader/domain`, unit-tested):
  below that nothing moves and nothing is consumed, so a slightly shaky tap
  still reaches `detectTapGestures` and double-tap zooms back out instead of
  nudging the page (it was flaky on phones, where taps wobble more than on the
  Fold). Pinches skip the slop and take the events on the first move, exactly as
  before, so the pager can never steal a two-finger gesture.
- Instead of a flat-black letterbox, the whole reader background is an **ambient
  radial glow** derived from the current page's dominant color (androidx
  Palette), cross-fading when the page changes. See `ambient-backdrop` below.
- **Page-turn depth transition:** as the pager settles, each page is transformed
  by a `graphicsLayer` driven by its own scroll-offset fraction
  (`PagerState.getOffsetFractionForPage`). The outgoing page recedes with a
  subtle scale-down, fade, parallax translation, and a soft 3D `rotationY` (via a
  modest `cameraDistance`), so a turn reads with depth rather than a flat slide.
  The math lives in the pure `PageTurn.transform(pageOffset)` function
  (`reader/domain`, unit-tested): offset `0` is the identity (the settled page is
  perfectly flat and centred), and `|offset| → 1` reaches the depth extreme. The
  effect is scoped to this single-page surface; spread, tabletop and Guided View
  are unchanged. Because the pager is locked while a page is zoomed, the offset
  stays `0` there, so the transition never fights pinch-zoom or pan.

## Ambient backdrop

When opened from the library the backdrop starts from the cover's persisted
ambient colour (`ReaderScreen(initialAmbient)`), then follows each page.

Each decoded page carries an `ambient` color (Palette vibrant → muted → dominant
fallback), cached with the page bitmap. The reader paints a radial gradient from
a darkened ambient glow at the page center out to pure black at the edges, so the
page appears to emit light into the letterbox. The current page's ambient is
animated (`animateColorAsState`), so turning a page smoothly shifts the glow.

## Two-page spread

Default on `UnfoldedSpread`.

- Renders pages `n` and `n+1` side by side as an open book.
- **Double-page splash handling:** if a page is detected as a full-width spread
  (aspect ratio near 2:1, or metadata flag), it occupies both halves and pairing
  re-aligns so the artwork joins correctly across the seam.
- Page pairing parity is a per-comic setting ("cover alone in the spread",
  `ComicSettings.coverAlone`, set from the comic's settings screen). `PageOrder`
  owns the arithmetic (`spreadCount`, `spreadIndex`, `spreadFirstPage`): with
  the cover alone, spread 0 shows a blank left half and the cover, and the
  pairs become (1, 2), (3, 4)… The spread surface and Guided View's spread
  layout both use it; the thumbnail scrubber maps a page to its spread through
  the same helper.

## Book mode (tabletop)

Active in `Tabletop` posture (half-folded, horizontal hinge).

- The layout splits at the real hinge: `FoldingFeature.bounds`, converted to dp,
  drives the pure `splitAtHinge` helper so the page area stops exactly above the
  occluded band and controls start exactly below it. See `foldable.md`.
- Above the hinge shows the page; below the hinge shows controls/next-page
  affordance — hands-free reading on a surface.
- When hinge bounds are unavailable or don't fit the measured surface, this
  falls back to the previous proportional split (62% page / 38% controls).

## Guided View

Available on `CompactSingle`, and on demand elsewhere. Full detail in
`guided-view.md`.

- Auto-focuses one detected panel at a time with an animated pan/zoom whose
  duration and shape come from the geometry of the move (`DirectorCut`: pan with
  an arc on long travel, push in, pull back, reveal, jump on a page turn). See
  `guided-view.md`.
- Tap advances to the next panel in reading order; at the last panel, advances
  to the next page's first panel.
- Double-tap zooms around the tapped point and one finger pans (with an inertial
  fling on release), so any page (including whole-page fallbacks and wide panels)
  stays readable. Panning runs in normalized page space, so the release fling uses
  `exponentialDecay` (spline decay is pixel-calibrated and would not move a 0..1
  value).

## Enlarged speech bubbles

An alternative to Guided View for small lettering that keeps the whole page (and
its art) on screen: the ChatBubble button in the HUD (`ReaderUiState.bubblesEnlarged`,
hidden while Guided View is on) redraws every detected speech bubble and caption
scaled up **in place** (1.3× by default, a slider under the HUD buttons sets 1.1–2×, persisted), as an overlay on the regular page surfaces
(`ZoomablePage`, so it works on single pages, the spread and tabletop and follows
pinch/double-tap zoom). It is a static render enhancement — no page zoom, no
bubble-to-bubble navigation, nothing to order — so it deliberately sidesteps
panel segmentation and reading-order errors. Full detail in `speech-bubbles.md`.

## Split wide pages

Some PDFs and scans store two comic pages side by side in one landscape file
page. The per-comic setting "Split wide pages"
(`ComicSettings.splitWidePages`, in the comic settings screen and in the
reader's gear menu) cuts every landscape page in half and presents each half as
its own page.

- The split lives in the data layer, in `SplitPagesComicSource`, a `ComicSource`
  that wraps the real one. On open it probes every page's aspect ratio
  (`ComicSource.pageAspect`: a bounds-only `BitmapFactory` decode for images,
  `PdfRenderer.Page` width/height for PDF), builds the logical page list once
  with the pure `SplitPages.of` (`reader/domain/SplitPages.kt`) and keeps it for
  the life of the source. A page counts as wide when its aspect ratio is greater
  than `WIDE_PAGE_ASPECT` (1), so portrait and square pages pass through
  untouched.
- Half order follows the effective reading direction: left half first in
  `LeftToRight`, right half first in `RightToLeft`. Changing the direction while
  the setting is on rebuilds the source.
- A half is decoded by asking the inner source for twice the target width and
  cropping; everything downstream (pager, thumbnails, preloading, reading
  position, Guided View, bubbles) only sees a longer page list.
- Persisted detections are keyed by document Uri + page index, so the same index
  must not return the unsplit page's panels and bubbles: `PageDetectionStore`
  appends `+split` to `DETECTIONS_VERSION` when the setting is on. The row's
  primary key stays (Uri, page), so a mode change overwrites rather than
  duplicating, and deleting a comic's detections still clears everything with
  one query.
- Toggling from inside the reader rebuilds the source
  (`ReaderViewModel.reopenComic`) and maps the position exactly: the current
  page is converted to its source page through the old source and back to the
  first half of that source page through the new one. The reader surfaces are
  keyed on the `PageLoader`, so the pager is recreated on the mapped page.

### Suggesting the split

Opening a comic with the setting off probes every page's aspect (the same
`pageAspect` the split uses) and, when at least 80 % of the pages after the
cover are wider than tall (`SplitSuggestion.shouldSuggest`, at least two
such pages), shows a snackbar once: "This comic looks like it has two pages
per image · Split". The action turns the setting on; accepting or dismissing
records `ComicSettings.splitSuggested` so the comic never asks again. The
check is purely geometric, so a landscape-format comic gets the same
suggestion — that is why it suggests rather than splits on its own. On
RAR files the probe waits for the archive extraction, so the snackbar can
appear a few seconds after the first page.


## Continuous vertical scroll

A third top-level mode, chosen per comic (`ComicSettings.verticalScroll`, HUD
toggle in the settings menu). It takes precedence over Guided View and the
two-page spread and hides their toggles, because both are page-based and cannot
coexist with a strip. Whole pages are stacked in one `LazyColumn`, fit to width,
with no page-turn animation: scroll instead of paginate. Nothing in its path
detects anything, so unlike Guided View it cannot misread a page.

The strip asks `PageLoader.aspects()` for **every page's aspect ratio before
composing anything** (`ComicSource.pageAspect` reads image headers only, the
same probe the split suggestion already runs at open). Each item is then a
`fillMaxWidth().aspectRatio(aspect)` box, so the list has its full scroll extent
from the first frame and a page decoding never changes the height of anything
above it. Margin cropping can make the decoded page slightly narrower than the
file, which `ContentScale.Fit` letterboxes against the black background rather
than resizing the item.

Zoom and vertical pan are dropped in this mode: the vertical drag is the scroll,
so there is no gesture left to pan with. A tap anywhere toggles the chrome; the
thumbnail scrubber and volume keys still work, jumping and stepping by item.
Reading position stays the first visible page, so it maps to the same
`ReadingPosition.pageIndex` every other surface uses and survives a mode switch.

Measured on the emulator over a long fast scroll in both directions
(330 frames): 0.91 % janky, p99 32 ms, **0 slow bitmap uploads**, 0 missed
vsync. Slow bitmap uploads is the number that matters — a strip keeps more pages
resident than the pager, and it was thrashing the HWUI texture cache that caused
the original 78 %-jank bug (`performance.md`).

## Per-comic settings

`ReaderViewModel` reads the comic's `comic_settings` row (`ComicSettingsDao`)
on open: `bubblesEnlarged` and `guided`, when not null, replace the reader's
initial off state; `rightToLeft`, when not null, overrides the global reading
direction, and the reader's direction toggle then writes the override instead
of the global preference; `bubbleScale` works the same way for the HUD slider
(override wins, and dragging the slider updates the override when one exists);
`coverAlone` feeds the spread pairing above, `splitWidePages` the split
described above and `verticalScroll` the continuous strip.
Everything else stays global in `ReaderPreferencesRepository`.

## Reading direction

The reader has a `ReadingDirection` setting — `LeftToRight` (default) or
`RightToLeft` (for manga and other right-bound comics) — persisted with
DataStore (`ReaderPreferences`, key `reading_direction_rtl`) and exposed as
`ReaderUiState.direction`, toggled from the ViewModel
(`ReaderViewModel.toggleReadingDirection`) and from the reader settings menu.

When `RightToLeft` is active:

- The single-page, two-page spread, and tabletop pagers scroll in reversed
  physical order, so swiping in the natural direction still advances the
  story forward.
- In the two-page spread (both the plain spread surface and Guided View's
  spread layout), the pages swap sides: the earlier page renders on the
  right and the later page on the left.
- Tap zones and next/previous semantics mirror: in Guided View, the
  left/right tap zones swap which one advances vs. goes back; the tabletop
  chevrons keep their on-screen position (left arrow always means "earlier
  in the story", right arrow "later") but drive the pager in the opposite
  physical direction.

All of this is driven by a single pure helper, `PageOrder`
(`feature/reader/domain/PageOrder.kt`), which maps a logical (reading-order)
page or spread index to its physical pager index and back, decides which
logical page renders on the left/right of a spread, and mirrors a tap's
zone (previous/center/next). It has no Compose or Android dependency, so it
is unit-tested directly (`PageOrderTest`).

## Shared reader chrome

- Immersive by default: system bars hidden, edge-to-edge, pure-black background
  with the page's ambient glow behind it (`AmbientBackdrop`: a radial gradient
  painted in an `Offscreen` layer at a quarter of the screen and scaled ×4,
  since the full-screen shader cost ≈ 2 ms of GPU per frame on the inner
  screen).
- The top bar keeps only two always-visible controls — the Guided View toggle and
  a settings **gear** — plus the close button. The gear opens a dropdown with the
  rest of the reader settings (night tint, reading direction, split wide
  pages, and, in the spread,
  the panel-layout toggle) and a posture label. Because immersive mode hides the
  status bar, the top bar pads for `displayCutout` (unioned with `statusBars`), so
  the controls never sit under the foldable's front-camera cutout.
- Center tap toggles a minimal overlay: progress, page number, quick settings.
  The chrome stays composed while hidden: `SlidingChrome` fades it with
  `ModulateAlpha` and slides it fully off-screen (so it receives no touches)
  instead of `AnimatedVisibility`, which recomposed and re-recorded ~40 nodes on
  every show (Fold: first frame 13 → 4 ms on the main thread). The scrubber
  skips thumbnail loads and re-centring while hidden; thumbnails are decoded
  as hardware bitmaps so showing the chrome uploads no textures.
- Left/right tap zones page back/forward (mirrored correctly for the surface).
- Volume-down/volume-up turn the page forward/back (panel-by-panel in Guided
  View) and consume the key so system volume is unaffected. Only active while
  a comic is open. A DataStore-backed preference (`ReaderPreferencesRepository`,
  default enabled) controls this; toggling it is not yet exposed in a settings
  screen.
- All transitions are spring/physics based, never linear.
- **Thumbnail scrubber:** the bottom chrome carries a horizontally scrollable
  filmstrip of low-resolution page thumbnails. Tapping a thumbnail jumps the
  reader to that page; the strip is draggable to scan the whole comic. The
  current page's thumbnail is highlighted and the strip centres on it when the
  chrome opens. See `thumbnail-scrubber` below. Hidden with the chrome, and
  suppressed in Guided View (which navigates panel by panel).

## Thumbnail scrubber

- Thumbnails come from a dedicated path in `PageLoader` (`loadThumb`) that decodes
  each page at a small thumb width into its own LRU cache, separate from the
  full-page cache so scrubbing never evicts reading-quality bitmaps.
- Decoding is lazy: the strip is a `LazyRow`, so only visible cells request their
  thumbnail as they scroll into view — pages are never decoded eagerly.
- Jumping is unidirectional: a tap raises a `pendingJump` on `ReaderUiState`; the
  active surface's pager consumes it (`scrollToPage`) and clears it, so the jump
  lands on the same page state the pager and `onPageChanged` already drive. In the
  two-page spread the target page maps to its spread step
  (`ThumbnailStrip.stepIndexForPage`).

## Preloading

Regardless of surface, the reader preloads the next `N` page bitmaps (and, in
spread mode, the next pair) through Coil so page turns have no decode latency. `N`
scales down under memory pressure.

## Night tint

An optional warm amber scrim for comfortable low-light reading, toggled from the
HUD. It is drawn as a translucent layer above the page content and ambient
backdrop but below the top/bottom chrome, so it never dims the controls
themselves. The preference is persisted with DataStore
(`ReaderPreferencesRepository`) and restored on the next session; it only
affects the reader, never the library.
