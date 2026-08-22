package com.comicify.feature.reader.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val INPUT_SIDE = 640
private const val LETTERBOX_GRAY = 114
private const val PANEL_CLASS = 0f
private const val PANEL_CONFIDENCE = 0.35f
private const val BUBBLE_CONFIDENCE = 0.25f
private const val BUBBLE_NMS_IOU = 0.7f
private const val BUBBLE_ROW_BOX = 4
private const val BUBBLE_CLASS_TEXT_BUBBLE = 4
private const val BUBBLE_CLASS_TEXT_FREE = 5

class MlPanelDetector(panelModelPath: String, bubbleModelPath: String) {
    private val environment = OrtEnvironment.getEnvironment()
    private val panels = environment.createSession(panelModelPath, OrtSession.SessionOptions())
    private val bubbles = environment.createSession(bubbleModelPath, OrtSession.SessionOptions())

    fun detect(bitmap: Bitmap): List<Rect> {
        val frame = Letterbox(bitmap)
        val output = run(panels, frame)
        val rows = output.size / 6
        return (0 until rows)
            .filter { output[it * 6 + 4] >= PANEL_CONFIDENCE && output[it * 6 + 5] == PANEL_CLASS }
            .map { frame.toPage(output[it * 6], output[it * 6 + 1], output[it * 6 + 2], output[it * 6 + 3]) }
    }

    fun bubbleBoxes(bitmap: Bitmap): List<Rect> {
        val frame = Letterbox(bitmap)
        val output = run(bubbles, frame)
        val anchors = output.size / 6
        val candidates = (0 until anchors)
            .filter { output[BUBBLE_CLASS_TEXT_BUBBLE * anchors + it] >= BUBBLE_CONFIDENCE && output[BUBBLE_CLASS_TEXT_BUBBLE * anchors + it] >= output[BUBBLE_CLASS_TEXT_FREE * anchors + it] }
            .map { i ->
                val cx = output[i]
                val cy = output[anchors + i]
                val w = output[2 * anchors + i]
                val h = output[3 * anchors + i]
                Scored(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), output[BUBBLE_CLASS_TEXT_BUBBLE * anchors + i])
            }
        return suppressed(candidates).map { frame.toPage(it.left, it.top, it.right, it.bottom) }
    }

    private fun run(session: OrtSession, frame: Letterbox): FloatArray {
        val shape = longArrayOf(1, 3, INPUT_SIDE.toLong(), INPUT_SIDE.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(frame.input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val value = result[0].value
                return flatten(value)
            }
        }
    }

    private fun flatten(value: Any): FloatArray {
        val batch = (value as Array<*>)[0] as Array<*>
        val rows = batch.map { it as FloatArray }
        val out = FloatArray(rows.sumOf { it.size })
        var at = 0
        rows.forEach { row -> row.copyInto(out, at); at += row.size }
        return out
    }

    private fun suppressed(candidates: List<Scored>): List<RectF> {
        val kept = ArrayList<RectF>()
        candidates.sortedByDescending { it.score }.forEach { c ->
            if (kept.none { iou(it, c.box) >= BUBBLE_NMS_IOU }) kept.add(c.box)
        }
        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val w = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val h = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = w * h
        return inter / (a.width() * a.height() + b.width() * b.height() - inter)
    }

    private class Scored(val box: RectF, val score: Float)

    private class Letterbox(source: Bitmap) {
        private val scale = INPUT_SIDE / max(source.width, source.height).toFloat()
        private val offsetX = (INPUT_SIDE - (source.width * scale).roundToInt()) / 2
        private val offsetY = (INPUT_SIDE - (source.height * scale).roundToInt()) / 2
        private val pageWidth = source.width
        private val pageHeight = source.height
        val input: FloatArray = chw(padded(source))

        private fun padded(source: Bitmap): Bitmap {
            val canvas = Bitmap.createBitmap(INPUT_SIDE, INPUT_SIDE, Bitmap.Config.ARGB_8888)
            Canvas(canvas).apply {
                drawColor(Color.rgb(LETTERBOX_GRAY, LETTERBOX_GRAY, LETTERBOX_GRAY))
                val w = (pageWidth * scale).roundToInt()
                val h = (pageHeight * scale).roundToInt()
                drawBitmap(source, null, RectF(offsetX.toFloat(), offsetY.toFloat(), (offsetX + w).toFloat(), (offsetY + h).toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return canvas
        }

        private fun chw(bitmap: Bitmap): FloatArray {
            val pixels = IntArray(INPUT_SIDE * INPUT_SIDE)
            bitmap.getPixels(pixels, 0, INPUT_SIDE, 0, 0, INPUT_SIDE, INPUT_SIDE)
            val plane = INPUT_SIDE * INPUT_SIDE
            val out = FloatArray(3 * plane)
            pixels.forEachIndexed { i, p ->
                out[i] = ((p shr 16) and 0xff) / 255f
                out[plane + i] = ((p shr 8) and 0xff) / 255f
                out[2 * plane + i] = (p and 0xff) / 255f
            }
            return out
        }

        fun toPage(left: Float, top: Float, right: Float, bottom: Float) = Rect(
            ((left - offsetX) / scale / pageWidth).coerceIn(0f, 1f),
            ((top - offsetY) / scale / pageHeight).coerceIn(0f, 1f),
            ((right - offsetX) / scale / pageWidth).coerceIn(0f, 1f),
            ((bottom - offsetY) / scale / pageHeight).coerceIn(0f, 1f),
        )
    }
}
