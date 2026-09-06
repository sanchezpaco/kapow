package com.comicify.feature.library.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryReconcilerTest {

    @Test
    fun `an arrival matching a missing hash relinks that comic`() {
        val arrival = arriving("content://tree/Batman 002.cbz", hash = "abc")

        val result = LibraryReconciler.reconcile(listOf(arrival), listOf(MissingComic(7, "abc")))

        assertEquals(listOf(ComicRelink(7, arrival)), result.relinks)
        assertEquals(emptyList<ArrivingComic>(), result.additions)
        assertEquals(emptyList<Long>(), result.removals)
    }

    @Test
    fun `an unmatched arrival is added and an unmatched row is removed`() {
        val arrival = arriving("content://tree/New.cbz", hash = "new")

        val result = LibraryReconciler.reconcile(listOf(arrival), listOf(MissingComic(7, "gone")))

        assertEquals(emptyList<ComicRelink>(), result.relinks)
        assertEquals(listOf(arrival), result.additions)
        assertEquals(listOf(7L), result.removals)
    }

    @Test
    fun `a row without a hash never relinks`() {
        val arrival = arriving("content://tree/New.cbz", hash = "abc")

        val result = LibraryReconciler.reconcile(listOf(arrival), listOf(MissingComic(7, null)))

        assertEquals(listOf(arrival), result.additions)
        assertEquals(listOf(7L), result.removals)
    }

    @Test
    fun `an arrival that could not be hashed never relinks`() {
        val arrival = arriving("content://tree/New.cbz", hash = null)

        val result = LibraryReconciler.reconcile(listOf(arrival), listOf(MissingComic(7, "abc")))

        assertEquals(listOf(arrival), result.additions)
        assertEquals(listOf(7L), result.removals)
    }

    @Test
    fun `two copies of one comic consume one missing row each`() {
        val first = arriving("content://tree/Copy 1.cbz", hash = "abc")
        val second = arriving("content://tree/Copy 2.cbz", hash = "abc")

        val result = LibraryReconciler.reconcile(
            listOf(first, second),
            listOf(MissingComic(1, "abc"), MissingComic(2, "abc")),
        )

        assertEquals(listOf(ComicRelink(1, first), ComicRelink(2, second)), result.relinks)
        assertEquals(emptyList<Long>(), result.removals)
    }

    @Test
    fun `a duplicate arrival beyond the missing rows is added`() {
        val first = arriving("content://tree/Copy 1.cbz", hash = "abc")
        val second = arriving("content://tree/Copy 2.cbz", hash = "abc")

        val result = LibraryReconciler.reconcile(listOf(first, second), listOf(MissingComic(1, "abc")))

        assertEquals(listOf(ComicRelink(1, first)), result.relinks)
        assertEquals(listOf(second), result.additions)
        assertEquals(emptyList<Long>(), result.removals)
    }

    @Test
    fun `nothing missing means every arrival is added`() {
        val arrival = arriving("content://tree/New.cbz", hash = "abc")

        val result = LibraryReconciler.reconcile(listOf(arrival), emptyList())

        assertEquals(listOf(arrival), result.additions)
        assertEquals(emptyList<Long>(), result.removals)
    }

    @Test
    fun `a rescan with no new files changes nothing`() {
        val result = LibraryReconciler.reconcile(emptyList(), emptyList())

        assertEquals(emptyList<ComicRelink>(), result.relinks)
        assertEquals(emptyList<ArrivingComic>(), result.additions)
        assertEquals(emptyList<Long>(), result.removals)
    }

    private fun arriving(documentUri: String, hash: String?): ArrivingComic =
        ArrivingComic(
            documentUri = documentUri,
            displayName = documentUri.substringAfterLast('/'),
            folderName = "Batman",
            contentHash = hash,
        )
}
