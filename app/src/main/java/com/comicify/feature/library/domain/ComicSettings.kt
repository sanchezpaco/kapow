package com.comicify.feature.library.domain

import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.domain.PageLook

data class ComicSettings(
    val direction: ReadingDirection? = null,
    val coverAlone: Boolean = false,
    val bubblesEnlarged: Boolean? = null,
    val guided: Boolean? = null,
    val bubbleScale: Float? = null,
    val splitWidePages: Boolean = false,
    val verticalScroll: Boolean = false,
    val pageLook: PageLook = PageLook.Original,
    val fitWidth: Boolean = false,
) {
    companion object {
        val Default = ComicSettings()
    }
}
