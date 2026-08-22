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

`PanelDetector.bubbles` only reads pixels (same pooling as panel detection:
the page is pooled so its **shorter** side is ~1000 cells, so a double-page
spread keeps the same cell size as a single page and its small bubbles survive);
`SpeechBubbles` and `BubbleLayout` are pure Kotlin in `feature/reader/domain`,
unit-tested on synthetic pages. Detection is ~50–200 ms per page off the main
thread and cached per page in `PageLoader` (only when the toggle is used).

## Detecting bubbles (`SpeechBubbles`)

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
   4.5 % of the page tall, at least 0.4 % wide, and at least half of it is
   `ink`. The ink test is what separates lettering from sky showing through
   clouds or moon texture.
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
8. **Outline.** The blob is inflated by 2 cells so the drawn outline stroke is
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
`bubble_scale`) around its own centre, then resolves conflicts with the policy **move into gaps
first, shrink only when moving is not enough** — copies never end up
overlapping each other:

1. Every box is grown to the full scale, clamped inside the page, and
   **pushed apart** (16 passes, along the axis with the smaller overlap, half
   the overlap each). Pushing is staged by how much of the original a copy must
   still cover (`COVERAGE_STEPS` = 1.0, 0.7, 0.4 per axis): copies first
   separate without uncovering their original at all, and only the pairs that
   still collide are allowed to slide further off it — so most bubbles stay
   put and only crowded ones move into neighbouring gaps.
2. Boxes that still overlap after pushing form a **cluster**. The whole cluster
   is scaled by one **uniform** factor — the tightest pairwise separating scale
   in the cluster — so a caption sandwiched between two others grows the same
   as its neighbours instead of being pinned to 1× by the pairwise minimum,
   floored at `MIN_ENLARGE_SCALE` (1.15×, capped at the requested scale).
3. Members are re-grown at their assigned scale and pushed apart again with the
   same staging.
4. **Residual overlaps** are then resolved pair by pair: each still-colliding
   pair is shrunk about its current centres by the factor that makes them just
   touch (never below 1×), followed by a few more push passes, until no copy
   overlaps another (`BubbleLayoutTest.crowdedClusterEndsWithoutOverlappingCopies`).
   Anything a copy uncovers by sliding is repainted at render time (below).

## Rendering

`ZoomablePage` shows a small spinner in the page corner while its bubbles are
being detected (at most two pages detect concurrently — `PageLoader` gates
detection with a semaphore so the visible page is not slowed by its
neighbours), draws the page as before and, in a `drawWithContent` after the
zoom/pan `graphicsLayer` (so the overlay pans and zooms with the page), draws
in two passes. First every enlarged bubble's **original footprint** (its
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

The ground truth is the device, not the source JPEGs: Android decodes and
subsamples differently, marginal ink shares shift, and the reader shows
spreads unsplit. Validate on the emulator by comparing a toggle-off and a
toggle-on screenshot of every page; when a page misbehaves, reproduce it
offline on a PNG of the page as the reader decoded it so the visualizer sees
the same pixels, and instrument `container` (box, fill, reach, what the
growth stopped on) before touching a threshold — the visible symptom rarely
names the real cause.
