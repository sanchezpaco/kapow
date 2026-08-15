package com.comicify.feature.library.ui

import com.comicify.feature.library.domain.LibraryComic

data class LibraryUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val scanFailed: Boolean = false,
    val hasFolder: Boolean = false,
    val comics: List<LibraryComic> = emptyList(),
    val continueReading: List<LibraryComic> = emptyList(),
)
