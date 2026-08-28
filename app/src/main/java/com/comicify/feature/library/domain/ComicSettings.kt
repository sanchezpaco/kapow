package com.comicify.feature.library.domain

import com.comicify.domain.model.ReadingDirection

data class ComicSettings(
    val direction: ReadingDirection? = null,
    val coverAlone: Boolean = false,
    val bubblesEnlarged: Boolean? = null,
    val guided: Boolean? = null,
) {
    companion object {
        val Default = ComicSettings()
    }
}
