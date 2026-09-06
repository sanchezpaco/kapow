package com.comicify.feature.reader.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toRect
import androidx.core.content.FileProvider
import com.comicify.R
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.reader.data.PageArt
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.data.PaintedBubble
import com.comicify.feature.reader.domain.PageOrder
import com.comicify.feature.reader.ui.BubbleOverlay.drawBubbles
import java.io.File
import kotlin.math.roundToInt

private const val SHARE_DIR = "share"
private const val SHARE_JPEG_QUALITY = 90
private const val SHARE_MIME_TYPE = "image/jpeg"
private const val SHARE_FILE_EXTENSION = ".jpg"
private const val SHARE_FILE_NAME_FALLBACK = "page"
private const val SHARE_FILE_NAME_EXTRA_CHARS = " -_.#"

data class SharePageRequest(
    val title: String,
    val pageIndex: Int,
    val pages: List<Int>,
    val panelView: Rect?,
    val bubbleScale: Float?,
)

class SharePage(private val context: Context) {

    suspend fun compose(loader: PageLoader, request: SharePageRequest): Intent {
        val label = context.getString(R.string.reader_share_title, request.title, request.pageIndex + 1)
        val dir = File(context.cacheDir, SHARE_DIR).apply { deleteRecursively(); mkdirs() }
        val file = File(dir, shareFileName(label))
        val image = render(loader, request)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, SHARE_JPEG_QUALITY, it) }
        image.recycle()
        return shareIntent(contentUri(file), label)
    }

    private suspend fun render(loader: PageLoader, request: SharePageRequest): Bitmap {
        val arts = request.pages.map { loader.load(it) }
        val overlays = request.pages.map { index -> request.bubbleScale?.let { loader.overlay(index, it) }.orEmpty() }
        val joined = drawPages(arts, overlays)
        val panelView = request.panelView ?: return joined
        val crop = panelCrop(panelView, IntSize(joined.width, joined.height))
        val cropped = Bitmap.createBitmap(joined, crop.left, crop.top, crop.width, crop.height)
        if (cropped !== joined) joined.recycle()
        return cropped
    }

    private fun drawPages(arts: List<PageArt>, overlays: List<List<PaintedBubble>>): Bitmap {
        val slots = joinedSlots(arts.map { IntSize(it.image.width, it.image.height) })
        val size = Size(slots.last().right.toFloat(), slots.maxOf { it.bottom }.toFloat())
        val target = Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(target.asImageBitmap()), size) {
            arts.forEachIndexed { index, art ->
                val page = art.image.readable()
                val slot = slots[index]
                drawImage(
                    image = page,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(page.width, page.height),
                    dstOffset = slot.topLeft,
                    dstSize = slot.size,
                    filterQuality = FilterQuality.High,
                )
                drawBubbles(page, slot.toRect(), overlays[index])
                page.asAndroidBitmap().recycle()
            }
        }
        return target
    }

    private fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun shareIntent(uri: Uri, label: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, label)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}

private fun ImageBitmap.readable(): ImageBitmap {
    val copy = asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    return requireNotNull(copy) { "The page bitmap could not be copied for sharing" }.asImageBitmap()
}

internal fun sharedPages(
    pageIndex: Int,
    pageCount: Int,
    spread: Boolean,
    coverAlone: Boolean,
    direction: ReadingDirection,
): List<Int> {
    if (!spread) return listOf(pageIndex)
    val firstPage = PageOrder.spreadFirstPage(PageOrder.spreadIndex(pageIndex, coverAlone), coverAlone)
    val secondPage = firstPage + 1
    return listOf(
        PageOrder.leftPage(direction, firstPage, secondPage),
        PageOrder.rightPage(direction, firstPage, secondPage),
    ).filter { it in 0 until pageCount }
}

internal fun joinedSlots(pages: List<IntSize>): List<IntRect> {
    val height = pages.maxOf { it.height }
    val widths = pages.map { (it.width.toFloat() * height / it.height).roundToInt() }
    val lefts = widths.runningFold(0) { left, width -> left + width }
    return widths.mapIndexed { index, width -> IntRect(lefts[index], 0, lefts[index] + width, height) }
}

internal fun panelCrop(view: Rect, page: IntSize): IntRect {
    val left = (view.left * page.width).roundToInt().coerceIn(0, page.width - 1)
    val top = (view.top * page.height).roundToInt().coerceIn(0, page.height - 1)
    val right = (view.right * page.width).roundToInt().coerceIn(left + 1, page.width)
    val bottom = (view.bottom * page.height).roundToInt().coerceIn(top + 1, page.height)
    return IntRect(left, top, right, bottom)
}

internal fun shareFileName(label: String): String {
    val kept = label.map { if (it.isLetterOrDigit() || it in SHARE_FILE_NAME_EXTRA_CHARS) it else '_' }
    return kept.joinToString("").trim().ifBlank { SHARE_FILE_NAME_FALLBACK } + SHARE_FILE_EXTENSION
}

internal fun comicNameOfPath(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')
