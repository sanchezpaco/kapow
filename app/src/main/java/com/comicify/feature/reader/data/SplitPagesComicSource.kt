package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.domain.PageSide
import com.comicify.feature.reader.domain.SplitPage
import com.comicify.feature.reader.domain.SplitPages
import java.io.IOException

private const val HALVES_PER_WIDE_PAGE = 2

class SplitPagesComicSource private constructor(
    private val inner: ComicSource,
    private val pages: List<SplitPage>,
) : ComicSource {

    override val pageCount: Int get() = pages.size

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap {
        val page = pages[index]
        val side = page.side ?: return inner.decodePage(page.sourceIndex, targetWidth)
        return inner.decodePage(page.sourceIndex, targetWidth * HALVES_PER_WIDE_PAGE).half(side)
    }

    override suspend fun pageAspect(index: Int): Float {
        val page = pages[index]
        val aspect = inner.pageAspect(page.sourceIndex)
        return if (page.side == null) aspect else aspect / HALVES_PER_WIDE_PAGE
    }

    fun sourcePageOf(index: Int): Int = pages[index.coerceIn(pages.indices)].sourceIndex

    fun pageOfSource(sourceIndex: Int): Int = SplitPages.firstPageOfSource(pages, sourceIndex)

    override fun close() = inner.close()

    companion object {
        suspend fun of(inner: ComicSource, direction: ReadingDirection): SplitPagesComicSource {
            val aspects = try {
                (0 until inner.pageCount).map { inner.pageAspect(it) }
            } catch (e: IOException) {
                throw ComicSourceException.ReadFailure(e)
            }
            return SplitPagesComicSource(inner, SplitPages.of(aspects, direction))
        }
    }
}

private fun Bitmap.half(side: PageSide): Bitmap {
    val halfWidth = (width / HALVES_PER_WIDE_PAGE).coerceAtLeast(1)
    val startX = if (side == PageSide.Left) 0 else width - halfWidth
    return Bitmap.createBitmap(this, startX, 0, halfWidth, height).also { if (it !== this) recycle() }
}
