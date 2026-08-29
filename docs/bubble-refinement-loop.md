# Speech-bubble refinement loop (design)

A plan for iterating on bubble detection/enlargement against a diverse corpus
(western comics, manga, negative bubbles, SFX-heavy, painted). Written to be
executed in a later session. Nothing here is built yet.

## Why not drive it from the emulator

The emulator is a poor iteration harness: SAF folder picking, flaky taps, a HUD
that auto-hides, multi-display `screencap`, ~10-15 s per page. Use it only for
the final on-device spot-check (render quality, perf, archive decoding).

## Harness: the offline visualizer, made faithful

`SpeechBubbleVisualizer` already runs the **exact** production domain code
(`SpeechBubbles.detect` + `BubbleLayout.enlarge`) and writes `-bubbles.png`
(detection) and `-enlarged.png` (what the reader sees) per page, headless,
~150 ms/page, deterministic.

It is a high-fidelity proxy, not pixel-identical to the device, because the
**inputs** differ (the algorithm and the `ANALYSIS_WIDTH = 1000` pool formula
are identical in `PanelDetector` and the visualizer):

1. **Decode resolution.** The app decodes with `decodeSampled(bytes, 2160)`
   (power-of-2 subsample, only when native width > 2160), then pools. The
   visualizer reads the native JPEG. For pages =< 2160 px wide (e.g. Venomverse
   at 1440) both decode at the same width and the same `pool`, so detection
   matches; for larger scans the pool can differ.
2. **Margin crop.** The app runs `contentCropped()` before detection; the
   visualizer does not. This shifts coordinates and the border-touch logic.
3. Different JPEG decoders (ImageIO vs `BitmapFactory`) nudge threshold-border
   pixels.

**Make it faithful first (~15 lines in `SpeechBubbleVisualizer`):** subsample
the source toward 2160 the same way `decodeSampled` does, and apply the same
margin crop before `detect`. After that the `-enlarged.png` is what the device
produces (modulo the render engine, which is geometrically equivalent). This is
the prerequisite for trusting the offline loop.

## Metrics (turn "does it work" into numbers)

Without a regression gate, LLM-driven threshold tuning overfits and reintroduces
the hard-won cloud/moon/SFX false-positive rejections. Per page, per comic:

- **Recall** = dialogue bubbles enlarged / dialogue bubbles present.
- **Precision** = real bubbles / all detections (false positives = art enlarged
  spuriously, e.g. the floodlight-truss case in `speech-bubbles.md`).
- **Layout quality** = copies that clip, cover a neighbour's original, or fail
  to grow (scale stuck at ~1x).

Ground truth is agent-labeled once per page and stored beside the corpus, so
every algorithm change is scored against the same labels and regressions are
caught immediately.

## Corpus (stress the algorithm, which is tuned for Marvel)

8-12 issues spanning: western white/cream bubbles; **manga B&W** (tall/narrow
bubbles, borderless bubbles, screentone, furigana, right-to-left); painted /
watercolor; SFX-heavy pages; and negative/black bubbles. Manga will hurt the
most.

## The loop

1. Run the faithful visualizer over the corpus -> `-bubbles.png` / `-enlarged.png`.
2. A vision judge subagent scores each `-enlarged.png` into structured findings
   `{bubbles_grown, bubbles_missed, spurious_art_enlarged, clipped_or_covering}`.
3. Aggregate -> recall/precision per comic + a ranked list of worst pages.
4. The worst pages drive the next change. Small threshold sweeps can be
   automated; structural changes (a new pass, a changed filter) are proposed to
   the human, because each risks the existing false-positive rejections.
5. Re-run, compare metrics against the stored baseline, keep only if it improves
   without regressing other comics. Guard against overfitting to the corpus.

## Shape

Fits a multi-agent workflow (fan out over pages -> judge per image ->
aggregate), which must be opted into explicitly and is token-heavy. A single
serial run over one comic is fine without a workflow.

## On-device validation (periodic, not per-iteration)

For the top-N worst or best pages, push to the Fold/emulator, toggle enlarge,
screenshot, confirm render + perf. Emulator serial `emulator-5556`
(Pixel_10_Pro_Fold) or a physical Fold.
