package com.comicify.feature.reader.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Rect
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

private const val MODEL_ASSET = "models/panels.onnx"
private const val INPUT_SIDE = 640
private const val LETTERBOX_GRAY = 114
private const val ROW_SIZE = 6
private const val PANEL_CLASS = 0f
private const val PANEL_CONFIDENCE = 0.35f

class MlPanelDetector(modelPath: String) {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelPath, OrtSession.SessionOptions())

    fun detect(bitmap: Bitmap): List<Rect> {
        val frame = Letterbox(bitmap)
        val output = run(frame)
        return (0 until output.size / ROW_SIZE)
            .map { it * ROW_SIZE }
            .filter { output[it + 4] >= PANEL_CONFIDENCE && output[it + 5] == PANEL_CLASS }
            .map { frame.toPage(output[it], output[it + 1], output[it + 2], output[it + 3]) }
    }

    private fun run(frame: Letterbox): FloatArray {
        val shape = longArrayOf(1, 3, INPUT_SIDE.toLong(), INPUT_SIDE.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(frame.input), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                return flatten(result[0].value)
            }
        }
    }

    private fun flatten(value: Any): FloatArray {
        val rows = ((value as Array<*>)[0] as Array<*>).map { it as FloatArray }
        val out = FloatArray(rows.sumOf { it.size })
        var at = 0
        rows.forEach { row -> row.copyInto(out, at); at += row.size }
        return out
    }

    private class Letterbox(source: Bitmap) {
        private val scale = INPUT_SIDE / max(source.width, source.height).toFloat()
        private val pageWidth = source.width
        private val pageHeight = source.height
        private val offsetX = (INPUT_SIDE - (pageWidth * scale).roundToInt()) / 2
        private val offsetY = (INPUT_SIDE - (pageHeight * scale).roundToInt()) / 2
        val input: FloatArray = chw(padded(source))

        private fun padded(source: Bitmap): Bitmap {
            val canvas = Bitmap.createBitmap(INPUT_SIDE, INPUT_SIDE, Bitmap.Config.ARGB_8888)
            Canvas(canvas).apply {
                drawColor(Color.rgb(LETTERBOX_GRAY, LETTERBOX_GRAY, LETTERBOX_GRAY))
                val w = (pageWidth * scale).roundToInt()
                val h = (pageHeight * scale).roundToInt()
                val target = RectF(offsetX.toFloat(), offsetY.toFloat(), (offsetX + w).toFloat(), (offsetY + h).toFloat())
                drawBitmap(source, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            return canvas
        }

        private fun chw(bitmap: Bitmap): FloatArray {
            val plane = INPUT_SIDE * INPUT_SIDE
            val pixels = IntArray(plane)
            bitmap.getPixels(pixels, 0, INPUT_SIDE, 0, 0, INPUT_SIDE, INPUT_SIDE)
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

    companion object {
        @Volatile
        private var shared: MlPanelDetector? = null

        fun shared(context: Context): MlPanelDetector = shared ?: synchronized(this) {
            shared ?: MlPanelDetector(installedModel(context).path).also { shared = it }
        }

        private fun installedModel(context: Context): File {
            val target = File(context.filesDir, MODEL_ASSET)
            context.assets.openFd(MODEL_ASSET).use { asset ->
                if (target.length() == asset.length) return target
            }
            target.parentFile!!.mkdirs()
            context.assets.open(MODEL_ASSET).use { input -> target.outputStream().use { input.copyTo(it) } }
            return target
        }
    }
}
