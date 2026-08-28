# ML runtime (ONNX Runtime minimal build)

Both detectors (`docs/guided-view.md`, `docs/speech-bubbles.md`) run through
ONNX Runtime 1.20.0. The Maven `onnxruntime-android` AAR ships a 17.6 MB
`libonnxruntime.so` with every kernel and execution provider; the app instead
bundles a **minimal build with only the operators the two YOLO26n graphs use**,
built by `tools/ort/build_aar.sh` into `app/libs/onnxruntime-minimal-1.20.0.aar`
(2026-08-28).

## Models

A minimal build loads only ORT-format models, and converting with the stock
`convert_onnx_models_to_ort` constant-folds the fp16 `Cast` that keeps the
weights small, so `tools/ort/make_ort_models.py` does it in three steps:

1. optimise the fp32 ONNX offline at `ORT_ENABLE_EXTENDED` (operator fusions
   such as `QuickGelu` for SiLU and `FusedMatMul`; not `ALL`, whose NCHWc
   layout transform is x86-only),
2. store every Conv weight as fp16 behind a `Cast` node (the fp16 weights give
   the same boxes as fp32 on the 147-page ground truth; weight-only int8 lost
   up to 0.04 F1 on single series and was rejected),
3. save as `.ort` with optimisations disabled, so the casts stay in the graph
   and run once per inference (~2 ms).

`assets/models/{panels,bubbles}.ort` are 5.2 MB each (9.8 MB fp32 ONNX before),
stored uncompressed and copied to `filesDir` on first use by `OnnxBoxDetector`.
`tools/ort/required_operators.config` is generated from the two `.ort` files
(`onnxruntime.tools.ort_format_model.create_config_from_models`).

## Rebuilding the AAR

`tools/ort/build_aar.sh <work dir>` creates a venv (onnxruntime 1.20.0 tools,
cmake 3.31, ninja), clones `v1.20.0` with submodules, refreshes the Eigen
archive hash in `cmake/deps.txt` (GitLab re-packs the zip, so the pinned sha1
no longer matches), and runs `build_aar_package.py` with
`tools/ort/aar_build_settings.json`: arm64-v8a only, minSdk 30, `--minimal_build
--disable_rtti --enable_lto --build_java`, no NNAPI/XNNPACK (XNNPACK made no
difference on these models), `MinSizeRel`. About 10 minutes on an M-series
Mac. The JNI layer resolves `ai.onnxruntime.*` by name, so
`app/proguard-rules.pro` keeps that package.

When a model changes: re-run `make_ort_models.py` on the fp32 exports, and if
`required_operators.config` changes, rebuild the AAR. Bump
`DETECTIONS_VERSION` in `PanelDetector.kt` either way.
