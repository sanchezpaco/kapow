package com.comicify.feature.reader.data

internal enum class ComicFileFormat { Zip, Rar, Pdf, Unsupported }

internal const val MAGIC_BYTE_COUNT = 4

private val zipMagic = byteArrayOf('P'.code.toByte(), 'K'.code.toByte())
private val rarMagic = "Rar!".toByteArray(Charsets.US_ASCII)
private val pdfMagic = "%PDF".toByteArray(Charsets.US_ASCII)

internal fun detectComicFileFormat(magic: ByteArray): ComicFileFormat = when {
    magic.startsWith(rarMagic) -> ComicFileFormat.Rar
    magic.startsWith(pdfMagic) -> ComicFileFormat.Pdf
    magic.startsWith(zipMagic) -> ComicFileFormat.Zip
    else -> ComicFileFormat.Unsupported
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
