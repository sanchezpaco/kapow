package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val PAGES_DIR_ENV = "COMICIFY_PANEL_VIZ_DIR"
private const val ANALYSIS_WIDTH = 1000

class PanelDetectionVisualizer {

    @Test
    fun renderDetectedPanels() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        dir!!.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }!!.sorted().forEach { file ->
            val source = ImageIO.read(file)
            val pixels = source.getRGB(0, 0, source.width, source.height, null, 0, source.width)
            val pool = maxOf(1, (source.width / ANALYSIS_WIDTH.toFloat()).roundToInt())
            val started = System.nanoTime()
            val panels = PanelDetection.detect(pixels, source.width, source.height, pool)
            val millis = (System.nanoTime() - started) / 1_000_000
            val page = scaled(source, source.width / pool, source.height / pool)
            draw(page, panels)
            ImageIO.write(page, "png", File(out, file.nameWithoutExtension + ".png"))
            println("${file.name}: ${panels.size} panels in ${millis}ms")
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

    private fun draw(page: BufferedImage, panels: List<Rect>) {
        val g = page.createGraphics()
        g.stroke = BasicStroke(3f)
        g.font = Font("SansSerif", Font.BOLD, 28)
        panels.forEachIndexed { index, rect ->
            val x = (rect.left * page.width).roundToInt()
            val y = (rect.top * page.height).roundToInt()
            val w = (rect.width * page.width).roundToInt()
            val h = (rect.height * page.height).roundToInt()
            g.color = Color(0, 255, 0, 200)
            g.drawRect(x + 1, y + 1, w - 3, h - 3)
            g.color = Color(255, 0, 255)
            g.drawString((index + 1).toString(), x + 8, y + 32)
        }
        g.dispose()
    }
}
