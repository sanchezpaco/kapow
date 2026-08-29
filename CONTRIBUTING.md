# Contributing

Kapow is a personal project published so that others can read, build and adapt
it. Issues and pull requests are welcome, but there is no support commitment
and a pull request may be declined when it does not fit the roadmap.

- Read `CLAUDE.md` first: it holds the code rules (no comments, lean, English
  identifiers, bilingual ES/EN strings) that every change must follow.
- `ROADMAP.md` lists what is planned and what was deliberately rejected;
  check it before proposing a feature.
- Each concern is documented in `docs/`. When a change alters how something
  works, update the matching doc in the same pull request.
- Run `make test` (or `./gradlew :app:testDebugUnitTest`) before opening a
  pull request; CI runs the same build.
- Bug reports about a page that renders wrongly are most useful with the
  files produced by **Report a visual glitch** in the reader (see
  `docs/glitch-report.md`); never attach a copyrighted comic.
