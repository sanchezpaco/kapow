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

- Layout splits at the hinge using `FoldingFeature.bounds`.
- Top half shows the page; bottom half shows controls/next-page affordance (or
  the facing page, configurable) — hands-free reading on a surface.

## Guided View

Available on `CompactSingle`, and on demand elsewhere. Full detail in
`guided-view.md`.

- Auto-focuses one detected panel at a time with an animated pan/zoom.
- Tap advances to the next panel in reading order; at the last panel, advances
  to the next page's first panel.
- Double-tap zooms around the tapped point and one finger pans, so any page
  (including whole-page fallbacks and wide panels) stays readable.

## Reading direction

The reader has a `ReadingDirection` setting — `LeftToRight` (default) or
`RightToLeft` (for manga and other right-bound comics) — persisted with
DataStore (`ReaderPreferences`, key `reading_direction_rtl`) and exposed as
`ReaderUiState.direction`, toggled from the ViewModel
(`ReaderViewModel.toggleReadingDirection`) and from a HUD button next to the
guided-view toggle.

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

- Immersive by default: system bars hidden, edge-to-edge, pure-black background.
- Center tap toggles a minimal overlay: progress, page number, quick settings.
- Left/right tap zones page back/forward (mirrored correctly for the surface).
- All transitions are spring/physics based, never linear.

## Preloading

Regardless of surface, the reader preloads the next `N` page bitmaps (and, in
spread mode, the next pair) through Coil so page turns have no decode latency. `N`
scales down under memory pressure.
