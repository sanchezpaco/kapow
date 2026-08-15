package com.comicify.feature.reader.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ComicSourceFactoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun detectsZipMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))

        assertEquals(ArchiveFormat.Zip, ComicSourceFactory.detectFormat(file))
    }

    @Test
    fun detectsRarMagicBytes() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte()))

        assertEquals(ArchiveFormat.Rar, ComicSourceFactory.detectFormat(file))
    }

    @Test
    fun classifiesUnrecognizedMagicBytesAsUnsupported() {
        val file = temporaryFolder.newFile("comic.dat")
        file.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))

        assertEquals(ArchiveFormat.Unsupported, ComicSourceFactory.detectFormat(file))
    }

    @Test
    fun classifiesEmptyFileAsUnsupported() {
        val file = temporaryFolder.newFile("comic.dat")

        assertEquals(ArchiveFormat.Unsupported, ComicSourceFactory.detectFormat(file))
    }
}
