package com.comicify.feature.reader.domain

import com.comicify.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingTypeTest {

    @Test
    fun `manga reads right to left`() {
        assertEquals(ReadingDirection.RightToLeft, ReadingType.Manga.direction)
    }

    @Test
    fun `comics and webcomics read left to right`() {
        assertEquals(ReadingDirection.LeftToRight, ReadingType.Comic.direction)
        assertEquals(ReadingDirection.LeftToRight, ReadingType.Webcomic.direction)
    }

    @Test
    fun `cycling walks every type and comes back`() {
        assertEquals(ReadingType.Manga, ReadingType.Comic.next())
        assertEquals(ReadingType.Webcomic, ReadingType.Manga.next())
        assertEquals(ReadingType.Comic, ReadingType.Webcomic.next())
    }

    @Test
    fun `the persisted names are the ones the room migration writes`() {
        assertEquals("Comic", ReadingType.Comic.name)
        assertEquals("Manga", ReadingType.Manga.name)
        assertEquals("Webcomic", ReadingType.Webcomic.name)
    }
}
