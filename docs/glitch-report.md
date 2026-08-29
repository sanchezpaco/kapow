# Glitch report

"Report a visual glitch" lives in the reader HUD settings menu (gear → bug icon).
It exists so a reader can send a reproducible sample of a rendering problem —
a badly enlarged bubble, a missed panel, a wrong crop — without a backend, an
SDK or a free-text form.

## Flow

1. The bug icon opens a confirmation dialog (`GlitchReportDialog`) stating that
   an image of the page and technical data, no personal data, will be attached
   to an email.
2. On confirm `ReaderViewModel.reportGlitch(posture)` snapshots the current
   state into a `GlitchReportRequest` (comic URI, page, page count, guided,
   bubble scale when enlarged, posture) and composes the report on
   `Dispatchers.IO`.
3. The resulting chooser `Intent` is emitted on `ReaderViewModel.shareRequests`
   and `ReaderScreen` starts it. The user picks the mail app; the mail body
   carries a one-line summary they can edit.

## Payload

`GlitchReport` (`feature/reader/ui`) writes two files into `cache/reports/`,
wiping the previous report first:

- `page.jpg` — the page at analysis size (`PageArt.analysis`, ≤1000 px on the
  short side) with the bubble overlay drawn through the same
  `BubbleOverlay.drawBubbles` the reader uses. The HUD is never part of it.
- `report.json` — `file` (display name only), `page`, `pageCount`, `guided`,
  `bubblesEnlarged`, `bubbleScale`, `posture`, `device`, `androidSdk`,
  `appVersion`, `appVersionCode`, `build` (`BUILD_LABEL`), `detectionsVersion`
  and the page's `panels` and `bubbles` in the exact `PageDetectionCodec`
  strings Room stores, so the layout can be replayed offline with the
  visualizer without the comic.

Both files are shared through `androidx.core.content.FileProvider`
(`${applicationId}.fileprovider`, `res/xml/file_paths.xml`) with
`ACTION_SEND_MULTIPLE`, `EXTRA_EMAIL = sanchezpacodev@gmail.com`, a prefilled
subject, `FLAG_GRANT_READ_URI_PERMISSION` and the two URIs as `ClipData` so
the sharesheet can preview them. The chooser lists every share target rather
than mail apps only: a `mailto:` selector does not resolve on every device
(the emulator's Gmail does not answer it), and any mail app picked from the
plain chooser still prefills recipient and subject.

## Verifying

Share from the emulator to any mail app, then inspect
`run-as <appId> ls -l cache/reports/` and pull both files.
