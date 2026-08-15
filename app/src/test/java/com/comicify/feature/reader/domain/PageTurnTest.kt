package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTurnTest {

    private val delta = 0.0001f

    @Test
    fun settledPageIsIdentity() {
        val transform = PageTurn.transform(0f)
        assertEquals(1f, transform.scale, delta)
        assertEquals(1f, transform.alpha, delta)
        assertEquals(0f, transform.rotationY, delta)
        assertEquals(0f, transform.translationFraction, delta)
    }

    @Test
    fun fullyOffscreenPageReachesTheDepthExtreme() {
        val outgoing = PageTurn.transform(1f)
        assertEquals(0.86f, outgoing.scale, delta)
        assertEquals(0.45f, outgoing.alpha, delta)
        assertEquals(-16f, outgoing.rotationY, delta)
        assertEquals(-0.12f, outgoing.translationFraction, delta)
    }

    @Test
    fun transformIsMirroredAcrossDirection() {
        val incoming = PageTurn.transform(-1f)
        assertEquals(0.86f, incoming.scale, delta)
        assertEquals(0.45f, incoming.alpha, delta)
        assertEquals(16f, incoming.rotationY, delta)
        assertEquals(0.12f, incoming.translationFraction, delta)
    }

    @Test
    fun offsetBeyondOnePageIsClamped() {
        assertEquals(PageTurn.transform(1f).scale, PageTurn.transform(2.5f).scale, delta)
        assertEquals(PageTurn.transform(-1f).rotationY, PageTurn.transform(-4f).rotationY, delta)
    }

    @Test
    fun depthGrowsMonotonicallyWithDistance() {
        val quarter = PageTurn.transform(0.25f)
        val half = PageTurn.transform(0.5f)
        assertTrue(quarter.scale > half.scale)
        assertTrue(quarter.alpha > half.alpha)
        assertTrue(quarter.scale < 1f)
        assertTrue(quarter.alpha < 1f)
    }
}
