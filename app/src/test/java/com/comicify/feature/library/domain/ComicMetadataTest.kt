package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicMetadataTest {

    private val parsed = ParsedComicName(series = "scan batch 007", issueNumber = 7, year = 2011)

    private fun comicInfo(
        series: String? = null,
        number: Int? = null,
        year: Int? = null,
        storyTitle: String? = null,
        readsRightToLeft: Boolean? = null,
    ) = ComicInfo(
        series = series,
        number = number,
        year = year,
        storyTitle = storyTitle,
        publisher = null,
        writer = null,
        penciller = null,
        inker = null,
        colorist = null,
        summary = null,
        readsRightToLeft = readsRightToLeft,
    )

    @Test
    fun withoutXmlEverythingComesFromTheFileName() {
        val metadata = mergeComicMetadata(info = null, parsed = parsed)

        assertEquals("scan batch 007", metadata.series)
        assertEquals(7, metadata.issueNumber)
        assertEquals(2011, metadata.year)
        assertNull(metadata.storyTitle)
        assertNull(metadata.summary)
        assertNull(metadata.readsRightToLeft)
    }

    @Test
    fun xmlWinsOverTheFileName() {
        val metadata = mergeComicMetadata(
            info = comicInfo(series = "The Amazing Spider-Man", number = 121, year = 1973, storyTitle = "The Night Gwen Stacy Died"),
            parsed = parsed,
        )

        assertEquals("The Amazing Spider-Man", metadata.series)
        assertEquals(121, metadata.issueNumber)
        assertEquals(1973, metadata.year)
        assertEquals("The Night Gwen Stacy Died", metadata.storyTitle)
    }

    @Test
    fun blankXmlFieldsFallBackToTheFileName() {
        val metadata = mergeComicMetadata(info = comicInfo(storyTitle = "A story"), parsed = parsed)

        assertEquals("scan batch 007", metadata.series)
        assertEquals(7, metadata.issueNumber)
        assertEquals(2011, metadata.year)
        assertEquals("A story", metadata.storyTitle)
    }

    @Test
    fun nonIntegerNumberKeepsTheFileNameIssue() {
        val metadata = mergeComicMetadata(info = comicInfo(series = "X-Men", number = null), parsed = parsed)

        assertEquals("X-Men", metadata.series)
        assertEquals(7, metadata.issueNumber)
    }

    @Test
    fun mangaBecomesTheComicsReadingDirection() {
        val metadata = mergeComicMetadata(info = comicInfo(readsRightToLeft = true), parsed = parsed)

        assertTrue(metadata.readsRightToLeft == true)
    }

    @Test
    fun credentialsAndSummaryComeOnlyFromXml() {
        val info = ComicInfo(
            series = null,
            number = null,
            year = null,
            storyTitle = null,
            publisher = "Marvel Comics",
            writer = "Gerry Conway",
            penciller = "Gil Kane",
            inker = "John Romita Sr.",
            colorist = "Dave Hunt",
            summary = "A summary.",
            readsRightToLeft = null,
        )

        val metadata = mergeComicMetadata(info = info, parsed = parsed)

        assertEquals("Marvel Comics", metadata.publisher)
        assertEquals("Gerry Conway", metadata.writer)
        assertEquals("Gil Kane", metadata.penciller)
        assertEquals("John Romita Sr.", metadata.inker)
        assertEquals("Dave Hunt", metadata.colorist)
        assertEquals("A summary.", metadata.summary)
    }
}
