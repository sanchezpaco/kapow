# Foldable

The core differentiator. Kapow treats the Z Fold as the primary device and
adapts the reading surface to **device posture** and **window size**, keeping the
reader's exact position across a fold/unfold.

## Posture tracking

We observe folding state with `androidx.window`:

- `WindowInfoTracker.windowLayoutInfo(activity)` emits `WindowLayoutInfo`.
- A `FoldingFeature` in that info tells us hinge orientation, occlusion, and
  `state` (`FLAT` vs `HALF_OPENED`).
- Combined with `WindowSizeClass` (width/height buckets) we derive a single
  domain enum consumed by the reader.

```
enum class ReadingPosture {
    CompactSingle,   // cover screen / folded — one page, guided-friendly
    UnfoldedSingle,  // unfolded portrait — one full page at near-physical size
    UnfoldedSpread,  // unfolded landscape — two pages side by side
    Tabletop,        // half-opened, horizontal hinge — book mode across halves
}
```

Derivation rules:

| Signal                                            | Posture          |
|---------------------------------------------------|------------------|
| `HALF_OPENED` + horizontal hinge                  | `Tabletop`       |
| Width size class ≥ Expanded, landscape            | `UnfoldedSpread` |
| Width size class ≥ Medium (unfolded, portrait)    | `UnfoldedSingle` |
| Compact width (cover screen)                      | `CompactSingle`  |

The mapping lives in `core/window` as a pure function
`derivePosture(layoutInfo, sizeClass): ReadingPosture` so it is unit-testable
without a device.

`rememberReadingWindowState()` (`core/window/PostureTracking.kt`) exposes both the
derived `ReadingPosture` and, when the hinge is horizontal, its occlusion band as
a `HingeOcclusion(topDp, bottomDp)` converted from `FoldingFeature.bounds` using
the current density. The posture derivation itself stays untouched by this: it is
still the pure `WindowState.toPosture()` covered by `PostureTest`.

## Adaptive reading surface

The reader renders a strategy chosen from posture (see `reading-modes.md`):

| Posture          | Surface                                                   |
|------------------|----------------------------------------------------------|
| `CompactSingle`  | Single page; Guided View available for panel reading     |
| `UnfoldedSingle` | Single full page, near physical size                     |
| `UnfoldedSpread` | Two-page spread (left/right), respects double splashes   |
| `Tabletop`       | Book mode: one page rendered per screen half, hinge-aware |

In `Tabletop`, `TabletopReader` measures its own height with `BoxWithConstraints`
and calls the pure helper `splitAtHinge(containerHeight, hinge): TabletopSplit`
(`core/window/TabletopSplit.kt`) to get a `pageHeight` / `hingeHeight` /
`controlsHeight` triple. The page area stops exactly at the hinge, a spacer of
`hingeHeight` skips the occluded band, and controls fill the rest below it. When
the hinge bounds are missing or don't fit inside the measured container,
`splitAtHinge` falls back to the previous proportional split (62% page / 38%
controls) so the surface degrades gracefully on devices or emulator states that
don't report a usable `FoldingFeature`. `splitAtHinge` is pure and unit-tested
(`TabletopSplitTest`) independently of any device or Compose runtime.

## Position continuity

The single most important detail: **the reading position must survive a posture
change.** The user reads panel-by-panel on the cover screen, opens the device,
and lands on the same page — now full-size.

To achieve this the reader's position is stored in a posture-independent form:

```
data class ReadingPosition(
    val pageIndex: Int,
    val panelIndex: Int?,        // set only in Guided View
    val normalizedFocus: Offset, // 0..1 focal point within the page
)
```

On posture change:

1. Map current position to the new surface (e.g. leaving Guided View → keep
   `pageIndex`, drop `panelIndex`, center focus).
2. When entering a spread, snap `pageIndex` to its spread pair.
3. Animate the transition rather than cutting, so it reads as continuous.

Position is held in `ReaderUiState` and persisted (per comic) via Room so it also
survives process death.

## Testing without a foldable

- `derivePosture` (`WindowState.toPosture()`) is pure → unit tested with
  fabricated inputs (`PostureTest`).
- `splitAtHinge` is pure → unit tested with fabricated container heights and
  hinge bounds, including the fallback path (`TabletopSplitTest`).
- Emulator: use the Android Studio foldable emulator + virtual sensors to toggle
  postures. Resizable emulator covers size-class transitions.
