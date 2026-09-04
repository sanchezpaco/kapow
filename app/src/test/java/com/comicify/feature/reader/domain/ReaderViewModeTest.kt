package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderViewModeTest {

    @Test
    fun theStripWinsOverGuided() {
        assertEquals(ReaderViewMode.Strip, ReaderViewMode.of(guided = true, verticalScroll = true))
    }

    @Test
    fun guidedWinsWhenThereIsNoStrip() {
        assertEquals(ReaderViewMode.Guided, ReaderViewMode.of(guided = true, verticalScroll = false))
        assertEquals(ReaderViewMode.Pages, ReaderViewMode.of(guided = false, verticalScroll = false))
    }

    @Test
    fun onlyGuidedRefusesEnlargedBubbles() {
        assertTrue(ReaderViewMode.Pages.allowsBubbles())
        assertTrue(ReaderViewMode.Strip.allowsBubbles())
        assertFalse(ReaderViewMode.Guided.allowsBubbles())
    }
}
