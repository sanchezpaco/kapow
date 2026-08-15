package com.comicify.feature.reader.ui

import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingPosition
import com.comicify.feature.reader.domain.ComicOpenError

data class ReaderUiState(
    val loading: Boolean = true,
    val error: ComicOpenError? = null,
    val pageCount: Int = 0,
    val position: ReadingPosition = ReadingPosition(),
    val chromeVisible: Boolean = false,
    val guided: Boolean = false,
    val guidedFullScreen: Boolean = false,
    val volumeKeyPagingEnabled: Boolean = true,
    val nightTintEnabled: Boolean = false,
    val pendingJump: Int? = null,
    val direction: ReadingDirection = ReadingDirection.LeftToRight,
)
