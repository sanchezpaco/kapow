# Roadmap

Kapow — a comic reader for any Android phone or tablet, developed and
tuned on the Z Fold so foldables are first-class rather than an afterthought.
Built incrementally: every phase ends with something that already feels good
to use.

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
- [x] Tap zones (center toggles chrome; left/right edges turn the page)
- [x] Immersive mode: edge-to-edge, hidden system bars

## Phase 2 — Foldable magic

Goal: the reading experience adapts to device posture, and reading position
survives fold/unfold. This is the heart of the product. See `docs/foldable.md`.

- [x] Observe `WindowLayoutInfo` posture + window size class
- [x] Unfolded portrait → full single page at near-physical size
- [x] Unfolded landscape → two-page spread (open-book layout)
- [x] Tabletop posture → page on top half, controls on bottom half (implemented,
      verified on the Fold 2026-08-29)
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
- [x] Guided navigation: tap zones advance panel with animated pan/zoom,
      entering a page straight on its first panel, and a HUD stops indicator
      with an end-of-page cue
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
- [x] Bubble-aware guided tour (2026-09-01, `feature/guided-splash-tour`): panel boxes alone fail on splash / full-art / painted
      pages and drop dialogue where the panel model missed a region. New pure
      `GuidedTour` composes the stop list from panels + bubbles + reading
      direction — orphan bubbles still get a stop, a large panel (≥0.45) with
      several bubble clusters is split into per-cluster reading windows over the
      art, windows clamp to the page, a redundancy pass drops near-duplicate
      consecutive stops, and order is reading-direction-aware (manga RTL).
      Bubbles fetched only when panels cover < 80 % of the page. Verified on the
      Fold: Ruinas splash, Black Cat, and Titan RTL tour cleanly, one distinct
      stop per tap. 11 unit tests. See `docs/guided-view.md`.
- [x] Guided View evaluation harness (2026-09-02): offline/headless pipeline
      (`tools/eval` + `GuidedTourVisualizer`) that runs the production
      algorithm over a comic, draws the numbered ordered camera stops on each
      page (+ per-stop phone/tablet crops), applies deterministic objective
      gates and an LLM judge with an explicit policy rubric (order / framing /
      harmony) to produce a scorecard and a worst-offenders gallery. Single-
      comic trial on Ben Reilly #01 drove six composition fixes in
      `GuidedTour` (order 22/22, no bad page left); judge self-consistency
      measured. See `docs/guided-view-eval.md`.
- [x] Guided View eval, sample round 1 (2026-09-02): the stratified sample
      (Ruinas, Arkham, Titanes RTL, Blacksad, Venomverse) exposed three
      composition families, fixed in `GuidedTour`: panel stops grow over the
      balloons they own, a balloon needs a quarter of its area inside a panel
      to belong to it (else orphan window), a splash gets a window even for a
      single cluster, the bubble gate measures the panels' union, and reading
      order runs the guillotine on panel/cluster anchors with a cut tolerance
      relative to the box. Round 2 dropped the bubble gate (the bubble model
      runs on every page), made the non-owning neighbour shrink past a shared
      balloon instead of re-showing it, and let balloon anchors float over
      gutters. 129 pages: gates 116 → 129, order `bad` 9 → 2, pages with a
      `bad` 23 → 9, Blacksad clean, no regression on Ben Reilly. What is left
      is detector work (hand lettering, missed/overshooting boxes) and two
      semantic-order paintings. See `docs/guided-view-eval.md`.
- [x] Guided View eval, second stratified sample (2026-09-03): One Piece
      (RTL manga), Rapaces, Spiderman 2099 (90s scan), LOK (PDF), Superman
      (Johns) and Sueñan los Androides (painted), ~20 pages each, Opus judge.
      Manga and the European album were clean on arrival; the scan's
      page-sized box and dark painted pages exposed three composition
      families, fixed in `GuidedTour` (wordless sliver strips dropped, the
      page box no longer buries strips, tall panels beside a column ordered
      apart, wordless fragment pages shown whole, enclosed panel stops
      dropped); three candidate rules rejected on the twelve-comic regression
      gate. 252 judged pages, gates 274/274, 15 pages with a `bad`, all
      detector or semantic. `tools/eval/pending.py` / `restore.py` make the
      re-judge incremental. See `docs/guided-view-eval.md`
- [x] Guided View eval, third stratified sample (2026-09-04): Zombillenium
      (dense European), Los Defensores (70s Marvel), Aliens (80s Dark Horse),
      Sonic (cartoon), Shangri-La Frontier (digital manga RTL) and Vader Down
      (widescreen), ~20 pages each, Opus judge. Three composition rules added
      (duplicate panel boxes merge, painted pages open whole and drop their
      wordless scraps, balloon clusters and reading windows are bounded to a
      readable size) and four candidate rules rejected on the eighteen-comic
      regression gate. 370 judged pages, gates 398/398, 20 pages with a `bad`;
      Shangri-La, Vader Down, Rapaces, Spiderman 2099 and Blacksad are clean.
      `tools/eval/render.sh` renders any list of comics. See
      `docs/guided-view-eval.md`
- [x] Guided View eval, fourth round (2026-09-04): four composition rules added
      (dead-tap pass, reading-window minimum scaled to the page's median panel,
      orphan windows clamped to their panel, gutter captions owned by the panel
      across the gutter) and three rejected, plus a real crash fixed in `sized()`.
      Eight pages fixed, seven broken: **the corpus score did not move**
      (cost/page 1.016 → 1.036, 372 judged, gates 398/398). Composition has
      plateaued; the remaining bads are mostly detector. The scorecard now leads
      with a **cost per page** (`good` 0, `minor` 1, `bad` 3) because "pages with
      a bad" proved too brittle to steer by, and two pages were caught flipping
      verdict on byte-identical tours. See `docs/guided-view-eval.md`
- [x] Guided View eval, the judge's error bar (2026-09-04): twelve pages spanning
      the verdict spectrum judged three times on byte-identical input
      (`eval/_variance/run{1,2,3}/`). Pairwise agreement `harmony` 1.00, `order`
      0.83, **`framing` 0.67**; every disagreement one step, never `good`→`bad`.
      Cost per page over the sample ranged 1.83–2.33 and "pages with a bad" ranged
      3–5 of 12 with the algorithm held still. Within-page cost variance is five
      times higher on pages carrying a `bad` (1.156) than on clean ones (0.222) —
      the pages we tune on are the ones the judge cannot grade twice the same way.
      **The rule: a round re-judging k pages must move total cost by at least
      1.96·σ·√(2k) ≈ 1.03·√(2k) points (σ = 0.52 at the corpus mix) to mean
      anything.** The fourth round moved 7.4 points over 56 changed pages against a
      threshold of 10.9 — it did not regress, it did nothing measurable. See
      `docs/guided-view-eval.md`
- [x] Guided View detector, bubble text gate — **investigated and rejected**
      (2026-09-04): gating a detection on holding at least one text line drops 352
      of 1432 detections against the bubble ground truth and **344 of them (98 %)
      are true positives**; recall 0.913 → 0.685, F1 0.937 → 0.798, precision flat
      (0.962 → 0.957). `textIn` measures scan quality, not balloon-ness: 26 % of
      corpus detections hold no text line, 83 % on a scanned omnibus against 0.4 %
      on a clean digital book. An ink-share variant fails too — the one true false
      positive (`titanes:034`) has 2.5 % ink, above the low-ink tail. Production
      never asks for text, so nothing ships this; the offline enlarged-bubble text
      metrics are lower bounds on scans. New harness `BubbleTextDump`. See
      `docs/speech-bubbles.md`
- [ ] Bubble text extraction, `textIn` fails on scans: interior-hole segmentation
      finds no word on 26 % of detections and the rate tracks the book, not the
      art (aliens-03 83 %, rapaces-1 80 %, ruinas 45 %, ben-reilly 0.4 %). Nothing
      in production reads it today, so this is only worth fixing if a feature
      starts needing text — but the ground truth and `BubbleTextDump` now measure
      it directly
- [ ] Guided View detector, hand lettering — **scoped 2026-09-04, a retrain will
      not fix it**: on `arkham:027` the model returns zero bubbles because there is
      no bubble, only red hand-lettering floating on painted black; `031b` and
      `037` detect only the half of the page that holds balloons. The ground truth
      cannot measure the family either — three of Arkham's seven GT pages are
      labelled 0 bubbles and 0 panels, so bubble recall 0.96 is measured only where
      balloons exist. Needs a new label class for free-floating lettering, or
      text-region detection over the art — not the v4/v5 retrain pipeline. Ceiling
      is 15 cost points over three pages. See `docs/speech-bubbles.md`
- [x] Measured on the Fold what always-on bubble detection costs (2026-09-04):
      turning bubbles on costs ~0.5 pt of janky frames (1.6–2.0 % → 2.2–2.7 %)
      and moves p99 from ~12 to ~20 ms, with zero slow bitmap uploads — it does
      not drop frames, folded or unfolded, at 1.3× or 1.4×, however fast pages
      are turned. The large number is the cold per-page path: decode 89–152 ms
      + bubbles 131–242 ms + plan 0–106 ms ≈ 250–350 ms before bubbles appear
      (59–67 ms warm from Room). See `docs/performance.md`
- [~] Reader lag reported by the user with bubbles on — **a cause was found on
      2026-09-04 (evening), reproduced in the vertical strip**: `BubbleLayout.enlarge`
      and the `PageDetectionCodec` decoding of stored detections both inherited
      the caller's dispatcher, which was the main thread, so every page that came
      into view blocked the UI thread. Fixed in `35263ea`. The paged reader pays
      the same cost for one or two pages per turn, which is very likely the
      original report — but that has **not been confirmed** with the reporter, so
      leave this open until it is. Note `gfxinfo` cannot see this class of bug at
      all (it measures frame duration, not whether the frame changed): use a
      `screenrecord` contact sheet, see `docs/performance.md`. Note the bubble
      toggle and scale are **per comic**
- [ ] **Deferred until reader performance is fixed** (2026-09-04): Guided View is
      closed at cost/page 1.03, 351 of 372 pages with no `bad`, and the cumulative
      improvement (−89 points over 340 common pages against a ±27 noise floor) is
      demonstrated. The two items below wait for the bubble-latency work, and when
      we come back the order is the rubric first, the lettering after it.
- [ ] Guided View eval, tighten the `framing` rubric: at 0.67 pairwise agreement
      it is the one criterion whose noise swamps the deltas, and every
      disagreement is a policy boundary we own (whole-panel stop, sliced balloon,
      wide tier small on the phone). Sharpen those three boundaries in
      `judge_prompt.md`, re-run `eval/_variance` and check the agreement moved
      before spending another tuning round
- [ ] Guided View eval, next: human calibration set (~10 pages, judge vs
      maintainer, Fable vs Opus), then the full-corpus sweep as the regression
      gate; only then revisit the reading-order model.

## Phase 5 — Polish

Goal: the details that make it feel premium.

- [x] Continuous vertical scroll ("infinite strip", 2026-09-04): a third
      top-level per-comic mode that stacks whole pages fit to width in a
      `LazyColumn` instead of paginating, with no detector anywhere in its
      path. It does not stop at the end of an issue — the next issue of the
      series is appended to the same list across a named boundary band, and
      progress, the counter and the scrubber follow whichever issue is under
      the viewport. 0.33 % janky frames and zero slow bitmap uploads over a
      long fast scroll across two boundaries. See `docs/reading-modes.md`
- [x] More formats: format is detected by content (magic bytes), not extension.
      ZIP (CBZ, and `.cbr` files that are really ZIP), RAR4/RAR5 (via
      7-Zip-JBinding) and PDF (`PdfRenderer`, verified 2026-08-28: scan,
      cover, series stacking and reading) all work
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
      six series in `tools/training/gt/silhouettes.json`, scored by
      `SpeechBubbleVisualizer` (`out/silhouettes.json`: mean IoU, share
      ≥ 0.9, box fallbacks per series). Baseline: mean IoU 0.861, 64/115
      ≥ 0.9, 18 box fallbacks — 17 of them the whole Spiderman 2099 page.
      Prerequisite for the outlining work in `docs/improvement-ideas.md`; see
      `docs/speech-bubbles.md`
- [x] Per-bubble paper colour for scans (2026-08-27, idea 2): the paper of
      each ML box is estimated from its own interior and scanned paper
      (luminance 200-235) is classified relative to it, digital pages keep
      the global threshold. Spiderman 2099 box fallbacks 17 → 0, mean IoU
      0.758 → 0.879; digital series unchanged. See `docs/speech-bubbles.md`
- [x] Bubble student v4 (2026-08-28): retrained on every comic in `comics/`
      (private, not distributed; pipeline in `docs/training.md`) (121 files, 4,723 training pages of 31 series, 73 issues in 12 series
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
- [x] Reader → library exit (2026-08-28, traced): one 36 ms frame, 26 ms
      recomposing a fresh `LibraryScreen`; left as is, since keeping the
      library composed under the reader would recompose it on every
      `saveProgress` during page turns. See `docs/performance.md`
- [x] Fluidity in the unfolded posture (2026-08-28, spread on the inner
      screen): GPU-bound at ≈ 6 ms/frame (two full-height pages), spread
      turns 4–5 % janky at p50 7 ms, Guided View 3.3 %, HUD 8 % on the first
      show; the ambient gradient cost 2 ms of GPU per frame and is now
      painted downscaled in a layer (`AmbientBackdrop`, GPU p50 7 → 6 ms).
      Tabletop not measured. See `docs/performance.md`
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
- [x] Library hero + per-comic settings + shared-element transition (shipped
      2026-08-28, `7fb4327`). Hero with the cover's ambient colour, progress
      and a flat-pace time left, dismissable with "×"; tapping a cover reads
      directly (an intermediate detail screen with a page mosaic was built
      and rejected as a UX detour); a gear on each card or series stack opens
      the per-comic settings (direction, cover-alone spread parity, bubbles
      on open + scale, guided on open), series-wide writes to every issue;
      covers are shared elements into the settings header and the reader
      starts its glow from the cover's ambient. Absorbs "per-comic settings"
      below
- [x] Library usability pass on the Z Fold (2026-08-28, `902eeb2`…`b8ab467`):
      long-press is the only entry to per-comic and per-series actions (the
      per-card gear is gone; series stacks and the series header get a menu
      with settings, read/unread, favorite, delete); 3-column grid, compact
      hero and undoable dismiss (× or swipe) on folded widths; search and a
      Recent sort; the settings screen names its scope, spells out the global
      value behind "Default" and no longer uses the danger red for selection
- [x] App settings screen + themes (2026-08-28, `feature/settings`). Gear in
      the library header → full-screen settings page (Reading defaults incl.
      bubbles/guided on open and bubble size, Screen with night tint and a new
      keep-screen-on toggle, Library with folder + rescan moved out of "⋮",
      Appearance, About). Theme = ground (pure black / graphite / paper) × accent
      (7 curated presets with AA contrast on both grounds + Material You),
      picked from split-diagonal swatches with live preview; `LibraryPalette`
      constants became getters over a `KapowPalette` composition local
      and `MaterialTheme.colorScheme`; the reader keeps page, ambient and
      chrome, only the accent follows. Cover-alone stays per-comic (the
      column is not tri-state). See `docs/settings.md`
- [x] Guided View director transitions (2026-09-04, `71c1791`…`e3028b2`).
      Every stop-to-stop move used to be the same `tween(520 ms,
      FastOutSlowIn)`; `DirectorCut` (pure, unit-tested) now picks it from the
      two rects — pan whose duration grows with the travel and arcs past 0.45
      of the page, push in with a long brake, pull back fast, reveal from
      black, jump on a page turn. Thresholds were set against the 815
      stop-to-stop moves of the 193 ground-truth pages, not by eye: mean move
      401 ms, no move slower than the flat 520 ms it replaced. A page turn now
      cuts under an opaque veil whose opacity is decided **in composition** —
      a turn fires the focus animation twice, the first time with the previous
      page's stops, and a veil that animates up cannot win the race against a
      preloaded bitmap. Verified on the Fold's cover screen, 4.3 % janky
      frames, 0 slow bitmap uploads. See `docs/guided-view.md`
- [ ] Reading stats / recently read
- [ ] Gesture and transition tuning pass
- [ ] Home-screen thumbnails

## Phase 6 — Last tasks to 1.0

Goal: a signed bundle on Google Play (internal testing first). **Reached:**
Kapow is on Play in closed/open testing since 2026-09-04, bundles uploaded by
hand. The app is
Kapow (`com.sanchezpaco.kapow`, decided 2026-08-28). Logo decided 2026-08-29:
a white speech bubble with a Luckiest Guy "K" (yellow→red) over a tilted,
halftoned comic-page grid in blue — adaptive vector in `res/drawable/`,
generated by `tools/store_assets/icon.py` (see `docs/release.md`).

Blocking:

- [x] Release signing: upload keystore outside the repo (`local.properties` /
      env), `signingConfigs.release`, Play App Signing. See `docs/release.md`
- [x] AAB pipeline: `bundleRelease` with R8 + resource shrinking, confirm the
      7-Zip-JBinding keep rules and the arm64-only `abiFilter` survive the
      bundle, install the bundle-derived APK (`make deploy-bundle`; verified
      on the emulator, Fold pending)
- [x] Play Console (done, confirmed 2026-09-04): the app is created as Kapow,
      several bundles uploaded by hand, and Kapow is live on a **closed/open
      testing** track — past the "internal testing first" goal of this phase.
      The repository went public under AGPL-3.0 before the first upload and the
      listing links to it
- [ ] Play publishing automation: link a service account so
      `make publish-internal` works end to end. Uploads are by hand today and
      that is fine — convenience, not a blocker
- [x] Onboarding on first launch (3 steps): choose the folder, reader
      gestures (centre = HUD, edges = page turn, pinch = zoom), Guided View +
      enlarged bubbles with the HUD icons; "Show the introduction again" from
      Settings → About. Flag in DataStore. See `docs/onboarding.md`
- [x] Privacy policy URL + Play "Data safety" form (no data collected).
      Policy as `docs/privacy.md` (EN + ES: nothing collected, the glitch
      report is user-initiated mail from the user's own account, backups are
      Android's own), live since 2026-08-29 at
      <https://sanchezpaco.github.io/kapow/privacy> (GitHub Pages, `docs/` on
      `main`); the Data safety form in Play Console is filled in (confirmed
      2026-09-04). `dataExtractionRules` / `fullBackupContent` done: Room +
      DataStore only (`docs/settings.md` → Backup)
- [x] Licences screen under Settings → About (`LicencesScreen`). Both model
      sources are Apache-2.0; the bubble student was trained with Ultralytics
      (AGPL-3.0) — the app only ships weights and runs them with ONNX Runtime,
      but confirm that reading of the AGPL before publishing
- [x] Store listing (2026-08-29, design review done: feature graphic redone
      as a logo lockup with a real sample page, "bigger bubbles" shot as a
      before/after split, every "built for the Fold" claim dropped — Kapow is
      sold as a reader for any Android device that adapts on foldables):
      EN + ES title/short/long descriptions, 512 px icon, 1024×500 feature
      graphic, 6 phone + 3 seven-inch screenshots per language in
      `fastlane/metadata/android/{en-US,es-ES}/`, all generated by
      `tools/store_assets/` (see `docs/release.md`). Screenshots show the
      bundled sample and public-domain Golden Age comics
- [ ] Launcher icon picker under Settings → Appearance (decided 2026-08-29):
      the shipped blue page plus ink, red, violet and mix stages, same bubble
      and K — one `activity-alias` per variant, switched with
      `PackageManager.setComponentEnabledSetting`; the launcher redraws after
      a moment and the store icon stays blue. Stages generated by
      `tools/store_assets/icon.py`
- [x] Animated cold-start splash in the logo's language (2026-08-29): the
      system splash icon becomes the animation — its circle opens, the page
      grows and draws in the hidden panels, the bubble and the K pop to hero
      size with impact lines, and a diagonal wipe reveals the app; 1.3 s,
      cold start only, skipped for `VIEW` intents and "Remove animations"
      (`core-splashscreen` + Compose overlay, see `docs/splash.md`)
- [x] Honest copy for the smart modes (2026-08-29): the onboarding modes
      step says what Guided View works best on and a
      footnote states both are still being improved and points to "Report a
      visual glitch"; the store description's privacy paragraph says the same
      (EN + ES)
- [x] Closing commit of the phase (2026-08-29): `versionCode 1`,
      `versionName "1.0.0"`, tag `v1.0.0`. Still open after the close: the
      Play Console upload and the launcher icon picker (moves to post-1.0)
- [x] Rating prompt (2026-08-29): Play In-App Review requested when the
      reader closes after the third finished comic, at most every 60 days;
      no custom "do you like it?" dialog (see `docs/review-prompt.md`)
- [x] Crash visibility: rely on Play Vitals (no third-party SDK). Opening a
      comic rethrows anything that is not a `ComicSourceException`; a page
      that fails to decode shows "Page N could not be decoded" and logs the
      cause instead of spinning forever

Strongly recommended:

- [x] Tap zones left/right for page turns (Phase 1 item still `[~]`)
- [x] Verify tabletop posture on the physical Fold (verified 2026-08-29)
- [x] `ComicNameParser`: split a number glued to the name (`Venomverse001`,
      `DoctorDoom9`) so issues group into their series
- [x] Error states with clear copy: folder grant revoked after reinstall
      (`LibraryScanError.AccessLost`, `ComicOpenError.AccessLost`), corrupted
      archive (`ReadFailure`), password-protected PDF
      (`ComicOpenError.PasswordProtected`, verified on the emulator)
- [x] Cold start on a rebooted Fold (release) confirmed 2026-08-29; the
      emulator's >10 s splash is the emulator
- [x] Accessibility floor: ghost actions and the series back button are
      48 dp; decorative icons next to text stay `null`; TalkBack pass on
      library + settings done on the Fold 2026-08-29
- [x] "Report a visual glitch" from the reader HUD settings menu (gear):
      a confirmation dialog ("an image of this page and technical data will be
      sent, no personal data") then ACTION_SEND via FileProvider with
      `sanchezpacodev@gmail.com` and the subject prefilled, user picks the
      mail app. Payload: the composed page as seen (overlay included, HUD
      excluded) plus `report.json` with file name, page, mode (guided /
      bubbles + scale), posture, device, version, build and the page's
      detections (boxes + silhouettes) so it can be reproduced offline without
      the comic. No free-text field: the mail body carries a one-line summary
      the user can edit in the mail app. No backend, no SDK. Decided
      2026-08-29
- [x] Licensing decision before the first upload: the bubble student was
      trained with Ultralytics (AGPL-3.0). Decided 2026-08-29: the repository
      is published under AGPL-3.0 (`LICENSE`); no Ultralytics licence needed

Rejected for 1.0: anonymous glitch reporting (Crashlytics non-fatal for the
JSON, or an own upload endpoint for the full package) — the report is mail from
the user's own account and that is accepted; a `mailto:` intent selector does
not resolve on every device, so the plain chooser stays (decided 2026-08-29).

Deferred to 1.1: **continuous vertical scroll** (whole pages stacked, fit to
width, no page-turn effect — not Cinematic Flow, which cropped to panels and was
rejected; this one has no detector in its path, see `docs/product-ideas.md`),
tip jar under Settings → About as a one-off Play Billing
product (an external Ko-fi / Buy Me a Coffee link risks a Play payments-policy
rejection; decided 2026-08-29), reading stats / recently read, home-screen widget, automatic
day/night theme, ComicVine metadata, panel-level posture transition,
outliner idea 3b.

---

## Tester feedback — 1.0.x

Feedback from the first testers (2026-08-30). Each item shipped on its own
`feedback/*` branch, verified on the Fold and phone emulators and on the Z
Fold, and closed as version 1.0.1 (`versionCode 2`, tag `v1.0.1`).

- [x] Onboarding folder step: no visual confirmation after picking the
      folder. Show the chosen folder name and a "selected" state on the
      button, so the user knows the step is done before moving on
- [x] Library does not notice comics added to the folder while the app is
      open. Rescan the folder whenever the library comes to the foreground
      (app start and return from background), keeping the manual refresh
- [x] Split double pages: some PDFs (and scans) come with two comic pages per
      file page. Per-comic "Split wide pages" toggle, in the comic settings
      screen and in the reader gear menu, that cuts landscape pages in half
      and presents each half as its own page in reading-direction order
- [x] Double-tap to zoom on regular phones often pans the page a little
      instead of toggling zoom: the second tap is read as a tiny drag.
      Tolerate touch slop between the taps so double-tap wins
- [x] Suggest the split (decided 2026-08-30, no centre-gutter check): when
      ≥ 80 % of the pages after the cover are landscape, a one-time snackbar
      offers "Split"; accepted or dismissed, it never asks again for that
      comic. Onboarding stays untouched — it is a niche setting, surfaced at
      the moment it matters
- [x] Guided View: drop the whole-page overview stop when entering a page —
      go straight to the first panel so the flow is not interrupted on every
      page turn

---

## Design review — 1.0.x

Senior-designer UX pass over every screen on the emulator (2026-09-04), then
every finding applied the same day on `design/*` branches and verified on the
emulator at unfolded, spread and folded widths.

- [x] Reader bottom chrome without a scrim: thumbnails and page counter vanished
      over light pages. Symmetric dark gradient under the bottom chrome
- [x] Eye menu: four identical pills for an exclusive choice plus a toggle, art
      showing through them, menu staying open. One solid panel: modes with a
      check mark, hairline, "Enlarged bubbles" switch with a −/+ stepper
- [x] Gear menu: unlabelled 12 %-white circles, both menus open at once. Same
      panel with labelled rows and trailing state; opening one closes the other
- [x] Bubble-size slider always expanded and invisible over saturated art:
      folded into the panel as a stepper
- [x] Library filter chips jumped ~185 dp when a filter hid "Continue reading":
      chips pinned above the shelf, section collapses below them
- [x] Paging back into a finished comic un-completed it: `completed` is sticky
- [x] End-of-comic overlay left the chrome bright above the scrim: chrome hides,
      "Next issue" first, "Close" instead of "Keep reading"
- [x] Snackbars in Material default colours: palette-themed host
- [x] Settings: On/Off chip pairs → switches; per-comic strip + guided toggles
      → one "Mode on open" choice; grouped raised surfaces; centred column, two
      columns from 840 dp; background preview tiles; About rows with chevrons
- [x] Paper theme: selected chip gets an accent border and fill
- [x] Bubble-size row in Settings read as three values: value pill + captions
- [x] Group-by-series used a folder icon (means "choose folder" elsewhere):
      layers icon
- [x] Folded width clipped "Recent": sort toggle on the shelf title row
- [x] Onboarding said "Two ways to read" and had no illustration on page 3:
      three cards and a glyph illustration at the same height as pages 1–2
- [x] Same red meant "open" on the gear and "something active" on the eye: red
      only while open, a dot for non-default state
- [x] Returning from Licences reset the settings scroll; scrubber did not
      re-centre after a jump to the last page

---

## Non-goals

- No online store, DRM, or downloading of copyrighted content. Kapow reads
  files the user already owns and supplies.
- No iOS / cross-platform. Native Android only, to fully exploit foldable APIs.
