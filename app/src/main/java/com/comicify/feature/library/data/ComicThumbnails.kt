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

private const val THUMB_WIDTH_PX = 240
private const val THUMB_CACHE_SIZE = 96

class ComicThumbnails(private val context: Context, private val uri: Uri) {
    private val cache = LruCache<Int, ImageBitmap>(THUMB_CACHE_SIZE)
    private val openLock = Mutex()
    private var source: ComicSource? = null

    suspend fun pageCount(): Int = source().pageCount

    suspend fun thumb(index: Int): ImageBitmap {
        cache[index]?.let { return it }
        val decoded = source().decodePage(index, THUMB_WIDTH_PX)
        val thumb = decoded.copy(Bitmap.Config.HARDWARE, false)?.also { decoded.recycle() } ?: decoded
        return thumb.asImageBitmap().also { cache.put(index, it) }
    }

    private suspend fun source(): ComicSource = openLock.withLock {
        source ?: ComicSourceFactory.open(context, uri, startPage = 0).also { source = it }
    }

    fun close() {
        source?.close()
        source = null
    }
}
