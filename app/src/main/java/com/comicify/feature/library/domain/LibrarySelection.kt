package com.comicify.feature.library.domain

object LibrarySelection {

    fun toggle(selected: Set<Long>, comicId: Long): Set<Long> =
        if (comicId in selected) selected - comicId else selected + comicId

    fun reconcile(selected: Set<Long>, comics: List<LibraryComic>): Set<Long> {
        if (selected.isEmpty()) return emptySet()
        val shelved = comics.mapTo(mutableSetOf()) { it.id }
        return selected.intersect(shelved)
    }

    fun comics(selected: Set<Long>, comics: List<LibraryComic>): List<LibraryComic> =
        comics.filter { it.id in selected }

    fun allSelected(selected: Set<Long>, comics: List<LibraryComic>): Boolean =
        comics.isNotEmpty() && comics.all { it.id in selected }
}
