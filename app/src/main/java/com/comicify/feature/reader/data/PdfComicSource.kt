package com.comicify.feature.reader.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class PdfComicSource private constructor(
    private val cacheFile: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : ComicSource {

    private val renderLock = Mutex()

    override val pageCount: Int get() = renderer.pageCount

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            renderLock.withLock { renderPage(index, targetWidth) }
        }

    private fun renderPage(index: Int, targetWidth: Int): Bitmap =
        renderer.openPage(index).use { page ->
            val width = if (targetWidth > 0) targetWidth else page.width
            val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        runCatching { cacheFile.delete() }
    }

    companion object {
        suspend fun fromFile(cacheFile: File): PdfComicSource =
            withContext(Dispatchers.IO) {
                val descriptor =
                    ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                PdfComicSource(cacheFile, descriptor, renderer)
            }
    }
}
