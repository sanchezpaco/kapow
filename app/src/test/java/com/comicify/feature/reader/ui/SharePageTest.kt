package com.comicify.feature.reader.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.comicify.domain.model.ReadingDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SharePageTest {

    @Test
    fun singlePageSharesOnlyThatPage() {
        assertEquals(
            listOf(4),
            sharedPages(pageIndex = 4, pageCount = 20, spread = false, coverAlone = false, direction = ReadingDirection.LeftToRight),
        )
    }

    @Test
    fun spreadSharesBothPagesInReadingOrder() {
        assertEquals(
            listOf(4, 5),
            sharedPages(pageIndex = 4, pageCount = 20, spread = true, coverAlone = false, direction = ReadingDirection.LeftToRight),
        )
        assertEquals(
            listOf(5, 4),
            sharedPages(pageIndex = 4, pageCount = 20, spread = true, coverAlone = false, direction = ReadingDirection.RightToLeft),
        )
    }

    @Test
    fun spreadPairingFollowsTheCoverAloneSetting() {
        assertEquals(
            listOf(0),
            sharedPages(pageIndex = 0, pageCount = 20, spread = true, coverAlone = true, direction = ReadingDirection.LeftToRight),
        )
        assertEquals(
            listOf(1, 2),
            sharedPages(pageIndex = 1, pageCount = 20, spread = true, coverAlone = true, direction = ReadingDirection.LeftToRight),
        )
    }

    @Test
    fun spreadDropsAPageOutsideTheComic() {
        assertEquals(
            listOf(19),
            sharedPages(pageIndex = 19, pageCount = 20, spread = true, coverAlone = true, direction = ReadingDirection.LeftToRight),
        )
    }

    @Test
    fun onePageKeepsItsOwnSize() {
        assertEquals(listOf(IntRect(0, 0, 1200, 1800)), joinedSlots(listOf(IntSize(1200, 1800))))
    }

    @Test
    fun twoPagesAreJoinedSideBySideAtACommonHeight() {
        val slots = joinedSlots(listOf(IntSize(1000, 1500), IntSize(1200, 3000)))
        assertEquals(IntRect(0, 0, 2000, 3000), slots[0])
        assertEquals(IntRect(2000, 0, 3200, 3000), slots[1])
    }

    @Test
    fun panelCropIsPixelsOfThePage() {
        assertEquals(
            IntRect(100, 300, 500, 900),
            panelCrop(Rect(0.1f, 0.1f, 0.5f, 0.3f), IntSize(1000, 3000)),
        )
    }

    @Test
    fun panelCropStaysInsideThePageAndKeepsAPixel() {
        assertEquals(IntRect(0, 0, 1000, 3000), panelCrop(Rect(-0.2f, -0.2f, 1.4f, 1.4f), IntSize(1000, 3000)))
        assertEquals(IntRect(999, 2999, 1000, 3000), panelCrop(Rect(1f, 1f, 1f, 1f), IntSize(1000, 3000)))
    }

    @Test
    fun fileNameKeepsReadableCharactersAndReplacesTheRest() {
        assertEquals("Saga #3 _ page 7.jpg", shareFileName("Saga #3 · page 7"))
        assertEquals("page.jpg", shareFileName("  "))
    }

    @Test
    fun comicNameFallsBackToTheFileNameWithoutExtension() {
        assertEquals("Saga 003", comicNameOfPath("primary:Comics/Saga 003.cbz"))
        assertEquals("", comicNameOfPath(""))
    }
}
