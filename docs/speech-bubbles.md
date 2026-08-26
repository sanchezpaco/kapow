# Enlarged speech bubbles

A readability toggle for small lettering that keeps the page at its normal zoom:
each detected speech bubble or caption is drawn again, scaled up in place, over
the page. Toggle: the ChatBubble button in the HUD (`ReaderUiState.bubblesEnlarged`).
Nothing moves and nothing has to be tapped through — it is a render enhancement,
not a navigation mode.

## Pipeline

```
page bitmap ──▶ PanelDetector.bubbles (data) ──▶ OnnxBoxDetector (ML boxes) ──▶ SpeechBubbles.outlined (domain) ──▶ List<SpeechBubble> ──▶ cache
                                                                                                                              │
                                          BubbleLayout.enlarge (domain, pure) ──▶ List<EnlargedBubble> ──▶ ZoomablePage overlay ◀─┘
```

`PanelDetector.bubbles` asks the ML model *where* the bubbles are and the pixel
heuristic *what shape* they have: `OnnxBoxDetector` returns normalized boxes,
`SpeechBubbles.outlined` turns each box into a `SpeechBubble` with outlines.
Pixels are classified at the analysis size shared with panel detection (the
page is scaled so its **shorter** side is ~1000 px, so a double-page spread
keeps the same cell size as a single page and its small bubbles survive). `SpeechBubbles` and `BubbleLayout` are pure
Kotlin in `feature/reader/domain`, unit-tested on synthetic pages. Detection
runs off the main thread and is cached per page in `PageLoader` (only when the
toggle is used).

## ML boxes (`OnnxBoxDetector`)

The model is a **YOLO26n distilled from `ogkalu/comic-speech-bubble-detector-yolov8m`**.
The YOLOv8m teacher (F1 0.96, but 99 MB fp32 / 26 MB weight-only int8 and
~1.4 s per page on the Fold emulator) was run over ~2,500 pages of the
16-comic corpus (capped at 250 pages per series, the 98 ground-truth pages
excluded) and its boxes used as labels to train YOLO26n for 60 epochs at
640 px (`.claude/ml-spike-kit/build.py`, `label.py`). The current student was
fine-tuned from the first one (11 comics, 300 pages each, Marvel ×3) after the
corpus grew with 1970s halftone (Defensores), 1990s scans (Spiderman 2099,
Gambito) and modern digital Marvel (4F, Capitana Marvel). Exported with
ultralytics (`format=onnx imgsz=640 nms=True`, so the graph ends in
NonMaxSuppression and outputs `[1, 300, 6]` like the panel model) it is 9.4 MB
in `assets/models/bubbles.onnx` (stored uncompressed). Boxes with score ≥ 0.25
are kept (the threshold baked into the exported NMS).

On the 98-page ground truth (14 series × 7 pages) the student scores bubble
F1 0.96 — the same as the teacher, up from 0.94 for the first student, 0.78
for the pixel heuristic alone — at ~35 ms per page on a laptop CPU and
~230 ms including outline extraction on the software-rendered Fold emulator
(the teacher needed ~1.4 s; a 2953 × 4528 scan took 650–900 ms before the
page was scaled to the analysis size first). While bubbles are enlarged,
`PageLoader.preload` also detects the neighbouring pages, nearest first, so a
page turn normally finds its bubbles already cached. New styles get 4F 0.99, Spiderman 2099 0.99,
Defensores 0.90 (up from 0.88; see below — the teacher itself only reaches
0.90 there). On new styles the teacher is the reference to re-distil from;
re-distilling is a fine-tune from the previous student. The only comics under
0.9 are manga where the ground truth itself under-counts.

Defensores was the one series where the student inherited a teacher mistake:
1970s reprints carry a page-wide yellow recap caption ("EPISODIO ANTERIOR…")
that the teacher's pseudo-labels routinely missed, capping the student at the
teacher's own 0.90. Beyond-the-teacher correction: 120 of Defensores'
~230 training pages (stratified across its four source volumes) were rendered
with their existing pseudo-label boxes overlaid and reviewed by vision
subagents told to report only *missed* bubbles/captions — 7 boxes added
across 6 pages, mostly that recap caption. Fine-tuning from the previous
student for 60 epochs on the corrected set moved Defensores 0.88 → 0.90–0.91
depending on seed, with no consistent cost elsewhere (a second run with a
different seed served as a check against training noise, since a handful of
corrected pages barely shifts 60 epochs over 2,500). A parallel YOLO26s
fine-tune on the same corrected data reached bubble F1 0.97 overall but did
not fix Defensores itself (0.898, unchanged) and cost 4× the model size
(36 MB vs 9.4 MB) and ~2.75× the inference time — rejected, since detection
speed was already not the bottleneck and the series that motivated the
exercise didn't benefit. A page where the model finds
nothing yields no bubbles: falling back to the heuristic there was tried and
only added false positives (manga pages without bubbles).

Compression of the teacher, for the record: weight-only int8 (per-channel
int8 Conv weights + `DequantizeLinear`, fp32 activations,
`.claude/ml-spike-kit/quant_wo.py`) kept identical detections at 26 MB and
loads on ORT mobile; `ConvInteger` dynamic quantization does not load there
and QDQ static quantization destroyed accuracy; XNNPACK made no speed
difference.

Occasionally the exported NMS lets two near-identical boxes for the same
physical bubble through (observed IoU 0.99 on a real page). Each got outlined
and enlarged independently, and `BubbleLayout.enlarge` — seeing two distinct
bubbles that collide once grown — pushed them apart instead of recognising
them as one, rendering the same balloon twice, side by side. `detect()` now
merges boxes with IoU ≥ 0.5 (their union) before returning, for both the
panel and bubble models. Diagnosed by reproducing on-device (`Muerte by
ALHma[CRG]`, Marvel Saga 14 "Swing Shift") and dumping raw box coordinates.

## Outlining a box (`SpeechBubbles.outlined`)

For each ML box the detector looks for the bubble body inside it: the
connected component of `solidPaper` (white or cream) or of the solid dark mask
(negative bubbles) that owns most cells of the box, searched in a frame grown
by 1 % of the page side so the rim is connected. The component is clipped to
the box, dilated by the rim thickness like heuristic bubbles (so the black
border is part of the shape), and traced into outlines. If no body covers at
least a quarter of the box (lettering straight over art, sound effects) the
box itself becomes the bubble.

## Detecting bubbles without the model (`SpeechBubbles.detect`)

Still used by `SpeechBubbleVisualizer` when no box file is given and kept as
the reference pixel pipeline; the app no longer calls it.

Built on `PixelClasses` (see `guided-view.md`), plus four extra classes:

- `solidPaper`: every source pixel in the cell is white **or cream** (the pale
  yellow of Marvel-style caption boxes) — bubble and caption interiors.
- `solidDark`: every source pixel in the cell is dark and near-neutral (low
  chroma) — the body of a black/negative bubble.
- `ink`: some source pixel in the cell is dark — lettering strokes.
- `light`: some source pixel in the cell is light (luminance ≥ 155) — the
  lettering of negative bubbles. It is much looser than `white` on purpose:
  white type is anti-aliased and JPEG-softened, so its strokes fail the strict
  white test and whole lines ("¿QUÉ QUIE-") went missing.

Detection is **typography-first**: the primary unit is the **text run**, not the
bubble. It finds lettering, groups it into a text block, and enlarges that block
(plus a small padding). A drawn container (bubble or caption box) is only an
**optional refinement** — when a clean one is found it gives a nicer backing and
silhouette, but the container is never a gate that can reject a valid text run.
This is what fixed the recall failure where clear small bubbles enlarged nothing:
the old pipeline grew a container and then discarded the text if that container
failed strict shape filters. Three tones are processed independently and
merged at the end: **white** (dark ink on white paper), **cream** (dark ink on
the pale yellow of caption boxes, so a cream caption never grows into a white
moon behind it), and **negative** (light ink on `solidDark` — white lettering
on a solid-black bubble, the mirror of the white pass: the `ink` role is played
by `light` cells and the paper role by `solidDark`). The negative tone is first
restricted to **solid dark bodies**: only `solidDark` components that keep a
core after an opening (radius 3) are used as paper, so a real filled bubble body
survives with its interior text intact while the thin strokes of black lettering
and small dark features (a pupil, a bit of hair) — which have no solid core —
never act as a bubble and so are not enlarged over the artwork. Steps per tone:

1. **Strip gutters.** Long thin runs of paper (≥ 30 % of the page long, ≤ 1.2 %
   thick) are removed, so a bubble cut by a panel edge no longer merges with an
   axis-aligned white gutter.
2. **Words.** Non-paper cells enclosed by paper (not reachable from the page
   border) are segmented; a component is a *word* when it is between 0.4 % and
   4.5 % of the page tall, at least 0.4 % wide, and enough of it is `ink`:
   40 % for dark lettering on paper, 50 % for light lettering in a dark body
   (`BlockRules.minWordInkShare`). The ink test is what separates lettering
   from sky showing through clouds or moon texture. The paper threshold is
   lower because scanned print (1980s Aliens at 1282 px, 15-px letters) leaves
   an anti-aliased grey fringe around every stroke, and whole pages of plain
   white bubbles sat at 0.40-0.49 and were never seen.
3. **Text blocks.** Words within a word gap horizontally (0.8 % of the page)
   and a line gap vertically (1.2 %) **and sitting on the same connected paper
   region** are clustered (union-find). The paper test is what keeps the texts
   of two touching bubbles apart (the drawn outline separates their paper),
   so each gets its own container instead of one wide block whose ring growth
   starves the arcs of both; the word's paper region is the one above the
   first cell of its top row. A block must
   be dense (words cover ≥ 25 % of its box), be **text-shaped** (wider than
   0.6 × its height, or ≥ 40 % dense when taller — a grid of building windows
   is neither), and contain at least one line-shaped **row** (words sharing a
   baseline, joined, at least 1.5 × wider than tall). Rows rather than single
   words: when every letter is its own hole, "ME" or "HARTÉ" has no word wider
   than tall, but the row is. Per tone (`BlockRules`): ink on paper needs 2
   words; light on dark needs 4 and a density ≤ 1 — overlapping word boxes
   (density > 1) are art, never type, and two or three light holes in a dark
   shape (the eyes and emblem of a black costume, a shield star) are the
   typical false positive there.
4. **Grow the bubble.** Paper is flooded outwards ring by ring (Chebyshev
   distance to the block box), only within a rounded reach around the block.
   The flood is **seeded from the paper touching the words** (each word box
   inflated by one cell), never from the block box itself: the corner of a
   block box regularly pokes out of a round bubble (a long middle line under
   a short first one), and a seed placed there floods the page background
   around the bubble. Growth stops as soon as a ring adds less than 1 % of
   what was grown so far (creeping along a thin keyline or down a tail into
   same-colour art stops; the bubble body is filled even for a tight
   caption). For ink-on-paper tones that 1 % is measured against the **rings
   only** (the block's interior paper is excluded) — a tightly filled bubble
   has a large interior and small arcs beyond its text, and counting the
   interior made the last rings of its arcs look like a leak and cut through
   the first or last line of text. The negative tone keeps the interior in
   the total: black bubbles run into black art routinely and that stricter
   stop is their main containment. The reach is **scale-invariant**: it grows
   with the block (0.8 × its shorter side), floored at 2.4 % of the page and
   capped at 8 %. If the container grown at the block reach is not clean, or
   was still growing when it hit its reach (`reachLimited`), it is regrown at
   the 8 % cap; the far container is used when it is clean and cuts no word,
   otherwise the near one stands (a one-line block whose bubble is bigger than
   the minimum reach gets its full silhouette instead of a straight edge, and a
   far container that leaks through a gap in the outline is not preferred over
   a truncated but sound one).
5. **Container or padded text.** The grown blob is a *clean container* when it
   does not touch the page border, covers the block, has a compact fill (≥ 35 %
   of its box), is at most 12 % of the page, not thinner than 1.2 % of the
   page, no longer than half the page's shorter side (a recap page's white
   text on black is a text block, not a bubble), aspect ≤ 6, and has 3–50 %
   ink inside with ≥ 75 % of it text-like
   (line/block heights are the larger of the page fraction and a multiple of
   the blob's own median glyph height, so large manga lettering counts like
   dense Marvel type). A dark body must also be **lettered**: at least 60 %
   of the cells in its interior holes are `light` (`minHoleInkShare`, dark
   tone only). A black bubble's holes are its lettering; an open mouth's holes
   are a tongue and gums around a row of teeth (19–55 % light on the
   Venomverse pages versus ≥ 67 % for every real black bubble), and such a
   body is dropped outright — it never falls back to a padded rect.
   Otherwise the text still enlarges: the unit becomes the
   block padded by 1.2 × its glyph height (`Blob.filled`, enough to enclose the
   drawn outline of a bubble sitting on a white page margin), provided ≥ 60 %
   of the padding ring is paper (`MIN_MARGIN_PAPER_SHARE` — text must sit on
   flat paper; a TV ticker over art, a single open mouth or tattoo are dropped
   here), the rect does not touch any other inked hole (a rect is never pasted
   over a neighbour's text), and the block is not dominated by oversized words
   (> 2 × the median glyph covering > 50 % of the word area — a white costume
   silhouette with a few specks).
6. **Shared paper.** Containers whose cells overlap (two text blocks inside one
   drawn shape, e.g. a bubble chained to the next with no separator between
   them) are unioned, so the copy carries both texts instead of one covering
   the other. Bubbles that merely touch stay separate units.
7. **No cut text.** Ring growth stops at a straight Chebyshev ring, so a
   container can end halfway through a neighbouring bubble's letters (a wide
   bubble whose words were only partly found, a chain whose next link has no
   block of its own). After the union, a merged shape that still **cuts a
   word** — a word hole with pixels both inside and outside the shape's
   row/column hull — is regrown from a seed extended with every word it
   overlaps (up to 5 rounds) so the neighbour becomes part of the unit; if no
   clean container results, the member blocks fall back to padded rects.
   The check runs after the union on purpose: a link's own container
   legitimately stops at the neck and the neighbour's completes it.
8. **Outline.** For the dark tone, cells of the container that belong to a
   **thin long band** of the tone (a run ≥ 30 % of the page long whose band
   of such runs is ≤ 1.2 % thick — a panel border line) are removed first: a
   black caption sitting on a panel's border line is one dark component with
   it, the gutter stripper keeps the piece of the line above the caption
   (its paper run continues into the caption), and the copy of that piece
   landed as a comb in the white gutter. This is done on the finished blob,
   not on the paper mask, so words and enclosure are unchanged (stripping
   the mask fragmented a recap page's black background into new false
   containers). A finished container is then dropped when it is **combed by
   art**: the short gaps (≤ one glyph height) between its cells along every
   row and column are filled, and if more than 10 % of that filled shape is
   ink outside any word (`MAX_GAP_ART_SHARE`, `Blob.gapArtShare`) the shape
   is not a bubble but paper crawled through art — halo lettering over a
   building facade grows a clean-looking comb whose fingers follow the window
   mullions (Attack on Titan p003 "ESTE MUNDO…", and the STOMP slabs), and
   bead/eye highlights grow a comb through the art lines around them. Real
   bubbles sit ≤ 4 % (Venom, all 140 containers); a merged pair with a
   concavity full of art stays under because a wide concavity is not a short
   gap (Doom "CLARO QUE LO ES" + "HAY MUCHOS PLANOS", 8.5 %). No scalar shape
   gate separated that comb from real bubbles (box fill 0.46, hull solidity
   0.66, opened solidity 0.79 all overlap Venom), nor did the ring-growth
   profile. The blob is then inflated by its **rim thickness**, at least 2 cells so the
   drawn outline stroke is included: the ink density of successive one-cell
   rings around the blob is measured up to 1.5 % of the page, and the rim is
   the last ring still ≥ 35 % ink (gaps inside a ragged rim are tolerated — a
   whisper bubble's rim is a halo of detached fragments with grey between
   them); a rim that is still dense at the cap is dark art around the bubble,
   not a rim, and the default margin stands. Without this the copy of a
   ragged-rim bubble was a plain white blob and the original rim showed around
   it so the drawn outline stroke is
   included, and the exact contour of each of its connected components is
   traced (crack following along cell edges, corners only) into a polygon
   (`SpeechBubble.outlines`, normalized). The contour is exact on purpose: a
   per-row span would turn a three-bubble chain into its hull and the copy
   would carry the art between the links. One polygon per component matters
   because step 9 unions blobs whose cells are disjoint; tracing only the
   first component rendered such a unit as a sliver.
9. **Overlapping tones** (boxes overlapping by ≥ 15 %) are merged into one
   unit, so they grow together instead of covering each other.

Known limits: lettering-only SFX and interjections are not enlarged by design
(SFX lettering is not enclosed by any tone, big SFX are too tall to be words,
one- or two-hole shouts fall under the word minimum); a bubble fused with a
large white area (a moon, a white costume) is found only when its letters do
not touch the outline, and its copy carries a bit of that background up to the
reach limit, so a faint seam can show there; a letter touching the bubble
outline merges with it and is lost from the block, which can trim the padded
fallback by a glyph — and a whole line fused with the outline (the first or
last line of a tightly lettered bubble) walls off the paper beyond it, so the
container ends at that line and its copy shows it clipped; a bold jagged font whose letters fuse across lines is too
tall to be a word, so such a bubble is missed rather than cut; a tinted
(blue-grey shadowed) bubble is not paper at all and is invisible to every
tone, including the cut check of its neighbours. A black bubble's tail that
runs into same-colour art is clipped where growth trickles out, which can
leave a small seam at the tail. The negative pass trades a small false-positive
risk for black-bubble support; the solid-core restriction removes the common
cases (black lettering and small dark shapes read as tiny negative bubbles over
the art), but a large solid dark shape with a light gap — a floodlight truss
against a bright sky, a big eye or open mouth — still has a core and can be
enlarged spuriously, rare and confined to genuinely dark artwork (white/cream
pages are unaffected).

## Laying out the enlargement (`BubbleLayout`)

`enlarge(bubbles, scale)` scales every bubble by the user's scale (default
`BUBBLE_ENLARGE_SCALE` = 1.3×, adjustable 1.1–2.0× with the slider that appears
under the HUD buttons while the toggle is on; persisted in DataStore as
`bubble_scale`). The whole original silhouette is repainted flat at render
time, so whatever the copy does not cover shows as a paper-coloured hole the
size of the displacement. The policy is therefore **leave the smallest
possible uncovered area**: keep the copy over its original, give up scale down
to a floor before sliding, and slide only when nothing else fits.

1. Every bubble grows about its own centre, bounded by the page only: a bubble
   at a panel edge grows over the gutter instead of sliding inward.
2. Two copies **collide** when their silhouettes intrude into each other more
   than the originals already did. Each outline is resampled to 48 points
   along its perimeter; a point of one copy intrudes when it lands inside the
   other copy's silhouette shrunk to its text body (`TEXT_BODY_SHARE` = 90 %,
   so rims may touch or overlap by a few pixels, as stacked balloons do in the
   art). The count is compared with the same count on the originals: ML boxes
   of neighbours overlap routinely (tails, a caption over a balloon, a tail
   drawn across the next balloon) and reproducing that overlap is not a
   collision. Box-based collision was tried first and failed both ways — it
   either forbade placements the art itself uses or, with a box-overlap
   allowance, let one copy cover the neighbour's text.
3. Colliding copies are pushed apart (16 passes, along the axis with the
   smaller overlap, a quarter of the overlap each) **without uncovering their
   original**, and each colliding pair then searches the 3 × 3 grid of
   positions in which both copies still contain their originals for the one
   with the fewest collisions against every neighbour. The joint pair search
   matters: a chain such as caption → balloon → balloon on Ben Reilly #01
   p.17 has two valid contained arrangements out of 729 and a per-bubble
   greedy search finds neither.
4. Pairs that still collide are **shrunk by 10 % steps down to
   `CONTAINED_SCALE_FLOOR` = 1.15×**, pushed and re-anchored again.
5. Only then do the coverage steps relax (`COVERAGE_STEPS` = 1.0, 0.85, 0.5,
   0): a copy may uncover up to 15 %, 50 % and finally the whole original per
   axis, never farther than one box away; what still collides is shrunk
   towards 1× (`BubbleLayoutTest.crowdedClusterEndsWithoutOverlappingCopies`).

Measured with the uncovered-area metric in `SpeechBubbleVisualizer`
(`metrics.json`: per bubble and per page, as a share of the page; original
silhouette minus the union of all copies). Ben Reilly #01 (24 pages, 271
bubbles): 0.2005 → 0.0254 page-areas summed, no bubble uncovering more than
0.2 % of the page (26 before); the 98-page corpus (873 bubbles): 0.840 →
0.213, 1 bubble stuck at 1× (32 before) and 149 below the full scale (138
before, now all ≥ 1.15× unless the coverage steps ran out). Layout costs
≈20 ms per page on the JVM, 0.5 s worst case on a 19-bubble Defensores page.
The earlier "compact group zoom" step (scale a cluster about the group's
centre) was removed: it uncovered a strip on every outer member by
construction and the contained-anchor search covers its cases.

## Rendering

`ZoomablePage` shows a small spinner in the page corner while its bubbles are
being detected (one page at a time — `PageLoader` gates detection with a
semaphore and serves the visible page before its neighbours, so a page turn
never competes with several detections for the cores), draws the page as
before and, in a `drawWithContent` after the zoom/pan `graphicsLayer` (so the
overlay pans and zooms with the page), draws in two passes straight into the
Compose `DrawScope` (`BubbleOverlay.drawBubbles`). Nothing is rasterised in
between: an earlier version rendered a full-page ARGB overlay bitmap per page
and per slider step, which on a 2953 × 4528 scan meant 53 MB allocations and
page turns at ~10 fps. The only precomputed part is `BubbleOverlay.plan`: the
paper colour of each enlarged bubble. First every enlarged bubble's **original footprint** (its
outline polygons) is repainted with the bubble's paper colour — the median
luminance of samples taken **inside the silhouette** (`SpeechBubble.interiorSamples`,
never the bounding box, which would pick up surrounding art and paint a grey
smear) — so a copy that slid into a gap leaves flat paper, not a half-covered
original. Then each copy is drawn: clip to the scaled outline polygons, draw the
source bitmap region of the bubble box into its target box with high filter
quality, plus a faint 1 dp outline so the copy reads as intentional. Bubbles at
scale 1 (fully constrained) are skipped. The offline visualizer mirrors both
passes so its output matches the device.

## Iterating on detection

`SpeechBubbleVisualizer` (unit test, skipped by default) renders both the
detected outlines (`<name>-bubbles.png`) and the actual enlarged result
(`<name>-enlarged.png`) for a folder of page JPEGs:

```
COMICIFY_PANEL_VIZ_DIR=/path/to/pages ./gradlew :app:testDebugUnitTest \
    --tests '*SpeechBubbleVisualizer*' --rerun -i
```

It also writes `out/metrics.json` (per page: count, scale, box and target of
every bubble), which makes recall regressions measurable: keep the file from
before a change and match boxes by IoU afterwards.

With a `boxes.json` in the folder (the `.claude/ml-spike-kit` dump format:
`[{"file": ..., "bubbles": [[l, t, r, b], ...]}, ...]`, normalized to the
uncropped page) the visualizer runs `SpeechBubbles.outlined` on those boxes
instead of the heuristic, which is how the ML path is checked offline.
`MlDetectorBenchmark` (androidTest) dumps ML panels and bubble boxes plus
timings from the device into `files/mlspike/device.json` for `score.py`.

The ground truth is the device, not the source JPEGs: Android decodes and
subsamples differently, marginal ink shares shift, and the reader shows
spreads unsplit. Validate on the emulator by comparing a toggle-off and a
toggle-on screenshot of every page; when a page misbehaves, reproduce it
offline on a PNG of the page as the reader decoded it so the visualizer sees
the same pixels, and instrument `container` (box, fill, reach, what the
growth stopped on) before touching a threshold — the visible symptom rarely
names the real cause.
