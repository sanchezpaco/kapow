package com.comicify.feature.reader.domain

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Image
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PAGES_DIR_ENV = "COMICIFY_PANEL_VIZ_DIR"
private const val ANALYSIS_WIDTH = 1000
private const val TARGET_WIDTH_PX = 2160
private const val PREVIEW_WIDTH = 1400
private const val STUCK_SCALE_EPSILON = 0.02f

class SpeechBubbleVisualizer {

    @Test
    fun renderEnlargedBubbles() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        val pages = ArrayList<String>()
        dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted().forEach { file ->
            val page = contentCropped(subsampledToAnalysis(ImageIO.read(file)))
            val pixels = page.getRGB(0, 0, page.width, page.height, null, 0, page.width)
            val pool = maxOf(1, (page.width / ANALYSIS_WIDTH.toFloat()).roundToInt())
            val started = System.nanoTime()
            val bubbles = SpeechBubbles.detect(pixels, page.width, page.height, pool)
            val millis = (System.nanoTime() - started) / 1_000_000
            val enlarged = BubbleLayout.enlarge(bubbles, BUBBLE_ENLARGE_SCALE)
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

    private fun contentCropped(image: BufferedImage): BufferedImage {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val content = MarginCrop.detect(pixels, image.width, image.height)
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
            g.drawPolygon(polygon(bubble.outline.map { it }, page.width, page.height))
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
        enlarged.filter { it.scale > 1f }.forEach { item ->
            val shape = polygon(item.bubble.outline.map(item::map), page.width, page.height)
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
            g.drawPolygon(shape)
        }
        g.dispose()
        return page
    }

    private fun polygon(points: List<androidx.compose.ui.geometry.Offset>, width: Int, height: Int) = Polygon(
        IntArray(points.size) { (points[it].x * width).roundToInt() },
        IntArray(points.size) { (points[it].y * height).roundToInt() },
        points.size,
    )

    private fun copy(image: BufferedImage): BufferedImage {
        val target = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return target
    }
}
