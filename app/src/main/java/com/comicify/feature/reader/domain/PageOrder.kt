package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection
import com.comicify.domain.model.ReadingDirection.LeftToRight
import com.comicify.domain.model.ReadingDirection.RightToLeft

enum class TapZone { Previous, Center, Next }

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
