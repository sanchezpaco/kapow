package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.core.util.naturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class CbzComicSource private constructor(
    private val cacheFile: File,
    private val zip: ZipFile,
    private val entries: List<ZipEntry>,
) : ComicSource {

    override val pageCount: Int get() = entries.size

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val bytes = zip.getInputStream(entries[index]).use { it.readBytes() }
            decodeSampled(bytes, targetWidth)
        }

    override fun close() {
        runCatching { zip.close() }
        runCatching { cacheFile.delete() }
    }

    companion object {
        suspend fun fromFile(cacheFile: File): CbzComicSource =
            withContext(Dispatchers.IO) {
                val zip = ZipFile(cacheFile)
                val entries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.hasImageExtension() }
                    .sortedWith(compareBy(naturalOrder) { it.name })
                    .toList()
                CbzComicSource(cacheFile, zip, entries)
            }
    }
}
