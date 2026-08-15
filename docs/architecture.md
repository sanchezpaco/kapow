# Architecture

## Overview

Native Android, Kotlin + Jetpack Compose, single Gradle module packaged **by
feature**. MVVM with strict unidirectional data flow (UDF).

```
com.comicify
├── app            App entry, single Activity, navigation host, theme
├── core           Shared building blocks (no feature knowledge)
│   ├── ui         Design system: theme, tokens, reusable composables
│   ├── image      Coil setup, decoding helpers, dominant-color extraction
│   ├── storage    Room database, DAOs, DataStore preferences
│   └── window     Foldable posture tracking (androidx.window wrappers)
├── feature
│   ├── reader     The reading surfaces (full page, spread, guided)
│   ├── library    Collection grid, import, continue-reading
│   └── settings   Preferences UI
└── domain         Pure models + use cases shared across features
```

Each `feature/*` is internally layered:

```
feature/reader
├── ui        Composables + ReaderViewModel + ReaderUiState (immutable)
├── domain    Use cases, pure models (Page, Panel, Posture-agnostic state)
└── data      Repositories, archive readers, decoders (side effects live here)
```

## Data flow (UDF)

```
User gesture ──▶ ViewModel event ──▶ Use case ──▶ Repository/data
                     │                                   │
                     └────── new immutable UiState ◀──────┘
                                    │
                              StateFlow ──▶ Composable renders
```

- Composables are **dumb**: they render `UiState` and emit events. No business
  logic, no direct data access.
- `ViewModel` owns state as `StateFlow<UiState>`. `UiState` is an immutable data
  class. State is only ever replaced, never mutated in place.
- Side effects (archive IO, image decode, DB) are confined to the `data` layer
  and invoked through use cases.

## Key decisions

- **Single module first.** Feature modules add build complexity we don't need
  yet. Package boundaries enforce structure; we split into modules only if build
  times demand it.
- **Hilt for DI.** Standard, low-boilerplate, scales with the app. Constructor
  injection everywhere; no service locators.
- **Coil for images.** Compose-native, good memory behavior for large pages,
  easy custom decoders for archive entries.
- **Room + DataStore.** Room for the library and reading positions; DataStore
  for simple key-value preferences.
- **Posture is state, not a branch everywhere.** The current foldable posture is
  observed once and folded into `UiState`; the reader picks a rendering strategy
  from it. See `foldable.md`.

## Testing

- Domain use cases and panel-detection logic are pure and unit-tested.
- Repositories tested against real small sample archives in `androidTest`
  assets.
- UI: screenshot/interaction tests deferred until surfaces stabilize.

## Module dependency rule

`feature → domain → core`. Features never depend on each other. `core` never
depends on `feature`. `domain` holds no Android framework types where avoidable.
