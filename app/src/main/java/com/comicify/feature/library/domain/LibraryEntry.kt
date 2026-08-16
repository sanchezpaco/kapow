package com.comicify.feature.library.domain

sealed interface LibraryEntry {
    data class Single(val comic: LibraryComic) : LibraryEntry
    data class Group(val series: String, val comics: List<LibraryComic>) : LibraryEntry
}
