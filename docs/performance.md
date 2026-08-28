# Performance

Baseline and method for the app-wide fluidity research (2026-08-28). Numbers
are from the **release** build (`make deploy-release`) on the Z Fold
(`SM-F971B`, Android 17), **folded** (cover screen 1248×1972, 60 Hz render
rate on a 120 Hz-capable panel — `dumpsys display` → `mActiveRenderFrameRate`).
The unfolded posture is not measured yet; the same recipe applies. Debug
builds run 10–30× slower in hot Kotlin and are useless for these numbers.

## Method

- **Frames**: `dumpsys gfxinfo com.comicify reset` → one interaction →
  `dumpsys gfxinfo com.comicify`. Read `Janky frames`, the percentiles, the
  GPU percentiles and the `Number …` causes. The harness used here is a
  shell function that runs a list of `adb shell` commands between the reset
  and the dump; keep the settle (`sleep`) inside the window so the frames
  the interaction triggers are counted.
- **Pipeline timings**: `adb logcat -s PageLoader:D`. `PageLoader.timed`
  logs `decode | panels | bubbles | bubble plan on page N in M ms`. Open
  latency = tap timestamp (`adb shell date +%s%N` before `input tap`) to the
  first `decode` line minus its duration.
- **Attribution**: `adb shell perfetto -o /data/misc/perfetto-traces/x.pftrace
  -t 25s -b 64mb sched freq idle am wm gfx view binder_driver input dalvik`,
  then the `perfetto` Python package (`TraceProcessor`) on the pulled file:
  top-level main-thread slices > 8 ms, `actual_frame_timeline_slice` jank
  types, children of the long `Choreographer#doFrame`.
- **Startup**: `am start -W` (`TotalTime`) plus a burst of `screencap`
  compared against the settled screen.
- Device gotchas: `screencap` needs `-d <physical display id>` on the Fold
  (two displays); `am start -a VIEW -d content://…Download%2FX.cbz
  --grant-read-uri-permission -n com.comicify/.MainActivity` opens a comic
  only when no task of the app is alive (`onNewIntent` is ignored), so back
  out of the app first; with the library in front, use the "Seguir leyendo"
  card or a shelf cover. Reader HUD (folded): tap (624,986) toggles it,
  bubbles ≈ (850,190), Guided View ≈ (995,190), settings ≈ (1145,190),
  back ≈ (60,190).

## Baseline (release, Fold folded, 60 Hz)

Comics: Aura (CBZ, 15 light JPEG pages), Doctor Doom #9 (CBZ 25 MB, 24
pages ≈ 2000×3000), Blacksad #1 (CBR/RAR 165 MB), Spiderman 2099 #4 (CBR
14 MB).

| Interaction | Frames | Janky | p50 / p90 / p99 | GPU p90 | Notes |
|---|---|---|---|---|---|
| Cold start → first window | — | — | 111–117 ms | — | warm 18–20 ms; covers settled ≈ 0.3 s later |
| Library fling ×8 (38 comics) | 594 | 0.7 % | 5 / 5 / 5 ms | 3 ms | 2nd pass 0.9 %, slow drag 0.2 % |
| Open comic → first page | — | — | Aura 85–130 ms, 2099 139, Doom #9 175–204, Blacksad 509 | — | ≈ 2.4 ms/MB SAF copy, then decode 16–104 ms |
| Page turns ×6–8, bubbles off | 228–374 | 2.1–2.6 % | 5 / 5 / 10–13 ms | 3–5 ms | decode 60–75 ms (Doom), 90–131 ms (Blacksad), never on the main thread |
| Page turns ×8, bubbles on, cold | 371 | 2.2 % | 5 / 11 / 19 ms | 11 ms | per page: decode ≈ 70 + bubbles 150–235 + plan 5–70 ms, serial |
| Page turns ×8, bubbles on, cached | 367 | 2.2 % | 5 / 6 / 16 ms | 6 ms | |
| Bubble toggle on (cold, Doom p10) | 52 | 3.9 % | 14 / 15 / 28 ms | 14 ms | GPU-bound frames while the overlay appears |
| Double-tap zoom + pan flings | 182–280 | 1.6–3.3 % | 5 / 5–7 / 14–16 ms | 3–6 ms | |
| Guided View enter (cold panels) | 27–30 | 0–3 % | 5 / 6 / 7–12 ms | 5 ms | panels 128–214 ms, detected when the page becomes current |
| Guided View next panel ×8–10 | 269–304 | 3.0–3.6 % | 5 / 6 / 14–15 ms | 6 ms | |
| HUD show/hide ×6 | 154 | 3.9 % | 5 / 5 / 16 ms | 3 ms | Blacksad ×4: p95 12 / p99 20 ms |
| Settings panel toggle ×6 | 163 | 3.7 % | 5 / 5 / 13 ms | 3 ms | |
| Thumbnail scrubber drag (CBR) | 633 | 0.6 % | 5 / 5 / 6 ms | 5 ms | |
| Reader → library | 20 | 5 % | 5 / 12–15 / 20–27 ms | 2 ms | one slow frame per exit (n = 4) |

Zero `Slow bitmap uploads` anywhere (hardware bitmaps, see
`file-formats.md`).

## What the trace says

Perfetto over 25 s of Blacksad page turns and HUD taps: 469 of 474 app
frames under 8.4 ms, four between 8.4 and 16.7 ms, one at 21 ms. Decode and
detection live on `DefaultDispatcher` threads; the main thread's only work
is `Choreographer#doFrame`. The pattern behind almost every janky frame in
the table is the same: **the first frame after an interaction spends 9–11 ms
in `Record View#draw()`** (Compose re-recording the reader's display list
when the HUD, the overlay or the pager state changes), reported by `gfxinfo`
as one `Slow issue draw commands` frame per gesture. It fits inside the
16.7 ms budget at 60 Hz most of the time, which is why the app feels smooth,
and it is what would break first at 120 Hz.

## Findings, ranked

1. **Bubble overlay GPU cost.** With bubbles on, the page-turn animation's
   GPU time doubles (p90 11 ms vs 5 ms) and the toggle renders 50 frames at
   ≈ 14 ms GPU. The overlay is layer-cached for static frames, but during the
   pager's `graphicsLayer` animation the per-bubble bitmaps (copy + crescent
   fill) are composited every frame. Largest measured cost; the only one
   that touches the frame budget at 60 Hz.
2. **First-frame display-list re-record (9–11 ms main thread)** on every
   gesture: HUD toggle, settings panel, page turn, guided step, zoom. Root of
   the 3–4 % janky figures. Candidate: isolate the HUD and the page in
   separate layers so toggling chrome does not invalidate the page draw.
3. **Open latency scales with archive size**: the whole file is copied from
   SAF to `cacheDir` (≈ 2.4 ms/MB) before the archive is opened, and CBR then
   extracts every item to disk again (`CbrComicSource.extractAllItems`). A
   165 MB RAR shows its first page after 509 ms; a 400 MB one would take
   ≈ 1.2 s. Candidates: open CBZ/PDF from the SAF file descriptor without
   copying; extract RAR items in reading order starting at the saved page.
4. **Cold detection pipeline is serial per page** (`PARALLEL_DETECTIONS = 1`,
   ≈ 250 ms per page in bubble mode; panels 130–210 ms in Guided View, only
   started when the page becomes current). It keeps up at one turn every
   0.5 s; faster flipping or scrubber jumps show bubbles/panels late.
   Candidate: preload panels with the pages when Guided View is on.
5. **Reader → library transition**: one 20–27 ms frame while the library
   recomposes and Coil re-binds covers.
6. **Refresh rate**: the app renders at 60 Hz. Requesting 120 Hz for scroll
   and page turns is a product decision that only pays off after 1 and 2.

Not worth touching (baseline is at the noise floor): library scroll,
startup, thumbnail scrubber, zoom/pan, decode placement.

## Follow-up: findings 1 and 2 (2026-08-28)

- **1, bubble overlay** — `ZoomablePage` draws the overlay in its own
  `graphicsLayer` with `CompositingStrategy.Offscreen` (Auto while zoomed),
  see `speech-bubbles.md`. Doom #9, release, folded: bubble toggle p50 14 →
  6 ms (GPU p50 13 → 5 ms); page turns with bubbles p90 6 / GPU 5 ms — the
  same as with bubbles off. Graphics PSS unchanged.
- **2, first-frame re-record** — a second trace split the pattern in two.
  The HUD's slow frame was real: 5 ms composing + 7.4 ms recording the chrome
  that `AnimatedVisibility` rebuilt on every show; the chrome now stays
  composed (`SlidingChrome`, `ModulateAlpha` fade + off-screen slide,
  thumbnails gated on visibility and decoded as hardware bitmaps), and warm
  toggles trace at ≈ 4 ms on the main thread with nothing over 3 ms on the
  RenderThread. The first show after opening still pays one-off Vulkan
  allocations (chrome layer, thumbnail textures; p99 ≈ 30 ms once). The
  per-gesture slow frame on page turns, guided steps and settings is **not
  app work**: the RenderThread sits in `queueBuffer → waitForever` (9–10 ms,
  "Buffer Stuffing" in the frame timeline) on the first frame after idle,
  and `gfxinfo` still counts it as one janky frame per gesture. Nothing to
  fix on the app side; it is invisible at 60 Hz.
- **3, open latency** — RAR and PDF now open on the SAF descriptor
  (`DescriptorInStream`, `PdfRenderer(descriptor)`), and RAR extraction starts
  at the page the reader resumes on when the archive is not solid; ZIP keeps
  its cache copy (`ZipFile` needs a path; `/proc/self/fd` reopen is `EACCES`
  under scoped storage). Blacksad #1 (165 MB RAR): first page 509 → 361 ms;
  Muerte (62-page RAR) resumed at page 19: ≈ 800 → 222 ms. The remaining
  ≈ 240 ms before the first Blacksad decode is the archive listing plus the
  first three 7 MB items coming out of the extractor. See `file-formats.md`.
- **4, serial cold detections** — Guided View now preloads panels with the
  pages (`PageLoader.preload(panels = true)`, per-page mutex, shared
  detection slot), so Blacksad #1 fresh: pages 1–2 detected 440–575 ms after
  entering, page N+2 detected while N is shown; each step finds its panels
  cached. Bubble mode already preloaded overlays; its per-page cost stays
  serial (≈ 250 ms) by design (one detection at a time). See `guided-view.md`.
- **5, reader → library exit** — traced: one 36 ms frame, 26 ms of it
  recomposition (`ComicifyRoot` swaps the reader for a fresh `LibraryScreen`)
  and 7 ms measure after the system bars come back. Deliberately left alone:
  keeping the library composed underneath the reader would make every
  `saveProgress` recompose it during the page-turn animation, trading a single
  frame on close for work on the primary interaction. Revisit only with
  Compose runtime tracing if the close ever feels slow.

## Where this leaves the app

Every interaction in the table now sits at 2–4 % `gfxinfo` jank with the
per-gesture frame being SurfaceFlinger's idle wake-up, main-thread frames
≈ 4 ms warm, GPU ≤ 6 ms with or without bubbles, RAR/PDF opening independent
of file size and Guided View never waiting for a detection. The unfolded
posture is still unmeasured; the same recipe applies.

## Unfolded posture (inner screen 2448×1848, spread, 60 Hz, 2026-08-28)

Same build family (after fixes 1–4), Muerte (62-page RAR, resumed at p.20)
unless noted. HUD on the inner screen: bubbles ≈ (2032,88), Guided View
≈ (2192,88), settings ≈ (2352,88), back ≈ (96,88); centre tap (1224,924).

| Interaction | Frames | Janky | p50 / p90 / p99 | GPU p50 / p90 | Notes |
|---|---|---|---|---|---|
| Cold start → first window | — | — | 118–131 ms | — | |
| Library fling ×8 (5 columns) | 477 | 1.9–2.1 % | 5 / 6–8 / 13 ms | 5 / 5–7 ms | |
| Open Muerte, resumed at p.20 | — | — | first pages at +347–362 ms | — | RAR tail-first; head batch lands at ≈ 1 s |
| Spread turns ×8, bubbles off | 411–429 | 4.0–5.0 % | 7 / 9–10 / 14–15 ms | 7 / 9 ms | two 2160 px hardware bitmaps per frame |
| Spread turns ×8, bubbles on, cold | 417 | 3.4 % | 7 / 15 / 26 ms | 7 / 14 ms | fresh overlay layers rendered mid-animation |
| Spread turns ×8, bubbles on, cached | 423 | 4.3 % | 7 / 10 / 18 ms | 7 / 9 ms | = bubbles off |
| Bubble toggle on (cold, spread) | 29 | 6.9 % | 11 / 24 / 34 ms | 7 / 13 ms | six overlays planned + two layers created |
| Guided View enter (cold panels) | 30 | 10 % (3/30) | 9 / 12 / 14 ms | 7 / 10 ms | panels 233–395 ms, preloaded two ahead |
| Guided View next ×10 | 334 | 3.3 % | 7 / 13 / 20 ms | 7 / 13 ms | |
| Double-tap zoom + pan | 44 | 4.5 % | 5 / 9 / 18 ms | 5 / 8 ms | |
| HUD show/hide ×6 (first after open) | 84 | 8.3 % | 7 / 11 / 20 ms | 6 / 10 ms | first show pays the one-off allocations |
| Settings toggle ×6 | 181 | 3.9 % | 8 / 9 / 16 ms | 7 / 9 ms | |
| Reader → library | 20 | 5 % | 7 / 32 / 40 ms | 6 / 6 ms | bigger library composition on the inner screen |

The inner screen is GPU-bound, not CPU-bound: the RenderThread spends
0.7 ms per frame and the GPU ≈ 6 ms (Perfetto `GPU completion`, 296 frames,
max 12 ms). Two full-height hardware bitmaps sampled down from 2160 px plus
the ambient radial gradient over 4.5 MP. An A/B without the gradient took
GPU p50 7 → 5 ms and p90 9 → 7 ms; `AmbientBackdrop` now paints it in an
`Offscreen` layer at a quarter of the screen size scaled ×4 (1/16 of the
shader work; the blend is a smooth gradient, indistinguishable), landing at
GPU p50 6 / p90 8 ms. The single-page pager's turn layer uses `ModulateAlpha`
when no overlay is drawn, so its fade no longer needs a per-page
`saveLayer`; measured only on the inner screen (which does not use it) —
re-check folded. Everything sits under the 16.7 ms budget; at 120 Hz the
spread would not.
