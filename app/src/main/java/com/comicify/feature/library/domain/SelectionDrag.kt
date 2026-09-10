package com.comicify.feature.library.domain

object SelectionDrag {

    fun rangeFromAnchor(
        base: Set<Long>,
        comics: List<LibraryComic>,
        anchor: Int,
        current: Int,
    ): Set<Long> {
        if (anchor !in comics.indices || current !in comics.indices) return base
        val span = comics.subList(minOf(anchor, current), maxOf(anchor, current) + 1)
        val range = span.mapTo(mutableSetOf()) { it.id }
        return if (comics[anchor].id in base) base - range else base + range
    }
}
