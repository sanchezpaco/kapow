package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PAGES_DIR_ENV = "COMICIFY_RECTS_DUMP_DIR"
private const val ANALYSIS_SIDE = 1000

class HeuristicRectsDump {

    @Test
    fun dumpRects() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val pages = dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted().map { file ->
            val source = ImageIO.read(file)
            val pixels = source.getRGB(0, 0, source.width, source.height, null, 0, source.width)
            val pool = maxOf(1, (minOf(source.width, source.height) / ANALYSIS_SIDE.toFloat()).roundToInt())
            val panelsStart = System.nanoTime()
            val panels = PanelDetection.detect(pixels, source.width, source.height, pool)
            val panelsMs = (System.nanoTime() - panelsStart) / 1_000_000
            val bubblesStart = System.nanoTime()
            val bubbles = SpeechBubbles.detect(pixels, source.width, source.height, pool).map { it.box }
            val bubblesMs = (System.nanoTime() - bubblesStart) / 1_000_000
            "{\"file\": \"${file.name}\", \"width\": ${source.width}, \"height\": ${source.height}, " +
                "\"panels_ms\": $panelsMs, \"bubbles_ms\": $bubblesMs, " +
                "\"panels\": ${rects(panels)}, \"bubbles\": ${rects(bubbles)}}"
        }
        File(dir, "heuristic.json").writeText(pages.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun rects(rects: List<Rect>) = rects.joinToString(", ", "[", "]") { r ->
        "[${f(r.left)}, ${f(r.top)}, ${f(r.right)}, ${f(r.bottom)}]"
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)
}
