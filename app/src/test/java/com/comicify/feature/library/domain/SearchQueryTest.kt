package com.comicify.feature.library.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun comic(
        displayName: String = "file",
        series: String = "Immortal Hulk",
        issueNumber: Int? = 1,
        storyTitle: String? = null,
        publisher: String? = null,
        writer: String? = null,
        penciller: String? = null,
        inker: String? = null,
        colorist: String? = null,
        year: Int? = null,
        addedAt: Long = 0L,
        pageIndex: Int = 0,
        completed: Boolean = false,
        rating: Int = 0,
    ) = LibraryComic(
        id = 1,
        documentUri = "uri",
        displayName = displayName,
        title = LibraryCatalog.title(series, issueNumber),
        series = series,
        issueNumber = issueNumber,
        coverPath = null,
        coverAmbient = null,
        pageCount = 20,
        pageIndex = pageIndex,
        completed = completed,
        favorite = false,
        lastReadAt = null,
        shelved = true,
        storyTitle = storyTitle,
        publisher = publisher,
        writer = writer,
        penciller = penciller,
        inker = inker,
        colorist = colorist,
        year = year,
        addedAt = addedAt,
        rating = rating,
    )

    private fun matches(query: String, comic: LibraryComic): Boolean =
        SearchQuery.parse(query).matches(comic, now)

    @Test
    fun emptyQueryMatchesEverything() {
        assertTrue(matches("", comic()))
        assertTrue(matches("   ", comic()))
    }

    @Test
    fun bareWordMatchesTitleAndSeries() {
        assertTrue(matches("hulk", comic()))
        assertTrue(matches("HULK", comic()))
        assertFalse(matches("thor", comic()))
    }

    @Test
    fun bareWordMatchesEveryStoredField() {
        assertTrue(matches("ewing", comic(writer = "Al Ewing")))
        assertTrue(matches("bennett", comic(penciller = "Joe Bennett")))
        assertTrue(matches("jose", comic(inker = "Ruy Jose")))
        assertTrue(matches("mounts", comic(colorist = "Paul Mounts")))
        assertTrue(matches("marvel", comic(publisher = "Marvel")))
        assertTrue(matches("descent", comic(storyTitle = "The Descent")))
        assertTrue(matches("v01", comic(displayName = "Immortal Hulk v01")))
    }

    @Test
    fun summaryIsNotSearched() {
        assertFalse(matches("radiation", comic().copy(summary = "A story about radiation")))
    }

    @Test
    fun matchingIgnoresAccents() {
        assertTrue(matches("pena", comic(writer = "Peña")))
        assertTrue(matches("peña", comic(writer = "Pena")))
        assertTrue(matches("writer:pena", comic(writer = "Peña")))
    }

    @Test
    fun fieldTermNarrowsToOneField() {
        val comic = comic(writer = "Al Ewing", penciller = "Joe Bennett")
        assertTrue(matches("writer:ewing", comic))
        assertFalse(matches("writer:bennett", comic))
        assertTrue(matches("penciller:bennett", comic))
    }

    @Test
    fun fieldTermMatchesEveryKey() {
        val comic = comic(
            series = "Immortal Hulk",
            storyTitle = "The Descent",
            publisher = "Marvel",
            writer = "Al Ewing",
            penciller = "Joe Bennett",
            inker = "Ruy Jose",
            colorist = "Paul Mounts",
        )
        assertTrue(matches("series:immortal", comic))
        assertTrue(matches("title:descent", comic))
        assertTrue(matches("publisher:marvel", comic))
        assertTrue(matches("writer:ewing", comic))
        assertTrue(matches("penciller:bennett", comic))
        assertTrue(matches("inker:jose", comic))
        assertTrue(matches("colorist:mounts", comic))
    }

    @Test
    fun spanishKeysAreAliases() {
        val comic = comic(
            series = "Immortal Hulk",
            storyTitle = "The Descent",
            publisher = "Marvel",
            writer = "Al Ewing",
            penciller = "Joe Bennett",
            inker = "Ruy Jose",
            colorist = "Paul Mounts",
            year = 2018,
        )
        assertTrue(matches("serie:immortal", comic))
        assertTrue(matches("titulo:descent", comic))
        assertTrue(matches("título:descent", comic))
        assertTrue(matches("guionista:ewing", comic))
        assertTrue(matches("dibujante:bennett", comic))
        assertTrue(matches("entintador:jose", comic))
        assertTrue(matches("color:mounts", comic))
        assertTrue(matches("editorial:marvel", comic))
        assertTrue(matches("año>2015", comic))
        assertTrue(matches("ano>2015", comic))
    }

    @Test
    fun fieldTermNeverMatchesANullField() {
        assertFalse(matches("writer:ewing", comic(writer = null)))
        assertFalse(matches("year:2018", comic(year = null)))
        assertFalse(matches("year>1900", comic(year = null)))
    }

    @Test
    fun yearComparisons() {
        val comic = comic(year = 2018)
        assertTrue(matches("year:2018", comic))
        assertFalse(matches("year:2019", comic))
        assertTrue(matches("year>2015", comic))
        assertFalse(matches("year>2018", comic))
        assertTrue(matches("year>=2018", comic))
        assertTrue(matches("year<2020", comic))
        assertFalse(matches("year<2018", comic))
        assertTrue(matches("year<=2018", comic))
    }

    @Test
    fun addedWithinDays() {
        val recent = comic(addedAt = now - 3 * day)
        val old = comic(addedAt = now - 90 * day)
        assertTrue(matches("added:30d", recent))
        assertFalse(matches("added:30d", old))
        assertTrue(matches("added:120d", old))
    }

    @Test
    fun readingStates() {
        val started = comic(pageIndex = 4, completed = false)
        val untouched = comic(pageIndex = 0, completed = false)
        val finished = comic(pageIndex = 19, completed = true)
        assertTrue(matches("is:reading", started))
        assertFalse(matches("is:reading", untouched))
        assertFalse(matches("is:reading", finished))
        assertTrue(matches("is:unread", untouched))
        assertFalse(matches("is:unread", finished))
        assertTrue(matches("is:read", finished))
        assertFalse(matches("is:read", started))
    }

    @Test
    fun termsAreAnded() {
        val comic = comic(writer = "Al Ewing", year = 2018)
        assertTrue(matches("writer:ewing year>2015", comic))
        assertFalse(matches("writer:ewing year>2020", comic))
        assertFalse(matches("writer:bennett year>2015", comic))
        assertTrue(matches("hulk writer:ewing", comic))
    }

    @Test
    fun unknownKeyIsTreatedAsText() {
        assertTrue(matches("foo:bar", comic(storyTitle = "Enter foo:bar now")))
        assertFalse(matches("foo:bar", comic()))
        assertTrue(matches("hulk:1", comic(displayName = "hulk:1 scan")))
    }

    @Test
    fun emptyFieldValueIsIgnored() {
        assertTrue(matches("writer:", comic(writer = null)))
        assertTrue(matches("writer: hulk", comic()))
    }

    @Test
    fun unparseableFieldValueFallsBackToText() {
        assertTrue(matches("year:soon", comic(storyTitle = "Coming year:soon")))
        assertFalse(matches("year:soon", comic(year = 2018)))
        assertTrue(matches("is:sideways", comic(displayName = "is:sideways")))
        assertTrue(matches("added:lately", comic(displayName = "added:lately")))
    }

    @Test
    fun quotedPhrasesStayOneTerm() {
        val comic = comic(writer = "Al Ewing", penciller = "Joe Bennett")
        assertTrue(matches("writer:\"al ewing\"", comic))
        assertFalse(matches("writer:\"al bennett\"", comic))
        assertTrue(matches("\"al ewing\"", comic))
        assertFalse(matches("\"ewing al\"", comic))
    }

    @Test
    fun catalogSearchAppliesTheQuery() {
        val ewing = comic(writer = "Al Ewing").copy(id = 1)
        val bennett = comic(writer = "Joe Bennett").copy(id = 2)
        val found = LibraryCatalog.search(listOf(ewing, bennett), "writer:ewing", now)
        assertTrue(found == listOf(ewing))
    }

    @Test
    fun ratingAtLeastWithPlusSugar() {
        assertTrue(matches("rating:4+", comic(rating = 5)))
        assertTrue(matches("rating:4+", comic(rating = 4)))
        assertFalse(matches("rating:4+", comic(rating = 3)))
        assertFalse(matches("rating:4+", comic()))
    }

    @Test
    fun ratingComparisonsAndExactValue() {
        assertTrue(matches("rating>=4", comic(rating = 4)))
        assertTrue(matches("rating:3", comic(rating = 3)))
        assertFalse(matches("rating:3", comic(rating = 4)))
        assertTrue(matches("rating<2", comic()))
        assertTrue(matches("valoración:5", comic(rating = 5)))
    }

    @Test
    fun unparseableRatingFallsBackToText() {
        assertTrue(matches("rating:great", comic(displayName = "rating:great")))
        assertFalse(matches("rating:great", comic(rating = 5)))
    }
}
