package com.comicify.feature.reader.domain

import kotlin.math.abs

data class PageTurnTransform(
    val scale: Float,
    val alpha: Float,
    val rotationY: Float,
    val translationFraction: Float,
)

object PageTurn {

    private const val MIN_SCALE = 0.86f
    private const val MIN_ALPHA = 0.45f
    private const val MAX_ROTATION_DEGREES = 16f
    private const val PARALLAX_FRACTION = 0.12f

    fun transform(pageOffset: Float): PageTurnTransform {
        val clamped = pageOffset.coerceIn(-1f, 1f)
        val distance = abs(clamped)
        return PageTurnTransform(
            scale = lerp(1f, MIN_SCALE, distance),
            alpha = lerp(1f, MIN_ALPHA, distance),
            rotationY = -clamped * MAX_ROTATION_DEGREES,
            translationFraction = -clamped * PARALLAX_FRACTION,
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction
}
