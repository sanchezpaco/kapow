# Possible ideas

Product-level ideas from the app review of 2026-08-28, not scheduled in
`ROADMAP.md`. Technical polish items live in `improvement-ideas.md`.

Where the app stood at review time: the reader (Guided View, enlarged
bubbles, foldable postures, jank-free page turns) is the strong part and has
no equivalent in other readers. The library is a plain file grid with no
hierarchy, there is no comic/series detail screen, no per-comic settings, no
stats, and the reader HUD is functional but generic.

## Rejected

### Cinematic Flow (vertical panel stream) — rejected 2026-08-28

Cut every detected panel (plus the bubbles overhanging it) out of the page
and stack the strips in one continuous vertical scroll, webtoon-style, with
enlarged bubbles drawn inside each strip. A PoC was built on a worktree with
the persisted panel/bubble detections only (no new ML), a 1.7× magnification
cap and a HUD toggle, and tried on the emulator with Venomverse #1: crops,
page crossing, splash fallback and bubbles all worked.

Rejected after seeing it: the format throws away the page composition
(overlapping panels, insets, layout rhythm) and the result did not feel like
reading a comic. Do not re-propose. If the idea ever comes back, the known
gaps were: bubble copies pushed outside their panel by the page-level layout
get clipped at strip edges (layout would have to be per strip), no merging of
over-segmented tiny panels, and Flow ↔ page position mapping only at page
granularity.

## Scheduled for 1.1

### ~~Continuous vertical scroll ("infinite strip")~~ — **Shipped 2026-09-04** (`a635510`, `1df066c`; see `docs/reading-modes.md`). It went further than the spec below: the strip does not stop at the end of an issue, it chains into the next one in the series across a named boundary band.

Stack the **whole pages** vertically in one continuous scroll, fit to width,
with no page-turn animation — scroll instead of paginate. Proposed 2026-09-04.

**This is not Cinematic Flow, and the rejection above does not apply to it.**
Flow changed *what you see*: it cropped the page into per-panel strips and
magnified them, so it depended entirely on the panel detector and threw the page
composition away. This changes only *how you move*: same pages, same pixels, no
crop, no magnification. **Nothing in its path can misdetect anything**, which is
its main argument — Guided View's remaining failures are all detector-shaped, and
this mode has no detector.

Why it is worth building: continuous vertical scroll is the native reading mode
for manga and webtoons, and the library already holds One Piece, Attack on Titan,
Shangri-La and Kingdom Hearts. Every serious reader (Mihon, Perfect Viewer,
Panels) has it; it is one of the few absences a manga reader would notice. It is
also cheap next to everything else here — no ML, no ground truth, no new labels —
and the deleted Flow PoC already proved the skeleton (a `LazyColumn` of pages)
works; this is that skeleton without the strip cropping. It pairs naturally with
**Split wide pages**, which is already shipped.

Three risks, all engineering rather than design:

1. **HWUI texture budget.** This bit the app once already: software 24 MB pages
   thrashing a 72 MB texture cache, 78 % janky frames, fixed with hardware
   bitmaps. A continuous strip keeps **more pages resident at once** than the
   pager does. It needs a tighter recycling window, and the gate before shipping
   is the `gfxinfo` recipe over a long fast scroll on the Fold, watching **Slow
   bitmap uploads** specifically — the number that caught the original jank.
2. **Zoom versus scroll.** Vertical drag is the scroll, so one-finger pan while
   zoomed conflicts with it. For v1, **lock to fit-width** and drop vertical pan,
   which is what most webtoon readers do; it sidesteps the whole gesture
   arbitration problem and costs little.
3. **Mode precedence and state.** Guided View and the two-page spread are
   page-based and cannot coexist with a strip, so this is a third top-level mode
   that turns them off (same precedence `flow` had in `ReaderSurface`). Two things
   the Flow PoC left half-done must be finished properly here: **reading position**
   (stored per page today; needs a fraction, or at least first-visible-page) and
   the **thumbnail scrubber**, which Flow simply hid.

Scope for v1: whole pages, fit to width, a per-comic setting alongside reading
direction and split-wide-pages, zoom locked, disabled in landscape spread.

## Reading (build on the ML and the Fold)

1. ~~**Guided View "director" transitions.**~~ **Shipped 2026-09-04**
   (`71c1791`…`e3028b2`, see ROADMAP Phase 5 and `docs/guided-view.md`). The
   crossfade over the ambient colour on a page change was replaced by an opaque
   black veil — the ambient dip needs to time a swap the reader does not
   control, while the veil only has to be opaque in the same frame the page
   changes. **Auto-play is still open** and was deliberately split off: it is a
   new top-level mode (state, HUD, pause on touch) and its pace estimate leans
   on `textIn`, which the 2026-09-04 session found unreliable on scans, so the
   pacing would need another source.
2. **Tabletop with two real surfaces.** Top half: the current panel in
   Guided View. Bottom half: the whole page as a map with the current panel
   highlighted; tapping any panel jumps to it. The bottom half stops being a
   row of buttons.
3. **Smart spreads.** Detect double-page splashes by art continuity across
   the inner edge (not only aspect ratio), pick the pairing parity
   automatically, animate the book opening with the hinge as the spine
   (shadow at the gutter), dim the inactive half under the ambient glow in
   Guided View.
4. **Bubble OCR** (ML Kit text recognition on the silhouettes already
   extracted): text search that jumps to the panel, **text-to-speech in
   reading order** (audio-comic mode in tabletop), and later in-place
   translation writing the translated text back into the silhouette over the
   existing fill.

4b. **Tablets read like an open Fold.** A tablet turned to landscape should
   get the same two-page spread the Z Fold gets when unfolded in landscape.
   `WindowState.toPosture()` already picks `UnfoldedSpread` from window width
   (≥ 840 dp) plus landscape rather than from the hinge, so a 10" tablet
   should qualify today, but it has never been verified on one and an 8"
   tablet (600-839 dp) falls to `UnfoldedSingle`. Decide the spread from the
   window aspect ratio (wide enough for two portrait pages at a readable
   size) instead of a fixed width, verify on a tablet AVD in both
   orientations, and make sure the tabletop split stays hinge-only.

## Library and navigation (make the app feel finished)

5. **Library hero.** A large "continue reading" block with the cover, the
   page's ambient colour as background, progress and "20 min left" from the
   reader's real pace. Grid covers with a shadow tinted by their dominant
   colour, series grouped as cover stacks. Drop the permanent "Choose folder"
   button and the duplicated title/series line.
6. **Comic / series detail screen.** Ordered issues, next unread, per-comic
   settings (reading direction, spread parity, bubbles, guided), page mosaic,
   long-press context menu (mark read, favourite, clear detections).
7. **Shared-element transition** cover → reader with the ambient glow
   unfolding, and back with the progress updated.
8. **Cover screen as remote.** Folded: cover, progress and "resume on the
   inner screen"; on unfolding, the page expands into place.

## Reading comfort and data

9. **Paper & Light.** Surface modes (newsprint with warm white and light
   grain for old scans, pure white for digital) plus art brightness/contrast
   independent of the system, all via `ColorMatrix`.
10. **Reading stats that mean something.** Pace per series, time per page
    guided vs normal, streaks, "finished this month" as a cover mosaic.
11. **Panel bookmarks.** Long-press in Guided View saves the panel crop as a
    "moment"; gallery per comic; share as an image.
12. **`ComicInfo.xml` metadata** inside the archive (title, series, number,
    authors) before any online source; and PDF support, still pending.

## Suggested order

5 + 6 + 7 first (no ML dependency, visible on the first screen), then 1
(improves the flagship feature without touching detection).

**Updated 2026-09-04 (evening):** 5, 6 and 7 shipped earlier, **1's
transitions shipped too** (leaving only its auto-play half), and **continuous
vertical scroll shipped** the same day, chained across issues. That closes the
1.1 list above.
Idea 4 (bubble OCR) still deserves a second look: the hand-lettering family was
scoped on 2026-09-04 as needing *text* detection rather than balloon detection,
which is exactly what idea 4 proposes, and it would replace the `textIn`
extractor that the same session found unreliable on scans — and would also give
auto-play a pace estimate it can trust.
