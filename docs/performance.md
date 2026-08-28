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
