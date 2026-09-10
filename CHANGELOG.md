# Changelog

All notable user-facing changes to Kapow. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions match the
`versionName` in `app/build.gradle.kts` and the `v*` git tags. The Play
"What's new" text lives in `fastlane/metadata/android/*/changelogs/`.

## [Unreleased]

### Added
- Autoplay: a Timer switch in the reader's view-mode panel reads the comic on
  its own, with a stepper for the seconds per page (3–120 s, one second per tap;
  hold a button and it repeats, then moves in fives so the number stays
  readable). It turns the page in Pages — twice the time on the unfolded spread,
  where two pages are on screen — moves panel by panel in Guided View, sharing a
  page's seconds between its panels and never going below 1.5 s each, and
  scrolls the vertical strip down continuously at one page per interval,
  carrying on into the next issue. The first interval it offers is your own
  average from reading stats.
- While autoplay runs, a pause button in the bottom corner counts the page down
  around its rim and stays put when the rest of the HUD hides. Tap it to pause,
  tap again to carry on. Touch the page anywhere and autoplay holds by itself —
  turn the page, scroll, zoom or read on at your own speed, and it picks up
  again from a full page when you lift your finger. Switching it off is the
  switch in the view-mode panel, leaving the app, or the end of the comic.

### Fixed
- Pages turned by autoplay no longer skew the reading pace behind "≈ 13 min
  left" and the stats screen's pace tiles. Those sessions still count as time
  and pages read.

## [1.0.5] - 2026-09-07

### Fixed
- Reading stats now follow the vertical scroll into the next issue. Scrolling
  from the end of one issue into the next closes the session of the one you
  finished, marks it finished, and opens a session for the next issue; before,
  everything read past the boundary was lost.
- A session's mode is resolved with the same rule the reader uses to open the
  comic, so a Webcomic that opens in the vertical scroll counts as such.

## [1.0.4] - 2026-09-06

### Added
- A comic is now recognised by its content, not its path: rename it, move it
  into a subfolder or pick the library folder again after a reinstall and it
  keeps its progress, favourite, bookmarks, settings and cached detections.
- `.cb7` (7z) and `.cbt` (tar) archives open like CBZ and CBR; the format is
  read from the file's bytes, not its extension.
- Library search understands fields: `writer:ewing`, `series:hulk`,
  `publisher:marvel`, `year>2015`, `rating:4+`, `added:30d`, `is:reading`,
  with Spanish keys too (`guionista:`, `editorial:`, `año>`). Three preset
  chips under the empty field — In progress, Highly rated, Recently added —
  type their query for you so the syntax is learned by seeing it. Words
  without a key still search every field, accents ignored.
- Bookmarks: the bookmark button beside the page counter marks the page you
  are on; a second chip counts them and filters the thumbnail strip down to
  the bookmarked pages. Bookmarks follow a comic through renames and splits.
- Long-press a cover → **Choose cover** to make any page the cover; **Use
  first page** puts it back. The shelf, the series stack, the hero glow and the
  reader's opening colour all follow.
- Reading lists: tap the shelf title ("All comics ▾") to switch to a list or
  create one. Add comics from a cover's long-press menu, reorder with Move up /
  Move down, remove or delete a list with undo. Reading from a list, "Next
  issue" follows the list across series.
- Rate a comic out of five in its Details; tap the current star to clear.
  **Edit details** corrects a wrong series, issue number or story title, and
  the correction survives every rescan until you choose **Reset to file**.
- **Share this page** in the reader's gear panel sends the page you are looking
  at at full resolution, with enlarged bubbles and page look baked in and no
  chrome; a spread shares both pages joined, Guided View shares the panel.
- **Page look** per comic in the gear panel and in the comic's settings:
  Original, Brighter, More contrast or Paper, for dark or yellowed scans.
- **Page fit** per comic in the eye panel: fit to screen or fit to width, so a
  dense page on the folded screen no longer needs a pinch on every turn.
  Double-tap under fit width shows the other framing.
- Settings → Appearance: pick the launcher icon. The same bubble and K over a
  blue, ink, red, violet or mixed comic page; the home screen shows the new
  icon a moment after you choose it.
- Comics that carry a `ComicInfo.xml` now show what is really inside them
  instead of what the file happens to be called: the right series, issue and
  year on the covers, and the story title on "Continue reading" and inside a
  series.
- Long-press a cover → **Details** for the summary, the credits (writer,
  pencils, inks, colours), the publisher and the file name the metadata
  replaced.
- Reading stats: a new screen from the library toolbar with the pages you read
  in the last 30 days, the time you spent, your pace, the days you read, a
  30-day chart, your pace per series and the comics you finished this month.
  Everything is measured on this phone only, "time left" on the shelf now
  follows your real pace, and the history can be deleted from Settings.

### Changed
- "Reading direction" is now a **Reading type**: Comic, Manga (right to left)
  or Webcomic, per comic and as the app default. A webcomic opens in vertical
  scroll unless the comic says otherwise; picking Webcomic in the reader
  offers the switch instead of forcing it. A `ComicInfo.xml` marked manga
  sets the type by itself. Existing right-to-left comics become Manga.
- The library counts "1 comic" rather than "1 comics".

### Fixed
- Library search no longer drops or reorders letters when you type fast.
- Enlarged bubbles keep up with page turns the first time through a comic.
  Every page was being decoded twice — once for the bubble detector and again
  for the layout that runs after it — so on a comic the app had not seen
  before, the bubbles fell several pages behind the reader and the page you
  were looking at showed a spinner instead of enlarged balloons. Pages are now
  decoded once, and the pages ahead of you are prepared before the one behind.
- A `.cbt` written on a Mac counted its `PaxHeader` bookkeeping entries as
  pages (a three-page tar showed six); those and `__MACOSX` resource forks are
  skipped in every archive format.
- Changing any setting on a comic's settings screen no longer resets its page
  look and page fit, nor re-arms the "split wide pages" suggestion.
- The pages-per-day average keeps a decimal so a light month does not read as
  zero.

## [1.0.3] - 2026-09-04

### Changed
- Reader controls: the eye and the gear now open one labelled panel each
  instead of floating pills and unlabelled icons. Pages / Guided view /
  Vertical scroll are a single choice with a check mark, "Enlarged bubbles"
  is a switch with a −/+ size stepper under it, the gear rows carry their
  state, opening one panel closes the other, and a small dot on the button
  marks a non-default setting.
- Reader: the bottom chrome (thumbnails, page counter) sits on a dark gradient
  like the top bar, so it stays readable over light pages; the end-of-comic
  card hides the chrome, leads with "Next issue" when there is one and closes
  with "Close".
- Library: the filter chips sit above "Continue reading" and never move when a
  filter hides it; "Recent" is a sort toggle on the shelf title instead of a
  fifth chip; the group-by-series button uses the stack icon.
- Settings: both settings screens are grouped surfaces with switches for
  on/off options, chips only for real choices, a single "Mode on open" choice
  per comic (Pages / Guided view / Vertical scroll), background preview tiles,
  a centred column (two on wide screens) and a kept scroll position when
  coming back from the licences.
- Snackbars use the app palette instead of the Material defaults.
- Onboarding's third page now covers all three reading modes — Guided View,
  the continuous vertical strip and enlarged speech bubbles — under the title
  "Three ways to read", with a new illustration so its title lines up with
  the other two pages.

### Fixed
- A finished comic stays "Completed" when you page back into it; only "Mark as
  unread" clears it.

## [1.0.2] - 2026-08-31

### Fixed
- Tinted caption boxes (parchment-yellow narration in LOK: Soul Reaver,
  Fallen Brothers) no longer cut their last lines when enlarged: the per-box
  paper tone now tolerates the texture of strongly tinted paper.
- Enlarged bubbles no longer bury each other's text. Two balloons the
  artist drew overlapping used to grow into each other unchecked — on the
  worst pages a balloon ended up almost entirely hidden and unreadable
  (Venomverse, Shangri-La, Spiderman 2099, LOK: Soul Reaver). No enlarged
  copy may now cover more than a tenth of its neighbour, whatever the artist
  drew; anything beyond that is resolved by moving or shrinking the pair, and
  collision resolution picks positions by how deep bubbles intrude instead of
  how many collide. Across a broad test corpus the pages left with any
  balloon's text still hidden dropped to zero, and no balloon is ever left
  un-enlarged — at the cost of slightly smaller bubbles on crowded pages.
- No more faint ghost of the original behind linked balloons. When the artist
  draws one continuous speech as two heavily overlapping lobes, the two
  enlarged copies used to be pushed apart and the gap between them showed a
  faint double of the text underneath (Shangri-La, manga shout clusters).
  Such linked pairs are now enlarged as a single balloon, so no gap opens.
  Saved detections are recomputed on next read (detections version bump).
- Less smudge behind enlarged bubbles. When a copy has to slide aside to avoid
  covering a neighbour, it now slides back over its own original as far as the
  free space allows, so the vacated area it leaves for the background fill to
  paint is as small as possible — noticeably cleaner over detailed art. It
  never slides back over a neighbour's text.

### Changed
- Better speech-bubble detection (student v5): trained on eleven more series
  including painted, cartoon, European-album and PDF comics; fewer boxes
  drawn over sound effects, logos and screen graphics, and better caption
  coverage in dense European pages. Saved detections are recomputed on next
  read (detections version bump).

## [1.0.1] — 2026-08-30

First round of tester feedback.

### Added
- Per-comic "Split wide pages" setting that cuts landscape pages in half and
  reads each half as its own page, in the comic settings and in the reader
  gear menu. Kapow suggests it once when most of a comic's pages are
  landscape.
- The onboarding folder step confirms the folder you picked and shows its
  name.

### Changed
- The library rescans the folder whenever the app comes back to the
  foreground, so comics added while it was open show up on their own.
- Guided View enters every page on its first panel instead of showing the
  whole page first.

### Fixed
- Double-tap to zoom no longer nudges the page instead of toggling the zoom
  on phones whose taps wobble a few pixels.
- The thumbnail strip refreshes when the page list changes.

## [1.0.0] — 2026-08-29

First release: CBZ, CBR and PDF reading, foldable-aware layouts (single page,
two-page spread, tabletop, cover screen), Guided View with ML panel
detection, enlarged speech bubbles, library with series, progress and
per-comic settings, bilingual UI, three themes, onboarding and glitch
reports.

[1.0.1]: https://github.com/sanchezpaco/kapow/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/sanchezpaco/kapow/releases/tag/v1.0.0
