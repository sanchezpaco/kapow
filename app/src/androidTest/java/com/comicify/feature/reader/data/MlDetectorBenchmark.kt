package com.comicify.feature.reader.data

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicify.feature.reader.domain.PanelDetection
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PAGES_DIR = "mlspike"
private const val WARMUP_RUNS = 2
private const val ANALYSIS_SIDE = 1000

@RunWith(AndroidJUnit4::class)
class MlDetectorBenchmark {

    @Test
    fun benchmarkAgainstHeuristic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, PAGES_DIR)
        assumeTrue(dir.isDirectory)
        val detector = PanelDetector(MlPanelDetector.shared(context))
        val pages = dir.listFiles { f -> f.extension == "jpg" }!!.sorted()
        repeat(WARMUP_RUNS) { detector.detect(BitmapFactory.decodeFile(pages.first().path)) }
        val rows = pages.map { file ->
            val bitmap = BitmapFactory.decodeFile(file.path)
            val mlStart = System.nanoTime()
            val panels = detector.detect(bitmap)
            val mlMs = (System.nanoTime() - mlStart) / 1_000_000
            val heuristicStart = System.nanoTime()
            val heuristicPanels = heuristicPanels(bitmap)
            val heuristicMs = (System.nanoTime() - heuristicStart) / 1_000_000
            "{\"file\": \"${file.name}\", \"width\": ${bitmap.width}, \"height\": ${bitmap.height}, " +
                "\"panels_ms\": $mlMs, \"heuristic_panels_ms\": $heuristicMs, " +
                "\"panels\": ${rects(panels)}, \"heuristic_panels\": ${rects(heuristicPanels)}}"
        }
        File(dir, "device.json").writeText(rows.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun heuristicPanels(bitmap: android.graphics.Bitmap): List<Rect> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val pool = max(1, (min(bitmap.width, bitmap.height) / ANALYSIS_SIDE.toFloat()).roundToInt())
        return PanelDetection.detect(pixels, bitmap.width, bitmap.height, pool)
    }

    private fun rects(rects: List<Rect>) = rects.joinToString(", ", "[", "]") { r ->
        "[${f(r.left)}, ${f(r.top)}, ${f(r.right)}, ${f(r.bottom)}]"
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)
}
