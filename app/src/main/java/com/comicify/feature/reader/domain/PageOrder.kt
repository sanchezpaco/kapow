package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingDirection.LeftToRight
import com.comicify.domain.model.ReadingDirection.RightToLeft

enum class TapZone { Previous, Center, Next }

private const val PREVIOUS_ZONE_END = 0.28f
private const val NEXT_ZONE_START = 0.72f
private const val NEVER = 2f
private const val SPREAD_PREVIOUS_ZONE_END = PREVIOUS_ZONE_END * 2
private const val SPREAD_NEXT_ZONE_START = 1f - (1f - NEXT_ZONE_START) * 2

data class TapZones(val previousEnd: Float, val nextStart: Float) {
    fun at(direction: ReadingDirection, xFraction: Float): TapZone = PageOrder.tapZone(direction, xFraction, previousEnd, nextStart)

    companion object {
        val FullWidth = TapZones(PREVIOUS_ZONE_END, NEXT_ZONE_START)
        val LeftHalf = TapZones(SPREAD_PREVIOUS_ZONE_END, NEVER)
        val RightHalf = TapZones(-NEVER, SPREAD_NEXT_ZONE_START)
    }
}

object PageOrder {

    fun pagerIndex(direction: ReadingDirection, logicalIndex: Int, count: Int): Int = when (direction) {
        LeftToRight -> logicalIndex
        RightToLeft -> (count - 1 - logicalIndex).coerceAtLeast(0)
    }

    fun logicalIndex(direction: ReadingDirection, pagerIndex: Int, count: Int): Int =
        pagerIndex(direction, pagerIndex, count)

    fun leftPage(direction: ReadingDirection, firstPage: Int, secondPage: Int): Int = when (direction) {
        LeftToRight -> firstPage
        RightToLeft -> secondPage
    }

    fun rightPage(direction: ReadingDirection, firstPage: Int, secondPage: Int): Int = when (direction) {
        LeftToRight -> secondPage
        RightToLeft -> firstPage
    }

    fun spreadCount(pageCount: Int, coverAlone: Boolean): Int =
        (pageCount + spreadOffset(coverAlone) + 1) / 2

    fun spreadIndex(pageIndex: Int, coverAlone: Boolean): Int =
        (pageIndex.coerceAtLeast(0) + spreadOffset(coverAlone)) / 2

    fun spreadFirstPage(spreadIndex: Int, coverAlone: Boolean): Int =
        spreadIndex * 2 - spreadOffset(coverAlone)

    private fun spreadOffset(coverAlone: Boolean): Int = if (coverAlone) 1 else 0

    fun step(direction: ReadingDirection): Int = when (direction) {
        LeftToRight -> 1
        RightToLeft -> -1
    }

    fun tapZone(direction: ReadingDirection, xFraction: Float, previousZone: Float, nextZone: Float): TapZone {
        val zone = when {
            xFraction < previousZone -> TapZone.Previous
            xFraction > nextZone -> TapZone.Next
            else -> TapZone.Center
        }
        return if (direction == LeftToRight) zone else zone.mirrored()
    }

    private fun TapZone.mirrored(): TapZone = when (this) {
        TapZone.Previous -> TapZone.Next
        TapZone.Next -> TapZone.Previous
        TapZone.Center -> TapZone.Center
    }
}
