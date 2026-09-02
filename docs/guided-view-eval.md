# Guided View — evaluation harness

Automated, repeatable evaluation of the Guided View camera tour across the whole
comic corpus. It replaces ad-hoc eyeballing of a handful of pages (which let a
real ordering regression ship as "validated") with a coverage-wide scorecard, a
ranked gallery of the worst pages, and — later — a labelled dataset that can
bootstrap a learned reading-order model.

The thing under test is the **output of `GuidedTour.stops`**: an ordered
`List<Rect>` of camera framings per page (normalized 0..1), in reading order.
That list *is* the guided tour — where the camera stops and in what sequence.

## Why this exists

Guided View has two halves:

- **Detection** (panels, bubbles) — already ML, strong (panels F1 0.93, bubbles
  0.96).
- **Composition** (assign bubbles → panels, cluster, order, frame, dedup) —
  `GuidedTour` (pure Kotlin, `feature/reader/domain`). Mostly geometry; the one
  genuinely hard, learned-convention part is **reading order** on irregular
  layouts (multi-column painted pages, tall panels, manga RTL).

We cannot tell whether a composition change helps or regresses without looking
at many pages. This harness makes that measurable.

## Roles

The pipeline is four logical roles (optionally five). The **capturer** and
**runner/annotator** are deterministic code the orchestrator runs — not flaky
emulator taps and not a re-implementation of the algorithm. The **judge** is the
LLM role that benefits from parallel fan-out.

### 1. Capturer — page images

Produces one image per page for each comic under test. **The whole harness is
offline / headless — no emulator.** Decode pages through the app's own code path
(or a plain page-extraction of the CBZ/CBR/PDF): deterministic, fast, covers
every page, and free of the tap-driving flakiness. In fact roles 1 and 2 are the
same JVM pass — it decodes the page, runs the algorithm, and draws the overlay
in one step. The emulator is not part of this pipeline at all (at most an
optional, separate human sanity-check later; not required).

Output: `pages/<comic>/<index>.jpg` plus a manifest with, per comic, its
**reading direction** (LTR/RTL) — the judge and the algorithm both need it.

### 2. Runner / Annotator — algorithm output + overlays

Runs the **production** algorithm — `PanelDetector` (panels + bubbles) then
`GuidedTour.stops(panels, bubbles, direction)` — over each page. Reuse the
production code via a JVM visualizer (extend `PanelDetectionVisualizer` /
`SpeechBubbleVisualizer`), so there is **zero parity drift** between what the
harness scores and what the app ships. (The scratchpad `prototype_stops.py`
mirrors the algorithm for quick iteration, but it must not be the source of
truth for scoring — it has drifted from the app before.)

Emits, per page:
- `stops.json` — the ordered stop rects (the raw algorithm output).
- an **annotated page**: the full page with each stop drawn as a numbered,
  ordered rectangle (for judging order, coverage, harmony).
- a **crop sequence**: each stop cropped to a phone and to a tablet aspect ratio
  (for judging legibility — this is what the viewer actually sees).

This can be split into two roles (one emits `stops.json`, one renders) if that
parallelises better; the rendering must consume the JSON, never recompute the
algorithm.

### 3. Judge — LLM evaluation (fan-out)

One or more LLM agents, sharded across pages, score each annotated page + its
crop sequence against a fixed rubric. **Must be told the reading direction.**
Judges only the subjective criteria:

- **Order** — do the numbered stops follow the page's reading order (respecting
  LTR/RTL)?
- **Framing / legibility** — does each marked zone frame a sensible unit, and
  does its crop read comfortably on phone and tablet (text large enough, not
  cut, art visible)?
- **Harmony / flow** — does the sequence preserve the page's storytelling flow
  (no jarring jumps, no dead stops, dialogue not skipped)?

Output: **structured JSON per page** — verdict per criterion (enum, e.g.
`good` / `minor` / `bad`), a one-line reason, and the offending stop index when
applicable. No free-form prose scoring. Judge the crops, not vibes.

### 4. Orchestrator — spin up, shard, collect, aggregate

(The next session starts in this role.)

- Selects the page set — a **stratified sample first** (~100–200 pages spanning
  clean grids, splashes, painted multi-column, manga RTL, dense-dialogue), then
  the full ~4,700-page sweep as a regression gate.
- Spins up N runner/judge agents, shards the corpus, collects their JSON.
- Computes the **objective gates** itself (deterministic, no LLM — see below).
- Aggregates into a **scorecard** (pass rate per criterion, per comic, per page
  type) and a **worst-offenders gallery** (lowest-scoring pages with their
  annotated images and reasons) for us to act on.
- Re-runs the same set after an algorithm change to show regression/progress.

## Model per role

Match the model to the work; do not spend a strong model where none is needed.

- **Capturer + Runner/Annotator** — deterministic code (a JVM/Gradle pass), not
  reasoning. Ideally plain scripts the orchestrator invokes; if wrapped as agents
  for convenience, **Haiku** is the floor. No model earns its cost drawing
  rectangles.
- **Judge** — needs real visual reasoning and consistent taste, and must be
  vision-capable (it reads the annotated page and the crops). The judge is
  **Fable when it is available, otherwise Opus — never Sonnet or a cheaper
  model.** Which of Fable / Opus to prefer is a **calibrated choice, not an
  assumption**: run both on the human-verified calibration set and pick the one
  that agrees most with the maintainer and is most consistent across repeats
  (the user suspects Fable 5.1 is best; measure it rather than assume it). Cost
  is controlled only by the free, deterministic **objective gates below** doing
  the pre-filter — not by dropping the judge to a weaker model. The full
  ~4,700-page sweep is the expensive step, which is exactly why the single-comic
  trial and the stratified sample come first.
- **Orchestrator** — a single instance, no fan-out, so cost is a non-issue; use a
  capable model (**Sonnet/Opus**). It is the brain of the loop.

## Objective gates (deterministic — computed, not judged)

Cheap, exact, computed directly from `stops.json` + the bubble detections. These
are hard fails and should be measured for every page before any LLM spend:

- **Coverage** — every detected bubble's centre lies inside some stop.
- **No near-duplicate consecutive stops** — no two adjacent stops overlap ≥ 85 %
  of the smaller's area (the "tapping skips several panels" symptom).
- **In-bounds** — every stop is within `[0,1] × [0,1]`.
- **Sane count** — stop count is neither 0 nor absurdly high for the page.

Reserve the LLM judge for what only an eye can decide (order sensibility,
legibility, harmony).

## Breaking the circularity

If the LLM judge both **labels** and **evaluates**, we train/tune toward the
judge's taste, not the truth. Keep a small **human-verified calibration set**
(pages hand-checked by the maintainer) that the judge never sees, and use it to
sanity-check both the judge and any future model.

## How to run it (one comic)

Everything lives under `eval/<comic>/` (git-ignored: the corpus is private) and
`tools/eval/`. The venv is `tools/eval/venv` (`python3 -m venv`, then
`pip install onnxruntime pillow numpy`).

```
mkdir -p eval/<comic>/pages && unar -o /tmp/x "comics/<file>.cbr"   # then copy the
# page JPEGs into eval/<comic>/pages as 000.jpg, 001.jpg, … in the app's page order
tools/eval/venv/bin/python tools/eval/detect.py eval/<comic>          # boxes.json
KAPOW_GUIDED_EVAL_DIR=$PWD/eval/<comic> KAPOW_GUIDED_EVAL_DIRECTION=ltr \
  ./gradlew :app:testDebugUnitTest --tests '*GuidedTourVisualizer*' --rerun -i
tools/eval/venv/bin/python tools/eval/gates.py eval/<comic>           # gates.json
# judge: one Fable agent per page with tools/eval/judge_prompt.md → verdicts/NNN.json
tools/eval/venv/bin/python tools/eval/scorecard.py eval/<comic> <label>  # scorecard + worst/
```

- `tools/eval/detect.py` runs the **bundled** `panels.ort` / `bubbles.ort`
  through the same decode → margin crop → analysis-size → letterbox path as the
  device (`tools/training/device_pipeline.py`), same confidences and duplicate
  merge as `OnnxBoxDetector`. Output: one JSON line per page with the ML panel
  and bubble boxes in cropped-page coordinates.
- `GuidedTourVisualizer` (unit test, `app/src/test/.../domain`) is the
  runner/annotator: it reads `boxes.json`, runs the production
  `PanelDetection` + `PanelLayout.complemented`/`readingOrder` (exactly what
  `PanelDetector.detect` does), `SpeechBubbles.outlined`, the `needsBubbles`
  gate and `GuidedTour.stops`, then writes `stops/NNN.json`, `annotated/NNN.jpg`
  (stops = thick numbered rectangles, panels thin green, bubbles thin blue) and
  `crops/NNN-SS-{phone,tablet}.jpg` — the stop as the reader sees it, framed
  by `GuidedFocus.frame`/`fit` on the Fold cover (904×2316) and inner
  (1968×2184) screens, surroundings dimmed like the spotlight. The only
  parity gap left is ONNX inference running in Python instead of ORT-Android.
- `tools/eval/gates.py` — the objective gates (bubble-centre coverage against
  the raw ML bubbles, no near-duplicate consecutive stops using the same
  size-aware rule as `GuidedTour`, in-bounds, 1–12 stops).
- `tools/eval/judge_prompt.md` — the judge rubric and JSON schema. One agent per
  page; it reads the annotated page and every crop and writes
  `verdicts/NNN.json` with `order` / `framing` / `harmony` ∈ good/minor/bad,
  a one-line reason, the offending stops and an `ideal_tour` sentence (the
  most actionable field).
- `tools/eval/scorecard.py` — aggregates gates + verdicts into
  `scorecard.json` and a `worst/` gallery (`index.md` + the annotated pages of
  the lowest-scoring pages). Archive a round with
  `cp -r verdicts annotated stops scorecard.json gates.json worst rounds/<label>/`.

Between rounds only re-judge the pages whose `stops/NNN.json` changed and copy
the previous verdicts for the rest — the tour is identical, so is the verdict.
Judge agents run at most 20 at a time; a 100-page round is ~5 waves of a
minute each and roughly 55k tokens per page, and a session limit (Fable, then
Opus) was hit twice in one day — plan rounds around it.

## Artifacts

```
eval/<comic>/
  pages/NNN.jpg           # capturer (extracted pages, app page order)
  boxes.json              # detect.py (ML panels + bubbles per page)
  stops/NNN.json          # runner (panels, bubbles, stops, gate)
  annotated/NNN.jpg       # runner (numbered ordered rects)
  crops/NNN-SS-*.jpg      # runner (per-stop phone/tablet views)
  verdicts/NNN.json       # judge (LLM, per criterion)
  gates.json              # gates.py
  scorecard.json          # scorecard.py
  worst/                  # scorecard.py (ranked offenders gallery)
  rounds/<label>/         # archived copies of a round for before/after
```

## Trial run: Ben Reilly Spider-Man #01 (2026-09-01)

24 pages, LTR, 21–22 story pages judged per round; judge = Fable 5.1 in rounds
0–1 and Opus in round 2 (the Fable session limit was hit). Only pages whose
tour changed were re-judged each round. Scorecard (good / minor / bad):

| round | order | framing | harmony | all-good pages | pages with a bad |
|---|---|---|---|---|---|
| 0 (as handed over) | 19 / 0 / 2 | 7 / 11 / 3 | 11 / 4 / 6 | 5 | 6 |
| 1 | 22 / 0 / 0 | 5 / 14 / 3 | 13 / 9 / 0 | 5 | 3 |
| 2 | 22 / 0 / 0 | 7 / 14 / 1 | 15 / 7 / 0 | 6 | 1 |
| 3 (policy rubric) | 22 / 0 / 0 | 8 / 14 / 0 | 17 / 5 / 0 | 7 | 0 |
| 4 (final, splash opener) | 22 / 0 / 0 | 9 / 13 / 0 | 18 / 4 / 0 | 9 | 0 |

Objective gates passed 24/24 in every round. Round 0 found four real
composition defects, all fixed in `GuidedTour` (see `docs/guided-view.md`):

- a tall panel spanning two rows of a right-hand column was followed by the
  column's bottom row first (row-based order) → guillotine-cut ordering;
- a detector box around a whole row of insets owned all their balloons and was
  split into windows overlapping the insets → innermost assignment + container
  remainder strip;
- windows of neighbouring balloon clusters on a splash were near-identical
  nudges → overlapping windows merge;
- a splash toured only by its balloons never showed the art whole → an
  establishing whole-page stop, which round 1 then rejected on dialogue-dense
  pages (unreadable text, overview re-read by the windows) → kept only for a
  true splash with ≤ 3 balloons; windows clamp to their panel;
- round 1 also surfaced a dead stop on a credits sliver inside a panel → boxes
  under 4 % of the page inside another panel are absorbed;
- round 2 left window edges slicing a neighbouring cluster's captions (p005)
  → a window edge never crosses a bubble (round 3, judged with the policy
  rubric).

Round 4 is the first **human calibration** decision: the maintainer looked at
the p007 splash toured as three text windows and could not tell from the tour
what Spider-Man was doing while saying it. The judge had scored that `good`
(it measures legibility; the maintainer measures comprehension), so the
splash opener now applies to every single-panel page whatever its balloon
count, and the rubric policy was rewritten to match. Round 3 added the last
geometric composition rule — a window edge never crosses a
bubble: the window shrinks past a foreign balloon when its own cluster still
fits, otherwise grows to include it — which cleared the last `bad` (p005, a
painted page with 13 captions). Everything the judge still flags is
detection or device-dependent, not composition: wide full-width tiers whose
text is small on the narrow cover screen (a phone-only split would fix it),
and panel boxes that overshoot into a neighbour or clip a caption overhanging
the gutter.

## Judge policy and consistency

The rubric has a **Policy** section that decides the taste questions by rule
(when a whole-page stop is right, what a sliced balloon costs, that missing art
is never a `bad`, how wide tiers score on the phone). Judges score against it;
disagreement with the policy goes into `notes`, not into the score. Before it
existed, two judges gave opposite verdicts on the same splash (p021: whole page
last vs first) — with it, three runs agree.

Measured self-consistency (Opus, policy rubric, 10 disputed pages × 3 runs,
`tools/eval/consistency.py` over `eval/<comic>/consistency/run*/`):

| criterion | pages unanimous | pairwise agreement | disagreements |
|---|---|---|---|
| order | 10/10 | 1.00 | none |
| framing | 7/10 | 0.80 | 005 bad/bad/minor, 019 bad/minor/minor, 021 minor/good/good |
| harmony | 7/10 | 0.80 | 001 minor/bad/minor, 018 good/good/minor, 019 minor/minor/good |

Every disagreement is one step on the scale (good↔minor or minor↔bad); no
page flipped good↔bad, and the offending-stop lists matched in 21/30 cases.
Order is deterministic enough to gate on a single run. For framing and
harmony a single run has ~20 % one-step noise, so: treat only a good↔bad flip
or a change in the offending stops as a regression from one run, and use the
majority of three runs on the calibration set when a threshold decision
matters. The human calibration set is still pending.

## Stratified sample (2026-09-02, round 0 — not yet fixed)

Five more comics, ~20 story pages each, prepared with `tools/eval/prepare.py`
(extracts the archive, splits wide spreads into `NNNa`/`NNNb` halves the way
the app does, writes `manifest.json`), judged by Opus with the policy rubric.
`tools/eval/summary.py eval` prints the cross-comic table. Venomverse stopped
at 7 pages and one Blacksad page is missing: the Opus session limit was hit.

| comic | stratum | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|---|
| ben-reilly-01 | clean Marvel grid (tuned on) | 22 | 24/24 | 22/0/0 | 9/13/0 | 18/4/0 | 9 | – |
| ruinas | painted splash / collage | 20 | 19/20 | 16/2/2 | 5/15/0 | 11/7/2 | 3 | 015 018 028 |
| arkham | painted multi-column, hand lettering | 21 | 18/21 | 16/1/4 | 6/13/2 | 10/6/5 | 6 | 022 027 031a 031b 033 035 036 037 |
| titanes | manga RTL | 20 | 13/20 | 17/0/3 | 4/11/5 | 14/5/1 | 4 | 034 035 036 037 038 043 044 |
| blacksad-1 | European, dense captions | 19 | 18/20 | 18/1/0 | 4/11/4 | 15/2/2 | 4 | 013 015 017 029 |
| venomverse-001 | bleed-heavy modern Marvel | 7 | 24/24 | 7/0/0 | 3/4/0 | 5/1/1 | 2 | 004 |

The 23 pages with a `bad` fall into five families. The first three are
composition and are the next fixes; the last two are detection.

1. **Balloon straddling a gutter, sliced by both neighbouring panel stops**
   (titanes 035/036/038/044, blacksad 013/015, arkham 037). Manga and
   European letterers routinely hang balloons over the gutter; the ML panel
   box stops at the border. Fix: an ordinary panel's stop grows to include the
   balloons assigned to it (the heuristic detector's old "enclosed whites grow
   the frame" behaviour, lost with the ML boxes). The neighbour then still
   clips the balloon's border, which the policy scores `minor`.
2. **Dialogue covered by no stop** (ruinas 015, arkham 031a/036, blacksad
   017/029). Three causes: a caption barely touching a panel is assigned to it
   by the "any overlap" fallback in `hostOf` and never windowed (ruinas 015,
   arkham 036) → require the centre inside or ≥ 50 % of the balloon
   overlapping, else orphan; when every window of a splash merges into one,
   `frameStops` returns the bare frame instead of the merged window (arkham
   031a) → return the window; a panel the detector missed whose caption
   lands in a neighbour by overlap (blacksad 029) → same threshold fix.
3. **Order broken by a window crossing a gutter** (arkham 022/035, titanes
   037): an orphan window sized to the 0.42 × 0.30 minimum straddles the
   horizontal gutter, so no guillotine cut exists and the row fallback
   scrambles. Fix: run the guillotine on the *anchors* (panels and bubble
   cluster boxes), then expand to windows. Ruinas 018/028 and titanes 043 are
   different: a conversation on a painting whose order is semantic, a twin
   wordless panel, and duplicate detector boxes.
4. **Dead stops from spurious boxes** (arkham 027, titanes 034, ruinas 028):
   detector false positives (a pavement strip, a corner "bubble").
5. **Panels the detector never found** (venomverse 004's Carnage reveal,
   blacksad 014/029, arkham 025/036/037) and **hand-lettered text the bubble
   model does not see** (arkham 027/031b/033). Only a detector change helps.

The same `minor` bucket as Ben Reilly dominates everywhere: full-width tiers
whose text is small on the cover screen (a phone-only split), and detector
boxes that overshoot.

## After the scorecard: the model question (deferred)

Only after the scorecard tells us **where** the heuristic actually fails do we
decide whether to train a model — and if so, the sensible target is a small,
on-device **reading-order model** (sequence the already-detected boxes), not an
end-to-end Guided-View model; framing/coverage stay rules. The judge verdicts +
the human calibration set become the order labels, distilled the way the bubble
student was (see `docs/speech-bubbles.md`, `docs/training.md`). Do not commit to
training before the scorecard shows order is the dominant failure and rules
cannot capture it.
