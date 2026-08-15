package com.comicify.feature.library.domain

data class ParsedComicName(
    val series: String,
    val issueNumber: Int?,
    val year: Int?,
)
