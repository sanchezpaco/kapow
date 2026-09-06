package com.comicify.feature.library.domain

import com.comicify.feature.reader.domain.ReaderViewMode
import com.comicify.feature.reader.domain.ReadingType
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

    @Test
    fun `a webcomic opens in the strip when no mode is set`() {
        assertEquals(
            ReaderViewMode.Strip,
            ComicSettings.Default.openModeOnOpen(ReadingType.Webcomic, guidedOnOpen = false),
        )
    }

    @Test
    fun `a webcomic beats guided view on open`() {
        assertEquals(
            ReaderViewMode.Strip,
            ComicSettings.Default.openModeOnOpen(ReadingType.Webcomic, guidedOnOpen = true),
        )
    }

    @Test
    fun `an explicit mode beats the webcomic strip`() {
        val settings = ComicSettings.Default.withOpenMode(ReaderViewMode.Pages)
        assertEquals(ReaderViewMode.Pages, settings.openModeOnOpen(ReadingType.Webcomic, guidedOnOpen = true))
    }

    @Test
    fun `guided view on open applies to comics and manga`() {
        listOf(ReadingType.Comic, ReadingType.Manga).forEach { type ->
            assertEquals(ReaderViewMode.Guided, ComicSettings.Default.openModeOnOpen(type, guidedOnOpen = true))
        }
    }

    @Test
    fun `pages is the last rung of the ladder`() {
        assertEquals(ReaderViewMode.Pages, ComicSettings.Default.openModeOnOpen(ReadingType.Comic, guidedOnOpen = false))
    }

    @Test
    fun `the default chip follows the same ladder`() {
        assertEquals(ReaderViewMode.Strip, defaultOpenMode(ReadingType.Webcomic, guidedOnOpen = false))
        assertEquals(ReaderViewMode.Guided, defaultOpenMode(ReadingType.Manga, guidedOnOpen = true))
        assertEquals(ReaderViewMode.Pages, defaultOpenMode(ReadingType.Manga, guidedOnOpen = false))
    }
}
