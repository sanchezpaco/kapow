package com.comicify.feature.reader.domain

val AUTOPLAY_SECONDS_RANGE = 5..120
const val AUTOPLAY_SECONDS_STEP = 1

object Autoplay {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val MIN_STOP_MILLIS = 1_500L
    private const val HOLD_BEFORE_REPEAT_MILLIS = 400L
    private const val HOLD_FIRST_REPEAT_MILLIS = 220L
    private const val HOLD_SPEED_UP_MILLIS = 20L
    private const val HOLD_FASTEST_REPEAT_MILLIS = 45L

    fun secondsFor(pace: Int): Int = pace.coerceIn(AUTOPLAY_SECONDS_RANGE)

    fun stepped(seconds: Int, delta: Int): Int = (seconds + delta).coerceIn(AUTOPLAY_SECONDS_RANGE)

    fun pageMillis(seconds: Int): Long = seconds * MILLIS_PER_SECOND

    fun stopMillis(seconds: Int, stops: Int): Long =
        (pageMillis(seconds) / stops.coerceAtLeast(1)).coerceAtLeast(MIN_STOP_MILLIS)

    fun scrollPixels(pageHeight: Float, seconds: Int, elapsedNanos: Long): Float =
        (pageHeight * (elapsedNanos / NANOS_PER_SECOND) / seconds).toFloat()

    fun holdWaitMillis(repeatsSoFar: Int): Long {
        if (repeatsSoFar <= 0) return HOLD_BEFORE_REPEAT_MILLIS
        val sped = HOLD_FIRST_REPEAT_MILLIS - (repeatsSoFar - 1) * HOLD_SPEED_UP_MILLIS
        return sped.coerceAtLeast(HOLD_FASTEST_REPEAT_MILLIS)
    }
}
