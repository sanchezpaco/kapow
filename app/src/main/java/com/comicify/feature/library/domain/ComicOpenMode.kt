package com.comicify.feature.library.domain

import com.comicify.feature.reader.domain.ReaderViewMode

fun ComicSettings.openMode(): ReaderViewMode? = when {
    verticalScroll -> ReaderViewMode.Strip
    guided == null -> null
    guided -> ReaderViewMode.Guided
    else -> ReaderViewMode.Pages
}

fun ComicSettings.withOpenMode(mode: ReaderViewMode?): ComicSettings = when (mode) {
    null -> copy(guided = null, verticalScroll = false)
    ReaderViewMode.Pages -> copy(guided = false, verticalScroll = false)
    ReaderViewMode.Guided -> copy(guided = true, verticalScroll = false)
    ReaderViewMode.Strip -> copy(verticalScroll = true)
}
