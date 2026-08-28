# Guided View

Panel-by-panel reading: the reader auto-focuses one comic panel at a time with a
cinematic pan/zoom between them. It is the readability fix for the two-page
spread on the unfolded Fold and shines on the folded cover screen. Toggle: the
ViewCarousel button in the HUD (`ReaderUiState.guided`).

## Pipeline

```
page bitmap ──▶ PanelDetector (data) ──▶ OnnxBoxDetector (ONNX) ──────▶ PanelLayout.complemented ──▶ readingOrder ──▶ List<Rect> ──▶ cache
                      │                                                          ▲                                        │
                      └──▶ PanelDetection (domain, pure heuristic) ──────────────┘                                        │
                     tap ──▶ GuidedReader animates the focus rect to the next panel ◀────────────────────────────────────┘
```

`PanelDetector` (data layer) runs both detectors on every page: the ML boxes
are the primary answer and `PanelLayout.complemented` adds the heuristic panels
that overlap no ML box by more than 30 % of their own area. This covers what
the manga-trained model structurally misses — borderless panels on Western
pages, where it returns only the framed neighbours — while heuristic panels
that merge or loosely duplicate an ML box are discarded. With no ML boxes the
page is just the heuristic result. On the 77-page ground truth the union is
neutral (F1 0.931 → 0.934, recall +2 points, precision −1); the gain shows on
open-panel pages the ground truth barely contains. Everything after the boxes
is pure Kotlin in `feature/reader/domain` and unit-tested on synthetic pages.

## ML panel detection (default path)

`OnnxBoxDetector` (shared with speech bubbles, one session per model asset) runs a YOLO26n detector fine-tuned on manga frames
(`leoxs22/manga-panel-detector-yolo26n`, exported to ONNX, 9 MB in
`assets/models/panels.ort`, ORT format with fp16 Conv weights — 5.2 MB) through
a minimal ONNX Runtime build (`docs/ml-runtime.md`).
The page is letterboxed to 640×640 on a grey canvas, the end-to-end output
(`[1, 300, 6]` = x1, y1, x2, y2, score, class) is filtered to class `frame`
with score ≥ 0.35, and boxes are mapped back to normalized page coordinates.
The session is created once per process from a copy of the asset in
`filesDir` (ONNX Runtime needs a real file).

Measured on the 77-page sample of the 11-comic corpus (`.claude/ml-spike-kit`
holds the ground truth, scorer and dumps): panel F1 0.93 vs 0.77 for the
heuristic, 54/77 pages exact vs 29, ~100 ms per page on the Fold emulator vs
~540 ms. Largest wins on bleed-heavy and manga pages (Venomverse 7/7 exact);
the only regression is clean Marvel grids where the heuristic was already
near-perfect (Ben Reilly #01 0.94 → 0.79, boxes slightly loose or merged).

## Heuristic panel detection (fallback)

Real `.cbr`/`.cbz` files carry no panel metadata, so panels are detected from
pixels. Marvel-style pages mix clean grids, tilted gutters, bleeds, insets and
speech bubbles that overhang gutters, so the detector is built around a few
robust structural facts rather than a single threshold:

1. **Pixel classes** (`PixelClasses`). The page is first scaled with a
   filtered resize so its shorter side is ~1000 px (`PanelDetector`;
   classifying the 13 MP of a high-resolution scan cost ~800 ms per page and
   changed nothing on the ground truth), then every pixel is classified:
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

   Bleed layouts whose panels are separated only by **black keylines** (a
   full-bleed panel with framed panels stacked above/below it, no white gutter)
   get a second, stricter line search over `separator ∪ ink`. An ink line cuts
   only when it is thin (0.25–2.5 % of the page width, so solid dark areas are
   not lines), stands out from its flanks (≥ 0.15 more line coverage than the
   rows beside it, which rejects noise inside black skies), spans a box at
   least 60 % of the page (an art edge inside one panel never qualifies),
   leaves both parts ≥ 8 % of the page (caption-box outlines do not), and one
   of the two parts is bounded by white margin on the sides perpendicular to
   the cut (a railing or roof line inside a panel that bleeds to the page edge
   is not a keyline). Line thickness limits are page-relative, not box-relative,
   so gutters keep splitting inside already-cut boxes.
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
`PageLoader` (only computed when Guided View is used) and persisted in the
`page_detections` Room table so a re-read never re-runs the model (see
`docs/speech-bubbles.md`, "Persisted detections"). The heuristic takes
~100–250 ms per page on the JVM and is frozen since 2026-08-23: the remaining
failures are not separable by pixel rules, which is why the ML model is the
default path.

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
  smaller). Keyline-only cuts also need a white page margin next to one of the
  parts, so full-bleed pages with black keylines on every side stay merged.
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
  toggles the chrome. Multi-panel pages prepend a **whole-page overview stop**,
  so moving forward into a page shows it whole first, then the panels; moving
  back lands on the previous page's last panel. Splashes (a single stop) are
  entered whole as before.
- A HUD stops indicator (always visible in Guided View) shows the current
  position as a row of dots — the overview is a rounded marker, panels are
  circles — with a chevron that lights up on the last stop to signal that the
  next tap turns the page.
- `GuidedFocus.frame` pads the panel rect by 2 % of the page and clamps it to
  the page. `GuidedPage` animates an `Animatable<Rect>` (520 ms, FastOutSlowIn)
  and draws the focused panel bright over the rest of the page dimmed — a
  **spotlight** with soft, feathered edges, so the surrounding art stays visible
  for context instead of a hard letterbox.
- **Auto-pan** (implemented but currently **disabled** — `AUTO_PAN_ENABLED`
  in `GuidedReader`, no HUD toggle): a large stop (a splash or big panel,
  ≥ 40 % of the page) gently zooms in (~1.2×) and makes a single slow
  top-to-bottom pass, then rests, so its text enlarges without extra taps. It is
  off until it is content-gated to skip near-empty establishing panels (a large
  but blank corridor/wall would otherwise be toured as dead space).
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

## When detection falls short

There is deliberately no manual panel editor: a reader should never have to
draw rectangles. A page that falls back to one stop, or a merged stop, is read
with the double-tap zoom around the tapped point and one-finger pan, which work
on every page. Detection improvements are the only planned fix.

`MlDetectorBenchmark` (androidTest, skipped unless `filesDir/mlspike/*.jpg`
exist — copy pages in with `adb push` to `/data/local/tmp` and
`run-as com.comicify.debug cp`) dumps ML and heuristic rects plus timings to
`device.json`; `HeuristicRectsDump` (unit test, `COMICIFY_RECTS_DUMP_DIR`) does
the same on the JVM. Both JSONs are scored by `.claude/ml-spike-kit/score.py`.
