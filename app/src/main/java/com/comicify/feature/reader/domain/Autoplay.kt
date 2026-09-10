package com.comicify.feature.reader.domain

val AUTOPLAY_SECONDS_RANGE = 3..120
const val AUTOPLAY_SECONDS_STEP = 1
const val AUTOPLAY_COARSE_STEP = 5
const val AUTOPLAY_MIN_STOP_SECONDS = 1.5f
const val AUTOPLAY_REPEAT_DELAY_MS = 400L
const val AUTOPLAY_REPEAT_INTERVAL_MS = 120L
const val AUTOPLAY_COARSE_AFTER_MS = 1_200L

private const val NANOS_PER_MILLI = 1_000_000L

enum class AutoplayState {
    Off,
    Running,
    Paused;

    val on: Boolean get() = this != Off
    val running: Boolean get() = this == Running
    val paused: Boolean get() = this == Paused

    fun switched(): AutoplayState = if (on) Off else Running

    fun transported(): AutoplayState = when (this) {
        Off -> Off
        Running -> Paused
        Paused -> Running
    }
}

data class AutoplayDwell(val totalMillis: Long, val elapsedMillis: Long = 0L) {

    val progress: Float
        get() = if (totalMillis <= 0L) 1f else (elapsedMillis.toFloat() / totalMillis).coerceIn(0f, 1f)

    val done: Boolean get() = elapsedMillis >= totalMillis

    fun ticked(elapsedNanos: Long, held: Boolean): AutoplayDwell =
        if (held) this else copy(elapsedMillis = elapsedMillis + elapsedNanos / NANOS_PER_MILLI)
}

object Autoplay {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private val MIN_STOP_MILLIS = (AUTOPLAY_MIN_STOP_SECONDS * MILLIS_PER_SECOND).toLong()

    fun secondsFor(pace: Int): Int = pace.coerceIn(AUTOPLAY_SECONDS_RANGE)

    fun stepped(seconds: Int, delta: Int): Int = (seconds + delta).coerceIn(AUTOPLAY_SECONDS_RANGE)

    fun coarseStepped(seconds: Int, direction: Int): Int {
        val step = AUTOPLAY_COARSE_STEP
        val landing = if (direction > 0) {
            Math.floorDiv(seconds, step) * step + step
        } else {
            Math.floorDiv(seconds - 1, step) * step
        }
        return landing.coerceIn(AUTOPLAY_SECONDS_RANGE)
    }

    fun coarseAfter(repeatingMillis: Long): Boolean = repeatingMillis >= AUTOPLAY_COARSE_AFTER_MS

    fun pageMillis(seconds: Int, pagesOnScreen: Int = 1): Long =
        seconds * MILLIS_PER_SECOND * pagesOnScreen.coerceAtLeast(1)

    fun stopMillis(seconds: Int, stops: Int): Long =
        (pageMillis(seconds) / stops.coerceAtLeast(1)).coerceAtLeast(MIN_STOP_MILLIS)

    fun spreadPages(firstPage: Int, pageCount: Int, coverAlone: Boolean): Int =
        if (coverAlone && firstPage <= 0) 1 else if (firstPage + 1 < pageCount) 2 else 1

    fun scrollPixels(pageHeight: Float, seconds: Int, elapsedNanos: Long): Float =
        (pageHeight * (elapsedNanos / NANOS_PER_SECOND) / seconds).toFloat()
}
