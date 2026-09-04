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
tools/eval/render.sh <comic>…                                        # stops + annotated + crops
tools/eval/venv/bin/python tools/eval/gates.py eval/<comic>          # gates.json
tools/eval/venv/bin/python tools/eval/restore.py eval <comic>…       # verdicts back from rounds/
tools/eval/venv/bin/python tools/eval/pending.py eval <comic>… --skip <comic>:<page>…
# judge: one Opus agent per pending page with tools/eval/judge_prompt.md → verdicts/NNN.json
tools/eval/venv/bin/python tools/eval/scorecard.py eval/<comic> <label>  # scorecard + worst/
tools/eval/venv/bin/python tools/eval/summary.py eval                # the corpus table
```

- `tools/eval/detect.py` runs the **bundled** `panels.ort` / `bubbles.ort`
  through the same decode → margin crop → analysis-size → letterbox path as the
  device (`tools/training/device_pipeline.py`), same confidences and duplicate
  merge as `OnnxBoxDetector`. Output: one JSON line per page with the ML panel
  and bubble boxes in cropped-page coordinates.
- `GuidedTourVisualizer` (unit test, `app/src/test/.../domain`) is the
  runner/annotator: it reads `boxes.json`, runs the production
  `PanelDetection` + `PanelLayout.complemented`/`readingOrder` (exactly what
  `PanelDetector.detect` does), `SpeechBubbles.outlined` and
  `GuidedTour.stops`, then writes `stops/NNN.json`, `annotated/NNN.jpg`
  (stops = thick numbered rectangles, panels thin green, bubbles thin blue) and
  `crops/NNN-SS-{phone,tablet}.jpg` — the stop as the reader sees it, framed
  by `GuidedFocus.frame`/`fit` on the Fold cover (904×2316) and inner
  (1968×2184) screens, surroundings dimmed like the spotlight. The only
  parity gap left is ONNX inference running in Python instead of ORT-Android.
- `tools/eval/gates.py` — the objective gates (bubble-centre coverage against
  the raw ML bubbles, no near-duplicate consecutive stops using the same
  size-aware rule as `GuidedTour`, in-bounds, and a sane stop count —
  `1 … max(12, panels + 2)`, loose enough for a fourteen-panel European page).
- `tools/eval/judge_prompt.md` — the judge rubric and JSON schema. One agent per
  page; it reads the annotated page and every crop and writes
  `verdicts/NNN.json` with `order` / `framing` / `harmony` ∈ good/minor/bad,
  a one-line reason, the offending stops and an `ideal_tour` sentence (the
  most actionable field).
- `tools/eval/render.sh <comic>…` — runs the visualizer over any list of comics,
  reading each one's direction from its `manifest.json` and forcing the JBR JDK.
  Use it instead of hand-writing the Gradle invocation. **Never render while
  judges are running**: it rewrites every comic's `stops/annotated/crops` under
  them.
- `tools/eval/restore.py eval <comic>…` — for every page whose current stops
  match an archived round's, copies that round's verdict back. Run it before
  `pending.py` so a change that reverts a page costs no judging.
- `tools/eval/pending.py eval <comic>… --skip <comic>:<page>…` — lists the pages
  whose current stops have no verdict, deletes the stale ones and snapshots the
  stops under `judged/`. `--skip` is for covers and the pages held out to stay
  comparable with earlier rounds. Everything it lists, and nothing else, needs a
  judge. The held-out set, unchanged since the second sample, is:

  ```
  --skip spiderman-2099-01:001 ben-reilly-01:000 defensores-1:034 defensores-1:035 \
         sonic-malos:025 vader-down-01:006b lok-tdsr:015a … lok-tdsr:024b
  ```

  (covers and title pages, plus the second half of LOK, kept out so the corpus
  stays comparable with the earlier rounds).
- `tools/eval/scorecard.py` — aggregates gates + verdicts into
  `scorecard.json` and a `worst/` gallery (`index.md` + the annotated pages of
  the lowest-scoring pages). Archive a round **before** changing `GuidedTour`
  with `cp -r verdicts annotated stops scorecard.json gates.json worst
  rounds/<label>/`, so `restore.py` can undo a rejected rule for free.
- `tools/eval/summary.py eval` — the corpus table, and the only number to steer
  by: **cost per page**, `good` 0 + `minor` 1 + `bad` 3 summed over the three
  criteria of every judged page. "Pages with a `bad`" is a max over three
  criteria and moves on a single flip; keep it as a worklist, not a score.
- `tools/eval/consistency.py eval/_variance` — pairwise agreement across repeat
  judgings of the same pages. Judge one ~12-page sample spanning the verdict
  spectrum three times into `eval/_variance/run{1,2,3}/<comic>-<page>.json` and
  read the error bar off it. Measured on 2026-09-04: `framing` agrees only 0.67 of
  the time on identical input, and a round re-judging k pages must move total cost
  by ≈ 1.03·√(2k) points to mean anything. Read "The judge's error bar" below
  before accepting or rejecting any `GuidedTour` change.

Between rounds only re-judge the pages whose `stops/NNN.json` changed and copy
the previous verdicts for the rest — the tour is identical, so is the verdict.
Judge agents run at most 20 at a time; waves of 8–16 Opus judges (≈55k tokens
and 1–3 min per page) got through 58 pages without touching a session limit,
where 20-wide waves had hit it (Fable, then Opus) twice in one day — plan
rounds around it, and judge the pages that motivated a fix first so a limit
hit still leaves the informative verdicts.

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

## Stratified sample, round 1 (2026-09-02, composition fixes applied)

Before coding the three fixes, the pages behind each family were re-read from
`stops/NNN.json`, which corrected the round-0 diagnosis in four places:

- The "≥ 50 % of the balloon" assignment threshold is mathematically the same
  test as "centre inside" for axis-aligned boxes (a centre outside means one
  axis is under half covered, so the product is too), and the sample's
  gutter-hanging balloons with their centre outside every panel sit at 30–45 %
  overlap (titanes 036/041/043, ben-reilly 011). The threshold is therefore
  **a quarter** of the balloon: below it a caption is an orphan (arkham 036's
  "A diferencia de ti…" pair, at 0.3 % overlap), above it the balloon stays with
  its panel and the panel's stop grows over it.
- ruinas 015 and blacksad 029 never fetched bubbles (`needsBubbles` false):
  their panel areas *summed* to 0.91 and 0.88 while their *union* covers 0.795
  and 0.845. The gate now measures the union; ruinas 015 (Polaroid collage
  with captions in the gaps) and venomverse 009b flip to fetching bubbles,
  blacksad 029 stays a detector miss.
- titanes 037's order was not a window problem: the wide band's box overshoots
  the tall right panel's edge by 1.3 % of the page, more than the 1 % cut
  tolerance, so no vertical cut existed and the row fallback scrambled. A stop
  now blocks a cut only when the line enters it by more than 5 % of its own
  extent (floor 1 %).
- arkham 031a's five balloons form **one** cluster (every gap under 7 %), so no
  "windows merged into one" case existed; a true splash now gets its opener
  plus a window even for a single cluster (at whole-page scale no balloon is
  readable on the phone). Ben Reilly 023 and Venomverse 002/007a gain the same
  window; the judge scored all three `good`.

The four `GuidedTour` changes (grow over owned overhanging balloons, quarter
assignment threshold + single-cluster splash window, union coverage gate,
guillotine on anchors + relative cut tolerance; 26 unit tests) changed the
tour on 48 of the 129 pages (41 of them only by growing a panel stop over a
balloon). Only those 48, plus the never-judged Venomverse 008–021 and Blacksad
024, were re-judged: 58 Opus judges in five waves of 8–16 (≈55k tokens and
1–3 min each, no session limit hit). Round-0 verdicts were copied for the rest.

| comic | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|
| ben-reilly-01 | 23 | 24/24 | 23/0/0 | 9/14/0 | 20/3/0 | 9 | – |
| ruinas | 20 | 20/20 | 17/1/2 | 5/15/0 | 11/9/0 | 3 | 018 031 |
| arkham | 21 | 21/21 | 20/1/0 | 8/11/2 | 14/4/3 | 8 | 027 031b 033 037 |
| titanes | 20 | 16/20 | 19/1/0 | 5/12/3 | 15/4/1 | 5 | 035 038 043 044 |
| blacksad-1 | 20 | 18/20 | 19/1/0 | 5/11/4 | 16/2/2 | 5 | 013 015 017 029 |
| venomverse-001 | 24 | 24/24 | 24/0/0 | 9/15/0 | 20/3/1 | 6 | 004 |

Against round 0: gates 116 → 123 of 129 (the six left are `needsBubbles`
false pages whose gutter captions the gate compares against raw ML bubbles,
and the two Blacksad detector misses); order `bad` 9 → 2 (both ruinas 018 and
031, conversations painted across a canvas whose order is semantic — 031 was
`minor` in round 0 with the same offending stop and an identical tour, one-step
judge noise); pages with a `bad` 23 → 15; `missed_dialogue` on the sample
fell from 9 pages to 4 (arkham 027/031b/033 hand lettering, blacksad 017/029
and arkham 037/venomverse 004 missed panels are the survivors — all family 4–5).
Of the 41 re-judged pages with a round-0 verdict, 25 criterion scores
improved and 6 dropped one step (wide-tier `minor`s and one dead tap), none
good↔bad, none with a new offending stop. Ben Reilly is unchanged at zero
`bad` (p005 framing good → minor: the same six-caption window, "least zoomed
of the tour").

Family 1 (gutter balloons) is closed on the composition side: titanes 036 went
from four sliced balloons to none, and what the judge still flags there is the
neighbouring panel clipping the balloon's border (`minor` by policy). Family 2
is closed (arkham 036/031a, ruinas 015 all `harmony: good`). Family 3 is
closed (arkham 022/035, titanes 037 order `good`). The remaining `bad` pages
are families 4–5 (detector) plus the two semantic-order Ruinas paintings.

## Stratified sample, round 2 (2026-09-02, bubble gate removed)

Six of round 1's `bad` pages (titanes 035/038/044, blacksad 013/015/029) were
gutter balloons on dense grids where `needsBubbles` was false, so the panel
stop had nothing to grow over. The gate is gone: the bubble model now runs on
every page. That alone changed 42 tours (mostly grown stops) and surfaced two
more composition rules, each found by re-judging and reading the reasons:

- **Neighbour shrinks past a foreign balloon.** Growing *both* panels over a
  shared balloon fixed the slice but doubled the reading: titanes 036 went
  `harmony: bad` with four re-reads, titanes 044 stayed `framing: bad` because
  the non-owner still cut words. Now the owner grows and the neighbour shrinks
  past the balloon when it keeps its own balloons and ≥ 70 % of its area; the
  first version checked "keeps its own balloons" before growing over them, so a
  panel whose own balloon overhangs could never shrink (titanes 036 again) —
  grow over owned first, then trim.
- **Floating anchors.** blacksad 017's orphan caption pokes 22 % of its width
  past the left column and blocked the vertical cut, pushing the tall left
  panel after the right column (`order: bad`). Balloon-cluster anchors now
  tolerate 30 % intrusion where panels tolerate 5 %.
- **No window for an orphan a panel already shows whole** (ruinas 015 read one
  caption three times).

Judged in five waves (14 / 16 / 16 / 20 / 9 Opus judges); between waves only
the pages whose `stops/NNN.json` differed from the version their verdict saw
were re-judged (a `judged/` snapshot of stops per page, kept in the session
scratchpad, makes that exact).

| comic | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|
| ben-reilly-01 | 23 | 24/24 | 23/0/0 | 7/16/0 | 20/3/0 | 7 | – |
| ruinas | 20 | 20/20 | 18/0/2 | 6/14/0 | 11/9/0 | 4 | 018 031 |
| arkham | 21 | 21/21 | 20/1/0 | 8/11/2 | 14/4/3 | 8 | 027 031b 033 037 |
| titanes | 20 | 20/20 | 19/1/0 | 6/12/2 | 14/5/1 | 6 | 034 043 |
| blacksad-1 | 20 | 20/20 | 20/0/0 | 5/15/0 | 18/2/0 | 5 | – |
| venomverse-001 | 24 | 24/24 | 24/0/0 | 10/14/0 | 20/3/1 | 7 | 004 |

Against round 1: gates 123 → 129 of 129, pages with a `bad` 15 → 9,
`missed_dialogue` 4 → 3 pages (all hand-lettered Arkham paint). 18 criterion
scores improved, 9 dropped one step; the two new `bad`s are detector
artefacts — titanes 034's phantom corner "bubble" now gets a text-free window
(`framing: bad`, it was a `minor` dead tap), titanes 043's top-tier box that
overshoots into row 2. Ben Reilly's two drops are wide-tier `minor`s.

What is left, by cause: hand-lettered text without balloons (arkham 027/031b/
033), missed or overshooting panel boxes (arkham 037, titanes 034/043,
venomverse 004) and the two Ruinas paintings whose order is semantic. None of
it is composition. Next: the human calibration set, then the full-corpus
sweep.

## Second stratified sample (2026-09-03)

Six more comics chosen for the strata the first sample lacked, prepared with
`tools/eval/prepare.py` (which now renders PDFs through `pdftoppm`) and
judged by Opus with the policy rubric, ~20 story pages each: `one-piece-ace-01`
(modern manga, heavy SFX, RTL), `rapaces-1` (European colour album),
`spiderman-2099-01` (90s scan, page 001 is editorial and never judged),
`lok-tdsr` (PDF whose every page is a spread split into `a`/`b`, 005a–014b
judged), `superman-johns-1` (modern DC grid) and `androides-1` (painted album,
dark bleed pages). The first sample's twelve-comic union is the regression
gate: after every `GuidedTour` change all twelve are re-rendered and only pages
whose `stops/NNN.json` differ from the snapshot their verdict saw are
re-judged (`tools/eval/pending.py`); a tour that reverts to an archived round's
stops gets that round's verdict back (`tools/eval/restore.py`). The Opus
session limit was hit once, at ~120 judges in an evening (it resets at 03:00);
waves of 20 were otherwise fine.

Round 0 (rules as of the first sample) for the four comics judged before any
change — Superman and Androides were only judged after the fixes:

| comic | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|
| one-piece-ace-01 | 21 | 21/21 | 21/0/0 | 10/10/1 | 19/2/0 | 10 | 023 |
| rapaces-1 | 20 | 20/20 | 20/0/0 | 11/9/0 | 20/0/0 | 11 | – |
| spiderman-2099-01 | 19 | 20/20 | 17/1/1 | 5/12/2 | 12/4/3 | 4 | 008 009 016 |
| lok-tdsr | 11 | 40/40 | 11/0/0 | 7/4/0 | 10/1/0 | 6 | – |

The manga and the European album were clean on arrival — the eight rules from
the first sample carried over. The scan exposed one new family, and the rest of
the sample two more:

1. **Page-sized box from the scan's paper edge** (2099 008/009/016). The
   panel model returns a box around the whole page plus the real panels. The
   box became a container whose free strip was the blank left margin (a dead
   opening tap), and it "buried" every other container's remainder, so the top
   panel's balloons were orphaned into a window straddling two tiers. Fixes:
   a container's strip is dropped when it is a wordless sliver (short side
   under 15 % of the page), and a box enclosing the host no longer counts as
   burying its strip.
2. **Tall panel overlapping the column beside it** (LOK 013b, 2099 009). No
   guillotine cut exists, and the mutual-centre row test read the column by x
   alone (bottom-left before middle-left). An anchor spanning ≥ 80 % of its
   group's height with no vertical companion is set aside and slotted after the
   ordered column (before it in RTL / when it lies to the left).
3. **Detector fragments on painted pages** (androides 012b, 018): a wordless
   page whose one or two boxes cover under half of it is shown whole; a panel
   stop that the previous panel stop encloses (a caption box returned as a
   panel, swallowed when its neighbour grew) is redundant.

Three rules were tried and rejected on the regression gate:

- growing a panel stop over a foreign balloon it cuts by 10–25 % (fixed One
  Piece 023, but chained across manga grids where every balloon overhangs its
  neighbour: titanes 037/038/039 and venomverse 009b went `harmony: bad`) —
  kept as shrink-only, so 023 stays the one framing `bad` of the manga;
- dropping a wordless frame a third covered by other frames (the false band on
  2099 009) — it also drops legitimate wordless panels of staggered layouts;
- dropping every wordless container strip — Venomverse 009b's art strip holds
  a balloon the bubble model missed.

Final state, all twelve comics (Ben Reilly archived as `round7`, the first
sample's five as `round3`, the six new ones as `round1`):

| comic | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|
| ben-reilly-01 | 23 | 24/24 | 23/0/0 | 7/16/0 | 20/3/0 | 7 | – |
| ruinas | 20 | 20/20 | 18/0/2 | 6/14/0 | 11/9/0 | 4 | 018 031 |
| arkham | 21 | 21/21 | 20/0/1 | 7/12/2 | 14/4/3 | 7 | 027 031b 033 037 |
| titanes | 20 | 20/20 | 20/0/0 | 5/14/1 | 14/6/0 | 5 | 034 |
| blacksad-1 | 20 | 20/20 | 20/0/0 | 6/14/0 | 18/2/0 | 6 | – |
| venomverse-001 | 24 | 24/24 | 24/0/0 | 9/15/0 | 21/2/1 | 6 | 004 |
| one-piece-ace-01 | 21 | 21/21 | 21/0/0 | 9/11/1 | 19/2/0 | 9 | 023 |
| rapaces-1 | 20 | 20/20 | 20/0/0 | 11/9/0 | 20/0/0 | 11 | – |
| spiderman-2099-01 | 19 | 20/20 | 18/1/0 | 5/13/1 | 13/5/1 | 4 | 009 |
| lok-tdsr | 20 | 40/40 | 18/1/1 | 12/8/0 | 18/2/0 | 9 | 009a |
| superman-johns-1 | 21 | 21/21 | 21/0/0 | 6/15/0 | 19/2/0 | 6 | – |
| androides-1 | 23 | 23/23 | 22/0/1 | 5/15/3 | 14/7/2 | 5 | 014 015 016 023 |

252 judged pages, gates 274/274, 15 pages with a `bad`; the first sample went
9 → 8 (titanes 043 cleared, nothing regressed good↔bad). What is left, by
cause: hand-lettered paint the bubble model cannot see (arkham 027/031b/033),
missed, split or overshooting panel boxes (arkham 037, titanes 034, venomverse
004, 2099 009's false band, androides 014/015/016/023 on dark painted
bleeds), the two semantic-order Ruinas paintings, One Piece 023's two balloons
touching across a gutter (any cut slices one; the judge's ideal needs the
detector's box to stop at the gutter) and LOK 009a's inset straddling two
columns (identical tour scored `minor` in round 0, `bad` on re-judge — order
is not as deterministic as the Ben Reilly consistency run suggested). None of
it is composition. Not measured yet: the always-on bubble cost on the Fold.

## After the scorecard: the model question (deferred)

Only after the scorecard tells us **where** the heuristic actually fails do we
decide whether to train a model — and if so, the sensible target is a small,
on-device **reading-order model** (sequence the already-detected boxes), not an
end-to-end Guided-View model; framing/coverage stay rules. The judge verdicts +
the human calibration set become the order labels, distilled the way the bubble
student was (see `docs/speech-bubbles.md`, `docs/training.md`). Do not commit to
training before the scorecard shows order is the dominant failure and rules
cannot capture it.

## Third stratified sample (2026-09-03/04)

Six more comics for strata the first two samples lacked, prepared with
`tools/eval/prepare.py` and judged by Opus with the policy rubric: `zombillenium`
(European integral, 10–14 panels a page), `defensores-1` (70s Marvel, staggered
tiers and caption-heavy), `aliens-03` (80s/90s Dark Horse, full-width tiers),
`sonic-malos` (cartoon flat colour, stitched spreads), `shangri-la-01` (modern
digital manga, RTL, screentone and UI pages) and `vader-down-01` (widescreen
Star Wars, wordless splashes). `tools/eval/render.sh <comic>…` renders any list
of comics (reads the direction from `manifest.json`, forces the JBR JDK).

The stop-count gate was too tight for dense European pages: it is now
`1 … max(12, panels + 2)`, so a 14-panel Zombillenium page passes.

### Composition rules added in this round

Round 0 on the new comics plus a fix round on the twelve-comic regression gate
added three rules to `GuidedTour` (all in `docs/guided-view.md`):

1. **Duplicate panel boxes merge.** Two boxes overlapping by 70 % of the smaller
   with neither containing the other are one panel. This is what finally cleared
   Spiderman 2099 009 (`framing`+`harmony` bad → `minor`/`good`: the scan's
   full-width paper-edge band folds into the page-sized box instead of opening
   the tour on a dead strip) and Sueñan los Androides 023 (the page's two
   overlapping halves stopped being one giant stop). 0.5 was tried first and
   merged a legitimately overlapping tall panel with its neighbour (the LOK 013b
   layout), so the threshold is 0.7.
2. **Painted page.** Largest box ≥ 80 % of the page, the rest adding < 20 % of
   the page outside it and covering < half of it → whole-page establishing stop;
   and if there are at most two other boxes and none holds a balloon they are
   scraps of the painting and drop out. Cleared Arkham 027 (bad/bad/bad →
   good/good/bad, only the invisible hand-lettering left) and Androides 016
   (`framing` bad → `minor`). A box under 8 % of the page that balloons already
   cover by 70 % does not count towards those two — it is a caption the panel
   model also fired on.
3. **Readable window bounds.** Balloons cluster only while the cluster stays
   within the minimum window height (0.30); two windows merge only while the
   merged view stays under 0.45; and a window stops growing over a foreign
   balloon once it would pass 0.45 tall or 0.6 wide.

### Regressions this round taught us (all fixed)

- Dropping *every* wordless fragment on a painted page turned One Piece 026 (a
  wordless five-panel manga splash) into one whole-page stop → cap at two
  fragments.
- Applying the "balloon-shaped box is not a panel" filter globally orphaned
  gutter captions whose box was the only thing hosting them: Blacksad 017 and
  Androides 026 went `harmony: bad`. The filter now only decides the painted-page
  scraps test; everywhere else a caption-shaped box stays a panel.
- Bounding clusters without bounding `mergeOverlapping` left Androides 029 with
  one band half the page tall, which `dropRedundant` then removed against the
  establishing stop — eight captions with no window at all. Both joins are
  bounded now.
- Making a balloon that touches exactly one panel belong to it (to stop
  Zombillenium 014's giant orphan window) contradicts the quarter-overlap rule
  from the first sample and broke two of its unit tests. **Rejected** — do not
  retry without revisiting Arkham 036.

### State at the end of the round

370 judged pages over eighteen comics, gates 398/398, 20 pages with a `bad`:

| comic | judged | gates | order g/m/b | framing g/m/b | harmony g/m/b | all good | pages with a bad |
|---|---|---|---|---|---|---|---|
| aliens-03 | 20 | 20/20 | 20/0/0 | 9/11/0 | 17/2/1 | 9 | 027 |
| androides-1 | 23 | 23/23 | 22/0/1 | 5/17/1 | 14/6/3 | 5 | 014 015 027 |
| arkham | 21 | 21/21 | 21/0/0 | 8/10/3 | 13/5/3 | 7 | 027 031b 033 037 |
| ben-reilly-01 | 23 | 24/24 | 23/0/0 | 10/13/0 | 20/3/0 | 9 | – |
| blacksad-1 | 20 | 20/20 | 20/0/0 | 6/14/0 | 18/2/0 | 6 | – |
| defensores-1 | 18 | 20/20 | 16/1/1 | 6/11/1 | 12/3/3 | 6 | 030 032 049 |
| lok-tdsr | 20 | 40/40 | 18/1/1 | 13/7/0 | 17/3/0 | 9 | 009a |
| one-piece-ace-01 | 21 | 21/21 | 21/0/0 | 9/11/1 | 18/3/0 | 9 | 023 |
| rapaces-1 | 20 | 20/20 | 20/0/0 | 11/9/0 | 20/0/0 | 11 | – |
| ruinas | 20 | 20/20 | 18/0/2 | 6/14/0 | 12/8/0 | 5 | 018 031 |
| shangri-la-01 | 20 | 20/20 | 19/1/0 | 7/13/0 | 15/5/0 | 6 | – |
| sonic-malos | 19 | 20/20 | 19/0/0 | 6/12/1 | 17/1/1 | 6 | 016 |
| spiderman-2099-01 | 19 | 20/20 | 18/1/0 | 4/15/0 | 14/5/0 | 4 | – |
| superman-johns-1 | 21 | 21/21 | 21/0/0 | 6/15/0 | 19/2/0 | 6 | – |
| titanes | 20 | 20/20 | 20/0/0 | 5/14/1 | 14/6/0 | 5 | 034 |
| vader-down-01 | 21 | 24/24 | 21/0/0 | 7/14/0 | 20/1/0 | 7 | – |
| venomverse-001 | 24 | 24/24 | 24/0/0 | 10/14/0 | 21/2/1 | 7 | 004 |
| zombillenium | 20 | 20/20 | 19/1/0 | 7/12/1 | 14/4/2 | 7 | 014 021 |

Shangri-La, Vader Down and Rapaces are clean; Spiderman 2099 and Blacksad have
become clean. The 20 `bad` pages split into two groups.

**Still composition — the next fixes.** All five are the same shape: a stop that
is neither a panel nor a tight reading window, produced by a window growing or
an orphan cluster sized to the page-scale minimum on a page of small panels.

- **Overlapping windows on one panel** (defensores 030 — stops 3/4/5 share half
  their width each and stop 4 carries no new text; sonic 016 — a staircase of
  balloons in one tier where every sub-window cuts a neighbour, judge's ideal is
  a single tier stop; androides 027 — one window then a 0.59-tall catch-all).
  A window that adds no balloon of its own, or a set of windows on one panel that
  pairwise share most of their area, should collapse back to the panel.
- **Catch-all region on a dense grid** (zombillenium 014's stop 6 spanning four
  panels, zombillenium 021's stop 6, defensores 032's stop 2, defensores 049's
  stop 6). All come from an orphan balloon whose window is sized to
  0.42 × 0.30 — page-scale on a page whose panels are 0.05–0.15 of the page.
  The minimum window size should scale with the page's panels, not be a constant.
  (Do **not** solve it by attaching such a balloon to the single panel it
  touches: that rule was tried and rejected, see above.)
- defensores 049 also wants a panel stop to grow *up* to the top of a caption it
  clips, instead of a separate band covering it.

**Detector, unchanged from the previous round.** Hand-lettered paint the bubble
model cannot see (arkham 027/031b/033), missed or overshooting panel boxes
(arkham 037, titanes 034, venomverse 004, zombillenium 014's missing row-3
panel), a balloon the model never returned (aliens 027 — `bubbles` is empty for
that page and the only spoken line falls in the gap between two stops), false
positive bubbles that become text-free stops (arkham 031b stop 3, titanes 034
stop 6), the two semantic-order Ruinas paintings, One Piece 023's two balloons
touching across a gutter, LOK 009a and androides 015 (order judged `bad` on a
tour whose individual crops the judge calls clean — the noisiest criterion).

A cheap detector-side fix worth trying: `SpeechBubbles.outlined` already knows
how to extract text lines (`extractText`), but the visualizer and `PageLoader`
call it with `extractText = false`, so a white blob with no text inside is
indistinguishable from a balloon. Gating a detected bubble on "holds at least one
text line" would kill the text-free windows on arkham 031b and titanes 034; it
costs a pass over each bubble's interior and also touches the enlarged-bubbles
feature, so it needs its own measurement.

## Fourth round — composition plateau, and what it taught us (2026-09-04)

No new comics. The round re-rendered and re-judged the eighteen-comic gate after
each `GuidedTour` change, chasing the two fixes the previous round queued. It
ended **flat**, and that is the round's real finding.

| | start | end |
|---|---|---|
| judged pages | 370 | 372 |
| gates | 398/398 | 398/398 |
| **cost per page** | 1.016 | 1.036 |
| pages with a `bad` | 20 | 21 |

Eight pages were fixed (Defensores 030 and 049, Sonic 016, Zombillenium 014 and
021, Androides 014 and 015, Arkham 033, LOK 009a) and seven broke (Androides 016
and 027, Arkham 035 and 036, Ruinas 028, Spiderman 2099 005/008/019). The rules
below are worth keeping on their merits — each fixes a family the judge named in
prose — but **the corpus score did not move**, and the next section explains why
that number cannot be trusted at this resolution anyway.

### The scorecard now leads with a cost, not a page count

`scorecard.py` emits `cost` and `costPerPage`: `good` 0, `minor` 1, `bad` 3,
summed over the three criteria of every judged page. "Pages with a `bad`" is a
max over three criteria — one criterion sliding from `minor` to `bad` on one page
moves it — so it is far too brittle to steer by. The cost is the headline in
`summary.py`; the bad list stays as a worklist, not a score.

### The judge's noise is the same size as the deltas we were chasing

Two pages were re-judged this round on **byte-identical** stop lists and changed
verdict:

- `shangri-la-01:027` — `framing: bad` then `framing: minor`, with the *same*
  observation in prose both times ("one huge band covering four panels").
- `sonic-malos:016` — `framing: minor` then `framing: bad`.

Both are policy-boundary flips, not perception failures: the judge saw the same
thing and graded it differently. Several other pages (Zombillenium 021,
Androides 014) flip-flopped across rounds on tours that barely changed.

**So: never accept or reject a `GuidedTour` change on a delta of one or two
pages.** `consistency.py` already computes repeat-run agreement; run the same
sample three times into `eval/_variance/run{1,2,3}/` and use the pairwise
agreement as the error bar before reading any round's result. Until that number
exists, treat anything under roughly five pages of movement as noise.

### Rules kept

1. **Dead-tap pass** (`withoutDeadTaps`). After ordering, a stop that shows at
   least one balloon, shows no balloon whole that the other stops do not already
   show whole, and is ≥ 90 % covered by them, is dropped. A stop with no balloon
   at all is never dead — that is art. The establishing opener neither covers nor
   is dropped. Cleared Defensores 030 (`harmony` bad → all good), Sonic 016 and
   Zombillenium 014's stripped panel.
2. **The minimum reading window scales with the page.** 0.42 × 0.30 became a
   ceiling; the floor is the **median panel's** width and height when smaller.
   On a fourteen-panel European page the page-scale minimum was turning one
   caption into a band across four panels.
3. **Orphan windows clamp to the panel they overlap most**, grown to include
   their cluster, instead of to the page — Zombillenium 014's tai-chi caption
   stopped biting into the row above.
4. **A caption in the gutter belongs to the panel across it**: no panel covers a
   quarter of it, it is within 3 % of the page above or below one, at least 80 %
   of it lies in that panel's column, nearest wins — and only when the best
   overlapping panel does not already cut a tenth of it. That last guard matters:
   without it the rule reached balloons that merely brushed a panel and made
   stops swallow their neighbours. Cleared Defensores 049 (three bads → none).

### Rules tried and rejected

- **Collapse a panel's overlapping windows back to the panel.** Killed every
  reading window on painted pages: Androides 029 went to a single stop, the exact
  regression the previous round had fixed.
- **Unbounded window growth over a crossing balloon** (removing the 0.45 × 0.6
  cap). A 2-for-2 wash: it cleared a sliced caption on Androides 016/027 and cost
  Androides 017 and Blacksad 029, where a window swallowed the tier beside it.
  The cap stays.
- **Dead taps must have two covering stops** (or one that encloses them).
  Intended to stop a panel dying because a neighbour overshot into it; it did not
  fix that case and regressed Sonic 016.

### A crash, not a taste question

`sized()` clamped with `coerceIn(bounds.left, bounds.right - width)`, and float
rounding can make that maximum a hair below the minimum:
`IllegalArgumentException: Cannot coerce value to an empty range`. It took a
tighter `bounds` (rule 3) to expose it, but the same page would have thrown in
the app. Now clamped with `coerceAtMost(...).coerceAtLeast(...)`, which cannot
throw.

### Where the remaining bads live

Composition has plateaued: of the 21 pages with a `bad`, the large majority are
**detector** failures the geometry cannot reach — hand lettering the bubble model
never returns (Arkham 027/031b/033/037), panels the model misses or overshoots
(Venomverse 004, Shangri-La 027, Zombillenium 014's row-3 panel), false-positive
bubbles that become text-free stops (Titanes 034), and the two semantic-order
Ruinas paintings. The cheapest next experiment is still the **bubble text gate**:
`SpeechBubbles.outlined` can already extract text lines, and gating a detection on
"holds at least one text line" would kill the text-free stops. It is measurable
against the bubble ground truth without spending a judge at all.

## The judge's error bar (2026-09-04)

The fourth round ended flat and caught two pages flipping verdict on byte-identical
tours, so before tuning `GuidedTour` again we measured the judge itself. Twelve pages
spanning the verdict spectrum — four all-`good`, three `minor`-only, five carrying a
`bad` — were judged **three times** with the same rubric, the same prompt shape and the
same inputs, into `eval/_variance/run{1,2,3}/`. `judged/NNN.json` was confirmed
byte-identical to `stops/NNN.json` on all twelve first, so the corpus's own verdicts are
a fourth judging of the same input and are quoted below as a cross-check.

### Agreement

| criterion | unanimous (3 runs) | pairwise (3 runs) | pairwise (4 samples) |
|---|---|---|---|
| order | 9/12 | 0.83 | 0.88 |
| framing | 6/12 | **0.67** | **0.64** |
| harmony | 12/12 | 1.00 | 0.96 |

Offending-stop lists identical across all three runs: 22/36.

**`framing` is the noisy one.** Two judgings of the same page agree on framing barely two
times in three. `harmony` is effectively deterministic, `order` is solid. Every
disagreement was one step (`good`↔`minor` or `minor`↔`bad`); nothing ever jumped
`good`→`bad`. The judge sees the same thing every time and grades it differently — a
policy-boundary problem, not a perception one.

### What that does to the score

Cost per page over the same twelve pages, on identical input:

| run1 | run2 | run3 | corpus |
|---|---|---|---|
| 2.083 | 2.333 | 1.833 | 2.250 |

And "pages with a `bad`" out of twelve: **4, 3, 3, 5**. That metric swings by two pages in
twelve — a two-thirds relative swing — with the algorithm held completely still. Demoting
it to a worklist in the fourth round was right.

The noise is not spread evenly. Mean within-page variance of cost is **0.222 on pages the
corpus scores clean** and **1.156 on pages carrying a `bad`** — five times higher. The
pages we tune on are exactly the pages the judge cannot grade twice the same way.

### The rule: what a round has to beat

Pooled within-page sd of cost is σ = 0.782 on this sample. The sample is deliberately
skewed hard (5/12 carry a `bad`, against 21/372 in the corpus); reweighted to the corpus
mix it is **σ = 0.52**. A round re-judges only the k pages whose stops changed, and each
of those contributes two independent judgings, so the noise on the round's total cost
delta is σ√(2k):

> **A round that re-judges k pages must move the total cost by at least 1.96·σ·√(2k)
> ≈ 1.03·√(2k) points before the movement means anything.**

| k pages re-judged | 10 | 20 | 30 | 50 |
|---|---|---|---|---|
| minimum believable move (cost points) | 4.6 | 6.5 | 8.0 | 10.3 |

Applied to the fourth round: it moved the corpus from 1.016 to 1.036 cost per page over
372 pages — **7.4 cost points, in the worse direction** — with `eval/changed.json` listing
56 changed pages. The threshold at k = 56 is 10.9. **The round did not regress; it did
not do anything measurable at all.** Its four kept rules stand on the families the judge
named in prose, not on the score.

Two corollaries for every round from here:

- Do not read a per-page delta at all. A single page moving from `minor` to `bad` is the
  judge's coin landing the other way about a third of the time.
- A rule whose whole case rests on `framing` needs more evidence than one whose case rests
  on `order` or `harmony`.

### Pages that are genuinely bad, and pages that only look it

Stable across all four judgings, so real: `ruinas:028` (`order: bad` every time — the
semantic-order painting) and `spiderman-2099-01:008` (`harmony: bad` every time).
Disputed, so not worth tuning against: `titanes:034` framing came out `bad / bad / good`,
and `arkham:035` `bad / minor / minor`. Both sit on the current worklist of 21 pages with
a `bad`; on this evidence that list is somewhere around a third softer than it reads.

`tools/eval/consistency.py eval/_variance` reproduces the agreement table; the cost and
threshold numbers are derived from the same three runs.
