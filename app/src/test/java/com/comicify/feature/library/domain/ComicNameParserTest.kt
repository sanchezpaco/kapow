package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComicNameParserTest {

    @Test
    fun parsesSeriesIssueAndParenthesizedYear() {
        val result = ComicNameParser.parse("Batman 001 (2016).cbz")
        assertEquals("Batman", result.series)
        assertEquals(1, result.issueNumber)
        assertEquals(2016, result.year)
    }

    @Test
    fun parsesHashIssue() {
        val result = ComicNameParser.parse("Saga #012.cbr")
        assertEquals("Saga", result.series)
        assertEquals(12, result.issueNumber)
        assertNull(result.year)
    }

    @Test
    fun parsesYearWithoutIssue() {
        val result = ComicNameParser.parse("Watchmen (1986).cbz")
        assertEquals("Watchmen", result.series)
        assertNull(result.issueNumber)
        assertEquals(1986, result.year)
    }

    @Test
    fun stripsVolumeMarkerFromSeries() {
        val result = ComicNameParser.parse("The Amazing Spider-Man Vol 2 #050 (2003).cbz")
        assertEquals("The Amazing Spider Man", result.series)
        assertEquals(50, result.issueNumber)
        assertEquals(2003, result.year)
    }

    @Test
    fun handlesDotsAndDashesAsSeparators() {
        val result = ComicNameParser.parse("invincible-025.cbz")
        assertEquals("invincible", result.series)
        assertEquals(25, result.issueNumber)
    }

    @Test
    fun handlesPlainTitle() {
        val result = ComicNameParser.parse("Maus.cbz")
        assertEquals("Maus", result.series)
        assertNull(result.issueNumber)
        assertNull(result.year)
    }

    @Test
    fun stripsLeadingZerosFromIssue() {
        val result = ComicNameParser.parse("Hellboy 0007.cbz")
        assertEquals(7, result.issueNumber)
    }

    @Test
    fun splitsIssueNumberGluedToTheName() {
        val venom = ComicNameParser.parse("Venomverse001.cbz")
        assertEquals("Venomverse", venom.series)
        assertEquals(1, venom.issueNumber)
        val doom = ComicNameParser.parse("DoctorDoom9.pdf")
        assertEquals("DoctorDoom", doom.series)
        assertEquals(9, doom.issueNumber)
    }

    @Test
    fun keepsGluedDigitsWhenAnExplicitIssueExists() {
        val result = ComicNameParser.parse("Area51 #003.cbz")
        assertEquals("Area51", result.series)
        assertEquals(3, result.issueNumber)
    }
}
