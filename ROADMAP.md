# Roadmap

Comicify — a foldable-first comic reader. Built incrementally: every phase ends
with something that already feels good to use. The differentiator is treating the
Z Fold as a first-class reading device, not an afterthought.

Legend: `[ ]` todo · `[~]` in progress · `[x]` done

---

## Phase 0 — Project skeleton

Goal: an app that launches, with the architecture and tooling in place.

- [x] Gradle project, Kotlin + Compose, Material 3, min SDK 30
- [x] Hilt, Coil, Room, DataStore, `androidx.window` wired up
- [x] Base theme (pure-black OLED dark theme, dynamic color off by default)
- [x] Bilingual resources scaffolding (`values/`, `values-es/`)
- [x] Single-activity + Compose navigation shell

## Phase 1 — MVP: read one CBZ

Goal: open a `.cbz` and read it full-page with buttery zoom on the unfolded
screen. This is the solid base everything else builds on.

- [x] CBZ parsing: list and decode page images in order (`docs/file-formats.md`)
- [x] Reader screen: horizontal pager, one full page per screen
- [x] Pinch-zoom + pan, double-tap to zoom
- [x] Page preloading (next N pages) for zero-lag turns
- [~] Tap zones (center toggles chrome; left/right page-turn zones pending)
- [x] Immersive mode: edge-to-edge, hidden system bars

## Phase 2 — Foldable magic

Goal: the reading experience adapts to device posture, and reading position
survives fold/unfold. This is the heart of the product. See `docs/foldable.md`.

- [x] Observe `WindowLayoutInfo` posture + window size class
- [x] Unfolded portrait → full single page at near-physical size
- [x] Unfolded landscape → two-page spread (open-book layout)
- [~] Tabletop posture → page on top half, controls on bottom half (implemented;
      pending runtime verification on a physical foldable)
- [x] Cover screen (folded) → single page, tighter controls
- [~] Posture transition preserving reading position (page-level done; panel-level with Guided View pending)
- [x] Ambient backdrop: radial glow from each page's dominant color (Palette),
      animated crossfade on page turn, replacing the flat-black letterbox

## Phase 3 — Library

Goal: a beautiful home for a collection, not just a file opener.
See `docs/library.md`.

- [x] Pick a comics folder (SAF `OPEN_DOCUMENT_TREE`, persistable permission,
      folder Uri stored in DataStore) and scan it recursively for `.cbz/.cbr/.pdf`
- [x] Cover grid (adaptive columns) with reading progress and "continue reading"
- [x] Room-backed library + per-comic reading position persistence
- [x] Filename parsing for series / issue number / year (tolerant, unit-tested)
- [x] Lazy per-comic cover thumbnails cached to internal storage
- [ ] Optional metadata enrichment (ComicVine API) — deferred if noisy

## Phase 4 — Guided View (panel by panel)

Goal: auto-focus each panel with smooth pan/zoom transitions, so text is large
and readable in any posture (the fix for small text on the two-page spread).
Toggle in the reader HUD. See `docs/guided-view.md`.

- [x] Automatic panel detection: adaptive gutter/border colours, max-pooled
      hairline gutters, art bodies merged by straight-line separability (grid,
      tilted, layered and inset layouts), bleed-over-keyline bodies split back
      apart (bordered panel over a full-page splash), speech bubbles/captions
      attached to the panels they overhang, horizontal reading-band fallback
      that keeps true splashes as one stop
- [x] Reading-order sorting of detected panels (mutual-centre rows, LTR,
      containers before their insets)
- [x] Guided navigation: tap zones advance panel with animated pan/zoom, a
      whole-page overview stop on entering each multi-panel page, and a HUD
      stops indicator with an end-of-page cue
- [x] Spread posture: guided within the two-page spread (active half focuses,
      other page as context) or full screen, toggle in the HUD
- [x] Robust base: double-tap zoom around the tapped point + one-finger pan in
      Guided View and on regular pages
- [x] Per-page cache of detected panels (lazy, only when Guided View is used)
- [x] Offline detection visualizer + unit tests on synthetic pages
- [ ] Manual panel-region override/editor for pages detected poorly

## Phase 5 — Polish

Goal: the details that make it feel premium.

- [~] More formats: format is detected by content (magic bytes), not extension.
      ZIP (CBZ, and `.cbr` files that are really ZIP) and RAR4/RAR5 (via
      7-Zip-JBinding) all work. PDF (`PdfRenderer`) still pending.
- [x] Night tint (amber) toggle
- [ ] Reading stats / recently read
- [ ] Gesture and transition tuning pass
- [ ] Home-screen thumbnails, per-comic settings

---

## Non-goals

- No online store, DRM, or downloading of copyrighted content. Comicify reads
  files the user already owns and supplies.
- No iOS / cross-platform. Native Android only, to fully exploit foldable APIs.
