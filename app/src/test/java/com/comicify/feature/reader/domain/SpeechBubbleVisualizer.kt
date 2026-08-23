package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PAGES_DIR_ENV = "COMICIFY_PANEL_VIZ_DIR"
private const val ANALYSIS_SIDE = 1000
private const val TARGET_WIDTH_PX = 2160
private const val PREVIEW_WIDTH = 1400
private const val STUCK_SCALE_EPSILON = 0.02f
private const val PAPER_SAMPLES_PER_AXIS = 12
private const val ML_BOXES_FILE = "boxes.json"

class SpeechBubbleVisualizer {

    @Test
    fun renderEnlargedBubbles() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        val pages = ArrayList<String>()
        val mlBoxes = mlBoxes(File(dir!!, ML_BOXES_FILE))
        dir.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted().forEach { file ->
            val full = subsampledToAnalysis(ImageIO.read(file))
            val content = content(full)
            val page = cropped(full, content)
            val pixels = page.getRGB(0, 0, page.width, page.height, null, 0, page.width)
            val pool = maxOf(1, (minOf(page.width, page.height) / ANALYSIS_SIDE.toFloat()).roundToInt())
            val started = System.nanoTime()
            val classes = PixelClasses.classify(pixels, page.width, page.height, pool)
            val boxes = mlBoxes[file.name]?.map { inContent(it, content) }
            val bubbles = boxes?.let { SpeechBubbles.outlined(classes, it) } ?: SpeechBubbles.detect(classes)
            val millis = (System.nanoTime() - started) / 1_000_000
            val panels = PanelDetection.detect(classes)
            val enlarged = BubbleLayout.enlarge(bubbles, BUBBLE_ENLARGE_SCALE, panels)
            val preview = scaled(page, PREVIEW_WIDTH, PREVIEW_WIDTH * page.height / page.width)
            ImageIO.write(withEnlarged(preview, page, enlarged), "png", File(out, file.nameWithoutExtension + "-enlarged.png"))
            ImageIO.write(withOutlines(preview, bubbles), "png", File(out, file.nameWithoutExtension + "-bubbles.png"))
            pages.add(pageMetrics(file.name, page.width, page.height, pool, millis, enlarged))
            println("${file.name}: ${bubbles.size} bubbles in ${millis}ms")
        }
        File(out, "metrics.json").writeText(pages.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun pageMetrics(name: String, width: Int, height: Int, pool: Int, millis: Long, enlarged: List<EnlargedBubble>): String {
        val stuck = enlarged.count { it.scale <= 1f + STUCK_SCALE_EPSILON }
        val constrained = enlarged.count { it.scale < BUBBLE_ENLARGE_SCALE - STUCK_SCALE_EPSILON }
        val bubbles = enlarged.joinToString(", ") { b ->
            val box = b.bubble.box
            val t = b.target
            "{\"scale\": ${f(b.scale)}, \"box\": [${f(box.left)}, ${f(box.top)}, ${f(box.right)}, ${f(box.bottom)}], " +
                "\"target\": [${f(t.left)}, ${f(t.top)}, ${f(t.right)}, ${f(t.bottom)}], \"coversOriginal\": ${covers(b)}}"
        }
        return "  {\"page\": \"$name\", \"width\": $width, \"height\": $height, \"pool\": $pool, \"ms\": $millis, " +
            "\"count\": ${enlarged.size}, \"stuckAtOne\": $stuck, \"constrained\": $constrained, \"bubbles\": [$bubbles]}"
    }

    private fun covers(b: EnlargedBubble): Boolean {
        val box = b.bubble.box
        val t = b.target
        return t.left <= box.left + 1e-3f && t.top <= box.top + 1e-3f && t.right >= box.right - 1e-3f && t.bottom >= box.bottom - 1e-3f
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)

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

    private fun mlBoxes(file: File): Map<String, List<Rect>> {
        if (!file.isFile) return emptyMap()
        val entry = Regex("""\"file\":\s*\"([^\"]+)\".*?\"bubbles\":\s*\[(.*?)\]\s*\}""", RegexOption.DOT_MATCHES_ALL)
        val box = Regex("""\[([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\]""")
        return entry.findAll(file.readText()).associate { page ->
            page.groupValues[1] to box.findAll(page.groupValues[2]).map { b ->
                Rect(b.groupValues[1].toFloat(), b.groupValues[2].toFloat(), b.groupValues[3].toFloat(), b.groupValues[4].toFloat())
            }.toList()
        }
    }

    private fun inContent(box: Rect, content: Rect) = Rect(
        ((box.left - content.left) / content.width).coerceIn(0f, 1f),
        ((box.top - content.top) / content.height).coerceIn(0f, 1f),
        ((box.right - content.left) / content.width).coerceIn(0f, 1f),
        ((box.bottom - content.top) / content.height).coerceIn(0f, 1f),
    )

    private fun content(image: BufferedImage): Rect {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return MarginCrop.detect(pixels, image.width, image.height)
    }

    private fun cropped(image: BufferedImage, content: Rect): BufferedImage {
        if (content == FullPage) return image
        return image.getSubimage(
            (content.left * image.width).roundToInt(),
            (content.top * image.height).roundToInt(),
            (content.width * image.width).roundToInt(),
            (content.height * image.height).roundToInt(),
        )
    }

    private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            drawImage(source.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null)
            dispose()
        }
        return target
    }

    private fun withOutlines(preview: BufferedImage, bubbles: List<SpeechBubble>): BufferedImage {
        val page = copy(preview)
        val g = page.createGraphics()
        g.stroke = BasicStroke(2f)
        bubbles.forEach { bubble ->
            g.color = Color(0, 120, 255)
            g.draw(shape(bubble.outlines, page.width, page.height) { it })
            g.color = Color(255, 0, 255, 160)
            g.drawRect(
                (bubble.box.left * page.width).roundToInt(), (bubble.box.top * page.height).roundToInt(),
                (bubble.box.width * page.width).roundToInt(), (bubble.box.height * page.height).roundToInt(),
            )
        }
        g.dispose()
        return page
    }

    private fun withEnlarged(preview: BufferedImage, source: BufferedImage, enlarged: List<EnlargedBubble>): BufferedImage {
        val page = copy(preview)
        val g = page.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val big = enlarged.filter { it.scale > 1f }
        big.forEach { item ->
            g.color = paperColor(source, item.bubble)
            g.fill(shape(item.bubble.outlines, page.width, page.height) { it })
        }
        big.forEach { item ->
            val shape = shape(item.bubble.outlines, page.width, page.height, item::map)
            val src = item.bubble.box
            val dst = item.target
            g.clip = shape
            g.drawImage(
                source,
                (dst.left * page.width).roundToInt(), (dst.top * page.height).roundToInt(),
                (dst.right * page.width).roundToInt(), (dst.bottom * page.height).roundToInt(),
                (src.left * source.width).roundToInt(), (src.top * source.height).roundToInt(),
                (src.right * source.width).roundToInt(), (src.bottom * source.height).roundToInt(),
                null,
            )
            g.clip = null
            g.color = Color(0, 0, 0, 90)
            g.stroke = BasicStroke(2f)
            g.draw(shape)
        }
        g.dispose()
        return page
    }

    private fun paperColor(source: BufferedImage, bubble: SpeechBubble): Color {
        val samples = bubble.interiorSamples(PAPER_SAMPLES_PER_AXIS).map {
            source.getRGB((it.x * source.width).toInt().coerceIn(0, source.width - 1), (it.y * source.height).toInt().coerceIn(0, source.height - 1))
        }
        val sorted = samples.sortedBy { val r = (it shr 16) and 0xff; val g = (it shr 8) and 0xff; val b = it and 0xff; r * 299 + g * 587 + b * 114 }
        return Color(sorted[sorted.size / 2])
    }

    private fun shape(outlines: List<List<Offset>>, width: Int, height: Int, map: (Offset) -> Offset) = Path2D.Float().apply {
        outlines.forEach { outline ->
            outline.forEachIndexed { i, point ->
                val at = map(point)
                if (i == 0) moveTo(at.x * width, at.y * height) else lineTo(at.x * width, at.y * height)
            }
            closePath()
        }
    }

    private fun copy(image: BufferedImage): BufferedImage {
        val target = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return target
    }
}
