package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

data class PanSlop(private val travelled: Offset = Offset.Zero) {

    fun plus(panChange: Offset) = PanSlop(travelled + panChange)

    fun exceeds(touchSlop: Float) = travelled.getDistance() > touchSlop

    fun isHorizontal() = abs(travelled.x) > abs(travelled.y)
}
