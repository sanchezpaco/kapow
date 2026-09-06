package com.comicify.feature.reader.data

internal enum class ComicFileFormat { Zip, Rar, SevenZip, Tar, Pdf, Unsupported }

internal const val TAR_MAGIC_OFFSET = 257

internal const val MAGIC_BYTE_COUNT = TAR_MAGIC_OFFSET + 5

private val zipMagic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
private val rarMagic = "Rar!".toByteArray(Charsets.US_ASCII)
private val pdfMagic = "%PDF".toByteArray(Charsets.US_ASCII)
private val sevenZipMagic = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
private val tarMagic = "ustar".toByteArray(Charsets.US_ASCII)

internal fun detectComicFileFormat(magic: ByteArray): ComicFileFormat = when {
    magic.matchesAt(0, rarMagic) -> ComicFileFormat.Rar
    magic.matchesAt(0, pdfMagic) -> ComicFileFormat.Pdf
    magic.matchesAt(0, zipMagic) -> ComicFileFormat.Zip
    magic.matchesAt(0, sevenZipMagic) -> ComicFileFormat.SevenZip
    magic.matchesAt(TAR_MAGIC_OFFSET, tarMagic) -> ComicFileFormat.Tar
    else -> ComicFileFormat.Unsupported
}

private fun ByteArray.matchesAt(offset: Int, expected: ByteArray): Boolean =
    size >= offset + expected.size && expected.indices.all { this[offset + it] == expected[it] }
