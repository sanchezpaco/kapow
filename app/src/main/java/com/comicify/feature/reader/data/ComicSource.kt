package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.feature.library.domain.COMIC_INFO_ENTRY
import java.io.Closeable

interface ComicSource : Closeable {
    val pageCount: Int
    suspend fun decodePage(index: Int, targetWidth: Int): Bitmap
    suspend fun pageAspect(index: Int): Float
    suspend fun comicInfoXml(): String?
}

internal fun String.isComicInfoPath(): Boolean =
    substringAfterLast('/').substringAfterLast('\\').equals(COMIC_INFO_ENTRY, ignoreCase = true)

private val archiveMetadataSegments = setOf("__MACOSX", "PaxHeader")
private const val APPLE_DOUBLE_PREFIX = "._"

internal fun String.isPageEntry(): Boolean {
    val segments = split('/', '\\')
    if (segments.any { it in archiveMetadataSegments }) return false
    if (segments.last().startsWith(APPLE_DOUBLE_PREFIX)) return false
    return hasImageExtension()
}
