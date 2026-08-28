# Reading modes

The reader chooses a **surface** from the current `ReadingPosture` (see
`foldable.md`). Each surface is a Compose strategy fed the same posture-agnostic
`ReadingPosition`.

## Full page (single)

Default on `UnfoldedSingle` and `CompactSingle`.

- Horizontal `Pager`, one page per screen.
- Content fits the screen; the unfolded screen shows a page at near-physical size
  so no zoom is needed for normal reading.
- Gestures: single-finger horizontal swipe changes page; pinch (two fingers)
  zooms; when zoomed, one finger pans (clamped to the page) with an inertial
  fling on release (velocity-tracked, `exponentialDecay` clamped to the pan
  bounds) and the pager is locked; double-tap toggles a 2.5× zoom centred on the
  tapped point. The
  zoom/pan detector only consumes events when two fingers are down or the page
  is already zoomed, so single-finger swipes always reach the
  pager (a custom `awaitEachGesture`, not `detectTransformGestures`).
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

- Auto-focuses one detected panel at a time with an animated pan/zoom.
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

## Per-comic settings

`ReaderViewModel` reads the comic's `comic_settings` row (`ComicSettingsDao`)
on open: `bubblesEnlarged` and `guided`, when not null, replace the reader's
initial off state; `rightToLeft`, when not null, overrides the global reading
direction, and the reader's direction toggle then writes the override instead
of the global preference; `coverAlone` feeds the spread pairing above.
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
  rest of the reader settings (night tint, reading direction, and, in the spread,
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
