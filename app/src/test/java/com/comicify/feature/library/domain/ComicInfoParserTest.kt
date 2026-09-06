package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicInfoParserTest {

    @Test
    fun parsesEveryReadField() {
        val info = ComicInfoParser.parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Series>The Amazing Spider-Man</Series>
              <Number>121</Number>
              <Year>1973</Year>
              <Title>The Night Gwen Stacy Died</Title>
              <Publisher>Marvel Comics</Publisher>
              <Writer>Gerry Conway</Writer>
              <Penciller>Gil Kane</Penciller>
              <Inker>John Romita Sr.</Inker>
              <Colorist>Dave Hunt</Colorist>
              <Summary>The Green Goblin takes Gwen to the bridge.</Summary>
            </ComicInfo>
            """.trimIndent(),
        )

        requireNotNull(info)
        assertEquals("The Amazing Spider-Man", info.series)
        assertEquals(121, info.number)
        assertEquals(1973, info.year)
        assertEquals("The Night Gwen Stacy Died", info.storyTitle)
        assertEquals("Marvel Comics", info.publisher)
        assertEquals("Gerry Conway", info.writer)
        assertEquals("Gil Kane", info.penciller)
        assertEquals("John Romita Sr.", info.inker)
        assertEquals("Dave Hunt", info.colorist)
        assertEquals("The Green Goblin takes Gwen to the bridge.", info.summary)
        assertNull(info.readsRightToLeft)
    }

    @Test
    fun blankAndMissingFieldsAreNull() {
        val info = ComicInfoParser.parse("<ComicInfo><Series>   </Series><Title></Title></ComicInfo>")

        requireNotNull(info)
        assertNull(info.series)
        assertNull(info.storyTitle)
        assertNull(info.publisher)
        assertNull(info.number)
    }

    @Test
    fun nonIntegerNumberIsNull() {
        val info = ComicInfoParser.parse("<ComicInfo><Number>1.MU</Number></ComicInfo>")

        assertNull(info?.number)
    }

    @Test
    fun trimsSurroundingWhitespace() {
        val info = ComicInfoParser.parse("<ComicInfo><Writer>\n  Gerry Conway\n</Writer></ComicInfo>")

        assertEquals("Gerry Conway", info?.writer)
    }

    @Test
    fun mangaRightToLeftReadsRightToLeft() {
        val info = ComicInfoParser.parse("<ComicInfo><Manga>YesAndRightToLeft</Manga></ComicInfo>")

        assertTrue(info?.readsRightToLeft == true)
    }

    @Test
    fun mangaYesReadsLeftToRight() {
        val info = ComicInfoParser.parse("<ComicInfo><Manga>Yes</Manga></ComicInfo>")

        assertEquals(false, info?.readsRightToLeft)
    }

    @Test
    fun mangaUnknownHasNoOpinion() {
        val info = ComicInfoParser.parse("<ComicInfo><Manga>Unknown</Manga></ComicInfo>")

        assertNull(info?.readsRightToLeft)
    }

    @Test
    fun rejectsAnotherRootElement() {
        assertNull(ComicInfoParser.parse("<Metadata><Series>Saga</Series></Metadata>"))
    }

    @Test
    fun rejectsMalformedXml() {
        assertNull(ComicInfoParser.parse("<ComicInfo><Series>Saga"))
    }
}
