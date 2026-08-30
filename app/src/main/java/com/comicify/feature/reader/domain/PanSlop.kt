package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset

data class PanSlop(private val travelled: Offset = Offset.Zero) {

    fun plus(panChange: Offset) = PanSlop(travelled + panChange)

    fun exceeds(touchSlop: Float) = travelled.getDistance() > touchSlop
}
