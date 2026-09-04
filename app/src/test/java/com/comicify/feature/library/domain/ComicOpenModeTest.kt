package com.comicify.feature.library.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicOpenModeTest {

    @Test
    fun `no override reads as the default mode`() {
        assertNull(ComicSettings.Default.openMode())
    }

    @Test
    fun `guided off reads as pages`() {
        assertEquals(ReaderViewMode.Pages, ComicSettings.Default.copy(guided = false).openMode())
    }

    @Test
    fun `guided on reads as guided`() {
        assertEquals(ReaderViewMode.Guided, ComicSettings.Default.copy(guided = true).openMode())
    }

    @Test
    fun `vertical scroll wins over guided`() {
        val settings = ComicSettings.Default.copy(guided = true, verticalScroll = true)
        assertEquals(ReaderViewMode.Strip, settings.openMode())
    }

    @Test
    fun `every mode round-trips`() {
        val modes = listOf(null, ReaderViewMode.Pages, ReaderViewMode.Guided, ReaderViewMode.Strip)
        modes.forEach { mode ->
            assertEquals(mode, ComicSettings.Default.withOpenMode(mode).openMode())
        }
    }

    @Test
    fun `choosing the default clears both overrides`() {
        val overridden = ComicSettings.Default.copy(guided = true, verticalScroll = true)
        assertEquals(ComicSettings.Default, overridden.withOpenMode(null))
    }

    @Test
    fun `choosing the strip keeps the guided override untouched`() {
        val settings = ComicSettings.Default.copy(guided = true).withOpenMode(ReaderViewMode.Strip)
        assertEquals(true, settings.guided)
        assertEquals(true, settings.verticalScroll)
    }

    @Test
    fun `switching mode leaves the other settings alone`() {
        val settings = ComicSettings.Default.copy(coverAlone = true, splitWidePages = true, bubbleScale = 1.4f)
        val switched = settings.withOpenMode(ReaderViewMode.Guided)
        assertEquals(settings.copy(guided = true), switched)
    }
}
