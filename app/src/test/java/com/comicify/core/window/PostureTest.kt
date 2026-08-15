package com.comicify.core.window

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureTest {

    @Test
    fun coverScreenIsCompactSingle() {
        val state = WindowState(widthDp = 380, isLandscape = false, isHalfOpen = false, hingeIsHorizontal = false)
        assertEquals(ReadingPosture.CompactSingle, state.toPosture())
    }

    @Test
    fun unfoldedPortraitIsSingle() {
        val state = WindowState(widthDp = 690, isLandscape = false, isHalfOpen = false, hingeIsHorizontal = false)
        assertEquals(ReadingPosture.UnfoldedSingle, state.toPosture())
    }

    @Test
    fun unfoldedLandscapeIsSpread() {
        val state = WindowState(widthDp = 900, isLandscape = true, isHalfOpen = false, hingeIsHorizontal = false)
        assertEquals(ReadingPosture.UnfoldedSpread, state.toPosture())
    }

    @Test
    fun halfOpenHorizontalHingeIsTabletop() {
        val state = WindowState(widthDp = 900, isLandscape = true, isHalfOpen = true, hingeIsHorizontal = true)
        assertEquals(ReadingPosture.Tabletop, state.toPosture())
    }

    @Test
    fun halfOpenVerticalHingeStaysSpread() {
        val state = WindowState(widthDp = 900, isLandscape = true, isHalfOpen = true, hingeIsHorizontal = false)
        assertEquals(ReadingPosture.UnfoldedSpread, state.toPosture())
    }
}
