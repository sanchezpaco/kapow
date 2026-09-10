package com.comicify.feature.library.domain

object SelectionDrag {

    fun rangeFromAnchor(
        base: Set<Long>,
        comics: List<LibraryComic>,
        anchor: IntRange,
        current: IntRange,
    ): Set<Long> {
        if (!comics.holds(anchor) || !comics.holds(current)) return base
        val span = comics.subList(minOf(anchor.first, current.first), maxOf(anchor.last, current.last) + 1)
        val ids = span.mapTo(mutableSetOf()) { it.id }
        val anchored = comics.subList(anchor.first, anchor.last + 1).map { it.id }
        return if (anchored.all { it in base }) base - ids else base + ids
    }

    private fun List<LibraryComic>.holds(span: IntRange): Boolean =
        !span.isEmpty() && span.first in indices && span.last in indices
}
