# Foldable

The core differentiator. Comicify treats the Z Fold as the primary device and
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

## Adaptive reading surface

The reader renders a strategy chosen from posture (see `reading-modes.md`):

| Posture          | Surface                                                   |
|------------------|----------------------------------------------------------|
| `CompactSingle`  | Single page; Guided View available for panel reading     |
| `UnfoldedSingle` | Single full page, near physical size                     |
| `UnfoldedSpread` | Two-page spread (left/right), respects double splashes   |
| `Tabletop`       | Book mode: one page rendered per screen half, hinge-aware |

In `Tabletop` we use the hinge bounds from `FoldingFeature.bounds` to avoid
drawing content under the fold, splitting the layout at the hinge.

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

- `derivePosture` is pure → unit tested with fabricated inputs.
- Emulator: use the Android Studio foldable emulator + virtual sensors to toggle
  postures. Resizable emulator covers size-class transitions.
