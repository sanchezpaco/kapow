package com.comicify.feature.library.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.comicify.feature.reader.data.ComicSource
import com.comicify.feature.reader.data.ComicSourceFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

private const val THUMB_WIDTH_PX = COVER_WIDTH_PX / 2
private const val THUMB_CACHE_SIZE = 64

class PageThumbnails private constructor(private val source: ComicSource) : Closeable {

    private val cache = LruCache<Int, ImageBitmap>(THUMB_CACHE_SIZE)
    private val decoding = Mutex()

    val pageCount: Int get() = source.pageCount

    suspend fun thumbnail(index: Int): ImageBitmap? =
        cache[index] ?: decoding.withLock {
            cache[index] ?: decode(index)?.also { cache.put(index, it) }
        }

    private suspend fun decode(index: Int): ImageBitmap? =
        runCatching { source.decodePage(index, THUMB_WIDTH_PX) }.getOrNull()?.let { decoded ->
            val hardware = decoded.copy(Bitmap.Config.HARDWARE, false) ?: return@let decoded.asImageBitmap()
            decoded.recycle()
            hardware.asImageBitmap()
        }

    override fun close() = source.close()

    companion object {
        suspend fun open(context: Context, documentUri: Uri): PageThumbnails =
            PageThumbnails(ComicSourceFactory.open(context, documentUri, startPage = 0))
    }
}
