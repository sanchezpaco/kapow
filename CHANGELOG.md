# Changelog

All notable user-facing changes to Kapow. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions match the
`versionName` in `app/build.gradle.kts` and the `v*` git tags. The Play
"What's new" text lives in `fastlane/metadata/android/*/changelogs/`.

## [Unreleased]

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
