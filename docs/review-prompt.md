# Rating prompt

Kapow asks for a Play Store rating through Google Play's **In-App Review**
flow (`com.google.android.play:review-ktx`). There is no home-made "do you
like the app?" dialog: the flow is requested and Google decides whether the
rating card actually appears (it has a quota and never reports whether it
was shown).

## When

`feature/review/domain/ReviewPromptPolicy.kt` (pure, unit-tested):

- at least **3 comics finished** (a comic counts once, when its progress
  first reaches the last page — `LibraryRepository.saveProgress` returns
  whether that just happened and `LibraryViewModel.comicFinished` relays it);
- at most **once every 60 days**;
- only when the reader closes back to the library, never on cold start and
  never inside the reader.

Counters live in the `review_prompt` DataStore
(`feature/review/data/ReviewPromptPreferences.kt`): finished comics and the
time of the last request. The request time is recorded before launching the
flow, so a quota-suppressed card still counts and does not retry sooner.

## Wiring

`KapowRoot` collects `LibraryViewModel.comicFinished` into
`ReviewPromptViewModel.onComicFinished()`, calls `onReaderClosed()` from the
reader's `onClose`, and `InAppReviewPrompt` launches the flow on the
activity when the view model emits. A `ReviewException` (no Play Store, an
unsupported install) is logged and dropped.

## Testing

The real card only appears for builds installed from Google Play (internal
testing counts); sideloaded debug builds log `In-app review unavailable`.
