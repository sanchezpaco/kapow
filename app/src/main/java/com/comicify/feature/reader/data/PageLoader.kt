package com.comicify.feature.reader.data

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.domain.BubbleLayout
import com.comicify.feature.reader.domain.GuidedTour
import com.comicify.feature.reader.domain.SpeechBubble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TARGET_WIDTH_PX = 2160
private const val THUMB_WIDTH_PX = 180
private const val CACHE_SIZE = 5
private const val CROP_MARGINS = true
private const val THUMB_CACHE_SIZE = 64
private const val PARALLEL_DETECTIONS = 1
private const val LOG_TAG = "PageLoader"

class PageLoader(
    private val source: ComicSource,
    private val scope: CoroutineScope,
    private val panelDetector: PanelDetector,
    private val detectionStore: PageDetectionStore,
) {
    private val cache = LruCache<Int, PageArt>(CACHE_SIZE)
    private val thumbCache = LruCache<Int, ImageBitmap>(THUMB_CACHE_SIZE)
    private val panelCache = LruCache<Int, List<Rect>>(CACHE_SIZE)
    private val bubbleCache = LruCache<Int, List<SpeechBubble>>(CACHE_SIZE)
    private val overlayCache = LruCache<Int, PageOverlay>(CACHE_SIZE)
    @Volatile private var focus = 0
    private val detectionSlots = Semaphore(PARALLEL_DETECTIONS)
    private val locks = HashMap<Int, Mutex>()
    private val thumbLocks = HashMap<Int, Mutex>()
    private val panelLocks = HashMap<Int, Mutex>()
    private val bubbleLocks = HashMap<Int, Mutex>()
    private val overlayLocks = HashMap<Int, Mutex>()
    private val aspectsMutex = Mutex()
    @Volatile private var aspects: List<Float>? = null

    val pageCount: Int get() = source.pageCount

    suspend fun aspects(): List<Float> = aspectsMutex.withLock {
        aspects ?: (0 until source.pageCount).map { source.pageAspect(it) }.also { aspects = it }
    }

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

    private suspend fun decode(index: Int): PageArt = timed("decode", index) {
        val decoded = source.decodePage(index, TARGET_WIDTH_PX)
        withContext(Dispatchers.Default) {
            val bitmap = if (CROP_MARGINS) decoded.contentCropped() else decoded
            val analysis = bitmap.atAnalysisSize()
            val image = bitmap.copy(Bitmap.Config.HARDWARE, false) ?: bitmap
            if (image !== bitmap && analysis !== bitmap) bitmap.recycle()
            PageArt(image.asImageBitmap(), analysis.asImageBitmap(), Color(analysis.ambientColorInt()))
        }
    }

    private suspend fun decodeThumb(index: Int): ImageBitmap {
        val decoded = source.decodePage(index, THUMB_WIDTH_PX)
        val thumb = decoded.copy(Bitmap.Config.HARDWARE, false) ?: return decoded.asImageBitmap()
        decoded.recycle()
        return thumb.asImageBitmap()
    }

    suspend fun panels(index: Int): List<Rect> {
        panelCache[index]?.let { return it }
        val mutex = synchronized(panelLocks) { panelLocks.getOrPut(index) { Mutex() } }
        return mutex.withLock {
            panelCache[index] ?: (detectionStore.panels(index) ?: detectPanels(index)).also { panelCache.put(index, it) }
        }
    }

    suspend fun stops(index: Int, direction: ReadingDirection): List<Rect> {
        val panels = panels(index)
        val bubbles = bubbles(index).map { it.box }
        return GuidedTour.stops(panels, bubbles, direction)
    }

    private suspend fun detectPanels(index: Int): List<Rect> {
        val art = load(index)
        return detectionSlots.withPermit {
            timed("panels", index) { withContext(Dispatchers.Default) { panelDetector.detect(art.analysis.asAndroidBitmap()) } }
        }.also { detectionStore.savePanels(index, it) }
    }

    suspend fun bubbles(index: Int): List<SpeechBubble> {
        bubbleCache[index]?.let { return it }
        val mutex = synchronized(bubbleLocks) { bubbleLocks.getOrPut(index) { Mutex() } }
        return mutex.withLock {
            bubbleCache[index] ?: (detectionStore.bubbles(index) ?: detectBubbles(index)).also { bubbleCache.put(index, it) }
        }
    }

    suspend fun overlay(index: Int, scale: Float): List<PaintedBubble> {
        cachedOverlay(index, scale)?.let { return it }
        val focused = focus
        if (index != focused) runCatching { overlay(focused, scale) }
        val mutex = synchronized(overlayLocks) { overlayLocks.getOrPut(index) { Mutex() } }
        return mutex.withLock {
            cachedOverlay(index, scale) ?: planOverlay(index, scale).also { overlayCache.put(index, PageOverlay(scale, it)) }
        }
    }

    private fun cachedOverlay(index: Int, scale: Float): List<PaintedBubble>? = overlayCache[index]?.takeIf { it.scale == scale }?.bubbles

    private suspend fun planOverlay(index: Int, scale: Float): List<PaintedBubble> {
        val bubbles = bubbles(index)
        val art = load(index)
        val enlarged = BubbleLayout.enlarge(bubbles, scale)
        return timed("bubble plan", index) { withContext(Dispatchers.Default) { BubblePlan.of(art.analysis, enlarged) } }
    }

    private suspend fun detectBubbles(index: Int): List<SpeechBubble> {
        val art = load(index)
        return detectionSlots.withPermit {
            timed("bubbles", index) { withContext(Dispatchers.Default) { panelDetector.bubbles(art.analysis.asAndroidBitmap()) } }
        }.also { detectionStore.saveBubbles(index, it) }
    }

    private suspend fun <T> timed(what: String, index: Int, detect: suspend () -> T): T {
        val started = SystemClock.elapsedRealtime()
        return detect().also { Log.d(LOG_TAG, "$what on page $index in ${SystemClock.elapsedRealtime() - started} ms") }
    }

    fun preload(around: Int, indices: Iterable<Int>, bubbleScale: Float?, panels: Boolean) {
        focus = around
        indices.filter { it in 0 until source.pageCount }
            .sortedBy { abs(it - around) }
            .forEach { index ->
                scope.launch {
                    runCatching {
                        if (bubbleScale != null) overlay(index, bubbleScale) else load(index)
                        if (panels) {
                            panels(index)
                            bubbles(index)
                        }
                    }
                }
            }
    }

    private class PageOverlay(val scale: Float, val bubbles: List<PaintedBubble>)
}
