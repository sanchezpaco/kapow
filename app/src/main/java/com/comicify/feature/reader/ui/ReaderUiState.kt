package com.comicify.feature.reader.ui

import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingPosition

data class ReaderUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val pageCount: Int = 0,
    val position: ReadingPosition = ReadingPosition(),
    val chromeVisible: Boolean = false,
    val guided: Boolean = false,
    val guidedFullScreen: Boolean = false,
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
)
