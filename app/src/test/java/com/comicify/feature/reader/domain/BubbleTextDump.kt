package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EVAL_DIR_ENV = "KAPOW_BUBBLE_DUMP_DIR"
private const val BOXES_FILE = "boxes.json"
private const val DUMP_FILE = "bubble-text.json"
private const val ANALYSIS_SIDE = 1000
private const val TARGET_WIDTH_PX = 2160
private const val JPEG_EXTENSIONS = "jpg,jpeg,png"
private const val BOX_FALLBACK_FILL = 0.97f

class BubbleTextDump {

    @Test
    fun dumpBubbleText() {
        val dir = System.getenv(EVAL_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val detected = mlBubbles(File(dir!!, BOXES_FILE))
        val pages = ArrayList<String>()
        File(dir, "pages").listFiles { f -> f.extension.lowercase() in JPEG_EXTENSIONS.split(",") }!!.sorted().forEach { file ->
            val analysis = atAnalysisSize(contentPage(ImageIO.read(file)))
            val pixels = analysis.getRGB(0, 0, analysis.width, analysis.height, null, 0, analysis.width)
            val classes = PixelClasses.classify(pixels, analysis.width, analysis.height, 1)
            val boxes = detected[file.name] ?: error("no detections for ${file.name}")
            val started = System.nanoTime()
            val bubbles = SpeechBubbles.outlined(classes, boxes, extractText = true)
            val withText = System.nanoTime()
            SpeechBubbles.outlined(classes, boxes)
            val withoutText = System.nanoTime()
            pages.add(pageJson(file.name, bubbles, classes, (withText - started) / 1_000_000, (withoutText - withText) / 1_000_000))
            val textless = bubbles.count { it.text.isEmpty() }
            println("${file.name}: ${bubbles.size} bubbles, $textless textless")
        }
        File(dir, DUMP_FILE).writeText(pages.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun pageJson(name: String, bubbles: List<SpeechBubble>, classes: PixelClasses, textMillis: Long, plainMillis: Long): String {
        val entries = bubbles.joinToString(", ") { bubble ->
            "{\"box\": ${rect(bubble.box)}, \"lines\": ${bubble.text.size}, " +
                "\"fallback\": ${isBoxFallback(bubble)}, \"ink\": ${f(inkShare(bubble, classes))}, " +
                "\"text\": ${bubble.text.joinToString(", ", "[", "]", transform = ::rect)}}"
        }
        return "  {\"file\": \"$name\", \"msWithText\": $textMillis, \"msWithoutText\": $plainMillis, \"bubbles\": [$entries]}"
    }

    private fun inkShare(bubble: SpeechBubble, classes: PixelClasses): Float {
        val left = (bubble.box.left * classes.width).roundToInt().coerceIn(0, classes.width - 1)
        val top = (bubble.box.top * classes.height).roundToInt().coerceIn(0, classes.height - 1)
        val right = (bubble.box.right * classes.width).roundToInt().coerceIn(left + 1, classes.width)
        val bottom = (bubble.box.bottom * classes.height).roundToInt().coerceIn(top + 1, classes.height)
        var ink = 0
        for (y in top until bottom) for (x in left until right) if (classes.ink[y * classes.width + x]) ink++
        return ink.toFloat() / ((right - left) * (bottom - top))
    }

    private fun isBoxFallback(bubble: SpeechBubble): Boolean {
        val box = bubble.box.width * bubble.box.height
        if (box <= 0f) return true
        return bubble.outlines.sumOf { polygonArea(it).toDouble() }.toFloat() >= box * BOX_FALLBACK_FILL
    }

    private fun polygonArea(outline: List<Offset>): Float {
        if (outline.size < 3) return 0f
        var sum = 0f
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[(i + 1) % outline.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    private fun rect(r: Rect) = "[${f(r.left)}, ${f(r.top)}, ${f(r.right)}, ${f(r.bottom)}]"

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)

    private fun mlBubbles(file: File): Map<String, List<Rect>> {
        val entry = Regex("""\"file\":\s*\"([^\"]+)\".*?\"bubbles\":\s*\[(.*?)\]\s*\}""", RegexOption.DOT_MATCHES_ALL)
        val box = Regex("""\[([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\]""")
        return entry.findAll(file.readText()).associate { page ->
            unescaped(page.groupValues[1]) to box.findAll(page.groupValues[2]).map { b ->
                Rect(b.groupValues[1].toFloat(), b.groupValues[2].toFloat(), b.groupValues[3].toFloat(), b.groupValues[4].toFloat())
            }.toList()
        }
    }

    private fun unescaped(name: String) = Regex("""\\u([0-9a-fA-F]{4})""").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun contentPage(source: BufferedImage): BufferedImage {
        val subsampled = subsampledToAnalysis(source)
        val pixels = subsampled.getRGB(0, 0, subsampled.width, subsampled.height, null, 0, subsampled.width)
        val content = MarginCrop.detect(pixels, subsampled.width, subsampled.height)
        if (content == FullPage) return subsampled
        return subsampled.getSubimage(
            (content.left * subsampled.width).roundToInt(),
            (content.top * subsampled.height).roundToInt(),
            (content.width * subsampled.width).roundToInt(),
            (content.height * subsampled.height).roundToInt(),
        )
    }

    private fun subsampledToAnalysis(source: BufferedImage): BufferedImage {
        val sample = inSampleSize(source.width, TARGET_WIDTH_PX)
        return if (sample <= 1) source else scaled(source, source.width / sample, source.height / sample)
    }

    private fun inSampleSize(sourceWidth: Int, targetWidth: Int): Int {
        if (targetWidth <= 0 || sourceWidth <= targetWidth) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    private fun atAnalysisSize(image: BufferedImage): BufferedImage {
        val scale = ANALYSIS_SIDE / minOf(image.width, image.height).toFloat()
        if (scale >= 1f) return image
        return scaled(image, (image.width * scale).roundToInt(), (image.height * scale).roundToInt())
    }

    private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            drawImage(source.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null)
            dispose()
        }
        return target
    }
}
