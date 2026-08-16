package com.comicify.feature.library.ui

import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryFilter

data class LibraryUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val scanFailed: Boolean = false,
    val hasFolder: Boolean = false,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val comics: List<LibraryComic> = emptyList(),
    val continueReading: List<LibraryComic> = emptyList(),
    val totalCount: Int = 0,
)
