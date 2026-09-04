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

## Bubble-aware stops

Panel boxes alone fail on the two hardest page kinds: a full-art splash (the
model returns one page-sized box, or nothing) and a painted page whose panels
the model only partly finds, leaving whole regions of dialogue uncovered. On
both, a single panel stop shows the reader everything at once with the text too
small, or skips speech entirely. `GuidedTour` (pure, `feature/reader/domain`)
turns the panel boxes plus the speech-bubble boxes into the final stop list:

- Two panel boxes that overlap by 70 % of the smaller, with neither
  containing the other, are **one panel detected twice** and merge. That is
  how a scan's paper edge, returned as a full-width band across the top of the
  page, folds into the page-sized box behind it instead of opening the tour on
  a dead strip (Spiderman 2099 009), and how a painted panel returned as two
  overlapping halves stops being toured as one giant region (Sueñan los
  Androides 023). A box properly contained in another is left alone — insets
  are real, and so is a tall panel that merely overlaps the column beside it.
- A page whose largest box covers at least 80 % of it, with the remaining
  boxes adding under 20 % of the page outside it and covering under half of
  it, is a **painted page**: one image the model broke up. It opens with a
  whole-page establishing stop. If it has at most two smaller boxes, none of
  them holding a balloon, they are scraps of the painting and drop out, and
  the page is toured whole and then balloon cluster by balloon cluster
  (Arkham 027, Sueñan los Androides 016). Otherwise they are real panels and
  every one is kept — a silent panel on a painted page is still a panel (LOK
  006a, Arkham 033), and four boxes across the top of a wordless manga splash
  are its top tier, not scraps (One Piece 026). A box under 8 % of the page
  that detected balloons already cover by 70 % does not count towards those
  two: it is a caption the panel model also fired on.
- A panel that **contains** other panels (a bleed host with insets, or a
  detector box glued around a whole row) never becomes a stop itself: only the
  largest strip its children leave free (above, below, left or right of them)
  does, and only when that strip is at least 8 % of the page and not already
  covered by another panel (a box that encloses the host itself, such as the
  page-sized box a scan's paper edge produces, does not count as covering —
  it used to bury every strip on those pages and orphan their balloons into a
  window straddling two tiers). So a bottom region with its own balloons under a
  row of insets is toured once, after the insets, instead of as a giant stop
  that repeats them. A strip narrower than 15 % of the page on its short
  side that holds no balloon is dropped: a scanned page whose visible paper
  edge makes the model return a page-sized box around the real panels used to
  open on the blank margin the children left free (Spiderman 2099 008/016), a
  dead tap by policy. A wide wordless strip stays — it is art the reader
  should see, and sometimes dialogue the bubble model missed (Venomverse
  009b).
- Each bubble is assigned to the **innermost** panel holding its centre, or
  else to the panel covering at least a quarter of it (a balloon hung over the
  gutter); failing both, to the panel **straight across the gutter** — within
  3 % of the page above or below it, with at least 80 % of the balloon inside
  that panel's column, nearest one wins. That last rule is what makes a
  caption sitting in the gutter over its panel read *with* that panel instead
  of becoming a band of its own across the page (Defensores 049's three yellow
  captions). A bubble that matches none of the three is an **orphan** and still
  becomes a stop, so dialogue on a region the panel model missed is never lost.
  (Owning a balloon merely because it *touches* one panel was tried and
  rejected — it contradicts the quarter-overlap rule; the gutter rule is
  narrower and does not.)
- An ordinary panel's stop **grows over the balloons it owns** that overhang
  its box (plus a hair of margin, clamped to the page), so a gutter-straddling
  balloon is read whole with its panel instead of sliced at the detector's
  edge. A stop edge that would still cut into a balloon it does not own by
  more than a hair (a tenth of the balloon or more) **shrinks past that
  balloon** when the trimmed stop keeps its own balloons and at least 70 % of
  the panel, so the balloon is read once, with its owner. When it cannot
  shrink that far and a quarter or more of the balloon is inside, it grows
  over the balloon instead (one re-read beats lost words); a smaller cut it
  cannot trim is left as it is — growing over it too chained across dense
  manga grids, where every balloon overhangs its neighbour, until one stop
  swallowed a whole tier (titanes 039) and the next stops re-read it. Below a
  tenth the cut is a clipped border with every word visible, which the policy
  scores `minor`.
- An orphan cluster that some panel stop already shows whole gets no window of
  its own (a caption caught between two grown panels was being read three
  times).
- A box smaller than 4 % of the page sitting inside another panel (a credits
  strip, a logo box) is absorbed into its host instead of becoming a dead stop.
  A panel stop whose view the previous panel stop already encloses (a caption
  box the detector returned as a panel, swallowed when its neighbour grew over
  the balloons around it — Androides 018) is dropped as redundant; a window
  inside its opener is not, that zoom is the point of the opener.
- A page with no balloon at all whose one or two boxes cover under half of it
  (a painted chapter title the detector cut into fragments, Androides 012b)
  is shown whole rather than as arbitrary slices of one image.
- Only a **large** panel (≥ 45 % of the page: a splash or full-art page) is
  split into **reading windows**, one per bubble cluster. A true splash — the
  page's only panel — keeps the whole page as an **establishing stop** before
  its windows, however many balloons it holds, so the reader sees what the
  character is doing before reading what they say (the maintainer's call over
  the judge's, which preferred to skip the opener on dense splashes); it gets a
  window even for a single cluster, since at whole-page scale no balloon is
  readable on the phone. A large panel among other panels goes straight to its
  windows, and only when it holds two or more clusters. Ordinary panels,
  however many balloons they hold, stay a single stop, so a manga page is
  toured panel by panel and is never chopped into overlapping fragments.
  Balloons within 7 % of the page cluster into one window, and two windows
  that overlap by half or more merge into one, so a row of balloons is read as
  one band instead of two nudged views. Both joins stop at a **readable
  height**: balloons only cluster while the cluster stays within the minimum
  window height (30 % of the page), and two windows only merge while the
  merged view stays under 45 % of it. Without those bounds a painted page of
  scattered narration collapsed into one band half the page tall, which the
  redundancy pass then dropped against the establishing stop, leaving eight
  captions with no reading window at all (Sueñan los Androides 029).
- A window is centred on its cluster, sized to a **minimum that scales with
  the page** (plus a small margin so surrounding art shows), and clamped to the
  panel **grown to include its cluster** — so it never drifts into the
  neighbouring panel, yet still covers a balloon the panel box cut off. The
  minimum is 0.42 × 0.30 of the page or the **median panel's** width and
  height, whichever is smaller: on a fourteen-panel European page a
  page-scale minimum turned one caption into a catch-all band spanning four
  panels (Zombillenium 014/021, Defensores 032/049). An orphan window clamps to
  the panel it overlaps most, grown to include its cluster — not to the page,
  which let it bite into the row above (Zombillenium 014's tai-chi caption).
  A window edge **never crosses a balloon**: when the minimum size reaches into
  a neighbouring cluster the window shrinks past that balloon (as long as its
  own cluster still fits), otherwise it grows to include it — so no caption is
  ever shown cut mid-word, stopping once the grown view would pass 45 % of the
  page tall or 60 % wide. Letting that growth run unbounded was tried: it
  cleared one sliced caption (Androides 016) and cost as many pages, where a
  window swallowed the tier beside it (Arkham 035, Androides 017).
- After ordering, a **redundancy pass** drops any stop of similar size (at
  least half of its neighbour's area) that is ≥ 85 % contained in it, so no two
  consecutive stops show almost the same view — the cause of taps that appeared
  to "skip" several panels at once. A zoom from a panel into its first window
  is not redundant.
- Then a **dead-tap pass** drops any stop that shows at least one balloon,
  shows no balloon whole that the other stops do not already show whole, and is
  ≥ 90 % covered by them: a tap that re-reads text the reader has just read and
  adds no art of its own (Defensores 030's middle gargoyle stop, Sonic 016's
  staircase of sub-windows, Zombillenium 014's stripped panel). A stop with no
  balloon at all is never dead — that is art, and a silent panel is a stop.
  The **establishing opener** neither counts as a cover nor is ever dropped,
  otherwise every window on a splash would look redundant against it.
- The bubble model runs on **every** page. A coverage gate that skipped it on
  dense grids (panels covering ≥ 80 %) cost more than it saved: manga and
  European letterers hang balloons over the gutter of exactly those grids, and
  without bubbles the panel stop cannot grow over them (titanes 035/038/044,
  blacksad 013/015 in the eval sample were sliced for that reason alone).
- The final stops are ordered by a recursive **guillotine cut** over their
  **anchors** — the panel box for a panel stop, the balloon cluster for a
  reading window — never over the windows themselves, whose minimum size
  routinely straddles a gutter and would block every cut. The page is split at
  the topmost horizontal line that crosses no anchor (top part first), else at
  the leftmost vertical line that crosses none (left part first, or right
  first for `ReadingDirection.RightToLeft`), and each part is cut again. A
  panel anchor blocks a cut only when the line enters it by more than 5 % of
  its extent (at least 1 % of the page), so a detector box overshooting the
  gutter by a hair does not merge two columns; a balloon-cluster anchor is
  **floating** and tolerates 30 %, because letterers hang captions over the
  gutter and a caption poking a fifth of its width past a column must not glue
  the two columns into one row. A tall panel spanning two rows of a
  right-hand column is therefore followed by that column top-to-bottom, where
  a row-based sort read the column's top panel last. Overlapping anchors that
  admit no cut fall back to the mutual-centre row test; a stop whose anchor
  contains others is inserted right before the first one it contains (an
  establishing stop precedes its windows). When no cut exists because a tall
  panel's box overlaps the column beside it (LOK 013b: a staggered layout
  whose right-hand panel reaches under the bottom-middle one), the row test
  would put every stop in one row and read the column by x alone; instead an
  anchor spanning at least 80 % of its group's height — the whole uncut
  group first, then each row of it — with no vertical companion (no other
  stop covering 60 % of its height, which a merely taller panel in a real
  row has, Ben Reilly 020) is set aside, the remaining column of
  two or more stops is ordered on its own, and the tall anchor is slotted
  before the first stop that lies beyond it in reading direction (a tall
  right-hand panel comes last in LTR; a tall left column whose box overshoots
  into a band across the page top still leads the page, 2099 009).

`PageLoader.stops(index, direction)` runs the panel model and the bubble
model (both cached in memory and persisted in Room), then builds the tour. `GuidedReader` consumes `stops`
instead of raw panels.

`GuidedReader` asks `PageLoader.preload(…, panels = true)` for the pages
around the current one, so their panels are detected (or read from Room)
before the reader gets there — and their bubbles too; one detection runs at a time behind the shared
semaphore, with a per-page mutex so the current page is never detected twice.
On the Fold, entering Guided View on a fresh comic detects the current page
and the next two within ≈ 600 ms, and each step then finds the next page's
panels already cached (they used to be detected on arrival, 130–210 ms late).

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

Measured on the 77-page sample of the 11-comic corpus (`tools/training/`
holds the ground truth and scorer, see `docs/training.md`; the corpus itself is
private and not distributed): panel F1 0.93 vs 0.77 for the
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
KAPOW_PANEL_VIZ_DIR=/path/to/pages ./gradlew :app:testDebugUnitTest \
    --tests '*PanelDetectionVisualizer*' --rerun -i
```

Annotated PNGs land in `<dir>/out`. Iterate there, then trust the unit tests.

## Navigation and framing

- Left 20 % / right 20 % tap zones go to the previous / next panel; the centre
  toggles the chrome. The stops of a page are exactly its detected panels:
  moving forward into a page lands on its first panel, moving back lands on the
  previous page's last panel. A splash or full-page fallback with two or more
  speech bubbles is toured bubble by bubble over the art (see **Bubble-aware
  stops**); one with fewer is a single stop showing the whole page.
- A HUD stops indicator (always visible in Guided View) shows the current
  position as a row of dots, with a chevron that lights up on the last stop to
  signal that the next tap turns the page.
- `GuidedFocus.frame` pads the panel rect by 2 % of the page and clamps it to
  the page. The focus animation is keyed on the page, the stop index **and the
  target rect**: a page turn first composes with the previous page's stops
  (the new page's list arrives asynchronously), so keying on the rect is what
  makes the view move on to the new page's first stop instead of staying on
  the old page's framing until the next tap. `GuidedPage` animates an
  `Animatable<Rect>` and draws the focused panel bright over the rest of the page
  dimmed — a **spotlight** with soft, feathered edges, so the surrounding art
  stays visible for context instead of a hard letterbox.
- **Director cuts** (`DirectorCut`, pure, `feature/reader/domain`). Every move
  used to be the same `tween(520 ms, FastOutSlowIn)` whether it was a step to the
  panel alongside, a close-up on a balloon, an opening onto a splash or a page
  turn. The move is now chosen from the geometry of the two rects:
  - **Pan** — similar area. Duration grows with how far the camera travels
    (240 ms + 420 ms per page-width, capped at 520), so a short step stops
    costing as much as a jump across the page. Past a quarter of the page the
    move **arcs**: it runs through an apex up to 30 % wider than the two ends, so
    the reader sees the art it crosses instead of teleporting. The arc is two
    chained `animateTo` calls, which carry the animatable's velocity across the
    apex; a `keyframes` spec would visibly stop the camera there.
  - **Push in** — target under 60 % of the current frame. 560 ms with a long
    deceleration; the braking is what makes a close-up read as deliberate.
  - **Pull back** — target over 1.7× the current frame. 340 ms; an opening reads
    better fast.
  - **Reveal** — arriving *forward* on a stop covering 86 % of the page (a splash,
    or the establishing stop of a painted page). The veil below starts opaque and
    lifts over 700 ms. Skipped backwards: a reveal you have already seen is a
    delay.
  - **Page turn** — the camera **jumps**, under the veil. It used to travel from
    the old page's framing to the new one while the bitmap had already swapped,
    describing a journey that never happened.
  Returning to the panel fit after a double-tap zoom or a drag keeps the old
  uniform 520 ms (`RETURN_ANIMATION_MILLIS`) — that is a correction, not a cut.
- **The veil** is what makes a page turn a cut instead of a flash. A turn fires
  the focus animation **twice**: once the moment the page changes, still carrying
  the *previous* page's stop list, and again when the new page's stops arrive.
  Without a cover the first one framed the new art on an old, usually much larger
  rectangle — measured on the Fold as a jump in mean screen brightness from 46 to
  70 and back over ~100 ms, a visible flash. `GuidedReader` therefore tracks
  which page the on-screen stops belong to (`stopsPage`), and while a turn is
  unsettled the camera **does not move at all** and an opaque black layer fades in
  over 140 ms; when the stops land, the camera jumps and the veil lifts over
  220 ms. Its cost scales with the wait: with detections cached the veil only
  reaches part way before it is on its way out, so a fast turn blinks rather than
  dips. The veil doubles as the Reveal, which is why the spotlight dim is a
  constant again — dimming only darkens the *surround* of the focus rect, so on a
  whole-page stop, where there is no surround, it did nothing at all.
- **Auto-pan** (implemented but currently **disabled** — `AUTO_PAN_ENABLED`
  in `GuidedReader`, no HUD toggle): a large stop (a splash or big panel,
  ≥ 40 % of the page) gently zooms in (~1.2×) and makes a single slow
  top-to-bottom pass, then rests, so its text enlarges without extra taps. It is
  off until it is content-gated to skip near-empty establishing panels (a large
  but blank corridor/wall would otherwise be toured as dead space).
- **Robust base**: double-tap zooms 2× around the tapped point, refitting the
  view to fill the viewport (`GuidedFocus.zoomed`); one finger pans while
  zoomed, clamped to the page (only once the finger passes `touchSlop`, see
  `PanSlop` in `reading-modes.md`, so a shaky tap stays a tap); double-tap
  again, or advancing, returns to the panel fit. This works on every page,
  including full-page fallbacks.
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
`device.json`; `HeuristicRectsDump` (unit test, `KAPOW_RECTS_DUMP_DIR`) does
the same on the JVM. Both JSONs are scored by `tools/training/score.py`.
