# Docs

How Kapow works, one concern per file.

- [architecture.md](architecture.md) — layers, modules, state flow, DI
- [foldable.md](foldable.md) — postures, adaptive reading modes, position continuity
- [reading-modes.md](reading-modes.md) — the reading surfaces and how they render
- [file-formats.md](file-formats.md) — CBZ / CBR / PDF loading pipeline
- [guided-view.md](guided-view.md) — automatic panel detection and navigation
- [speech-bubbles.md](speech-bubbles.md) — enlarged speech bubbles toggle (detection, layout, overlay)
- [training.md](training.md) — how the panel and bubble detectors are trained, scored and exported
- [ml-runtime.md](ml-runtime.md) — minimal ONNX Runtime build and ORT-format models: how the AAR and the `.ort` assets are produced
- [library.md](library.md) — import, storage, metadata, persistence
- [settings.md](settings.md) — app settings, themes, backup rules
- [onboarding.md](onboarding.md) — first-launch steps, the DataStore flag, replay from Settings
- [i18n.md](i18n.md) — the bilingual (ES/EN) resource strategy
- [performance.md](performance.md) — fluidity baseline on the Fold, how to measure, ranked findings
- [glitch-report.md](glitch-report.md) — "Report a visual glitch": composed page + report.json shared by mail through a FileProvider
- [release.md](release.md) — upload key, signed AAB, bundletool install, Play upload
- [privacy.md](privacy.md) — the privacy policy published on GitHub Pages (EN + ES)
- [bubble-refinement-loop.md](bubble-refinement-loop.md) — the iterative device-vs-offline loop used to polish bubble rendering

Keep these in sync with the code. If behavior changes, update the matching doc in
the same change.
