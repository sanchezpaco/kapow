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
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PAGES_DIR_ENV = "COMICIFY_PANEL_VIZ_DIR"
private const val ANALYSIS_WIDTH = 1000
private const val PREVIEW_WIDTH = 1400

class SpeechBubbleVisualizer {

    @Test
    fun renderEnlargedBubbles() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted().forEach { file ->
            val source = ImageIO.read(file)
            val pixels = source.getRGB(0, 0, source.width, source.height, null, 0, source.width)
            val pool = maxOf(1, (source.width / ANALYSIS_WIDTH.toFloat()).roundToInt())
            val started = System.nanoTime()
            val bubbles = SpeechBubbles.detect(pixels, source.width, source.height, pool)
            val millis = (System.nanoTime() - started) / 1_000_000
            val enlarged = BubbleLayout.enlarge(bubbles, BUBBLE_ENLARGE_SCALE)
            val preview = scaled(source, PREVIEW_WIDTH, PREVIEW_WIDTH * source.height / source.width)
            ImageIO.write(withEnlarged(preview, source, enlarged), "png", File(out, file.nameWithoutExtension + "-enlarged.png"))
            ImageIO.write(withOutlines(preview, bubbles), "png", File(out, file.nameWithoutExtension + "-bubbles.png"))
            println("${file.name}: ${bubbles.size} bubbles in ${millis}ms")
        }
    }

    private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
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
