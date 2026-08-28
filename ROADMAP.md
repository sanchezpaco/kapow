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
- [x] Doom's green-bordered square captions lose their last text line when
      enlarged (2026-08-27, Z Fold, black-hole arc). Reproduced offline on
      Doom #9: the ML box covers the whole caption, but the caption's
      white-to-grey gradient drops under the paper threshold, so the
      silhouette stopped at the last line and the repaint ate it. Fixed in
      outlining, not in the model: the paper body grows through pale cells
      towards the short side of its box (see `docs/speech-bubbles.md`).
      Second round on the Z Fold (pp. 5-8 still cut): the growth now judges
      "beyond the body" per column/row, not by bounding box; reproduced
      only with the device's own analysed bitmaps (`BubbleOutlineDump` →
      `DeviceOutlineRepro`)
- [x] Silhouette ground truth (2026-08-27): 115 validated bubble outlines on
      six series in `.claude/ml-spike-kit/gt/silhouettes.json`, scored by
      `SpeechBubbleVisualizer` (`out/silhouettes.json`: mean IoU, share
      ≥ 0.9, box fallbacks per series). Baseline: mean IoU 0.861, 64/115
      ≥ 0.9, 18 box fallbacks — 17 of them the whole Spiderman 2099 page.
      Prerequisite for the outlining work in `IMPROVEMENT_IDEAS.md`; see
      `docs/speech-bubbles.md`
- [x] Per-bubble paper colour for scans (2026-08-27, idea 2): the paper of
      each ML box is estimated from its own interior and scanned paper
      (luminance 200-235) is classified relative to it, digital pages keep
      the global threshold. Spiderman 2099 box fallbacks 17 → 0, mean IoU
      0.758 → 0.879; digital series unchanged. See `docs/speech-bubbles.md`
- [x] Bubble student v4 (2026-08-28): retrained on every comic in `comics/`
      (121 files, 4,723 training pages of 31 series, 73 issues in 12 series
      never seen by v3, incl. the first European albums Blacksad and Rapaces
      and the painted Marvels: Ruinas), teacher pseudo-labels plus 57
      subagent-reviewed corrections on the new styles. Box ground truth
      98 → 147 pages (21 series). F1 0.968 → 0.971 (teacher 0.967), new
      series 0.92-1.00 except Blacksad 0.88 (ground-truth geometry, all
      three models equal), silhouette IoU 0.879 → 0.881, corpus uncovered
      0.2269 → 0.2212, 29 ms/page. See `docs/speech-bubbles.md`
- [x] Bubble enlarge: fill the residual vacated area (2026-08-28). LaMa
      spike (2026-08-26) proved ML inpainting blends the hole into the art
      (≈90 % judged good on 138 real bubbles) but costs ≈4 s per 512² crop.
      Shipped a classical fill instead (`CrescentFill`): the whole original
      silhouette plus margin is onion-peeled from the surrounding art (minus
      neighbouring bubbles and the rim halo) and box-blurred; layout + fills
      0–37 ms per page on the Fold in release (mean 11 ms), cached with the
      overlay. On flat backgrounds the hole
      disappears; on busy scans it becomes a soft smudge instead of a cut-out
      (≈3/5 vs 1.6/5 flat). Only 77 of 876 corpus bubbles uncover > 0.05 % of
      the page. A tiny model stays the escalation path. See
      `docs/speech-bubbles.md`
- [x] Page-turn lag on the Z Fold (2026-08-28, reported on Doctor Doom #1
      pp. 18-21, bubbles off): software page bitmaps (24 MB each) thrashed
      HWUI's 72 MB texture cache, re-uploading a page per frame. Pages are
      now hardware bitmaps with a small software analysis copy for crop,
      ambient colour and ML. `gfxinfo` on the same turns: janky frames
      78 % → 1.8 %, median 73 → 5 ms, slow bitmap uploads 93 → 0. See
      `docs/file-formats.md`
- [x] Verify bubble student v4 on the physical Z Fold (2026-08-28: 2099
      Vol1 04 p.7 caption, Blacksad 1 p.13, Doom #9 pp. 5-8 all looked right)
- [x] Persist page detections (panels + bubbles) in Room (2026-08-28):
      `page_detections` keyed by document URI and page, tagged with
      `DETECTIONS_VERSION`; `PageLoader` reads the row before running a
      model and writes through after. Second open of a 5-page comic in
      bubble mode: 5 → 0 bubble runs. RAM and battery are measured after
      this, not before. See `docs/speech-bubbles.md`
- [x] Bubble enlarge latency (2026-08-28, profiled stage by stage on the
      Fold): the model was 60 ms and Room 1–20 ms; `BubbleLayout.enlarge`
      was the cost — 0.5–0.9 s for 6–9 bubbles, 3.8 s for a 13-caption Doom
      #9 page (`reanchorPair` recounting 81 × 2n collisions) — recomputed on
      every toggle, and the visible page queued ~1 s behind its neighbours'
      detections, which read as random latency. Fixed by decomposing the
      anchor search and memoising collisions (identical layouts on the
      101-page corpus, JVM 4.0 → 0.28 s), caching overlays per page and
      scale in `PageLoader` and serving the current page first. Release on
      the Fold, that Doom page: toggle → enlarged 3.9 s → 69 ms from Room,
      387 ms cold; page turns 3–5 ms from Room; off/on 0 ms. `drawBubbles`
      is not per-frame (layer-cached), so nothing to gain there. See
      `docs/speech-bubbles.md`
- [x] App-wide fluidity research (2026-08-28, release on the Fold, folded,
      60 Hz): `gfxinfo` per interaction, `PageLoader` timings (`decode` now
      logged next to the detections) and a Perfetto trace. Library scroll
      0.7 % janky, cold start 117 ms, page turns 2.1–2.6 % (p99 ≤ 13 ms),
      zoom 1.6–3.3 %, Guided View 3–3.6 %, HUD 3.9 %, no slow bitmap
      uploads; 469/474 traced frames under 8.4 ms. Baseline table, method
      and ranked findings in `docs/performance.md`; the follow-ups are the
      next five items
- [x] Bubble overlay GPU cost (2026-08-28): the overlay now lives in its own
      `Offscreen` layer under the zoom layer, so the turn animation composites
      one cached texture instead of re-running every bubble's `clipPath`.
      Doom #9 toggle p50 14 → 6 ms (GPU 13 → 5), turns with bubbles now
      cost the same as without. See `docs/speech-bubbles.md`
- [x] First-frame re-record (2026-08-28): the HUD's 13 ms frame was
      `AnimatedVisibility` recomposing the chrome on every show; it now stays
      composed and slides/fades in a `ModulateAlpha` layer (warm toggles
      ≈ 4 ms main thread), thumbnails load only while visible and decode as
      hardware bitmaps. The remaining one-slow-frame-per-gesture on page
      turns is SurfaceFlinger buffer stuffing after idle, not app work. See
      `docs/performance.md`
- [x] Open latency vs archive size (2026-08-28): RAR and PDF open on the SAF
      descriptor without the cache copy (`DescriptorInStream`), and non-solid
      RARs extract from the resumed page first. Blacksad 165 MB: first page
      509 → 361 ms; Muerte resumed at p.19: ≈ 800 → 222 ms. ZIP keeps the
      copy (`ZipFile` needs a path, `/proc/self/fd` is refused by scoped
      storage). See `docs/file-formats.md`
- [x] Cold detections (2026-08-28): Guided View preloads panels with the
      pages (`preload(panels = true)`, per-page mutex + shared detection
      slot), so each step finds the next page's panels cached instead of
      detecting them on arrival (130–210 ms). Bubble mode keeps its serial
      ≈ 250 ms per page (one detection at a time). See `docs/guided-view.md`
- [ ] Reader → library exit renders one 20–27 ms frame (library
      recomposition + cover re-bind); measure unfolded posture too
- [x] APK size (release, `apkanalyzer`, 2026-08-28): 50.4 → 20.8 MB
      (download 32.8 → 14.0 MB). Both models' Conv weights in fp16 (9.8 →
      5.2 MB each; identical boxes on the 147-page ground truth, bit-identical
      outlines on Doom #1 pp. 5-12 on the Fold; weight-only int8 rejected for
      per-series F1 losses), ONNX Runtime rebuilt as a minimal arm64 build
      with only the YOLO26 operators (`libonnxruntime.so` 17.6 → 2.7 MB,
      `tools/ort`, `docs/ml-runtime.md`), bundled sample comic re-encoded as
      JPEG (6.8 → 1.3 MB). arm64-v8a only was already in place. Detection
      times on the Fold unchanged (first page 596 vs 645 ms, then 56–347 ms),
      jank unchanged, release opens a real RAR and runs bubble mode. Left on
      the table: 7-Zip-JBinding (2.7 MB), dex (2.6 MB)
- [x] RAM (2026-08-28, release on the Fold, `dumpsys meminfo` PSS after 6
      page turns): reading 584 → 484 MB, bubble mode 641 → ~500-550 MB
      (native heap 156 → 73-85 MB), library 94 MB. Three cuts: page cache
      8 → 5 (the preload window is −1..+2), ORT CPU arena off
      (`setCPUArenaAllocator(false)`, which also removed the ~600 ms first
      detection), and pages now decode to exactly 2160 px (a 3000 px scan
      was kept full-res at 54 MB because power-of-two halving undershot;
      27 MB now). Jank unchanged (0.9-3 %, p90 ≤ 9 ms). What is left is
      Graphics: ~125 MB of page textures for 5 pages plus ~185 MB EGL
      (window buffers, the page-turn and zoom `graphicsLayer`s); not worth
      touching without a reason
- [x] Battery (2026-08-28, release on the Fold, `dumpsys battery unplug` +
      `batterystats --reset`, 20 page turns ≈ 1 m 50 s, Android's power model
      so relative numbers only): bubbles off 5.3 mAh (screen 3.5, cpu 1.9,
      11 s user CPU); bubbles on, first read 7.4 mAh (cpu 3.7, 24 s CPU);
      bubbles on, detections from Room 6.5 mAh (cpu 2.8, 17 s CPU). So
      bubble mode costs +40 % on a first read and +22 % on a re-read; the
      model is about half of the bubble cost, layout + overlay the other
      half (~0.3 s CPU per page — the latency item's target). Extrapolated:
      ~3.6 % of the battery per hour reading, ~5 % with bubbles, screen
      dominating. Nothing to change in preload or parallelism
      (`PARALLEL_DETECTIONS` = 1 already)
- [ ] Outliner: idea 3b (paper body leaking into pale art beside the rim) and
      a merge of ML boxes nested inside one paper body (2099 p.7 offline);
      independent of the resource items, scored on the silhouette ground truth
- [ ] Reading stats / recently read
- [ ] Gesture and transition tuning pass
- [ ] Home-screen thumbnails, per-comic settings

---

## Non-goals

- No online store, DRM, or downloading of copyrighted content. Comicify reads
  files the user already owns and supplies.
- No iOS / cross-platform. Native Android only, to fully exploit foldable APIs.
