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
  zooms; when zoomed, one finger pans (clamped to the page) and the pager is
  locked; double-tap toggles a 2.5× zoom centred on the tapped point. The
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
- Page pairing parity (which page starts a spread) is configurable and remembered
  per comic.

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
- Double-tap zooms around the tapped point and one finger pans, so any page
  (including whole-page fallbacks and wide panels) stays readable.

## Shared reader chrome

- Immersive by default: system bars hidden, edge-to-edge, pure-black background.
- Center tap toggles a minimal overlay: progress, page number, quick settings.
- Left/right tap zones page back/forward (mirrored correctly for the surface).
- Volume-down/volume-up turn the page forward/back (panel-by-panel in Guided
  View) and consume the key so system volume is unaffected. Only active while
  a comic is open. A DataStore-backed preference (`ReaderPreferencesRepository`,
  default enabled) controls this; toggling it is not yet exposed in a settings
  screen.
- All transitions are spring/physics based, never linear.

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
