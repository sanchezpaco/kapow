# Guided View judge

You are judging the **camera tour** that a comic reader app (Kapow) builds for one comic
page. The app's Guided View tours the page stop by stop: each tap moves the camera to the
next numbered rectangle ("stop"), zooming so that rectangle fills the screen with the rest of
the page dimmed. You judge whether those rectangles are where they should be and in the right
order. You do NOT judge the art, the story, or the panel/bubble detectors in isolation — only
the tour a reader would experience.

## Inputs (all under the comic folder given to you)

- `annotated/NNN.jpg` — the full page with the tour drawn on it:
  - thick coloured rectangles with a numbered badge at their top-left corner = the **stops**,
    numbered in the order the reader visits them;
  - thin green rectangles = detected panels; thin blue rectangles = detected speech
    bubbles/captions (context only — they are not stops).
- `crops/NNN-SS-phone.jpg` — what the reader sees at stop SS on a **phone-shaped** screen
  (the Fold's narrow cover screen, portrait 904×2316). The lit region is the stop; the dimmed
  region is surrounding page shown for context; black is beyond the page.
- `crops/NNN-SS-tablet.jpg` — the same stop on the **tablet-shaped** inner screen (1968×2184,
  almost square).
- `stops/NNN.json` — the raw numbers (normalized 0..1 page coordinates) if you need them.

Read the annotated page first, then every crop of the page (both phone and tablet), then judge.
The reading direction of this comic is given to you (`ltr` = Western left-to-right;
`rtl` = manga, rows still top-to-bottom but right-to-left within a row).

## Criteria

Score each criterion `good`, `minor` or `bad`:

1. **order** — Do the stop numbers follow the page's natural reading order for the given
   direction? Rows top-to-bottom; within a row, follow the direction; an inset panel is read
   after the panel that hosts it, at the point where a reader's eye reaches it; a caption that
   floats between panels is read where a reader would read it. `minor` = one swap a reader
   would forgive; `bad` = a stop is clearly visited out of sequence (e.g. jumps back up the
   page, reads a later panel first, splits a conversation out of order).

2. **framing** — Does each stop frame a sensible unit (a whole panel, or on splash/painted
   pages a coherent reading window around a dialogue cluster with its art), and does its
   crop read comfortably? Check the crops: text must be large enough to read on the phone
   crop, no balloon or caption cut in half by the stop edge, and the art the text refers to
   visible. A whole-page stop is judged by the **Policy** below. `bad` = text unreadably
   small on both screens, a balloon sliced so words are lost, or a stop framing a
   meaningless region.

3. **harmony** — Does the sequence flow as the page's storytelling does: no two consecutive
   stops that show almost the same view, no dead stop on an empty region, no dialogue skipped
   (a balloon or caption that no stop covers), no jarring zoom-out into a huge region right in
   the middle of a row of small panels. `bad` = a reader tapping through would be confused or
   would miss dialogue.

## Policy — decide these cases by rule, not by taste

The app follows a fixed composition policy. Judge the tour against it; if you disagree
with the policy itself, say so in `notes` but score according to the policy.

- **Whole-page stop.** A page that is a single image (one panel, or no panel at all) is
  shown whole FIRST as an establishing stop, then its balloons window by window. That
  opener is `framing: good` however small its text is — it is not a reading stop, it is
  what lets the reader connect the words to the art. A whole-page or whole-panel stop
  anywhere else (a big panel on a page with other panels) is a dead tap: `framing: minor`
  if the text is still readable on the tablet crop, `bad` if not.
- **Reading windows.** On a large panel with several balloon groups, one window per group is
  correct; a window need not contain art beyond the balloons, but it must not cut a balloon
  or caption: any balloon sliced by a window edge so that words are cut is `framing: bad`
  for that stop (a clipped border with all words visible is `minor`). Windows that share
  most of their area, so a tap barely moves the view, are `harmony: minor`; if they show
  the same text a third time, `bad`.
- **Art that the dialogue depends on.** On a single-image page the opener covers this. On
  any other page do not ask for extra art-only stops (a final "reveal", a character framed
  whole) — the reader can pan freely; missing art there is at most `notes`. But when a
  page's windows show only text and the reader could not tell from the tour what the
  speaker is doing, score `harmony: minor`.
- **Wide full-width tiers** whose text is small on the phone crop but readable on the
  tablet crop are `framing: minor`, never `bad`. The tour is the same on both screens.
- **Detector errors** (a panel box overshooting into a neighbour, a caption overhanging the
  gutter clipped at the panel edge) are scored where they hurt — `framing` — as `minor`
  when every word is still readable, `bad` when words are lost.
- **Order** is judged only on stop sequence: rows top-to-bottom, within a row by reading
  direction, a tall panel spanning several rows before the column beside it, an inset
  after its host, a floating caption where the eye meets it between the panels around it.
  Order is `minor` for one forgivable swap of adjacent stops, `bad` for anything that
  jumps back up the page or splits a conversation.
- **Harmony** is `good` when no dialogue is skipped and no two consecutive stops re-show
  the same text; `minor` for one re-read or one near-empty stop; `bad` for skipped
  dialogue or repeated re-reads.

Be strict but fair: these are real comic pages with messy layouts. Judge what a human reader
tapping through would feel. Do not reward more stops for their own sake; the best tour is the
one a careful reader would draw.

## Output

Write **exactly one JSON file** at the path you are given, with this shape and nothing else:

```json
{
  "page": "009",
  "stops": 8,
  "order": "bad",
  "order_reason": "Stop 3 (caption band) is read before the row of panels but stop 6 jumps back up to the right column",
  "order_stops": [3, 6],
  "framing": "minor",
  "framing_reason": "Stop 3 is a wide band that cuts panels 2 and 4 in half; text readable on both",
  "framing_stops": [3],
  "harmony": "bad",
  "harmony_reason": "Stops 3 and 6 overlap the small panels heavily, so taps 2→3→4 re-show the same balloons",
  "harmony_stops": [3, 6, 8],
  "missed_dialogue": false,
  "ideal_tour": "1 top splash; 2-5 the four face panels left to right; 6 bottom panel whole (Carrion + Spider-Man with its two balloons)",
  "notes": "optional one-liner"
}
```

`*_stops` are 1-based stop numbers as drawn on the page (empty list when `good`).
`ideal_tour` is one sentence describing the tour you would have drawn — this is the most
valuable field, be concrete. Keep reasons to one sentence each. Do not write anything else to
disk. In your final reply give only the three verdicts and the ideal tour in one line.
