package com.comicify.feature.reader.data

import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TARGET_WIDTH_PX = 2160
private const val THUMB_WIDTH_PX = 180
private const val CACHE_SIZE = 8
private const val CROP_MARGINS = true
private const val THUMB_CACHE_SIZE = 64

class PageLoader(
    private val source: ComicSource,
    private val scope: CoroutineScope,
) {
    private val cache = LruCache<Int, PageArt>(CACHE_SIZE)
    private val thumbCache = LruCache<Int, ImageBitmap>(THUMB_CACHE_SIZE)
    private val panelCache = LruCache<Int, List<Rect>>(CACHE_SIZE)
    private val locks = HashMap<Int, Mutex>()
    private val thumbLocks = HashMap<Int, Mutex>()

    val pageCount: Int get() = source.pageCount

    suspend fun load(index: Int): PageArt {
        cache[index]?.let { return it }
        val mutex = synchronized(locks) { locks.getOrPut(index) { Mutex() } }
        return mutex.withLock {
            cache[index] ?: decode(index).also { cache.put(index, it) }
        }
    }

    suspend fun loadThumb(index: Int): ImageBitmap {
        thumbCache[index]?.let { return it }
        val mutex = synchronized(thumbLocks) { thumbLocks.getOrPut(index) { Mutex() } }
        return mutex.withLock {
            thumbCache[index] ?: decodeThumb(index).also { thumbCache.put(index, it) }
        }
    }

    private suspend fun decode(index: Int): PageArt {
        val decoded = source.decodePage(index, TARGET_WIDTH_PX)
        return withContext(Dispatchers.Default) {
            val bitmap = if (CROP_MARGINS) decoded.contentCropped() else decoded
            PageArt(bitmap.asImageBitmap(), Color(bitmap.ambientColorInt()))
        }
    }

    private suspend fun decodeThumb(index: Int): ImageBitmap =
        source.decodePage(index, THUMB_WIDTH_PX).asImageBitmap()

    suspend fun panels(index: Int): List<Rect> {
        panelCache[index]?.let { return it }
        val art = load(index)
        return withContext(Dispatchers.Default) { PanelDetector.detect(art.image.asAndroidBitmap()) }
            .also { panelCache.put(index, it) }
    }

    fun preload(indices: Iterable<Int>) {
        indices.filter { it in 0 until source.pageCount && cache[it] == null }
            .forEach { index -> scope.launch { runCatching { load(index) } } }
    }
}
