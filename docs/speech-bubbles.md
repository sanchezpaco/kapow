# Enlarged speech bubbles

A readability toggle for small lettering that keeps the page at its normal zoom:
each detected speech bubble or caption is drawn again, scaled up in place, over
the page. Toggle: the ChatBubble button in the HUD (`ReaderUiState.bubblesEnlarged`).
Nothing moves and nothing has to be tapped through — it is a render enhancement,
not a navigation mode.

## Pipeline

```
page bitmap ──▶ PanelDetector.bubbles (data) ──▶ SpeechBubbles (domain, pure) ──▶ List<SpeechBubble> ──▶ cache
                                                                                             │
                     BubbleLayout.enlarge (domain, pure) ──▶ List<EnlargedBubble> ──▶ ZoomablePage overlay ◀─┘
```

`PanelDetector.bubbles` only reads pixels (same pooling as panel detection);
`SpeechBubbles` and `BubbleLayout` are pure Kotlin in `feature/reader/domain`,
unit-tested on synthetic pages. Detection is ~50–200 ms per page off the main
thread and cached per page in `PageLoader` (only when the toggle is used).

## Detecting bubbles (`SpeechBubbles`)

Built on `PixelClasses` (see `guided-view.md`), plus three extra classes:

- `solidPaper`: every source pixel in the cell is white **or cream** (the pale
  yellow of Marvel-style caption boxes) — bubble and caption interiors.
- `solidDark`: every source pixel in the cell is dark and near-neutral (low
  chroma) — the body of a black/negative bubble.
- `ink`: some source pixel in the cell is dark — lettering strokes.

Detection is **text-first**: it looks for lettering, then grows the enclosing
tone around it into the bubble. Three tones are processed independently and
merged at the end: **white** (dark ink on white paper), **cream** (dark ink on
the pale yellow of caption boxes, so a cream caption never grows into a white
moon behind it), and **negative** (light ink on `solidDark` — white lettering
on a solid-black bubble, the mirror of the white pass: the `ink` role is played
by `white` cells and the paper role by `solidDark`). The negative tone is first
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
   4.5 % of the page tall, at least 0.4 % wide, and at least half of it is
   `ink`. The ink test is what separates lettering from sky showing through
   clouds or moon texture.
3. **Text blocks.** Words within a word gap horizontally (0.8 % of the page)
   and a line gap vertically (1.2 %) are clustered (union-find); a block must
   be dense (words cover ≥ 25 % of its box) and contain at least one
   line-shaped word (a short horizontal band, or a wide short block for two or
   three lines merged by descenders, wider than tall) — a "que/trabajo" pair
   glued by a descender still bridges the block, only real lines prove text.
4. **Grow the bubble.** From the block, paper is flooded outwards ring by ring
   (Chebyshev distance to the block box), only within a rounded reach around the
   block, stopping as soon as a ring adds less than 1 % of what was reached
   (creeping along a thin keyline stops; the bubble body is filled row by row
   even for a tight caption). The reach is **scale-invariant**: it grows with
   the block (0.8 × its shorter side), floored at 2.4 % of the page and capped
   at 8 %, so a large manga balloon whose text sits far from its outline is
   still filled to that outline, while small captions keep the tight Marvel
   reach. This is what lets bubbles that overhang tilted keylines, bubbles
   fused with a white background, or two overlapping bubbles each be found from
   their own text.
5. **Shared paper.** Blobs whose cells overlap (two text blocks inside one
   drawn shape, e.g. a bubble chained to the next with no separator between
   them) are unioned before filtering, so the copy carries both texts instead
   of one covering the other.
6. **Bubble filters.** The grown blob must not touch the page border, have a
   compact fill (≥ 35 % of its box), be between 0.05 % and 12 % of the page,
   not thinner than 1.2 % of the page, aspect ≤ 6, have 3–50 % ink inside with
   ≥ 75 % of it text-like (the text-like line/block heights are the larger of
   the page fraction and a multiple of the blob's own median glyph height, so
   the large lettering of manga counts as text just like dense Marvel type),
   and be **ink-bounded**: ≥ 60 % of its outer contour
   must end on non-paper or on thin paper (an outline, art, a keyline) rather
   than on thick open paper at the reach limit — text sitting on an open white
   background is not a bubble.
7. **Silhouette.** For every row of the blob the leftmost/rightmost cell,
   inflated by 2 cells so the drawn outline stroke is included, gives a
   row-span polygon (`SpeechBubble.outline`, normalized).
8. **Chained / overlapping bubbles** of the same tone whose silhouettes touch,
   or of any tone whose boxes overlap by ≥ 15 %, are merged into one unit, so
   they grow together instead of covering each other.

Known limits: lettering-only SFX are not enlarged (their lettering is not
enclosed by any tone); a bubble fused with a large white area (a moon) is
found, but its copy carries a bit of that background up to the reach limit, so
a faint seam can show there. The negative pass trades a small false-positive
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
`bubble_scale`) around its own centre, then resolves conflicts without ever uncovering the
original bubble (the enlarged copy must always contain the original box, so the
small original never peeks out):

1. Every box is grown to the full scale, clamped inside the page, and
   **pushed apart** (up to 8 passes, along the axis with the smaller overlap,
   half the overlap each), but only as far as each copy still covers its
   original — isolated neighbours separate at full scale and keep it.
2. Boxes that still overlap after pushing form a **cluster**. The whole cluster
   is scaled by one **uniform** factor — the tightest pairwise separating scale
   in the cluster — so a caption sandwiched between two others grows the same
   as its neighbours instead of being pinned to 1× by the pairwise minimum.
3. That uniform factor is floored at `MIN_ENLARGE_SCALE` (1.15×, capped at the
   requested scale) so every bubble grows visibly; in a genuinely tight cluster
   the copies may then overlap a little, but each still covers its own original.
4. Members are re-grown at their assigned scale and pushed apart once more.

## Rendering

`ZoomablePage` shows a small spinner in the page corner while its bubbles are
being detected (at most two pages detect concurrently — `PageLoader` gates
detection with a semaphore so the visible page is not slowed by its
neighbours), draws the page as before and, in a `drawWithContent` after the
zoom/pan `graphicsLayer` (so the overlay pans and zooms with the page), for each
enlarged bubble clips to the scaled outline polygon and draws the source bitmap
region of the bubble box into its target box with high filter quality, plus a
faint 1 dp outline so the copy reads as intentional. Bubbles at scale 1 (fully
constrained) are skipped.

## Iterating on detection

`SpeechBubbleVisualizer` (unit test, skipped by default) renders both the
detected outlines (`<name>-bubbles.png`) and the actual enlarged result
(`<name>-enlarged.png`) for a folder of page JPEGs:

```
COMICIFY_PANEL_VIZ_DIR=/path/to/pages ./gradlew :app:testDebugUnitTest \
    --tests '*SpeechBubbleVisualizer*' --rerun -i
```
