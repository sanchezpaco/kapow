# Reading modes

The reader chooses a **surface** from the current `ReadingPosture` (see
`foldable.md`). Each surface is a Compose strategy fed the same posture-agnostic
`ReadingPosition`.

## Full page (single)

Default on `UnfoldedSingle` and `CompactSingle`.

- Horizontal `Pager`, one page per screen.
- Content fits the screen; the unfolded screen shows a page at near-physical size
  so no zoom is needed for normal reading.
- Gestures: single-finger horizontal swipe changes page; a tap on the outer 20 %
  of the width turns the page (`TapZones` in `PageOrder.kt`: the left zone goes
  back and the right one forward, mirrored for right-to-left; in a spread each
  half exposes its outer 40 %, i.e. 20 % of the screen per side, leaving a wide
  centre so a tap for the chrome does not turn the page) and a tap
  in the middle toggles the chrome; while zoomed every tap is a centre tap.
  Pinch (two fingers) zooms; when zoomed, one finger pans (clamped to the page) with an inertial
  fling on release (velocity-tracked, `exponentialDecay` clamped to the pan
  bounds) and the pager is locked; double-tap toggles a 2.5× zoom centred on the
  tapped point. The
  zoom/pan detector only consumes events when two fingers are down or the page
  is already zoomed, so single-finger swipes always reach the
  pager (a custom `awaitEachGesture`, not `detectTransformGestures`).
  While zoomed, a one-finger pan only starts once the finger has travelled past
  `viewConfiguration.touchSlop` (`PanSlop` in `reader/domain`, unit-tested):
  below that nothing moves and nothing is consumed, so a slightly shaky tap
  still reaches `detectTapGestures` and double-tap zooms back out instead of
  nudging the page (it was flaky on phones, where taps wobble more than on the
  Fold). Pinches skip the slop and take the events on the first move, exactly as
  before, so the pager can never steal a two-finger gesture.
- Instead of a flat-black letterbox, the whole reader background is an **ambient
  radial glow** derived from the current page's dominant color (androidx
  Palette), cross-fading when the page changes. See `ambient-backdrop` below.
- **Page-turn depth transition:** as the pager settles, each page is transformed
  by a `graphicsLayer` driven by its own scroll-offset fraction
  (`PagerState.getOffsetFractionForPage`). The outgoing page recedes with a
  subtle scale-down, fade, parallax translation, and a soft 3D `rotationY` (via a
  modest `cameraDistance`), so a turn reads with depth rather than a flat slide.
  The math lives in the pure `PageTurn.transform(pageOffset)` function
  (`reader/domain`, unit-tested): offset `0` is the identity (the settled page is
  perfectly flat and centred), and `|offset| → 1` reaches the depth extreme. The
  effect is scoped to this single-page surface; spread, tabletop and Guided View
  are unchanged. Because the pager is locked while a page is zoomed, the offset
  stays `0` there, so the transition never fights pinch-zoom or pan.

## Ambient backdrop

When opened from the library the backdrop starts from the cover's persisted
ambient colour (`ReaderScreen(initialAmbient)`), then follows each page.

Each decoded page carries an `ambient` color (Palette vibrant → muted → dominant
fallback), cached with the page bitmap. The reader paints a radial gradient from
a darkened ambient glow at the page center out to pure black at the edges, so the
page appears to emit light into the letterbox. The current page's ambient is
animated (`animateColorAsState`), so turning a page smoothly shifts the glow.

## Two-page spread

Default on `UnfoldedSpread`.

- Renders pages `n` and `n+1` side by side as an open book.
- **Double-page splash handling:** if a page is detected as a full-width spread
  (aspect ratio near 2:1, or metadata flag), it occupies both halves and pairing
  re-aligns so the artwork joins correctly across the seam.
- Page pairing parity is a per-comic setting ("cover alone in the spread",
  `ComicSettings.coverAlone`, set from the comic's settings screen). `PageOrder`
  owns the arithmetic (`spreadCount`, `spreadIndex`, `spreadFirstPage`): with
  the cover alone, spread 0 shows a blank left half and the cover, and the
  pairs become (1, 2), (3, 4)… The spread surface and Guided View's spread
  layout both use it; the thumbnail scrubber maps a page to its spread through
  the same helper.

## Book mode (tabletop)

Active in `Tabletop` posture (half-folded, horizontal hinge).

- The layout splits at the real hinge: `FoldingFeature.bounds`, converted to dp,
  drives the pure `splitAtHinge` helper so the page area stops exactly above the
  occluded band and controls start exactly below it. See `foldable.md`.
- Above the hinge shows the page; below the hinge shows controls/next-page
  affordance — hands-free reading on a surface.
- When hinge bounds are unavailable or don't fit the measured surface, this
  falls back to the previous proportional split (62% page / 38% controls).

## Guided View

Available on `CompactSingle`, and on demand elsewhere. Full detail in
`guided-view.md`.

- Auto-focuses one detected panel at a time with an animated pan/zoom whose
  duration and shape come from the geometry of the move (`DirectorCut`: pan with
  an arc on long travel, push in, pull back, reveal, jump on a page turn). See
  `guided-view.md`.
- Tap advances to the next panel in reading order; at the last panel, advances
  to the next page's first panel.
- Double-tap zooms around the tapped point and one finger pans (with an inertial
  fling on release), so any page (including whole-page fallbacks and wide panels)
  stays readable. Panning runs in normalized page space, so the release fling uses
  `exponentialDecay` (spline decay is pixel-calibrated and would not move a 0..1
  value).

## Enlarged speech bubbles

An alternative to Guided View for small lettering that keeps the whole page (and
its art) on screen: the ChatBubble button in the HUD (`ReaderUiState.bubblesEnlarged`,
hidden while Guided View is on) redraws every detected speech bubble and caption
scaled up **in place** (1.3× by default, a stepper in the view-mode panel sets 1.1–2×, persisted), as an overlay on the regular page surfaces
(`ZoomablePage`, so it works on single pages, the spread and tabletop and follows
pinch/double-tap zoom). It is a static render enhancement — no page zoom, no
bubble-to-bubble navigation, nothing to order — so it deliberately sidesteps
panel segmentation and reading-order errors. Full detail in `speech-bubbles.md`.

## Autoplay

A **Timer** switch at the bottom of the view-mode (eye) panel reads the comic on
its own, at a pace you set in seconds per page. Turning it on reveals a stepper —
a `−` circle, the value, a `+` circle, with `Per page` in the empty left half of
the row (`Paused` while it is) — that walks `AUTOPLAY_SECONDS_RANGE` (3–120 s) in
`AUTOPLAY_SECONDS_STEP` (1 s) steps, disabled at the bounds. A second is the
right grain to *tune* a pace with and a poor way to *cross* the range, so a held
button repeats and then coarsens: one step on press, then after
`AUTOPLAY_REPEAT_DELAY_MS` (400 ms) one step every `AUTOPLAY_REPEAT_INTERVAL_MS`
(120 ms), and after `AUTOPLAY_COARSE_AFTER_MS` (1.2 s) of repeating the
*increment* becomes `AUTOPLAY_COARSE_STEP` (5 s) at the same cadence. It is the
step that coarsens, not the value: `Autoplay.coarseStepped` moves to the next
multiple of 5 in the direction of travel, so the number stays readable while it
climbs (20, 25, 30…), a release always lands round, and a follow-up tap still
nudges by exactly one. A light haptic marks entering the coarse phase and
hitting a bound. The repeat is driven off the button's own
`MutableInteractionSource` (a `PressInteraction.Press` starts it, `collectLatest`
ends it on release or cancel) so the ripple, the disabled state and the
accessibility semantics stay `IconButton`'s; the trailing click after a hold is
swallowed so a hold does not add a stray step. The bubble-scale stepper shares
the step button but not the hold — 0.1× over 1.1–2× needs no shortcut.

Unlike enlarged bubbles, autoplay is offered in **all three view modes**,
because every one of them has something to advance. `N` is always *seconds per
page*:

- **Pages.** One `PageTurnDirection.Next` per dwell into the same
  `pageTurnRequests` flow the volume keys and the tap zones feed, so the single
  page, the spread and the tabletop surface all get autoplay from one driver and
  keep their page-turn animation. Nothing about paging is duplicated. On
  `UnfoldedSpread` the dwell is `N × 2`, because two pages are on screen —
  `Autoplay.spreadPages` drops back to `N` where the step really shows one (the
  lone cover, the last odd page).
- **Guided View.** The beat is a *stop*, not a page: a page with four panels
  gets `N ÷ 4` per panel, so a page still takes `N` however it is cut up.
  `Autoplay.stopMillis` floors that at `AUTOPLAY_MIN_STOP_SECONDS` (1.5 s) per
  stop so a dense page does not machine-gun — which is also why the range starts
  at 3 s, the point where a two-stop page lands exactly on the floor. The dwell
  starts when the **camera settles**, not when the advance is issued:
  `GuidedReader` reports `onCameraSettled` after the `DirectorCut` animation, so
  a long reveal is never counted against the reader's time on the panel.
- **Continuous vertical scroll.** Not a jump per page — a continuous creep. A
  frame-paced loop (`withFrameNanos` inside `LazyListState.scroll`) scrolls by
  `Autoplay.scrollPixels`, which spends one **laid-out item height** per `N`
  seconds. That is the on-screen height, not the decoded bitmap's, so the strip
  zoom (a draw-time `graphicsLayer`) does not change the reading rate. The strip
  keeps going across an issue boundary — chaining is that mode's promise.

## Holding, and stopping

Autoplay has exactly two states on screen, and the difference between them is
which control you used.

**A finger is a dead-man switch.** Touching the page anywhere holds autoplay;
lifting continues it. The dwell **freezes where it is and does not reset**
(`AutoplayDwell.ticked` returns itself while held), so a two-second glance does
not cost you a whole page. Manual navigation is not an interruption to be
punished: a tap zone, a swipe, a volume key, a guided advance, a pinch, a
double-tap and a scrubber jump all happen normally, and because the dwell is
keyed on the page and stop you land on, autoplay resumes from a **full** dwell
wherever you ended up. A centre tap raises the chrome, and a visible chrome
counts as held too — that is what lets you change the pace mid-run and watch the
number change without the page turning under the panel.

The observer is one `Modifier.pointerInput` on the reader `Box`, in the chain
only while autoplay is counting: it counts pointers down on
`PointerEventPass.Initial` and up on `PointerEventPass.Final` and **never
consumes**. That is deliberate and load-bearing — `ZoomablePage` still consumes
only with two fingers down or already zoomed, `PanSlop` is untouched, and the
pager swipe and `TapZones` see a byte-identical event stream. The strip does not
get the observer: `creepDown` takes its scroll at `MutatePriority.Default`, so a
drag pre-empts it at `MutatePriority.UserInput` and the loop waits for
`isScrollInProgress` to go quiet before creeping on from wherever the finger left
the page. That already *is* the dead-man switch, and a second one would only
fight it.

**Turning it off is always deliberate**: the panel switch, `ON_STOP`
(backgrounding latches it off — you do not come back to a page mid-turn), and
the end of the comic, where the tick that would advance past the last page
switches autoplay off and raises the end-of-comic card instead. Off is
deliberately **not** reachable from the reader surface: a hidden long-press on
the one visible control is exactly how the pill went wrong the first time.

## The pill

The pill is the running indicator and the playback transport: a 40 dp circle
that is a sibling of `TopChrome` but **outside `SlidingChrome`**, so it survives
the chrome auto-hiding — once the HUD is gone it is the only thing on screen
that says the page is about to turn, and the only way to interrupt it. It is
declared after the night-tint layer, like the chrome, so the amber scrim never
washes it.

It sits at the **bottom-end** corner (`navigationBarsPadding()`, 20 dp in, 84 dp
up) and **never moves** — the 84 dp is a constant, not derived from whether the
chrome is up, so it is the same pixel either way. Top-start was tried first and
was wrong three ways: unreachable one-handed on the 475 dp cover screen, sitting
on the page's reading entry point, and landing on the page half rather than the
controls half in Tabletop. At 84 dp it shares a band with the thumbnail
scrubber, which **yields rather than moves**: `ThumbnailScrubber` takes an
`endInset` of the pill plus 12 dp while autoplay is on. A near miss lands in the
"next page" tap zone, which is harmless under the dead-man rule — autoplay just
carries on from wherever you land.

**The ground is opaque** (`PanelColor`, the same near-black the `HudPanel`
uses), clipped to a circle, with the ring drawn inside that clip so a round cap
never spills past the rim. The first version used a `White 0.12` ground with a
`White 0.16` ring and a white glyph, and over a cream page every one of them
disappeared — the only surviving mark was the red arc, which itself died over
red art. This screen already learned that lesson once when the HUD moved to
`HudPanel` because a solid panel reads over any page. It is `PanelColor` and not
`palette.raised` because the reader keeps black chrome on every theme, Paper
included (see `settings.md`); there is no theme branch and no scrim, which would
otherwise leave a permanent dark smudge in the corner of every autoplayed page.

Three states share that one disc:

| | arc | glyph | motion |
|---|---|---|---|
| **Running** | sweeps, `colorScheme.primary` | `Pause` | the arc sweeps |
| **Held** | frozen in place, crossfades to `White 0.45` over 120 ms | `Pause` | none |
| **Paused** | frozen in place, stays `colorScheme.primary` | `PlayArrow` | glyph and arc pulse 1.0 → 0.55 → 1.0, 1600 ms |

Held and Paused differ on three axes at once — arc colour, glyph and motion — so
they cannot be read for each other. The pulse is applied to the **glyph and the
arc only, never the disc**: dropping the ground's opacity at the trough would
undo the legibility fix twice a second. Resuming rebases the dwell to a full
interval. The ring is a `White 0.16` track with a `colorScheme.primary` arc
sweeping from −90°, its progress read from an `Animatable` **inside the `Canvas`
draw**, so a running countdown redraws 40 dp and recomposes nothing
(`performance.md` records what compositing the reader's scrolling content once
cost). In the strip the pill draws the track ring and nothing else, ever: there
is no discrete next turn to promise, and a full accent ring would read as a
countdown that had already finished. TalkBack reads "Pause autoplay" /
"Resume autoplay" with a Playing / Held / Paused state.

The **switch in the eye panel is the mode** — the rare, deliberate on/off — and
the pill is the transport. Turning the switch off while paused clears the pause;
turning it on always enters Running. While paused, the stepper's caption slot
doubles as the state line: `Per page` becomes `Paused` in the accent colour. One
word, no new row, no new control.

## Where the interval comes from

The interval is **global and persisted** (`autoplay_seconds_per_page` in the
reader DataStore, beside `bubble_scale`) — not per comic and not in app
settings. Until you set one it is seeded from what reading stats already know
about you: `ReadingPace.secondsPerPage` for this comic's series **in the mode it
opened in**, clamped into the range, falling back to
`ReadingPace.FALLBACK_SECONDS_PER_PAGE` when there is no usable history.
`ReaderViewModel` reaches the stats repository through `StatsEntryPoint`, the
same `EntryPointAccessors` pattern it already uses for the DAOs, since it is
built by a plain factory rather than by Hilt.

Autoplay itself is **never persisted** and never on at open: it is session
state, switched off again every time a comic is opened. While it is on the
screen is kept awake regardless of the keep-screen-on setting, and the setting
itself is never written.

**Autoplayed sessions do not define your pace.** A comic left running at 8 s a
page would otherwise rewrite the median that seeds this very feature, and under
a dead-man switch far more pages reach `reading_session` that way. The first
autoplay-driven page event marks the session draft (`SessionRecorder.onAutoplay`
→ an `autoplayed` column on `reading_session`, Room v19 → v20), and
`ReadingPace.contributing` drops those sessions. They still count as time and
pages read on the stats screen — you *were* reading — they just do not set the
pace.


## Split wide pages

Some PDFs and scans store two comic pages side by side in one landscape file
page. The per-comic setting "Split wide pages"
(`ComicSettings.splitWidePages`, in the comic settings screen and in the
reader's gear menu) cuts every landscape page in half and presents each half as
its own page.

- The split lives in the data layer, in `SplitPagesComicSource`, a `ComicSource`
  that wraps the real one. On open it probes every page's aspect ratio
  (`ComicSource.pageAspect`: a bounds-only `BitmapFactory` decode for images,
  `PdfRenderer.Page` width/height for PDF), builds the logical page list once
  with the pure `SplitPages.of` (`reader/domain/SplitPages.kt`) and keeps it for
  the life of the source. A page counts as wide when its aspect ratio is greater
  than `WIDE_PAGE_ASPECT` (1), so portrait and square pages pass through
  untouched.
- Half order follows the effective reading direction: left half first in
  `LeftToRight`, right half first in `RightToLeft`. Changing the direction while
  the setting is on rebuilds the source.
- A half is decoded by asking the inner source for twice the target width and
  cropping; everything downstream (pager, thumbnails, preloading, reading
  position, Guided View, bubbles) only sees a longer page list.
- Persisted detections are keyed by document Uri + page index, so the same index
  must not return the unsplit page's panels and bubbles: `PageDetectionStore`
  appends `+split` to `DETECTIONS_VERSION` when the setting is on. The row's
  primary key stays (Uri, page), so a mode change overwrites rather than
  duplicating, and deleting a comic's detections still clears everything with
  one query.
- Toggling from inside the reader rebuilds the source
  (`ReaderViewModel.reopenComic`) and maps the position exactly: the current
  page is converted to its source page through the old source and back to the
  first half of that source page through the new one. The reader surfaces are
  keyed on the `PageLoader`, so the pager is recreated on the mapped page.

### Suggesting the split

Opening a comic with the setting off probes every page's aspect (the same
`pageAspect` the split uses) and, when at least 80 % of the pages after the
cover are wider than tall (`SplitSuggestion.shouldSuggest`, at least two
such pages), shows a snackbar once: "This comic looks like it has two pages
per image · Split". The action turns the setting on; accepting or dismissing
records `ComicSettings.splitSuggested` so the comic never asks again. The
check is purely geometric, so a landscape-format comic gets the same
suggestion — that is why it suggests rather than splits on its own. On
RAR files the probe waits for the archive extraction, so the snackbar can
appear a few seconds after the first page.


## Choosing a view mode

The reader has three layouts — paged, Guided View and the vertical strip — and
they are mutually exclusive, so `ReaderViewMode` (pure, in `reader/domain`) picks
one from the two flags that persist it and the HUD offers them as a single
choice behind one control rather than as separate toggles that hide each other.
Enlarged bubbles are **not** a fourth mode: they are an overlay that works over
the paged layouts and over the strip, and only Guided View turns them off
(`ReaderViewMode.allowsBubbles`), so they sit in the same menu as a toggle.

The eye button opens the **view-mode panel** (see `Shared reader chrome`). Its
first three rows are the exclusive choice: the selected one is drawn in the
accent colour with a trailing check, and picking one closes the panel, because
choosing a layout is a one-shot decision. Below a hairline, "Enlarged bubbles"
is a `Switch` row — a state you leave on, not a destination — and it disappears
entirely in Guided View. "Page fit" sits above it in the same block, visible only
in Pages mode (see `Page fit` below). Turning it on reveals the bubble-scale stepper
underneath it: `−` and `+` buttons around the current value, stepping by
`BUBBLE_SCALE_STEP` (0.1) inside `BUBBLE_SCALE_RANGE` (1.1–2×), clamped at both
ends. The stepper replaced a floating slider that used to sit under the HUD
buttons over the art: the setting now lives next to the switch that enables it,
and nothing permanently covers the page. Below one more hairline, "Autoplay"
repeats that shape — a `Switch` row that reveals its own seconds-per-page
stepper — but is offered in every mode and does not close the panel when you
flip it (see `Autoplay` above). Its hairline is always drawn; the bubbles block
above it is the one that disappears in Guided View, so the panel never shows two
rules in a row.

## Continuous vertical scroll

A third top-level mode, chosen per comic (`ComicSettings.verticalScroll`, HUD
toggle in the settings menu). It takes precedence over Guided View and the
two-page spread and hides their toggles, because both are page-based and cannot
coexist with a strip. Whole pages are stacked in one `LazyColumn`, fit to width,
with no page-turn animation: scroll instead of paginate. Nothing in its path
detects anything, so unlike Guided View it cannot misread a page.

The strip asks `PageLoader.aspects()` for **every page's aspect ratio before
composing anything** (`ComicSource.pageAspect` reads image headers only, the
same probe the split suggestion already runs at open). Each item is then a
`fillMaxWidth().aspectRatio(aspect)` box, so the list has its full scroll extent
from the first frame and a page decoding never changes the height of anything
above it. Margin cropping can make the decoded page slightly narrower than the
file, which `ContentScale.Fit` letterboxes against the black background rather
than resizing the item.

**Zoom scales the whole strip, not one page.** That is what removes the gesture
conflict the mode looked like it had: the vertical drag is always the scroll, the
horizontal drag is always the pan, and a two-finger pinch competes with neither.
Pinch and double-tap zoom up to 4x, horizontal drag pans within the scaled
bounds, and vertical drag keeps scrolling. Pages are decoded at 2160 px wide
against a much narrower screen, so there is real resolution behind the zoom.

The scale is applied through a `graphicsLayer` **only while actually zoomed**.
Wrapping a scrolling `LazyColumn` in a composited layer forces the whole viewport
to re-rasterise every frame instead of letting Compose move already-rasterised
items: measured, that alone took p99 from 29 ms to **700 ms** and janky frames
from 0.3 % to 6.8 %. At scale 1 the modifier is not in the chain at all.

A tap anywhere toggles the chrome, and **the chrome hides again as soon as the
strip starts scrolling** — in the paged reader the next tap dismisses it, but in
a strip you never tap, so the HUD would otherwise sit over the
artwork for as long as you kept reading. That is gated on a
`DragInteraction.Start` from the list's `interactionSource`, not on
`isScrollInProgress`: autoplay's own creep also sets a scroll in progress, and
hiding the chrome on it would close the panel holding the stepper the instant
you pressed the switch. The
thumbnail scrubber and volume keys still work, jumping and stepping by item.
**Enlarged bubbles work here too** — they are a render overlay on a page, with
nothing page-turn-specific about them, so the strip draws the same
`BubbleLayer` the paged surfaces do. The strip is what exposed that the bubble
layout pass and the detection decoding both ran on the main thread; see
`performance.md`, "When gfxinfo says the reader is fine and it is not".
Reading position stays the first visible page, so it maps to the same
`ReadingPosition.pageIndex` every other surface uses and survives a mode switch.

### The strip does not stop at the end of an issue

When the comic belongs to a series, the strip keeps going into the next issue —
the next issue of the *series*, whatever shelf or reading list the comic was
opened from, so a list never reads as a playlist.
The item list **grows** rather than being replaced (`StripChain.items`, pure and
unit-tested): pages of issue *n*, a boundary band, pages of issue *n+1*, and so
on. Appending only at the end means no index ever shifts under the reader, so
crossing into a new issue costs no scroll correction and no jump.

The next issue opens when the reader comes within three pages of the end of the
last one, so by the time the boundary is on screen its pages are already
decodable. The boundary is deliberately **visible** — a black band naming the
issue just finished and the one starting — because a seamless join loses the
reader's place and makes the next issue's cover and credits appear without
context.

Everything that reads as "the current comic" follows the issue under the top of
the viewport: reading progress (so an issue is marked completed the moment the
reader scrolls past it), the page counter, the thumbnail scrubber and the
next-issue action in the end-of-comic overlay. Each `PageLoader` behind the
reader has its caches dropped (`PageLoader.release`) once the reader is past its
issue, which is what keeps a long chain from multiplying the page cache; the
sources stay open so scrolling back only costs a re-decode.

Two details that are easy to get wrong and are covered here:

- **The last page is not always the last item index.** A page wider than it is
  tall is shorter than the viewport, so `firstVisibleItemIndex` never reaches the
  final page and the issue would never be marked read. The strip reports the last
  item whenever the list can no longer scroll forward.
- **The setting follows the series, not the file.** Chaining into an issue writes
  `verticalScroll` onto it, so resuming that issue later from the library still
  opens the strip instead of dropping back to the pager.

Measured on the emulator over a long fast scroll in both directions
(330 frames): 0.91 % janky, p99 32 ms, **0 slow bitmap uploads**, 0 missed
vsync, and 601 frames scrolling across two issue boundaries hold 0.33 % janky,
p99 29 ms, again with zero slow bitmap uploads. Slow bitmap uploads is the number
that matters — a strip keeps more pages resident than the pager, and it was
thrashing the HWUI texture cache that caused the original 78 %-jank bug
(`performance.md`).

## Sharing a page

The gear panel's action zone carries "Share this page" (labelled "Share this
panel" in Guided View) above "Report a visual glitch". It hands the page to the
Android share sheet as one full-resolution JPEG (quality 90) and closes the
panel; there is no confirmation dialog, because the chooser is the confirmation
and the image never leaves the device until a target is picked.

The image is what the reader is showing:

- the page at its decoded resolution (2160 px wide), not the ≤ 1000 px
  `PageArt.analysis` the glitch report attaches;
- the enlarged-bubble overlay drawn in when it is on, through the same
  `BubbleOverlay.drawBubbles` the reader uses;
- no HUD, no night tint, no ambient backdrop, no margin outside the page.

Posture and mode decide the framing (`sharedPages`, pure and unit-tested):
a single page on every posture but the spread; in `UnfoldedSpread` **both pages
joined into one image**, scaled to a common height and placed in the order they
are drawn (`joinedSlots`, so right-to-left swaps the sides); in Guided View the
current panel crop (`panelCrop` of the stop rect `GuidedReader` reports through
`onGuidedStop`), which is why the row is relabelled there; in the strip the page
under the top of the viewport, the one the counter names. Split halves and PDF
pages are ordinary pages here.

`SharePage` (`feature/reader/ui`) composes every page into one canvas on
`Dispatchers.IO` and writes a single JPEG into `cache/share/`, wiping the
previous one first. The page look filter, when it lands, belongs on the one
`drawImage` call in `SharePage.drawPages` as a `colorFilter`. The file is named
from `reader_share_title` ("<comic title> · page N", sanitised), which is also
`EXTRA_TITLE` so the sheet previews something meaningful; there is no
`EXTRA_TEXT`, which makes several targets drop the image, and no watermark. The
`Intent` is shared with `FileProvider` (`${applicationId}.fileprovider`,
`res/xml/file_paths.xml`), `ACTION_SEND`, `image/jpeg`,
`FLAG_GRANT_READ_URI_PERMISSION` and the URI as `ClipData` so the sheet renders
a preview, and it is emitted on the same `ReaderViewModel.shareRequests` flow
the glitch report already uses — `ReaderScreen` starts whatever arrives there.

## Per-comic settings

`ReaderViewModel` reads the comic's `comic_settings` row (`ComicSettingsDao`)
on open: `bubblesEnlarged` and `guided`, when not null, replace the reader's
initial off state; `readingType`, when not null, overrides the global reading
type, and the reader's type row then writes the override instead
of the global preference; `bubbleScale` works the same way for the HUD stepper
(override wins, and stepping updates the override when one exists);
`coverAlone` feeds the spread pairing above, `splitWidePages` the split
described above, `verticalScroll` the continuous strip, `pageLook` the image
adjustment and `fitWidth` the page fit, both below.
Everything else stays global in `ReaderPreferencesRepository`.

## Reading type and direction

What the user picks is a **`ReadingType`**
(`feature/reader/domain/ReadingType.kt`) — `Comic` (default), `Manga` or
`Webcomic` — persisted with DataStore (`ReaderPreferences`, key
`reading_type`; a pre-1.2 `reading_direction_rtl` still reads as `Manga` /
`Comic`) and exposed as `ReaderUiState.readingType`, cycled from the ViewModel
(`ReaderViewModel.cycleReadingType`) and from the reader settings menu.

`ReadingDirection` stays the low-level concept every pager, tap zone and split
is written against; the type maps onto it with `ReadingType.direction`:

| type | direction | mode on open |
| --- | --- | --- |
| `Comic` | `LeftToRight` | — |
| `Manga` | `RightToLeft` | — |
| `Webcomic` | `LeftToRight` | vertical scroll |

The effective type resolves as per-comic setting → the comic's ComicInfo
default → the global type, and its direction lands on
`ReaderUiState.direction`.

The implied mode is an **on-open** default only, one ladder in
`ComicSettings.openModeOnOpen` (`feature/library/domain/ComicOpenMode.kt`):
the comic's explicit "Mode on open", else `Strip` for a `Webcomic`, else
`Guided` when "Guided view on open" is on, else `Pages`. Picking `Webcomic`
inside the reader therefore never throws the current page into a scrolling
strip — it changes the direction and persists the type, and a snackbar
("Webcomics read in vertical scroll" · Switch) offers the mode change, exactly
as the split suggestion does.

When `RightToLeft` is active:

- The single-page, two-page spread, and tabletop pagers scroll in reversed
  physical order, so swiping in the natural direction still advances the
  story forward.
- In the two-page spread (both the plain spread surface and Guided View's
  spread layout), the pages swap sides: the earlier page renders on the
  right and the later page on the left.
- Tap zones and next/previous semantics mirror: in Guided View, the
  left/right tap zones swap which one advances vs. goes back; the tabletop
  chevrons keep their on-screen position (left arrow always means "earlier
  in the story", right arrow "later") but drive the pager in the opposite
  physical direction.

All of this is driven by a single pure helper, `PageOrder`
(`feature/reader/domain/PageOrder.kt`), which maps a logical (reading-order)
page or spread index to its physical pager index and back, decides which
logical page renders on the left/right of a spread, and mirrors a tap's
zone (previous/center/next). It has no Compose or Android dependency, so it
is unit-tested directly (`PageOrderTest`).

## Shared reader chrome

- Immersive by default: system bars hidden, edge-to-edge, pure-black background
  with the page's ambient glow behind it (`AmbientBackdrop`: a radial gradient
  painted in an `Offscreen` layer at a quarter of the screen and scaled ×4,
  since the full-screen shader cost ≈ 2 ms of GPU per frame on the inner
  screen).
- The top bar keeps only two always-visible controls — an **eye** for the view
  mode and a **gear** for the reader settings — plus the close button. Because
  immersive mode hides the status bar, the top bar pads for `displayCutout`
  (unioned with `statusBars`), so the controls never sit under the foldable's
  front-camera cutout.
- **One panel style, two panels.** Each button opens the same container
  (`HudPanel`): a 280 dp-wide near-opaque near-black card, 20 dp corners, a 1 dp
  white hairline, clamped to the screen width minus 24 dp so it still fits a
  compact phone. Its rows (`PanelRow`) are all built the same way — 48 dp tall,
  full width, a 24 dp leading icon, a `labelLarge` label, and a trailing slot
  that carries the row's state (a check, a `Switch`, or the current value as
  text). The row is `heightIn(min = 48.dp)` rather than a fixed height, so a
  long label — "Avance automático" in Spanish — grows the row instead of being
  clipped; every existing row is single-line and unchanged. The old design floated two different kinds of translucent pill through
  which the art showed; a solid panel with labelled rows reads over any page.
  The panels live inside the chrome, so they slide away with it, and only one is
  open at a time: `TopChrome` owns a single nullable `HudPanelKind`, so opening
  one closes the other instead of letting two columns interleave.
- The gear panel holds night tint, page look (trailing text, tapping cycles it),
  reading type (trailing text, tapping
  cycles Comic → Manga → Webcomic), split wide pages, the panel-layout toggle in the spread, and, below
  a hairline, "Report a visual glitch" as a plain action row that closes the
  panel.
- **The circle button's fill means "open", never "on".** It takes the accent
  colour only while its own panel is showing. Whether its contents are at the
  defaults is a separate signal: an 8 dp accent dot on the top-end corner of the
  circle, shown while the panel is closed and the eye is off Pages, has
  bubbles on or is fit to width, or the gear has night tint, a page look other
  than Original, a reading type other than Comic, or the split active. Autoplay
  is deliberately **not** in that list: the dot means "settings away from their
  defaults, and they persist", which session state never is, and autoplay
  already has a permanent pill of its own.
- Center tap toggles a minimal overlay: progress, page number, quick settings.
  Both bars carry a scrim so their white content stays legible over light pages:
  the top bar fades black `0.6` → transparent downwards, the bottom chrome
  transparent → black `0.75` downwards. The bottom scrim spans the whole bottom
  chrome (it starts above the scrubber and reaches the screen edge, so the
  thumbnails, the page counter, the Guided View stops and the progress bar all
  sit on it) and it slides and fades away with the chrome, so a hidden chrome
  darkens nothing. All three view modes draw the same `BottomChrome`, so the
  strip and Guided View get the scrim with the pager.
  The chrome stays composed while hidden: `SlidingChrome` fades it with
  `ModulateAlpha` and slides it fully off-screen (so it receives no touches)
  instead of `AnimatedVisibility`, which recomposed and re-recorded ~40 nodes on
  every show (Fold: first frame 13 → 4 ms on the main thread). The scrubber
  skips thumbnail loads and re-centring while hidden; thumbnails are decoded
  as hardware bitmaps so showing the chrome uploads no textures.
- Left/right tap zones page back/forward (mirrored correctly for the surface).
- Volume-down/volume-up turn the page forward/back (panel-by-panel in Guided
  View) and consume the key so system volume is unaffected. Only active while
  a comic is open. A DataStore-backed preference (`ReaderPreferencesRepository`,
  default enabled) controls this; toggling it is not yet exposed in a settings
  screen.
- All transitions are spring/physics based, never linear.
- **Thumbnail scrubber:** the bottom chrome carries a horizontally scrollable
  filmstrip of low-resolution page thumbnails. Tapping a thumbnail jumps the
  reader to that page; the strip is draggable to scan the whole comic. The
  current page's thumbnail is highlighted and the strip centres on it. See
  `thumbnail-scrubber` below. Hidden with the chrome, and suppressed in Guided
  View (which navigates panel by panel).
- **Bookmarks:** the counter row carries a bookmark chip on each side of the
  page counter, so the row reads "toggle · 07 / 32 · filter". The left chip (40 dp,
  circular, always in the row) bookmarks **the page the counter names** — the
  spread's first page in `UnfoldedSpread`, the page above the hinge in Tabletop,
  the issue page under the top of the viewport in the strip — and fills with the
  accent while that page is bookmarked. The right chip appears only once the
  comic has a bookmark: a ribbon and the count (`9+` above nine); tapping it
  filters the scrubber to the bookmarked pages and takes the accent, tapping it
  again restores the full strip. In Guided View the left chip still works and the
  right one is hidden, since there is no scrubber to filter; bookmarks are
  page-level, never panel-level. Both slots are the same width, so the counter
  stays centred whether or not the filter is showing. The filter is session
  state on `ReaderUiState` (`bookmarksOnly`): it is dropped when the chrome
  hides, when a thumbnail is tapped, and when the last bookmark is removed. The
  chips are the only affordance — no long-press, no new gesture, nothing added to
  the eye or gear panel. Bookmarks themselves live in Room (`bookmark`, keyed by
  the library comic id, see `docs/library.md`), so they survive the session; a
  comic that is not in the library has no id to key on and shows no chip.
- Reaching the end of a comic raises `EndOfComicOverlay`. It is the last child of
  the reader `Box`, so its scrim covers the chrome — including the progress bar,
  which never hides — and the chrome is hidden for as long as the overlay is up,
  so nothing behind it stays bright. That hiding is a gate on the rendered
  visibility, not a write to `chromeVisible`, so it leaves the strip's
  hide-on-scroll rule alone: dismissing the overlay returns the chrome to
  whatever the mode had it at. The card offers **Next issue** (filled, only when
  the library knows a next issue), **Back to library** (tonal) and **Close**
  (text), and a tap outside dismisses it.

## Thumbnail scrubber

- Thumbnails come from a dedicated path in `PageLoader` (`loadThumb`) that decodes
  each page at a small thumb width into its own LRU cache, separate from the
  full-page cache so scrubbing never evicts reading-quality bitmaps.
- Decoding is lazy: the strip is a `LazyRow`, so only visible cells request their
  thumbnail as they scroll into view — pages are never decoded eagerly.
- The strip re-centres on the current page every time that page changes while the
  chrome is visible, not only when the chrome opens, so a jump made from
  elsewhere (the end-of-comic overlay, a page turn) always leaves the highlighted
  thumbnail on screen. While the chrome is hidden it re-centres nothing.
- Jumping is unidirectional: a tap raises a `pendingJump` on `ReaderUiState`; the
  active surface's pager consumes it (`scrollToPage`) and clears it, so the jump
  lands on the same page state the pager and `onPageChanged` already drive. In the
  two-page spread the target page maps to its spread step
  (`ThumbnailStrip.stepIndexForPage`).
- A bookmarked page carries a small accent corner ribbon on the top-right of its
  thumbnail. The 2 dp accent border stays reserved for "current page", so a page
  that is both reads unambiguously.
- Filtering to bookmarks keeps the same `LazyRow` and the same cell; only the
  index list changes (`Bookmarks.scrubberPages`). Tapping a cell while filtered
  raises the usual `pendingJump` **and** restores the full strip, so a jump always
  leaves the reader in the normal state, and re-centring is skipped while filtered
  because the list is short. In the strip the filter covers the active issue's
  pages only, the same "active comic" rule the counter and progress follow.

## Preloading

Regardless of surface, the reader preloads the next `N` page bitmaps (and, in
spread mode, the next pair) through Coil so page turns have no decode latency. `N`
scales down under memory pressure.

## Page fit

A dense page on the folded screen is unreadable whole, and pinching it open on
every turn is not reading. The fit is therefore the **base scale of every page**,
kept per comic (`ComicSettings.fitWidth`, a row in the eye panel that flips
between Screen and Width and leaves the panel open, because it is a state you
keep rather than a destination).

- **Fit screen** (default) is today's `ContentScale.Fit` — the whole page
  visible, unchanged in every respect. Nothing extra enters the modifier chain.
- **Fit width** lays the page out as a box the width of the viewport with the
  page's own aspect, inside the pannable container — the construction the strip
  already uses — rather than hanging a scale off the zoom, whose pan bounds come
  from the container and not from the drawn image. When it overflows vertically,
  one finger pans it, clamped to the page and with the usual release fling.

All of the geometry is one pure object, `PageFit` (`reader/domain`,
unit-tested): the content size for a container and a page aspect, the pan bounds
for a scale, the clamp, and the landing offset. Fit screen falls out of the same
formulas with content size == container size, so there is one code path, not
two.

- **A page turn in fit width lands on the page's top edge**, horizontally
  centred, at the fit-width scale — the previous page's scroll offset is never
  carried over. That is free: the offset state is `null` until something moves
  it and resolves to the top edge, so nothing has to be reset on a turn.
- **Double-tap always means "show me the other framing."** Under fit screen it
  is unchanged (2.5× on the tapped point, and back). Under fit width it zooms
  *out* to fit screen, and a second double-tap returns to fit width **at the same
  vertical position**. `PageFit.doubleTap` decides which of the four actions a
  tap means.
- **Pinch is unchanged:** free up to `MAX_SCALE` (5×) measured from the fit base,
  reset on a page turn. The one-finger pan at the base scale only starts on a
  drag that is past `touchSlop` and *not* horizontal, so a horizontal swipe still
  reaches the pager and turns the page.
- **Posture needs no special case**, because the fit applies to each rendered
  page area: in `UnfoldedSpread` each half fits its own half-width (for a
  portrait page that is visually identical to fit screen), in `Tabletop` it
  applies to the short, wide area above the hinge, where it earns its keep, and
  `CompactSingle` is the case the feature exists for. PDF and split halves behave
  identically — the aspect comes from the decoded page.
- **Guided View and the strip hide the row** (`mode == ReaderViewMode.Pages`
  gates it, mirroring `mode.allowsBubbles()`): Guided View frames the panel and
  the strip is fit to width by definition. The stored value is untouched and
  returns with Pages.

The per-comic settings screen carries the same value as a Screen · Width choice
row (no "Default" chip, since there is no global default), so a whole series can
be set to fit width at once.

Reset is one tap of the row back to Screen. There is deliberately no fit height
(on a portrait screen showing a portrait page it *is* fit screen), no remembered
arbitrary pinch (a 4× state you cannot see, name or undo), and no global or
per-posture default — the setting follows the comic's page density, not the
device.

## Page look

Scans differ far more than screens do: a dark 90s scan, a washed-out one and a
yellowed newsprint one all want a different curve. The reader offers four named
looks per comic (`PageLook` in `reader/domain`, persisted as
`ComicSettings.pageLook` by enum name), cycled from one gear-panel row:
Original → Brighter → More contrast → Paper → Original, so Original — the reset
— is never more than three taps away.

Each look is a `ColorMatrix` built by a pure function from a contrast about the
mid level (128), an optional lift and optional per-channel gain:

- **Original** — identity, and **nothing is installed in the modifier chain at
  all**.
- **Brighter** — a +20 lift, then contrast ×1.05 about mid.
- **More contrast** — contrast ×1.25 about mid (which is exactly an offset of
  −32 per channel) then saturation ×1.05.
- **Paper** — contrast ×1.18 about mid, with channel gains R ×0.98, G ×1.00,
  B ×1.06 to pull the yellow cast out of newsprint scans.

They are unit-tested by applying the matrix to a colour rather than by
comparing coefficients (`PageLookTest`): mid grey survives More contrast
unchanged, Paper cools it, Brighter lifts black to 14.6.

The filter is applied by wrapping the page content in **one** `saveLayer`
(`Modifier.pageLook`: `drawWithContent` → `drawIntoCanvas` → `saveLayer(paint)`
→ `drawContent()` → `restore()`), so the page bitmap, the enlarged-bubble copies
and the crescent fill get the same treatment and can never diverge. It applies
on every surface — single page, spread (both halves), tabletop, Guided View, the
vertical strip, PDF and split halves — with no posture special cases.

Two rules keep it cheap:

- **In the strip the filter goes on each page item, never around the
  `LazyColumn`** — a composited layer over a scrolling list is the 700 ms p99
  regression described above.
- **Original adds no layer**, the same discipline as the strip's zoom
  `graphicsLayer`.

It is a draw-time filter only: no decode is invalidated and no detection cache
is keyed on it, so cycling is instant and costs no re-decode. It deliberately
does **not** reach the thumbnail scrubber (navigation) or library covers
(identity), and the glitch report's `page.jpg` stays unfiltered so it still
shows what the detector saw — the look is recorded as a `pageLook` field in
`report.json` instead.

Night tint is orthogonal: it stays global and is drawn above the page and below
the chrome, so it composes over an adjusted page. The two rows sit next to each
other in the gear panel so the relationship is legible.

The per-comic settings screen spells the four looks out as a choice row (again
with no "Default" chip), so a yellowed run can be set to Paper in one go.

## Night tint

An optional warm amber scrim for comfortable low-light reading, toggled from the
HUD. It is drawn as a translucent layer above the page content and ambient
backdrop but below the top/bottom chrome, so it never dims the controls
themselves. The preference is persisted with DataStore
(`ReaderPreferencesRepository`) and restored on the next session; it only
affects the reader, never the library.
