package com.comicify.feature.library.domain

import com.comicify.core.storage.ComicEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicCoverTest {

    @Test
    fun `each page writes its own cover file`() {
        assertNotEquals(ComicCover.fileName(7, 0), ComicCover.fileName(7, 3))
        assertEquals("7_3.jpg", ComicCover.fileName(7, 3))
    }

    @Test
    fun `a comic only owns its own cover files`() {
        assertTrue(ComicCover.belongsTo("7_3.jpg", 7))
        assertTrue(ComicCover.belongsTo("7.jpg", 7))
        assertFalse(ComicCover.belongsTo("71_3.jpg", 7))
        assertFalse(ComicCover.belongsTo("17_3.jpg", 7))
    }

    @Test
    fun `a chosen page past the end falls back to the last page`() {
        assertEquals(19, ComicCover.page(chosen = 40, pageCount = 20))
    }

    @Test
    fun `a negative page falls back to the first page`() {
        assertEquals(FIRST_PAGE, ComicCover.page(chosen = -1, pageCount = 20))
    }

    @Test
    fun `an unknown page count falls back to the first page`() {
        assertEquals(FIRST_PAGE, ComicCover.page(chosen = 4, pageCount = null))
    }

    @Test
    fun `a chosen page inside the comic is kept`() {
        assertEquals(4, ComicCover.page(chosen = 4, pageCount = 20))
    }

    @Test
    fun `a comic stored before the column existed uses the first page`() {
        val comic = ComicEntity(
            documentUri = "content://comics/1",
            displayName = "Kapow #1",
            series = "Kapow",
            issueNumber = 1,
            year = null,
            pageCount = 20,
            coverPath = null,
            addedAt = 0,
        )

        assertEquals(FIRST_PAGE, comic.coverPage)
        assertEquals(FIRST_PAGE, ComicCover.page(comic.coverPage, comic.pageCount))
    }
}
