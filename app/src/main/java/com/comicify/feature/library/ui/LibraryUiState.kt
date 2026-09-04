package com.comicify.feature.library.ui

import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryEntry
import com.comicify.feature.library.domain.LibraryFilter
import com.comicify.feature.library.domain.LibraryScanError
import com.comicify.feature.library.domain.LibrarySort

data class LibraryUiState(
    val loading: Boolean = true,
    val scanning: Boolean = false,
    val scanError: LibraryScanError? = null,
    val folderUri: String? = null,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sort: LibrarySort = LibrarySort.TITLE,
    val query: String = "",
    val openedSeries: String? = null,
    val grouped: Boolean = false,
    val comics: List<LibraryComic> = emptyList(),
    val allComics: List<LibraryComic> = emptyList(),
    val entries: List<LibraryEntry> = emptyList(),
    val continueReading: List<LibraryComic> = emptyList(),
    val continueReadingVisible: Boolean = false,
    val totalCount: Int = 0,
)
