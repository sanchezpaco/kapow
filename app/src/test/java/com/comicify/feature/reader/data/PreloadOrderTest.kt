package com.comicify.feature.reader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PreloadOrderTest {

    @Test
    fun focusedPageComesFirst() {
        assertEquals(7, ((6..9) + 5).sortedWith(preloadOrder(7)).first())
    }

    @Test
    fun pagesAheadComeBeforePagesBehind() {
        assertEquals(listOf(7, 8, 9, 6, 5), ((5..9).toList()).sortedWith(preloadOrder(7)))
    }

    @Test
    fun nearerPagesComeFirstOnTheSameSide() {
        assertEquals(listOf(3, 4, 5, 6), listOf(6, 4, 5, 3).sortedWith(preloadOrder(3)))
    }
}
