package com.comicify.feature.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StripChainTest {

    private fun link(title: String, pages: Int) = StripLink(title, List(pages) { 2f / 3f })

    @Test
    fun singleLinkHasNoBoundary() {
        val items = StripChain.items(listOf(link("#1", 3)))

        assertEquals(3, items.size)
        assertEquals(listOf(0, 1, 2), items.map { (it as StripItem.Page).page })
    }

    @Test
    fun boundarySitsBetweenIssuesAndNamesBoth() {
        val items = StripChain.items(listOf(link("#1", 2), link("#2", 2)))

        assertEquals(5, items.size)
        val boundary = items[2] as StripItem.Boundary
        assertEquals("#1", boundary.finished)
        assertEquals("#2", boundary.next)
        assertEquals(0, boundary.link)
        assertEquals(1, (items[3] as StripItem.Page).link)
    }

    @Test
    fun lastIssueNeverEndsInABoundary() {
        val items = StripChain.items(listOf(link("#1", 1), link("#2", 1), link("#3", 1)))

        assertEquals(5, items.size)
        assertEquals(StripItem.Page(2, 0, 2f / 3f), items.last())
    }

    @Test
    fun appendingAnIssueLeavesTheEarlierItemsInPlace() {
        val first = StripChain.items(listOf(link("#1", 3)))
        val grown = StripChain.items(listOf(link("#1", 3), link("#2", 3)))

        assertEquals(first, grown.take(first.size))
    }

    @Test
    fun pageLookupSkipsBoundaries() {
        val items = StripChain.items(listOf(link("#1", 2), link("#2", 2)))

        assertEquals(0, StripChain.indexOfPage(items, link = 0, page = 0))
        assertEquals(3, StripChain.indexOfPage(items, link = 1, page = 0))
        assertEquals(4, StripChain.indexOfPage(items, link = 1, page = 1))
        assertEquals(-1, StripChain.indexOfPage(items, link = 1, page = 9))
    }
}
