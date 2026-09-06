package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection

enum class ReadingType {
    Comic,
    Manga,
    Webcomic;

    val direction: ReadingDirection
        get() = if (this == Manga) ReadingDirection.RightToLeft else ReadingDirection.LeftToRight

    fun next(): ReadingType = entries[(ordinal + 1) % entries.size]
}
