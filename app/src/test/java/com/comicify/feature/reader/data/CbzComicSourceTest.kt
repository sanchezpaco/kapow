package com.comicify.feature.reader.data

import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CbzComicSourceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun archiveWithNoImageEntriesHasZeroPages() = runTest {
        val file = temporaryFolder.newFile("comic.cbz")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("readme.txt"))
            zip.write("not a page".toByteArray())
            zip.closeEntry()
        }

        val source = CbzComicSource.fromFile(file)

        assertEquals(0, source.pageCount)
        source.close()
    }

    @Test
    fun corruptArchiveFailsToOpenWithIOException() = runTest {
        val file = temporaryFolder.newFile("comic.cbz")
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x01, 0x02, 0x03))

        try {
            CbzComicSource.fromFile(file)
            fail("Expected an IOException opening a corrupt archive")
        } catch (expected: IOException) {
        }
    }
}
