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

## Reading (build on the ML and the Fold)

1. **Guided View "director" transitions.** Use the panel geometry: slide
   sideways when the next panel is to the right, zoom-in with ease-out when
   it is smaller, reveal from black on splashes, crossfade over the ambient
   colour on page change. Plus **auto-play** whose pace is estimated from the
   amount of text ink inside the page's bubbles — hands-free reading in
   tabletop posture.
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
