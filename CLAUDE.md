# CLAUDE.md

Guidance for working in this repository.

## Project

Kapow (repository `comicify`) is a native Android comic reader built for personal use, optimized first
and foremost for the **Samsung Z Fold** foldable experience. The reading
experience adapts to device posture (folded, unfolded, tabletop). See
`ROADMAP.md` for scope and `docs/` for how each part works.

## Language policy

- **Code, identifiers, docs, comments-in-VCS, commit messages: English only.**
- **The app UI is bilingual: Spanish and English** via Android resource
  qualifiers (`values-es/`, default `values/` in English). No user-facing string
  is ever hardcoded — every one lives in `strings.xml`. See `docs/i18n.md`.

## Code rules

These are hard rules. Do not relax them without being asked.

- **No code comments.** Code must explain itself through naming and structure.
  The only allowed exceptions are `// TODO(scope):` markers and KDoc on public
  library-style APIs when strictly necessary. If you feel the urge to comment a
  block, extract it into a well-named function instead.
- **Lean code.** Prefer the smallest correct solution. No speculative
  abstraction, no "just in case" parameters, no unused flexibility. Delete dead
  code on sight.
- **Clean code.** Small functions with a single responsibility. Descriptive,
  intention-revealing names. Early returns over nested conditionals. No magic
  numbers — name them.
- **Immutability by default.** `val` over `var`. Immutable data classes for
  state. Unidirectional data flow.
- **Pure where possible.** Push side effects (IO, DB, decoding) to the edges.
  Domain logic stays pure and testable.
- **Fail loud in dev.** No silent catches. Handle errors explicitly and model
  them in the type system (`Result`, sealed error types) rather than nulls.

## Architecture (summary)

- **Kotlin + Jetpack Compose**, single Gradle module, packaged by feature.
- **MVVM + unidirectional data flow (UDF).** Composables are dumb; state lives in
  `ViewModel`s exposed as immutable `StateFlow`; events flow up.
- **Layered per feature:** `ui` → `domain` → `data`. UI never touches data
  sources directly.
- Full details in `docs/architecture.md`.

## Tech stack

- UI: Jetpack Compose (Material 3, adaptive)
- Foldable: `androidx.window` (`WindowInfoTracker`, `FoldingFeature`)
- Images: Coil
- Persistence: Room (library, progress) + DataStore (preferences)
- DI: Hilt
- Archives: `java.util.zip` (CBZ), 7-Zip-JBinding (CBR, RAR4+RAR5), `PdfRenderer` (PDF)
- Panel detection (later phase): OpenCV / custom edge detection

## Working conventions

- Keep the diff minimal and focused on the request.
- Match the style of surrounding code.
- When adding a user-facing string, add it to **both** `values/strings.xml` and
  `values-es/strings.xml` in the same change.
- Update the relevant file in `docs/` when you change how something works.
