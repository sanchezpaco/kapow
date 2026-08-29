package com.comicify.core.ui.splash

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

internal object SplashTimeline {
    const val DURATION_MS = 1300
    private const val ICON_OUT_END = 140
    private const val KEYLINE_STAGGER = 35
    private const val KEYLINE_DRAW = 320
    private const val KEYLINES_TURN_INK = 620
    private const val FILL_START = 200
    private const val FILL_STAGGER = 40
    private const val FILL_DURATION = 130
    private const val GUTTER_START = 300
    private const val GUTTER_END = 700
    private const val BUBBLE_START = 380
    private const val BUBBLE_END = 720
    private const val K_START = 560
    private const val K_END = 780
    private const val BURST_START = 770
    private const val BURST_END = 980
    private const val WIPE_START = 1000
    private const val ICON_OUT_GROWTH = 0.6f
    private const val BUBBLE_SQUASH_X = 0.18f
    private const val BUBBLE_SQUASH_Y = 0.14f
    private const val BUBBLE_OVERSHOOT = 2.5f
    private const val K_ENTRY_SCALE = 4f

    fun iconOut(t: Float): Float = seg(t, 0, ICON_OUT_END)
    fun iconScale(t: Float): Float = 1f + ICON_OUT_GROWTH * iconOut(t)
    fun gutter(t: Float): Float = seg(t, GUTTER_START, GUTTER_END)
    fun keylineDrawn(t: Float, index: Int): Float =
        seg(t, index * KEYLINE_STAGGER, index * KEYLINE_STAGGER + KEYLINE_DRAW, ::outCubic)
    fun keylinesInked(t: Float): Boolean = t >= KEYLINES_TURN_INK
    fun panelFilled(t: Float, index: Int): Float =
        seg(t, FILL_START + index * FILL_STAGGER, FILL_START + index * FILL_STAGGER + FILL_DURATION)

    fun bubbleScale(t: Float): Float = seg(t, BUBBLE_START, BUBBLE_END, outBack(BUBBLE_OVERSHOOT))
    fun bubbleSquash(t: Float): Pair<Float, Float> {
        val wave = sin(seg(t, BUBBLE_START, BUBBLE_END) * PI).toFloat()
        return (1f + BUBBLE_SQUASH_X * wave) to (1f - BUBBLE_SQUASH_Y * wave)
    }

    fun kVisible(t: Float): Boolean = t >= K_START
    fun kScale(t: Float): Float = lerp(K_ENTRY_SCALE, 1f, seg(t, K_START, K_END, ::inCubic))
    fun burst(t: Float): Float = seg(t, BURST_START, BURST_END)
    fun wipe(t: Float): Float = seg(t, WIPE_START, DURATION_MS, ::inOutCubic)

    fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p
    fun outCubic(x: Float): Float = 1f - (1f - x).pow(3)
    private fun inCubic(x: Float): Float = x * x * x
    private fun inOutCubic(x: Float): Float = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    private fun outBack(overshoot: Float): (Float) -> Float =
        { x -> 1f + (overshoot + 1f) * (x - 1f).pow(3) + overshoot * (x - 1f).pow(2) }

    private fun seg(t: Float, start: Int, end: Int, ease: (Float) -> Float = { it }): Float =
        ease(((t - start) / (end - start)).coerceIn(0f, 1f))
}
