package com.comicify.feature.reader.domain

enum class ReaderViewMode {
    Pages,
    Guided,
    Strip;

    companion object {
        fun of(guided: Boolean, verticalScroll: Boolean): ReaderViewMode = when {
            verticalScroll -> Strip
            guided -> Guided
            else -> Pages
        }
    }

    fun allowsBubbles(): Boolean = this != Guided
}
