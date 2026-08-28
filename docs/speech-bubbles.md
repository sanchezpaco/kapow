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
toggle is used), first in memory and then in Room (see below).

## Persisted detections (`page_detections`)

Detections are never recomputed for a page already seen. `PageLoader.panels`
and `bubbles` check their `LruCache`, then `PageDetectionStore`, and only then
run `PanelDetector`, writing the result through to Room. The
`page_detections` table (`core/storage`, DB version 3) is keyed by document
URI and page index — the reader opens by URI, and a library rescan that
recreates the `comics` row does not invalidate detections — with nullable
`panels` and `bubbles` columns so Guided View and bubble mode fill their
own half independently. Rects and outlines are normalised page coordinates
serialised by `PageDetectionCodec` (pure Kotlin, round-trip tested): panels
`l,t,r,b;…`, bubbles `l,t,r,b|x,y,x,y,…/…;…`.

Every row carries `modelVersion` = `DETECTIONS_VERSION` (`PanelDetector.kt`,
e.g. `panels-v1+bubbles-v4`). Bump it whenever a bundled ONNX model, the
outliner or the panel heuristic changes; rows with another version are
ignored and overwritten lazily on the next visit, so there is no migration
to write when a model ships. `PageLoader` logs `detected bubbles on page N
in M ms` at debug level on every real model run; a second open of a comic in
bubble mode must log none. Measured on the Z Fold (Doctor Doom #1 pp. 5-12,
bubble mode, 4 page turns): first open 8 runs of 60–645 ms (page 5, the one
the toggle waits for: 645 ms); second open after a process kill 0 runs, the
same enlarged layout, frames 3.8 % → 3.2 % janky.

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
NonMaxSuppression and outputs `[1, 300, 6]` like the panel model) it is 9.8 MB
in fp32; the bundled `assets/models/bubbles.onnx` (stored uncompressed) keeps
the Conv weights in fp16 behind a `Cast` node (`.claude/ml-spike-kit/quant_fp16.py`,
2026-08-28), 5.1 MB, and ORT folds the casts at session creation so inference
cost is unchanged. On the 147-page ground truth the fp16 weights give the same
boxes on every page (F1 0.97 = fp32); weight-only int8 (2.8 MB) kept the
global F1 but lost up to 0.04 on single series (Ben Reilly, Vengadores 174,
Venomverse) and was rejected. Boxes with score ≥ 0.25
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

Student v4 (2026-08-28) is a fine-tune of v3 on the whole `comics/` folder:
4,723 pages of 31 series (250 per series, 8 % validation), 73 of the issues
new — Doctor Doom, Absolute Carnage, Escuadrón Suicida, Loki, Marvel
Zombies, Marvels: Ruinas (painted), Black Cat Strikes, Vader Down, two Panini
tomo scans and the first European albums, Blacksad and Rapaces. The teacher
labelled the pages (29,546 boxes); 500 training pages of the seven new
styles were then reviewed by vision subagents for missed balloons and
captions, which added 57 boxes on 33 pages (`v4_bubble_corrections.json`:
"?" / "?!" mini-balloons, borderless narration captions, the Marvel Zombies
recap captions on credits pages). 60 epochs, 4.6 h on MPS, val mAP50 0.992.
The box ground truth grew to 147 pages of 21 series (`gt_{doom,ruinas,
blacksad,rapaces,escuadron,vengadores174,carnage}.json`, 7 pages each).
On it, bubble F1 v3 0.968 → v4 0.971 (teacher 0.967; precision 0.964 →
0.965, recall 0.971 → 0.977), 29 ms per page on the laptop CPU. Per series
v4 gains Titanes 0.93 → 0.96, Ben Reilly #03 0.96 → 0.98, Vengadores tomo
0.97 → 0.99, Rapaces 0.99 → 1.00, loses Escuadrón and Ruinas 0.99 → 0.97;
Defensores 0.98, Doom 0.97, Carnage 0.92. Blacksad reads 0.88 for v3, v4
and the teacher alike with every balloon found on all seven pages — the
subagent-drawn ground-truth boxes are offset on a few tall balloons and
miss the IoU 0.3 match, a ground-truth artefact rather than a detector
miss. Silhouette IoU 0.879 → 0.881 (76 → 78 of 115 ≥ 0.9, 0 fallbacks),
98-page corpus uncovered area 0.2269 → 0.2212. Doom #7/#9 uncovered
0.0036 → 0.0072 because v4 now boxes six borderless character-introduction
captions (white lettering straight over art, so they fall back to the box)
and two false positives on a "LIVE STREAM" screen graphic. The Spiderman
2099 Vol1 04 p.7 split caption ("POR CULPA DE ESE SPIDERMAN", whose second
box over the last line hid "A POR MÍ. DE HECHO" once enlarged) still shows
the nested box on the 2000 px offline page, but on the Fold emulator — the
analysis runs at 1000 px there — v4 yields one box and the enlarged caption
reads complete; verified on the emulator, not yet on the physical Fold.

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

The paper mask is chosen per box (`PaperTone`, 2026-08-27). `solidPaper` is a
global threshold (luminance ≥ 228 or cream) tuned for digital pages; on a
1990s scan the bubble paper sits at 224-231 with scanner grain, so half its
cells fell under the threshold, the body was patchy and every bubble on the
Spiderman 2099 ground-truth page fell back to its box. Like the ground-truth
generator, the outliner now estimates the paper of each box as the median
luminance of its brightest 40 % of cells and the median red-green /
green-blue tints of the cells within 12 of it; when that paper is scanned —
luminance in 200 until 236 — the body is the component of cells within
`max(14, (255 - paper) / 2 + 8)` of the paper luminance and within 12 / 16
of its tints (`PixelClasses.colors`, the mean colour per cell). Brighter
paper keeps the global `solidPaper` so digital pages cannot move; darker
"paper" is art (a box full of red would otherwise become its own body) and
falls through to the old path. Spiderman 2099: 17 box fallbacks → 0, mean
IoU 0.758 → 0.879, 11/17 ≥ 0.9; Defensores 0.898 → 0.902 with its one
fallback gone; One Piece, Ben Reilly, Doctor Muerte and Doom identical;
98-page corpus uncovered area 0.2300 → 0.2268, only scan pages
changed. The two 2099 outlines still under 0.75 leak into pale art beside
the rim — the idea 3b family, not a paper-tone problem.

A paper body that covers that quarter is then **grown towards the short sides
of its box**. Doctor Doom's bordered square captions (2026-08-27) are filled
with a white-to-light-grey gradient whose bottom rows fall under the paper
threshold, so the paper body stopped at the last text line, the flat repaint
covered the whole silhouette and the copy showed the line half painted over.
The ML box is the second opinion: when the body's margin to the box on one
side exceeds twice the smallest margin on the other sides plus two cells
(`SHORT_SIDE_MARGIN_FACTOR`, `SHORT_SIDE_MARGIN_SLACK`; Doom captions sit
2-6 cells from the box on three sides and 11-37 on the fourth), the body is
flooded through `solidPale` cells (every source pixel with luminance ≥ 200
and chroma ≤ 22) lying beyond the body on that side. If the flood reaches the
box edge it is discarded — the band was background, not shading. "Beyond
the body" is judged per column and per row against the body's own cells,
not against its bounding box: on the Z Fold (analysis at 1000 px, bilinear
downscale) five Doom #9 captions on pp. 5-8 still lost their last line
(2026-08-27) because the paper body ended halfway down the last text row
with one deeper spike, so the pale gaps between the letters of that row
lay above the bounding-box bottom and the flood never reached the band.
Reproduced on the JVM only with the device's own analysed bitmaps
(`BubbleOutlineDump` androidTest → `DeviceOutlineRepro`, see below). Rejected
variants: growing through pale cells anywhere inside the box (light scanned
sky and halftone next to a bubble are pale and happen to be enclosed by dark
art at the box edges; Defensores 256, One Piece 28), gating by "the flood
through non-dark cells stays enclosed" (a box whose four edges land on dark
art encloses anything; Ben Reilly #01 p.2), and growing bodies that would
otherwise fall back to the box (patchy scanned paper in Spiderman 2099 grew
into shapes with notches cutting through text). Result: uncovered area on
nine Doom #7/#9 pages 0.0128 → 0.0036 with all 22 captions reaching their
bottom border, Ben Reilly #01 unchanged (no silhouette differs), 98-page
corpus 0.2157 → 0.2152 with `boxes.json` regenerated by student v3.

## Silhouette ground truth

Box F1 says whether a bubble was found, the uncovered-area metric how much of
the original a copy leaves visible; neither sees a silhouette that stops a
text line short or falls back to the box rectangle (the Doom last-line bug
was invisible to both). `.claude/ml-spike-kit/gt/silhouettes.json` holds 115
hand-validated bubble outlines on seven pages of six series — Ben Reilly #01
(digital Marvel, touching pairs, tails), One Piece (manga, bursts, bubbles on
white gutters), Defensores (1970s halftone), Spiderman 2099 (1990s scan),
Doctor Muerte (double-bordered captions) and Doctor Doom #9 (bordered
gradient captions, 33 outlines). Each entry stores the page file, a series
name, the ML box (`boxes.json` coordinates, normalised to the uncropped
page) and the polygon in the same frame. Four boxes were excluded as
ambiguous: borderless lettering over art, a non-bubble detection, a burst
open to a white background and a box covering two lines of a larger bubble.

The polygons were produced by `.claude/ml-spike-kit/silhouettes.py` at full
resolution — an independent pipeline from the app's outliner (paper colour
estimated per box, dilated ink as the barrier, text bays closed through
paper and ink pixels only, geodesic growth through pale shading and thin
tails inside the box, dilation by the rim thickness) — then reviewed on
contact sheets tile by tile, with hand overrides where it failed (convex
hull for bubbles whose bold lettering spans the whole width, green-border
rectangles for two Doom captions with notched corners, clip rectangles where
touching bubbles or white gutters leaked in, one hand-traced polygon). The
outlines stop at the ML box like the app's do, so a tail the box cuts off is
not held against the outliner.

`SpeechBubbleVisualizer` scores every ground-truth entry whose page is in
the folder: the bubble is matched by box IoU, both shapes are rasterised at
analysis resolution and the IoU goes into `metrics.json` per bubble, and
`out/silhouettes.json` sums up per series and overall — count, unmatched,
mean IoU, `good` (IoU ≥ 0.9) and `boxFallbacks` (outline filling ≥ 97 % of
its box). On `main` after the per-box paper tone (2026-08-27; the baseline
before it was mean 0.861, 64 ≥ 0.9, 18 fallbacks, 17 of them Spiderman 2099
at 0.758):

| Series | Outlines | Mean IoU | IoU ≥ 0.9 | Box fallbacks |
|---|---|---|---|---|
| One Piece (manga) | 11 | 0.914 | 10 | 0 |
| Defensores (1970s halftone) | 17 | 0.902 | 11 | 0 |
| Ben Reilly #01 (digital) | 24 | 0.881 | 15 | 0 |
| Spiderman 2099 (1990s scan) | 17 | 0.879 | 11 | 0 |
| Doctor Doom #9 (digital, captions) | 33 | 0.876 | 19 | 0 |
| Doctor Muerte (1990s) | 13 | 0.828 | 10 | 0 |
| All | 115 | 0.879 | 76 | 0 |

The worst cases are now one family — the
paper body leaks into pale art beside the rim and the outline runs to the
box (Doctor Muerte "FUE..." 0.36 and "¿QUÉ ES ESO?" 0.40 over grey walls,
Ben Reilly "HOY PASASTE" 0.46, Doom 009-004 "PERO AUN ASÍ" 0.43, "¿SABÍAS"
0.72, "CORRECTO" 0.73) — plus one Doom 009-005 caption whose body still
stops inside its last line (0.66) and Muerte's double-bordered captions,
outlined at the inner rule instead of the outer one (0.71). Two visualizer bugs surfaced while baselining: `boxes.json`
escapes non-ASCII file names (`D\u00eda`), which silently sent the
Defensores page through the heuristic detector, and the polygon parser
dropped the last vertex of every outline; both are fixed.

The scanned series are where the outliner falls back to the rectangle; the
ideas in `IMPROVEMENT_IDEAS.md` are now measurable against this file. When
adding outlines, run `silhouettes.py candidates`, review the sheets, extend
the override tables rather than editing polygons by hand, and keep the
excluded boxes excluded.

## Detecting bubbles without the model (`SpeechBubbles.detect`)

Still used by `SpeechBubbleVisualizer` when no box file is given and kept as
the reference pixel pipeline; the app no longer calls it.

Built on `PixelClasses` (see `guided-view.md`), plus five extra classes:

- `solidPaper`: every source pixel in the cell is white **or cream** (the pale
  yellow of Marvel-style caption boxes) — bubble and caption interiors.
- `solidPale`: every source pixel has luminance ≥ 200 and chroma ≤ 22 — the
  shaded bottom of a gradient-filled caption; only used to grow ML-boxed
  bodies towards a short box side (see above).
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
   than the originals already did. Each bubble's outlines are resampled to 48
   points spread along their total perimeter (per bubble, not per outline: a
   leaked fragment of the neighbour must not get 48 points of its own); a
   point of one copy intrudes when it lands inside the other copy's silhouette
   shrunk to its text body (`TEXT_BODY_SHARE` = 90 %, so rims may touch or
   overlap by a few pixels, as stacked balloons do in the art), and the
   intrusion is the sum of each such point's distance to the host outline,
   relative to the host copy's size. That sum is compared with the same sum on
   the originals: ML boxes of neighbours overlap routinely (tails, a caption
   over a balloon, a tail drawn across the next balloon) and reproducing that
   overlap is not a collision, while pushing a tail deeper into the neighbour
   is. Counting intruding points instead of summing depths missed exactly
   that case (Ben Reilly #01 p.17, "PERO SI PUEDO" over "BUENO"). Box-based
   collision was tried first and failed both ways — it either forbade
   placements the art itself uses or, with a box-overlap allowance, let one
   copy cover the neighbour's text.
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
bubbles): 0.2005 → 0.0265 page-areas summed, no bubble uncovering more than
0.2 % of the page (26 before); the 98-page corpus (873 bubbles): 0.840 →
0.224, 1 bubble stuck at 1× (32 before) and 163 below the full scale (138
before, now all ≥ 1.15× unless the coverage steps ran out). Layout costs
≈50 ms per page on the JVM, 0.8 s worst case on a 19-bubble Defensores page,
and runs on `Dispatchers.Default` (`ZoomablePage`) — it used to run on the
main thread.
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
`BubbleOutlineDump` (androidTest) writes the device's bubble boxes and
outlines (`files/mlspike/outlines.json`) and the analysed 1000 px bitmap of
every page (`<name>-analysed.png`); `DeviceOutlineRepro` (unit test,
`REPRO_PAGES` = folder with those PNGs, `REPRO_DUMP` = that JSON) reruns the
outliner on exactly those pixels, which is the only way a device-only
silhouette defect has reproduced offline. Copy pages in with
`cat page.jpg | run-as com.comicify.debug sh -c 'cat > files/mlspike/page.jpg'`
and run the test with `am instrument` — `connectedDebugAndroidTest`
uninstalls the app afterwards.

The ground truth is the device, not the source JPEGs: Android decodes and
subsamples differently, marginal ink shares shift, and the reader shows
spreads unsplit. Validate on the emulator by comparing a toggle-off and a
toggle-on screenshot of every page; when a page misbehaves, reproduce it
offline on a PNG of the page as the reader decoded it so the visualizer sees
the same pixels, and instrument `container` (box, fill, reach, what the
growth stopped on) before touching a threshold — the visible symptom rarely
names the real cause.
