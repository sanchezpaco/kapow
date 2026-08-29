# Training the detectors

How the two ML models bundled in `app/src/main/assets/models/` are produced,
scored and converted, so that anyone can retrain them from a corpus of their
own. Everything lives in `tools/training/`; the app-side runtime is described
in `docs/ml-runtime.md`, what the models are used for in
`docs/guided-view.md` (panels) and `docs/speech-bubbles.md` (bubbles).

## The two detectors

| Model | Origin | Training here | Shipped as |
|---|---|---|---|
| Panels | `leoxs22/manga-panel-detector-yolo26n` (Hugging Face, Apache-2.0), a YOLO26n fine-tuned on manga frames | none — used as published | `panels.ort`, 5.2 MB |
| Bubbles | YOLO26n **student v4**, distilled from the `ogkalu/comic-speech-bubble-detector-yolov8m` teacher (Apache-2.0) on the private corpus | this document | `bubbles.ort`, 5.2 MB |

Both graphs are exported end-to-end (`nms=True`): input `images`
`[1, 3, 640, 640]` letterboxed on grey 114, output `[1, 300, 6]` =
`x1, y1, x2, y2, score, class`. The app keeps class 0 (`frame` / `bubble`)
with score ≥ 0.35 for panels and ≥ 0.25 for bubbles.

The bubble student is a chain of fine-tunes: v1 from `yolo26n.pt` on the
teacher's pseudo-labels of 11 comics, v2 on 16 comics, v3 on the same data
plus 7 hand-added Defensores captions, v4 on 31 series (4,723 pages) plus 57
reviewed corrections. Re-distilling for a new style is a fine-tune from the
previous student, never a restart.

## Corpus

The corpus is **private and not distributed**: it is the owner's own comics,
copyrighted material. The repository ships only what a corpus of your own
needs to reproduce the numbers — the scripts, the box and silhouette
annotations (`tools/training/gt/`, page file names only, no pixels) and the
pinned environment.

Layout expected by `extract.sh`, under `comics/` at the repository root
(git-ignored; override with `KAPOW_COMICS`):

```
comics/
  Aliens Omnibus 03.cbz          -> series "aliens_omnibus_03"
  benreilly/                     -> series "benreilly" (every .cbr/.cbz inside)
    Ben Reilly Spider-Man #01.cbr
    Ben Reilly Spider-Man #02.cbr
```

One top-level entry is one series: a `.cbz`/`.cbr` file, or a folder whose
archives are all issues of one series. The series name is the entry name
slugified (lower-case, non-alphanumerics to `_`). The per-series cap in
`build.py` (250 pages) is what keeps a 1,900-page omnibus from dominating the
set, so group issues by series rather than dropping them all at the top
level. `unar` must be installed (`brew install unar`); CBZ and CBR (RAR4/RAR5)
both work. To regenerate the ground-truth pages with `pages.py`, the series
folders must carry the names used in `gt/sample.json` (`aliens`,
`benreilly`, `doom`, …; a trailing number in a sample key such as
`benreilly01` maps to the folder without it).

All intermediate data goes to `tools/training/work/` (override with
`KAPOW_TRAINING_WORK`), which must be git-ignored:

```
work/
  raw/<series>/        extracted pages
  data/                YOLO dataset (images/, labels/, data.yaml)
  pages/               ground-truth pages at 2000 px + detection dumps (*.json)
  models/              teacher.pt, panels.onnx, bubbles.onnx, bubbles_<tag>.{pt,onnx}
  runs/<name>/         ultralytics training runs
  review/, sheets/     contact sheets
```

## Environment

```
cd tools/training
python3.13 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
```

`requirements.txt` pins what v4 was trained with (ultralytics 8.4.127, torch
2.13.0, onnx 1.22.0, onnxslim 0.1.96, Python 3.13.5 on macOS with the MPS
backend). Training on another backend: set `KAPOW_TRAINING_DEVICE` (`cpu`,
`0` for CUDA); it is read by `label.py` and `train.sh`. The `.ort`
conversion in `tools/ort/` uses its own venv with onnxruntime 1.20.0 (the
runtime version the app bundles) — keep the two apart.

Models to stage in `work/models/` before starting:

- `teacher.pt`: `comic-speech-bubble-detector.pt` from the
  `ogkalu/comic-speech-bubble-detector-yolov8m` Hugging Face repository
  (`huggingface-cli download ogkalu/comic-speech-bubble-detector-yolov8m
  comic-speech-bubble-detector.pt`).
- `panels.onnx`: `manga_panel_detector_fp32.pt` from
  `leoxs22/manga-panel-detector-yolo26n`, exported with
  `YOLO(pt).export(format='onnx', imgsz=640, nms=True)`. *To be confirmed:*
  the export call for the panel model was not logged; this is the same call
  `post_train.sh` uses for the bubble model and yields the `[1, 300, 6]`
  output the app expects.
- The previous bubble student (`bubbles_v4.pt`, the `.pt` next to the
  shipped `.ort`) when fine-tuning rather than starting from `yolo26n.pt`.

## Pipeline

All commands run from `tools/training/` with the venv active.

1. **Extract the corpus** — `./extract.sh` unpacks every entry of `comics/`
   into `work/raw/<series>/` and prints the page count per series.
2. **Ground-truth pages** — `python pages.py` renders the 147 annotated pages
   listed in `gt/sample.json` to `work/pages/<series>__<stem>.jpg` at 2000 px
   on the long side. Needed for scoring only.
3. **Build the dataset** — `python build.py` walks `work/raw/`, drops every
   page whose file stem is in the box ground truth (held out by name), caps
   each series at 250 evenly spaced pages, resizes to 1024 px and writes
   `work/data/{images/{train,val}, data.yaml}` with an 8 % validation split
   (seed 0). v4: 4,723 pages from 31 series.
4. **Teacher pseudo-labels** — `python label.py [teacher.pt]` runs the YOLOv8m
   teacher at 640 px, confidence 0.25, and writes YOLO label files for class
   0 (`bubble`) only. About 20 minutes on an M1 Pro for 4,700 pages; v4 got
   29,546 training boxes. Pages already labelled are skipped, so the script
   resumes.
5. **Beyond-the-teacher corrections** (optional, what made v3 and v4 better
   than the teacher) — `python review.py <series> <pages>` draws the
   pseudo-label boxes on a stratified sample of that series' training pages
   into `work/review/<series>/` and writes one prompt per 25 pages
   (`work/review/<series>_<n>_prompt.md`) asking a vision model to list only
   *missed* balloons and captions as `[l, t, r, b]` fractions. The answers
   (`<series>_<n>_corrections.json`) are appended to the labels with
   `python apply_corrections.py work/review/*_corrections.json`. The
   corrections used for v3 and v4 are kept in `gt/` and can be re-applied to
   a dataset built from the same corpus (they address training files by name).
6. **Train** — `./train.sh <base.pt> <name>`, e.g.
   `nohup ./train.sh work/models/bubbles_v4.pt v5 > work/train_v5.log &`.
   This is `yolo detect train imgsz=640 epochs=60 batch=16 seed=0
   deterministic=True` with everything else at the ultralytics 8.4 defaults
   (mosaic 1.0, close_mosaic 10, fliplr 0.5, scale 0.5, lr0 0.01, AMP); the
   run lands in `work/runs/<name>/`. v4 was trained from
   `bubbles26n_v3.pt`; ~4.5 min per epoch on an M1 Pro (MPS), **4.6 h for
   60 epochs**, val mAP50 0.992 / mAP50-95 0.932. v1–v3 on ~2,500 pages
   took ~2.4 h.
7. **Export and score** — `./post_train.sh <name> <tag>` exports
   `best.pt` to ONNX (`imgsz=640 nms=True`, opset 20, onnxslim; 9.4 MB fp32),
   copies the pair to `work/models/bubbles_<tag>.{pt,onnx}` and
   `work/models/bubbles.onnx`, runs `run_onnx.py` over the ground-truth pages
   into `work/pages/student_<tag>.json` and prints the score table.
   (v3 was exported with `opset=17 simplify=True`; v4 with the defaults —
   the app runs both.)
8. **fp16 check** (optional) — `python quant_fp16.py work/models/bubbles.onnx
   work/models/bubbles_fp16.onnx`, then `python run_onnx.py bubbles_fp16.onnx`
   and `score.py`: the fp16 Conv weights must give the same boxes as fp32
   (they did for v4 on every page). Weight-only int8 was rejected: same global
   F1, up to −0.04 on single series.
9. **ORT format** — copy `panels.onnx` and `bubbles.onnx` into one folder and
   run `python tools/ort/make_ort_models.py <folder>` from the `tools/ort`
   venv (`docs/ml-runtime.md`): offline optimisation at `EXTENDED`, Conv
   weights to fp16 behind `Cast` nodes, saved as `.ort` with optimisations
   disabled. 9.8 MB → 5.2 MB each. If
   `tools/ort/required_operators.config` changes, rebuild the AAR.
10. **Install** — copy the two `.ort` files to
    `app/src/main/assets/models/{panels,bubbles}.ort` and bump
    `DETECTIONS_VERSION` in `PanelDetector.kt` (`panels-v1+bubbles-v4` →
    `…+bubbles-v5`) so cached Room detections are recomputed.

Steps 1–4 are needed once per corpus change; 6–10 per model.

## Scoring

`score.py <dump name>…` matches boxes against `gt/gt_<series>.json` (147
pages, 21 series, 7 pages each; panels at IoU ≥ 0.5, bubbles at IoU ≥ 0.3,
largest predictions first) and prints precision, recall and F1 per series and
in total, plus median milliseconds when the dump carries them. Dumps are
`work/pages/<name>.json` in the format `run_onnx.py`, `run_yolo.py`,
`MlDetectorBenchmark` (device) and `HeuristicRectsDump` (JVM) all write:
`[{"file", "width", "height", "panels": [[l,t,r,b]…], "bubbles": […],
"panels_ms", "bubbles_ms"}]`, normalised to the page.

- `python run_onnx.py [bubbles model file in work/models]` runs the panel and
  bubble ONNX graphs over `work/pages/` with the app's letterbox and
  thresholds, writing `work/pages/onnx.json` (rename it before scoring).
- `python run_yolo.py <hf repo or local .pt> <file in repo> <tag> [imgsz]
  [conf]` scores any ultralytics checkpoint the same way (used to pick the
  teacher and the panel model, and for the `teacher` row:
  `run_yolo.py work/models/teacher.pt - teacher 640`).
- `python draw.py gt|<tag>` writes contact sheets per series to
  `work/sheets/` for eyeballing.

Expected baselines on the 147-page ground truth:

| Dump | Panel F1 | Bubble P / R / F1 |
|---|---|---|
| teacher (YOLOv8m @640) | — | 0.96 / 0.97 / 0.967 |
| student v3 | 0.90 | 0.964 / 0.971 / 0.968 |
| **student v4** | 0.90 | 0.965 / 0.977 / **0.971** |
| pixel heuristic (77 pages) | 0.77 | 0.78 |

The panel column is the `leoxs22` model (0.93 on the original 77-page
sample, 0.90 on the 147-page set because the later series are Western pages
with more open panels); the app adds heuristic panels on top
(`docs/guided-view.md`). Blacksad reads 0.88 for every bubble model: the
ground-truth boxes are offset on a few tall balloons, not a detector miss.
Manga series under 0.93 are where the ground truth itself under-counts.

### Silhouette ground truth

`gt/silhouettes.json` holds 115 hand-validated bubble outlines on seven pages
of six series (`docs/speech-bubbles.md` → Silhouette ground truth). Each
entry: page file, series, the ML box and the polygon, all normalised to the
uncropped page. It scores the app's *outliner*, not the model, and is run by
the `SpeechBubbleVisualizer` unit test on a folder of those pages with a
`boxes.json` next to them:

```
python detect_boxes.py <folder of page JPEGs> [model.onnx]   # writes <folder>/boxes.json
KAPOW_PANEL_VIZ_DIR=<folder> ./gradlew :app:testDebugUnitTest \
    --tests '*SpeechBubbleVisualizer*' --rerun -i              # writes out/silhouettes.json
```

Expected with v4 boxes: mean IoU **0.881**, 78/115 ≥ 0.9, 0 box fallbacks
(v3: 0.879, 76/115); corpus uncovered area 0.2212. The polygons came from
`silhouettes.py candidates <root>` (a classical pipeline with hand override
tables at the bottom of the script; `<root>/{corpus,doomviz}/` hold the
pages and their `boxes.json`) reviewed on the contact sheets of
`silhouettes.py sheet <root> <candidates.json> <prefix>`. Rerun it only to
add outlines; the validated file is the reference.

## Hardware and time

Everything was done on a MacBook Pro M1 Pro (16 GB) with the MPS backend:
labelling ~20 min, training 4.6 h for 60 epochs on 4,723 pages (2.4 h on
2,500), ONNX export seconds, `run_onnx.py` on 147 pages ~10 s (29 ms per
page for the bubble model, 32 ms for panels, CPU). Training holds
`work/raw/` (7.9 GB for the v4 corpus) plus `work/data/` (~1 GB). Run the
training under `nohup`; ultralytics resumes with `resume=True` on
`work/runs/<name>/weights/last.pt` if interrupted.

## Licensing

The bubble student is trained with Ultralytics, which is AGPL-3.0; the
repository is published under AGPL-3.0 for that reason (`LICENSE`). The app
ships only the weights, converted to ORT format and executed by ONNX Runtime
(MIT) — no Ultralytics code runs on the device. Both model sources are
Apache-2.0. The training corpus is copyrighted comics owned by the author and
is not part of the repository; the ground-truth annotations reference pages
by file name only.
