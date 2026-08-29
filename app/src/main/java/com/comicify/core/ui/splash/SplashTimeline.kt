package com.comicify.core.ui.splash

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal object SplashTimeline {
    const val DURATION_MS = 1300
    private const val CIRCLE_OPEN_END = 560
    private const val PAGE_GROW_END = 620
    private const val KEYLINE_START = 120
    private const val KEYLINE_STAGGER = 30
    private const val KEYLINE_DRAW = 300
    private const val KEYLINES_TURN_INK = 700
    private const val FILL_START = 300
    private const val FILL_STAGGER = 35
    private const val FILL_DURATION = 120
    private const val MARK_POP_START = 300
    private const val MARK_POP_END = 760
    private const val BURST_START = 640
    private const val BURST_END = 880
    private const val WIPE_START = 1000
    private const val MARK_SQUASH_X = 0.14f
    private const val MARK_SQUASH_Y = 0.11f
    private const val MARK_OVERSHOOT = 2.6f

    fun circleOpen(t: Float): Float = seg(t, 0, CIRCLE_OPEN_END, ::inOutCubic)
    fun pageGrown(t: Float): Float = seg(t, 0, PAGE_GROW_END, ::outCubic)
    fun keylineDrawn(t: Float, index: Int): Float {
        val start = KEYLINE_START + index * KEYLINE_STAGGER
        return seg(t, start, start + KEYLINE_DRAW, ::outCubic)
    }
    fun keylinesInked(t: Float): Boolean = t >= KEYLINES_TURN_INK
    fun panelFilled(t: Float, index: Int): Float {
        val start = FILL_START + index * FILL_STAGGER
        return seg(t, start, start + FILL_DURATION)
    }

    fun markPop(t: Float): Float = seg(t, MARK_POP_START, MARK_POP_END, outBack(MARK_OVERSHOOT))
    fun markSquash(t: Float): Pair<Float, Float> {
        val wave = sin(seg(t, MARK_POP_START, MARK_POP_END) * PI).toFloat()
        return (1f + MARK_SQUASH_X * wave) to (1f - MARK_SQUASH_Y * wave)
    }

    fun burst(t: Float): Float = seg(t, BURST_START, BURST_END)
    fun wipe(t: Float): Float = seg(t, WIPE_START, DURATION_MS, ::inOutCubic)

    fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    fun outCubic(x: Float): Float = 1f - (1f - x).pow(3)
    private fun inOutCubic(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    private fun outBack(overshoot: Float): (Float) -> Float =
        { x -> 1f + (overshoot + 1f) * (x - 1f).pow(3) + overshoot * (x - 1f).pow(2) }

    private fun seg(t: Float, start: Int, end: Int, ease: (Float) -> Float = { it }): Float =
        ease(((t - start) / (end - start)).coerceIn(0f, 1f))
}
