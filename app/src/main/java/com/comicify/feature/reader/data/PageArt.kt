package com.comicify.feature.reader.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

data class PageArt(
    val image: ImageBitmap,
    val analysis: ImageBitmap,
    val ambient: Color,
)
