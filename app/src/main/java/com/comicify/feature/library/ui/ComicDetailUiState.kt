package com.comicify.feature.library.ui

import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryComic

data class ComicDetailUiState(
    val comic: LibraryComic? = null,
    val series: List<LibraryComic> = emptyList(),
    val nextUnread: LibraryComic? = null,
    val settings: ComicSettings = ComicSettings.Default,
    val pageCount: Int = 0,
)
