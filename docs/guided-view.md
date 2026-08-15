# Guided View

Panel-by-panel reading: the reader auto-focuses one comic panel at a time with a
cinematic pan/zoom between them. It is the readability fix for the two-page
spread on the unfolded Fold and shines on the folded cover screen. Toggle: the
ViewCarousel button in the HUD (`ReaderUiState.guided`).

## Pipeline

```
page bitmap ──▶ PanelDetector (data) ──▶ PanelDetection (domain, pure) ──▶ List<Rect> ──▶ cache
                                                                                     │
                     tap ──▶ GuidedReader animates the focus rect to the next panel ◀─┘
```

`PanelDetector` (data layer) only reads the bitmap pixels; everything else is
pure Kotlin in `feature/reader/domain` and unit-tested on synthetic pages.

## Panel detection

Real `.cbr`/`.cbz` files carry no panel metadata, so panels are detected from
pixels. Marvel-style pages mix clean grids, tilted gutters, bleeds, insets and
speech bubbles that overhang gutters, so the detector is built around a few
robust structural facts rather than a single threshold:

1. **Pixel classes** (`PixelClasses`). Classified at source resolution and
   max-pooled to ~1000 px wide, so hairline gutters survive downscaling:
   - `white`: near-white, low chroma (any source pixel in the cell) — gutters,
     bubble interiors, page margins.
   - `solidWhite`: every source pixel in the cell is white — used to find
     *enclosed* whites, because it keeps thin dark outlines intact.
   - `border`: the page's dominant margin colour (histogram of the outer ring,
     neutral colours only). Handles dark-grey/black frames and black gutters.
     When it is not white it is morphologically opened, so black ink lines and
     bubble outlines never count as gutters — only thick frames/gutters do.
   - `art` = neither white nor border.
2. **Bodies**. The art mask is opened (erode+dilate, radius 2) to cut thin
   bridges (bubble outlines, text, hairline bleeds), then 4-connected components
   above 1.2 % of the page become panel bodies.
3. **Merging bodies** (`PanelBodies`). Two bodies are distinct panels only if a
   straight line (vertical, horizontal or up to 60° tilted) separates their edge
   profiles, tolerating 12 % outlier lines. Fragments of one panel split by a
   rim light, a white sheet or a shadow are never separable and merge; grid,
   staggered, tilted and layered panels stay apart. A body mostly contained in a
   bigger one is kept as an **inset** only if a separator ring frames it.
4. **Frame splitting** (`PanelFrames`). A body that a bleed connected across a
   drawn keyline is split back apart: within its box the detector looks for a
   thin, near-continuous separator line (white or thick border) spanning the box,
   flanked by dense art on both sides, and cuts there. This tolerates the bleed
   gap (up to ~15 % of the span) yet ignores speech bubbles and bright bands
   (too thick) and page margins (art only on one side). Bodies without such a
   line are left whole, so borderless panels are never over-cut. Recursive, so a
   bordered banner over a full-page splash becomes two stops.
5. **Enclosed whites**. `solidWhite` components that do not touch the page
   border, with a compact fill, are bubbles/captions/white panel interiors.
   Each is attached to every panel it overlaps by ≥ 20 % of its area (or the
   best-overlapping one), growing that panel's frame so overhanging dialogue is
   framed instead of clipped. Detached large regions become panels themselves.
6. **Cleanup** (`PanelLayout`). Bodies contained in a grown frame are absorbed
   unless framed insets; thin slivers are folded into the panel they touch;
   background bands almost fully covered by the bordered panel on top of them
   are dropped.
7. **Confidence gate**. If nothing survives, more than 16 panels remain, or the
   panels cover less than 75 % of the page's art, the page falls back to
   horizontal reading bands (`PanelBands`, split on full-width gutter rows), or a
   single full-page panel when even bands find no gutters — which keeps true
   splashes as one stop. A single detected body is returned as-is, which also
   auto-crops margins on splashes.

Output: `List<Rect>` in normalized page coordinates (0..1), cached per page in
`PageLoader` (only computed when Guided View is used). Detection takes
~100–250 ms per page off the main thread.

### Reading order

`PanelLayout.readingOrder`: rows are built from a mutual-centre test (a box
joins a row when its centre lies inside the row's band and the row's centre
inside the box), rows sort top-to-bottom and boxes left-to-right. Container
boxes (a host or bleed panel enclosing other panels) are excluded from row
building and inserted right before the first panel they contain, so a host is
shown before its insets.

### Known limits

- Bleed art that connects panels across a drawn keyline is split back apart
  (step 4) when the keyline stays a thin, near-continuous line; a bleed that
  covers more than ~15 % of the line, or panels joined with no keyline at all,
  still merge into one larger stop (safe: nothing is clipped, text is just
  smaller).
- Chained bubbles crossing panels grow both panels' frames.
- Steeply tilted panels that overlap are not split by frames (their keylines are
  not axis-aligned); the reading-band fallback catches the worst of these.

## Iterating on detection

`PanelDetectionVisualizer` (unit test, skipped by default) renders detected
boxes over page images. Point it at a folder of extracted page JPEGs:

```
COMICIFY_PANEL_VIZ_DIR=/path/to/pages ./gradlew :app:testDebugUnitTest \
    --tests '*PanelDetectionVisualizer*' --rerun -i
```

Annotated PNGs land in `<dir>/out`. Iterate there, then trust the unit tests.

## Navigation and framing

- Left 28 % / right 28 % tap zones go to the previous / next panel; the centre
  toggles the chrome. After the last panel the next page's first panel follows;
  going back from a first panel lands on the previous page's last panel.
- `GuidedFocus.frame` pads the panel rect by 2 % of the page and clamps it to
  the page. `GuidedPage` animates an `Animatable<Rect>` (520 ms, FastOutSlowIn)
  and draws the source rect letterboxed on the ambient backdrop.
- **Robust base**: double-tap zooms 2× around the tapped point, refitting the
  view to fill the viewport (`GuidedFocus.zoomed`); one finger pans while
  zoomed, clamped to the page; double-tap again, or advancing, returns to the
  panel fit. This works on every page, including full-page fallbacks.
- **Spread posture** (unfolded landscape): by default the two-page spread stays
  on screen and only the active page's half animates panel by panel while the
  other page is shown whole as context (tapping it moves back/forward). A HUD
  button (`ReaderUiState.guidedFullScreen`) switches to the full-screen variant
  where each panel fills the whole screen.
- The regular page surfaces (`ZoomablePage`) also double-tap zoom around the
  tapped point and clamp panning to the page.

## Manual override

Not implemented yet. A per-page editor would let the user draw/adjust panel
rectangles; overrides would be stored per comic (Room) keyed by page.
