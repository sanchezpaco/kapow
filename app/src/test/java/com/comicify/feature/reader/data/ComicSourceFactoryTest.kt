package com.comicify.feature.reader.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TAR_BLOCK_SIZE = 512

class ComicSourceFactoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun detectsZipMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        assertEquals(ComicFileFormat.Zip, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }

    @Test
    fun detectsRarMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte()))

        assertEquals(ComicFileFormat.Rar, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }

    @Test
    fun detectsPdfMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))

        assertEquals(ComicFileFormat.Pdf, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }

    @Test
    fun detectsSevenZipMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C))

        assertEquals(ComicFileFormat.SevenZip, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }

    @Test
    fun detectsTarMagicBytesPastTheFirstBlockBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        val header = ByteArray(TAR_BLOCK_SIZE)
        "ustar".toByteArray().copyInto(header, destinationOffset = TAR_MAGIC_OFFSET)
        file.writeBytes(header)

        assertEquals(ComicFileFormat.Tar, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }

    @Test
    fun classifiesEmptyFileAsUnsupported() {
        val file = temporaryFolder.newFile("comic.dat")

        assertEquals(ComicFileFormat.Unsupported, ComicSourceFactory.detectFormat(file.inputStream().channel))
    }
}
