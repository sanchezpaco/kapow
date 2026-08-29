package com.comicify.feature.reader.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.comicify.BuildConfig
import com.comicify.R
import com.comicify.core.window.ReadingPosture
import com.comicify.feature.reader.data.DETECTIONS_VERSION
import com.comicify.feature.reader.data.PageLoader
import com.comicify.feature.reader.domain.PageDetectionCodec
import com.comicify.feature.reader.ui.BubbleOverlay.drawBubbles
import java.io.File
import org.json.JSONObject

private const val REPORTS_DIR = "reports"
private const val PAGE_FILE = "page.jpg"
private const val DATA_FILE = "report.json"
private const val PAGE_JPEG_QUALITY = 85
private const val REPORT_EMAIL = "sanchezpacodev@gmail.com"

data class GlitchReportRequest(
    val comicUri: Uri,
    val pageIndex: Int,
    val pageCount: Int,
    val guided: Boolean,
    val bubbleScale: Float?,
    val posture: ReadingPosture,
)

class GlitchReport(private val context: Context) {

    suspend fun compose(loader: PageLoader, request: GlitchReportRequest): Intent {
        val dir = File(context.cacheDir, REPORTS_DIR).apply { deleteRecursively(); mkdirs() }
        val page = File(dir, PAGE_FILE).also { writePage(it, loader, request) }
        val data = File(dir, DATA_FILE).also { it.writeText(reportJson(loader, request).toString(2)) }
        return shareIntent(listOf(page, data).map(::contentUri), request)
    }

    private suspend fun writePage(file: File, loader: PageLoader, request: GlitchReportRequest) {
        val art = loader.load(request.pageIndex).analysis
        val overlay = request.bubbleScale?.let { loader.overlay(request.pageIndex, it) }.orEmpty()
        val composed = Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888)
        val size = Size(art.width.toFloat(), art.height.toFloat())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(composed.asImageBitmap()), size) {
            drawImage(art)
            drawBubbles(art, Rect(0f, 0f, size.width, size.height), overlay)
        }
        file.outputStream().use { composed.compress(Bitmap.CompressFormat.JPEG, PAGE_JPEG_QUALITY, it) }
    }

    private suspend fun reportJson(loader: PageLoader, request: GlitchReportRequest): JSONObject = JSONObject()
        .put("file", displayName(request.comicUri))
        .put("page", request.pageIndex)
        .put("pageCount", request.pageCount)
        .put("guided", request.guided)
        .put("bubblesEnlarged", request.bubbleScale != null)
        .put("bubbleScale", request.bubbleScale?.toDouble() ?: JSONObject.NULL)
        .put("posture", request.posture.name)
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        .put("androidSdk", Build.VERSION.SDK_INT)
        .put("appVersion", BuildConfig.VERSION_NAME)
        .put("appVersionCode", BuildConfig.VERSION_CODE)
        .put("build", BuildConfig.BUILD_LABEL)
        .put("detectionsVersion", DETECTIONS_VERSION)
        .put("panels", PageDetectionCodec.encodePanels(loader.panels(request.pageIndex)))
        .put("bubbles", PageDetectionCodec.encodeBubbles(loader.bubbles(request.pageIndex)))

    private fun displayName(uri: Uri): String {
        if (DocumentsContract.isTreeUri(uri) && !DocumentsContract.isDocumentUri(context, uri)) {
            return DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
        }
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun shareIntent(attachments: List<Uri>, request: GlitchReportRequest): Intent {
        val summary = context.getString(
            R.string.glitch_report_summary,
            request.pageIndex + 1,
            request.pageCount,
            Build.MODEL,
            BuildConfig.VERSION_NAME,
        )
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.glitch_report_subject))
            putExtra(Intent.EXTRA_TEXT, summary)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachments))
            clipData = ClipData.newRawUri(null, attachments.first()).apply { attachments.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.glitch_report_chooser))
    }
}
