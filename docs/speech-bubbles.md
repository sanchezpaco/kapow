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

Built on `PixelClasses` (see `guided-view.md`), plus one extra class:

- `solidPaper`: every source pixel in the cell is white **or cream** (the pale
  yellow of Marvel-style caption boxes) — bubble and caption interiors.

Steps:

1. **Strip gutters and keylines.** Long thin runs of paper (≥ 30 % of the page
   long, ≤ 1.2 % thick) are removed, then the mask is opened with radius 1, so a
   bubble cut by a panel edge no longer merges with the white gutter or the
   panel's white keyline (which would make it touch the page border or swallow
   the whole gutter).
2. **Candidates.** 4-connected `solidPaper` components that do not touch the
   page border, with a compact fill (≥ 35 % of their box — bubbles with tails
   are around 0.4), between 0.05 % and 12 % of the page, not thinner than 1.2 %
   of the page and with aspect ≤ 6.
3. **Text gate (precision).** Interior *holes* of the component (non-paper cells
   inside its box that cannot be reached from the box edge) are the ink inside
   the bubble. A bubble must have 3–50 % ink, and ≥ 75 % of that ink must be in
   *text-like* holes: at pooled resolution a text line is a horizontal band
   (≤ 1.8 % of the page tall), and two or three lines merged by descenders are a
   wide, short block (≤ 4.5 % tall, wider than 1.2× their height). Blank white
   walls (no ink), white regions around a dark figure (one tall compact hole) and
   big SFX lettering are rejected — false positives look bad because the overlay
   covers visible art.
4. **Silhouette.** For every row of the component the leftmost/rightmost cell,
   inflated by 2 cells so the drawn outline stroke is included, gives a
   row-span polygon (`SpeechBubble.outline`, normalized). Tails are kept — the
   enlarged tail still points at the speaker.
5. **Chained bubbles** whose silhouettes touch are merged into one unit, so they
   grow together instead of covering each other.

Known limits: black/negative bubbles and lettering-only SFX are not enlarged;
a bubble whose white connects to a bright white area (a moon, a lab coat) can
grow a stair-step protrusion; a bubble that fails the text gate can be partly
covered by an enlarged neighbour.

## Laying out the enlargement (`BubbleLayout`)

`enlarge(bubbles, scale)` scales every bubble by `BUBBLE_ENLARGE_SCALE` (1.3×)
around its own centre, then resolves conflicts without ever uncovering the
original bubble (the enlarged copy must always contain the original box, so the
small original never peeks out):

1. Enlarged boxes are clamped inside the page.
2. Overlapping neighbours are **pushed apart** (up to 8 passes, along the axis
   with the smaller overlap, half the overlap each), but only as far as the
   copy still covers its original — stacked captions and dialogue pairs both
   grow instead of both shrinking.
3. Pairs that still overlap **shrink** together to the scale at which they just
   separate (never below 1×), re-anchored to keep covering the original.

## Rendering

`ZoomablePage` draws the page as before and, in a `drawWithContent` after the
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
