package com.comicify.feature.reader.data

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

private const val PAGES_DIR = "mlspike"
private const val WARMUP_RUNS = 2

@RunWith(AndroidJUnit4::class)
class MlDetectorBenchmark {

    @Test
    fun benchmarkAgainstHeuristic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, PAGES_DIR)
        assumeTrue(dir.isDirectory)
        val detector = MlPanelDetector(copiedAsset(context, "models/panels.onnx"), copiedAsset(context, "models/bubbles.onnx"))
        val pages = dir.listFiles { f -> f.extension == "jpg" }!!.sorted()
        repeat(WARMUP_RUNS) { detector.detect(BitmapFactory.decodeFile(pages.first().path)); detector.bubbleBoxes(BitmapFactory.decodeFile(pages.first().path)) }
        val rows = pages.map { file ->
            val bitmap = BitmapFactory.decodeFile(file.path)
            val panelsStart = System.nanoTime()
            val panels = detector.detect(bitmap)
            val panelsMs = (System.nanoTime() - panelsStart) / 1_000_000
            val bubblesStart = System.nanoTime()
            val bubbles = detector.bubbleBoxes(bitmap)
            val bubblesMs = (System.nanoTime() - bubblesStart) / 1_000_000
            val heuristicStart = System.nanoTime()
            val heuristicPanels = PanelDetector.detect(bitmap)
            val heuristicPanelsMs = (System.nanoTime() - heuristicStart) / 1_000_000
            val heuristicBubblesStart = System.nanoTime()
            PanelDetector.bubbles(bitmap)
            val heuristicBubblesMs = (System.nanoTime() - heuristicBubblesStart) / 1_000_000
            "{\"file\": \"${file.name}\", \"width\": ${bitmap.width}, \"height\": ${bitmap.height}, " +
                "\"panels_ms\": $panelsMs, \"bubbles_ms\": $bubblesMs, \"heuristic_panels_ms\": $heuristicPanelsMs, \"heuristic_bubbles_ms\": $heuristicBubblesMs, " +
                "\"panels\": ${rects(panels)}, \"bubbles\": ${rects(bubbles)}, \"heuristic_panels\": ${rects(heuristicPanels)}}"
        }
        File(dir, "device.json").writeText(rows.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun copiedAsset(context: android.content.Context, name: String): String {
        val target = File(context.cacheDir, name.substringAfterLast('/'))
        context.assets.open(name).use { input -> target.outputStream().use { input.copyTo(it) } }
        return target.path
    }

    private fun rects(rects: List<Rect>) = rects.joinToString(", ", "[", "]") { r ->
        "[${f(r.left)}, ${f(r.top)}, ${f(r.right)}, ${f(r.bottom)}]"
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)
}
