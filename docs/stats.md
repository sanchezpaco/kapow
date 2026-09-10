# Reading stats

What Kapow records while you read, and the one screen that shows it. Everything
here stays on the device: no account, no sync, no upload.

## What is recorded

One row per reading session, in the `reading_session` Room table
(`core/storage/ReadingSessionEntity.kt`, schema **v12**, migration `11 → 12`):

```
reading_session(
    id, comicId (FK Comic ON DELETE CASCADE, indexed),
    startedAt (epoch millis, indexed), endedAt,
    pages, mode (ReaderViewMode.name), finished,
)
```

Nothing else — no page numbers, no timestamps per page, no per-session log in
the UI. `finished` marks a session in which the comic reached its last page.

## When a session starts and ends

- **Starts** when the reader composes for a comic (`RecordReadingSession` in
  `KapowRoot.kt`, driving `ReadingSessionViewModel`). The mode is the one the
  comic opens in, resolved with the reader's own rule (`openModeOnOpen`: the
  comic's explicit mode, else the strip for a Webcomic, else the global
  Guided-on-open default) and it never changes for that session, so a
  session's pace is always comparable with the mode it belongs to.
- **Ends and is written** on the earliest of: the reader closing, `ON_STOP`
  (the app going to the background), or **10 idle minutes**. Coming back from
  the background starts a new session.
- `endedAt` is the **last page turn**, never `now` — a phone left on the sofa
  must not bill as reading time. The idle split is noticed on the next page
  turn: the old session is closed at its last turn and a new one opens.
- **Pages** ride the reader's existing `onPageChanged(comicId, pageIndex,
  pageCount)`; there is no new reader plumbing. Each event adds
  `min(abs(newIndex - lastIndex), 3)`, so a spread counts 2, Guided View counts
  1 per page (not per stop), the vertical strip counts 1 per dominant page and a
  scrubber jump is capped at 3. The first event after opening only anchors the
  position.
- **The vertical strip chains issues**, and a session belongs to one comic, so
  the first page event from the next issue closes the current session at that
  moment (the crossing is a page turn, unless the idle timeout already passed)
  and opens a new one for the next issue, in the same mode, anchored at its
  first page. `ReadingSessionViewModel` keeps the comic the reader was opened
  with apart from the comic the draft belongs to: closing the reader ends
  whichever session is open.
- **Discarded** (never written) when the session turned no page or lasted less
  than 20 s.

The state machine is pure and unit-tested (`feature/stats/domain/SessionRecorder.kt`,
`SessionRecorderTest`); the clock, the DAO and the lifecycle sit outside it.

## The pace rule

`feature/stats/domain/ReadingPace.kt` answers "how many seconds does this reader
spend on a page", and `LibraryCatalog.minutesLeft(pageIndex, pageCount,
secondsPerPage)` turns that into the library hero's `≈ 13 min left`:

1. sessions **of this series in the mode this comic will open in**, the last 10
   — the median of their seconds per page, used when at least 3 of them
   contribute and they cover at least 20 pages;
2. otherwise all sessions **in that mode**, the last 30, with the same
   "3 contributing sessions" gate;
3. otherwise **45 s** (`ReadingPace.FALLBACK_SECONDS_PER_PAGE`, the flat value
   the estimate used before stats existed).

A session contributes a value only when it turned at least 3 pages, and the
value is clamped to **10…300 s** before the median, so one session left open on
a page cannot poison the estimate. The string is unchanged: only the number
becomes true.

**Autoplayed sessions never contribute.** A session whose pages were turned by
autoplay (`reading_session.autoplayed`, set on the draft by
`SessionRecorder.onAutoplay` at the first autoplay-driven page event) is dropped
by `ReadingPace.contributing`, because a comic left running at 8 s a page would
rewrite the median that seeds autoplay's own default interval. Those sessions
still count everywhere else on the stats screen — time read, pages, streaks, the
series list — since the reader really was reading; they just do not get to say
how fast.

## Retention

Raw rows are kept — a personal library is a few hundred rows a year, and
aggregating over an indexed `startedAt` is free, so there is no rollup table.
Each write prunes rows older than **400 days**, and rows die with their comic
through the foreign key. **Settings → Delete reading history** empties the table
after a confirmation dialog. There is no on/off switch: nothing leaves the
device, so there is nothing to opt out of.

## The screen

Reached from the fourth button of the library toolbar
(`Search · Layers · Open file · Stats · Settings`). One scroll, no range chips —
every block states its own window:

1. **Hero** — pages read in the last 30 days.
2. **Four tiles** — time read, your pace, comics finished this month, days read
   out of 30.
3. **Pages per day** — 30 bars, always 30; a day without reading draws a stub so
   a gap reads as "no reading" rather than missing data. The tallest bar carries
   its value; under the chart, the first date, the average per day and "today".
4. **Series · Your pace** — up to 5 series, by time read, each with its issue
   count, its pace and a meter. Not tappable: a series detail screen is
   deferred.
5. **This month · Finished** — the covers of the comics finished this month
   (4 columns folded, 6 unfolded, capped at 12 then `+N`). Tapping one opens it.

On the unfolded screen the same blocks sit in two columns capped at 1040 dp:
hero, tiles and chart on the left, series and mosaic on the right.

Until there are **3 sessions and 20 pages**, the screen shows its empty state
instead — one bar at 100 % would be a lie — with a "Continue …" pill when there
is something to resume.

The aggregation (`feature/stats/domain/ReadingStats.kt`) is a pure function of
the session list, the time zone and the clock, and is unit-tested in
`ReadingStatsTest`. Numbers and dates are formatted with locale-aware APIs
(`StatsFormats.kt`), never concatenated; see `docs/i18n.md`.

## Deliberately not here

No streak (offline, single-user, personal library — there is nobody to retain),
no week-over-week deltas, no generated prose, no mode-mix breakdown, no session
log (`23:05 – 23:56` kept forever is a record of the user's bedtime), no export
and no all-time window.
