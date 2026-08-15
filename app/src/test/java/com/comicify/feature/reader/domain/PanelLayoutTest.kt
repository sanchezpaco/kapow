package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelLayoutTest {

    private val a = Box(0, 0, 100, 100)
    private val b = Box(110, 0, 210, 100)
    private val c = Box(0, 110, 100, 210)
    private val d = Box(110, 110, 210, 210)

    @Test
    fun gridReadsInZOrder() {
        assertEquals(listOf(a, b, c, d), PanelLayout.readingOrder(listOf(d, b, c, a)))
    }

    @Test
    fun tallLeftPanelPrecedesStackedRightPanels() {
        val tall = Box(0, 0, 100, 210)
        assertEquals(listOf(tall, b, d), PanelLayout.readingOrder(listOf(d, tall, b)))
    }

    @Test
    fun staggeredPanelsStayInTheirRow() {
        val lowered = Box(110, 30, 210, 130)
        assertEquals(listOf(a, lowered, c, d), PanelLayout.readingOrder(listOf(c, lowered, d, a)))
    }

    @Test
    fun containerComesRightBeforeItsFirstInset() {
        val host = Box(0, 110, 210, 400)
        val insetLeft = Box(10, 200, 100, 300)
        val insetRight = Box(110, 200, 200, 300)
        assertEquals(listOf(a, b, host, insetLeft, insetRight), PanelLayout.readingOrder(listOf(insetRight, host, b, insetLeft, a)))
    }

    @Test
    fun attachJoinsRegionToPanelWithLargestOverlap() {
        val bubble = Box(60, 20, 120, 60)
        val attached = PanelLayout.attach(panels(a, b), listOf(bubble)) { false }
        assertEquals(listOf(Panel(a, Box(0, 0, 120, 100)), Panel(b, b)), attached)
    }

    @Test
    fun attachJoinsSharedRegionToEveryPanelItSpans() {
        val chain = Box(60, 20, 160, 60)
        val attached = PanelLayout.attach(panels(a, b), listOf(chain)) { false }
        assertEquals(listOf(Panel(a, Box(0, 0, 160, 100)), Panel(b, Box(60, 0, 210, 100))), attached)
    }

    @Test
    fun attachAddsWorthyDetachedRegionAsPanel() {
        val caption = Box(0, 300, 200, 360)
        assertEquals(panels(a, caption), PanelLayout.attach(panels(a), listOf(caption)) { true })
        assertEquals(panels(a), PanelLayout.attach(panels(a), listOf(caption)) { false })
    }

    @Test
    fun absorbContainedMergesFragmentsButKeepsFramedInsets() {
        val host = Box(0, 0, 300, 300)
        val fragment = Box(20, 20, 80, 80)
        val inset = Box(200, 200, 280, 280)
        val absorbed = PanelLayout.absorbContained(panels(fragment, host, inset)) { it == inset }
        assertEquals(panels(host, inset), absorbed)
    }

    @Test
    fun absorbContainedJudgesFramingOnTheBodyNotTheGrownFrame() {
        val host = Panel(Box(0, 0, 300, 200), Box(0, 0, 300, 300))
        val inset = Panel(Box(200, 200, 280, 280), Box(200, 100, 280, 280))
        assertEquals(listOf(host, inset), PanelLayout.absorbContained(listOf(inset, host)) { it == inset.body })
    }

    @Test
    fun absorbSliversFoldsThinStripsIntoTheTouchingPanel() {
        val strip = Box(0, 0, 200, 10)
        val below = Box(0, 14, 200, 200)
        val far = Box(0, 400, 200, 600)
        val result = PanelLayout.absorbSlivers(panels(strip, below, far), { it.height < 20 }, reach = 6)
        assertEquals(listOf(Panel(below, Box(0, 0, 200, 200)), Panel(far, far)), result)
    }

    @Test
    fun containersMostlyCoveredByTheirInsetsAreDropped() {
        val band = Box(0, 0, 400, 100)
        val bordered = Box(10, 5, 390, 95)
        val host = Box(0, 200, 400, 600)
        val inset = Box(20, 220, 120, 320)
        assertEquals(listOf(bordered, host, inset), PanelLayout.dropRedundantContainers(listOf(band, bordered, host, inset)))
    }

    private fun panels(vararg boxes: Box) = boxes.map { Panel(it, it) }

    @Test
    fun coverageIsShareOfContentInsideBoxes() {
        val content = BooleanArray(10 * 10) { it % 10 < 5 }
        assertEquals(1f, PanelLayout.coverage(listOf(Box(0, 0, 5, 10)), content, 10))
        assertEquals(0.5f, PanelLayout.coverage(listOf(Box(0, 0, 5, 5)), content, 10))
    }
}
