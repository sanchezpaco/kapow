package com.comicify.domain.model

data class ReadingPosition(
    val pageIndex: Int = 0,
    val panelIndex: Int? = null,
    val normalizedFocusX: Float = 0.5f,
    val normalizedFocusY: Float = 0.5f,
)
