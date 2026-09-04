package com.comicify.feature.reader.domain

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import kotlin.math.hypot

private const val PAN_BASE_MILLIS = 240
private const val PAN_MILLIS_PER_TRAVEL = 420
private const val PAN_MAX_MILLIS = 520
private const val ARC_TRAVEL_THRESHOLD = 0.22f
private const val ARC_MAX_LIFT = 0.3f
private const val ARC_LIFT_GAIN = 4f
private const val PUSH_IN_MILLIS = 560
private const val PUSH_IN_AREA_RATIO = 0.6f
private const val PULL_BACK_MILLIS = 340
private const val PULL_BACK_AREA_RATIO = 1.7f
private const val REVEAL_MILLIS = 700
private const val WHOLE_PAGE_AREA = 0.86f

private val Travelling = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val Settling = CubicBezierEasing(0.22f, 0.9f, 0.2f, 1f)
private val Opening = CubicBezierEasing(0.05f, 0.7f, 0.15f, 1f)

data class Cut(
    val durationMillis: Int,
    val easing: Easing,
    val lift: Float,
    val jump: Boolean,
    val fromBlack: Boolean,
) {
    val arcing: Boolean get() = lift > 0f
}

object DirectorCut {

    val ArcOut: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val ArcIn: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    fun between(from: Rect, to: Rect, pageChanged: Boolean, forward: Boolean): Cut {
        val revealing = forward && to.area >= WHOLE_PAGE_AREA
        if (pageChanged) return Cut(
            durationMillis = if (revealing) REVEAL_MILLIS else 0,
            easing = Travelling,
            lift = 0f,
            jump = true,
            fromBlack = revealing,
        )
        if (revealing) return Cut(REVEAL_MILLIS, Travelling, 0f, jump = false, fromBlack = true)
        val ratio = to.area / from.area
        if (ratio < PUSH_IN_AREA_RATIO) return Cut(PUSH_IN_MILLIS, Settling, 0f, jump = false, fromBlack = false)
        if (ratio > PULL_BACK_AREA_RATIO) return Cut(PULL_BACK_MILLIS, Opening, 0f, jump = false, fromBlack = false)
        val travel = travel(from, to)
        return Cut(
            durationMillis = (PAN_BASE_MILLIS + PAN_MILLIS_PER_TRAVEL * travel).toInt().coerceAtMost(PAN_MAX_MILLIS),
            easing = Travelling,
            lift = arcLift(travel),
            jump = false,
            fromBlack = false,
        )
    }

    fun apex(from: Rect, to: Rect, lift: Float): Rect = GuidedFocus.clamp(widened(lerp(from, to, 0.5f), 1f + lift))

    private fun travel(from: Rect, to: Rect) = hypot(to.center.x - from.center.x, to.center.y - from.center.y)

    private fun arcLift(travel: Float) =
        if (travel <= ARC_TRAVEL_THRESHOLD) 0f
        else ((travel - ARC_TRAVEL_THRESHOLD) * ARC_LIFT_GAIN * ARC_MAX_LIFT).coerceAtMost(ARC_MAX_LIFT)

    private fun widened(rect: Rect, factor: Float): Rect {
        val width = rect.width * factor
        val height = rect.height * factor
        return Rect(
            rect.center.x - width / 2f,
            rect.center.y - height / 2f,
            rect.center.x + width / 2f,
            rect.center.y + height / 2f,
        )
    }

    private val Rect.area: Float get() = width * height
}
