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
- [x] Enlarged speech bubbles: an alternative to panel-by-panel — every
      detected bubble/caption (white or cream, gated on text-like ink inside) is
      redrawn ~1.3× in place over the normal page, pushed apart or shrunk so
      copies never overlap or uncover the original; HUD toggle. See
      `docs/speech-bubbles.md`
- [x] Panel detection on bleed-heavy art: black keylines over a full-bleed panel
      now split the page (`PanelFrames` ink-line search). Venomverse: 12 → 8
      full-page fallbacks, p2 1 → 5, p14 1 → 4; Ben Reilly unchanged or better
- [x] Heuristic detection frozen (2026-08-23): remaining failures (Venomverse
      p21/p24/p26/p27 gutters crossed by art, manga/painted bubbles) are not
      separable by pixel rules — see the ML spike below. No more threshold work.

## Phase 5 — Polish

Goal: the details that make it feel premium.

- [~] More formats: format is detected by content (magic bytes), not extension.
      ZIP (CBZ, and `.cbr` files that are really ZIP) and RAR4/RAR5 (via
      7-Zip-JBinding) all work. PDF (`PdfRenderer`) still pending.
- [x] Night tint (amber) toggle
- [x] ML panel detection: YOLO26n (manga-frame fine-tune) via ONNX Runtime is
      the default `PanelDetector` path, heuristic kept as fallback. 77-page
      ground truth: F1 0.93 vs 0.77, 54/77 exact pages vs 29, ~100 ms vs
      ~540 ms on the emulator; +27 MB APK. See `docs/guided-view.md`
- [x] ML speech bubbles: YOLO26n distilled from `ogkalu`'s YOLOv8m on the
      own corpus (9 MB, ~35 ms/page) gives the boxes, the pixel heuristic
      extracts the outline inside each box. Bubble F1 0.96 vs 0.78 on the
      98-page ground truth. See `docs/speech-bubbles.md`
- [x] Re-distil the bubble student when the corpus grows (done once: 1970s
      halftone, 1990s scans and modern digital Marvel added, 0.94 → 0.96);
      measure on the held-out ground truth, not only on pseudo-label val mAP
- [x] Bubble student beyond the teacher: hand-corrected 7 missed 1970s
      narration captions across 120 reviewed Defensores training pages,
      re-fine-tuned; Defensores F1 0.88 → 0.90–0.91, now at/above the
      teacher (0.90). A YOLO26s fine-tune on the same data reached 0.97
      overall but left Defensores unchanged at 4× the model size and
      ~2.75× the inference time — rejected, kept YOLO26n
- [x] Bubble render cleanup: on-device screenshots after the v3 install showed
      the same balloon enlarged twice, side by side, near-identical text.
      Root-caused on-device (`Muerte by ALHma[CRG]` p.20, Marvel Saga 14
      "Swing Shift" p.14): two ML boxes for one physical bubble survived the
      exported NMS at IoU 0.99, and `BubbleLayout.enlarge` pushed them apart
      as if they were a real colliding pair instead of recognising the
      duplicate. Fixed by merging boxes with IoU ≥ 0.5 in
      `OnnxBoxDetector.detect()` before they reach outlining/layout, for both
      the panel and bubble models. Verified fixed on both pages on the Fold.
      See `docs/speech-bubbles.md`
- [x] Bubble enlarge: minimise the vacated area. When a copy is pushed away
      from its original, the whole original silhouette is repainted flat and
      the part the copy no longer covers reads as a white blob the size of the
      displacement (Ben Reilly #01 p.17: "DOCTOR LIU" up-left, "¿Y QUÉ?"
      down-right → one giant white shape). Make the layout leave the smallest
      possible uncovered area first — anchoring, coverage steps, shrink-vs-move
      trade-off — before deciding how to fill what remains. On
      `feature/bubble-inpainting-polish`, prioritised ahead of reading stats
      (2026-08-26). Done on `feature/bubble-minimise-vacated-area`
      (2026-08-26): silhouette-based collisions relative to the originals,
      contained-anchor pair search, shrink to 1.15× before sliding, group zoom
      removed; uncovered area −87 % on Ben Reilly #01 and −75 % on the
      98-page corpus (see `docs/speech-bubbles.md`)
- [ ] Bubble enlarge: fill the residual vacated area. LaMa spike (2026-08-26)
      proved ML inpainting blends the hole into the art (≈90 % judged good on
      138 real bubbles, six styles) but costs ≈4 s per 512² crop on the Fold
      emulator, so the shipped fill must be far cheaper (small crop / tiny
      model / classical fill for thin crescents). Decide after the item above
      shows how much area is actually left. See `docs/speech-bubbles.md`
- [ ] Reading stats / recently read
- [ ] Gesture and transition tuning pass
- [ ] Home-screen thumbnails, per-comic settings

---

## Non-goals

- No online store, DRM, or downloading of copyrighted content. Comicify reads
  files the user already owns and supplies.
- No iOS / cross-platform. Native Android only, to fully exploit foldable APIs.
