# Improvement ideas

Ideas worth trying that are not scheduled in `ROADMAP.md`. Each entry says what
we measured, what we would change and how we would know it worked. Numbers date
from 2026-08-27.

## Where the numbers stand

| Metric | Value |
|---|---|
| Bubble box F1, 98-page ground truth (student v3, on device) | 0.96 (teacher 0.96, heuristic 0.78) |
| Bubble box F1, worst series (Defensores, 1970s halftone) | 0.88 (teacher 0.90) |
| Panel box F1 | 0.93 (heuristic 0.77) |
| Uncovered area after enlarge, Ben Reilly #01 (271 bubbles) | 0.0265 page-areas |
| Uncovered area, 98-page corpus (870 bubbles) | 0.2268 |
| Uncovered area, Doom #7/#9 (70 bubbles) | 0.0036 |
| Silhouette IoU, 115 hand-validated outlines (`gt/silhouettes.json`) | mean 0.879, 76/115 ≥ 0.9, 0 box fallbacks; series 0.83-0.91 |
| LaMa inpainting (spike) | 70-90 % judged good, ~4 s per crop — not shipped |

Box F1 at IoU ≥ 0.5 says whether a box exists, not whether it is tight; the
Doom last-line bug was invisible to it.

## 1. Silhouette ground truth (done 2026-08-27 — see ROADMAP Phase 5)

115 outlines on six series, scored per series by `SpeechBubbleVisualizer`
(`out/silhouettes.json`). The worst non-scan cases are one family: the paper
body leaks into pale art beside the rim and runs to the box (Doctor Muerte
0.36/0.40, Ben Reilly 0.46, Doom 009-004 0.43-0.73). That is a new idea 3b:
stop the body at the rim ink instead of the box when the box side it reaches
is pale art, the mirror image of the Doom short-side growth.

## 2. Per-bubble paper colour for old scans (done 2026-08-27 — see ROADMAP Phase 5)

`solidPaper` used a global threshold (luminance ≥ 228, or cream) tuned for
digital pages; scanner grain on 224-231 paper produced patchy bodies that
fell back to the box rectangle. `PaperTone` now estimates the paper per ML
box and classifies scanned paper relative to it. Spiderman 2099 fallbacks
17 → 0, IoU 0.758 → 0.879; Defensores 0.898 → 0.902; digital series
unchanged. Its two remaining weak outlines belong to idea 3b.

## 3. Box-relative rescue for text lines fused with the rim

When the first or last line of tightly lettered text touches the bubble
outline, the paper beyond it is walled off (Ben Reilly p.17 "Y HOY N /
UMANO", Spiderman 2099 "AAAARON"), and the copy loses part of the line.
`grownTowardsShortSides` handles the case where the ML box says the bubble is
taller than the body; a similar rule could close notches whose mouth lies on a
text row inside the box. Measurable against the ground truth since idea 1.

## 4. Student v4 retrain (scheduled, low urgency)

Expected F1 gain is small (0.96 already). What it would buy is tighter boxes
on unseen caption styles (Doom) and fewer near-duplicate boxes. Concrete
case: Spiderman 2099 Vol1 04 p.7, caption "POR CULPA DE ESE SPIDERMAN" gets a
second box over its last two lines (IoU too low for the NMS merge), and that
sub-bubble's copy hides the line "A POR MÍ. DE HECHO" above it — the outline
itself is complete, before and after idea 2. Do it after
idea 1 so box tightness can be scored, not just presence.

## 5. Cheap fill of the vacated crescents

Uncovered area is already residual on digital pages (0.0265 / 271 bubbles).
Before reaching for LaMa, try classical inpainting (border-colour average or
Telea) on the thin crescents; measure on the 98-page corpus with the existing
visualizer crops and only escalate to a tiny model if the crescents look wrong.

## 6. Realistic ceiling

Touching bubbles, text glued to the rim and borderless sound effects are
ambiguous even for a human annotator. Aim for ~95 % correct silhouettes on
digital pages and ~85-90 % on scans; digital is close already.
