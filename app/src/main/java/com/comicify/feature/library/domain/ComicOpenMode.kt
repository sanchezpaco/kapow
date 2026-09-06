package com.comicify.feature.library.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.reader.domain.ReadingType

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

fun defaultOpenMode(type: ReadingType, guidedOnOpen: Boolean): ReaderViewMode = when {
    type == ReadingType.Webcomic -> ReaderViewMode.Strip
    guidedOnOpen -> ReaderViewMode.Guided
    else -> ReaderViewMode.Pages
}

fun ComicSettings.openModeOnOpen(type: ReadingType, guidedOnOpen: Boolean): ReaderViewMode =
    openMode() ?: defaultOpenMode(type, guidedOnOpen)
