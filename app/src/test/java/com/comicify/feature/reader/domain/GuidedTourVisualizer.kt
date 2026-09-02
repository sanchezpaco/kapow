package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.comicify.domain.model.ReadingDirection
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val EVAL_DIR_ENV = "KAPOW_GUIDED_EVAL_DIR"
private const val DIRECTION_ENV = "KAPOW_GUIDED_EVAL_DIRECTION"
private const val BOXES_FILE = "boxes.json"
private const val ANALYSIS_SIDE = 1000
private const val TARGET_WIDTH_PX = 2160
private const val ORDERING_GRID = 1000
private const val PANEL_PADDING = 0.02f
private const val SPOTLIGHT_DIM = 0.72f
private const val ANNOTATED_WIDTH = 1400
private val PHONE_VIEWPORT = Size(904f, 2316f)
private val TABLET_VIEWPORT = Size(1968f, 2184f)
private const val PHONE_CROP_WIDTH = 720
private const val TABLET_CROP_WIDTH = 1000
private const val STOP_STROKE = 6f
private const val DETECTION_STROKE = 2f
private const val BADGE_SIZE = 44
private const val JPEG_EXTENSIONS = "jpg,jpeg,png"

private class Detections(val panels: List<Rect>, val bubbles: List<Rect>)

class GuidedTourVisualizer {

    @Test
    fun renderGuidedStops() {
        val dir = System.getenv(EVAL_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val direction = if (System.getenv(DIRECTION_ENV) == "rtl") ReadingDirection.RightToLeft else ReadingDirection.LeftToRight
        val detections = detections(File(dir!!, BOXES_FILE))
        val stopsDir = File(dir, "stops").apply { mkdirs() }
        val annotatedDir = File(dir, "annotated").apply { mkdirs() }
        val cropsDir = File(dir, "crops").apply { mkdirs() }
        File(dir, "pages").listFiles { f -> f.extension.lowercase() in JPEG_EXTENSIONS.split(",") }!!.sorted().forEach { file ->
            val detected = detections[file.name] ?: error("no detections for ${file.name}")
            val page = contentPage(ImageIO.read(file))
            val analysis = atAnalysisSize(page)
            val pixels = analysis.getRGB(0, 0, analysis.width, analysis.height, null, 0, analysis.width)
            val classes = PixelClasses.classify(pixels, analysis.width, analysis.height, 1)
            val panels = panels(detected.panels, classes)
            val gate = GuidedTour.needsBubbles(panels)
            val bubbles = if (gate) SpeechBubbles.outlined(classes, detected.bubbles).map { it.box } else emptyList()
            val stops = GuidedTour.stops(panels, bubbles, direction)
            val name = file.nameWithoutExtension
            File(stopsDir, "$name.json").writeText(stopsJson(file.name, direction, page, gate, panels, bubbles, stops))
            ImageIO.write(annotated(page, panels, bubbles, stops), "jpg", File(annotatedDir, "$name.jpg"))
            stops.forEachIndexed { index, stop ->
                val label = "$name-${String.format(Locale.ROOT, "%02d", index + 1)}"
                ImageIO.write(viewport(page, stop, PHONE_VIEWPORT, PHONE_CROP_WIDTH), "jpg", File(cropsDir, "$label-phone.jpg"))
                ImageIO.write(viewport(page, stop, TABLET_VIEWPORT, TABLET_CROP_WIDTH), "jpg", File(cropsDir, "$label-tablet.jpg"))
            }
            println("${file.name}: ${panels.size} panels, ${bubbles.size} bubbles (gate=$gate) -> ${stops.size} stops")
        }
    }

    private fun panels(detected: List<Rect>, classes: PixelClasses): List<Rect> {
        val heuristic = PanelDetection.detect(classes).map(::toBox)
        return PanelLayout.readingOrder(PanelLayout.complemented(detected.map(::toBox), heuristic)).map { it.toRect(ORDERING_GRID, ORDERING_GRID) }
    }

    private fun toBox(rect: Rect) = Box(
        (rect.left * ORDERING_GRID).roundToInt(),
        (rect.top * ORDERING_GRID).roundToInt(),
        (rect.right * ORDERING_GRID).roundToInt(),
        (rect.bottom * ORDERING_GRID).roundToInt(),
    )

    private fun stopsJson(file: String, direction: ReadingDirection, page: BufferedImage, gate: Boolean, panels: List<Rect>, bubbles: List<Rect>, stops: List<Rect>): String {
        val dir = if (direction == ReadingDirection.RightToLeft) "rtl" else "ltr"
        return "{\"file\": \"$file\", \"direction\": \"$dir\", \"width\": ${page.width}, \"height\": ${page.height}, \"needsBubbles\": $gate, " +
            "\"panels\": ${rects(panels)}, \"bubbles\": ${rects(bubbles)}, \"stops\": ${rects(stops)}}\n"
    }

    private fun rects(rects: List<Rect>) = rects.joinToString(", ", "[", "]") { rect ->
        listOf(rect.left, rect.top, rect.right, rect.bottom).joinToString(", ", "[", "]") { String.format(Locale.ROOT, "%.4f", it) }
    }

    private fun annotated(page: BufferedImage, panels: List<Rect>, bubbles: List<Rect>, stops: List<Rect>): BufferedImage {
        val image = scaled(page, ANNOTATED_WIDTH, ANNOTATED_WIDTH * page.height / page.width)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.stroke = BasicStroke(DETECTION_STROKE)
        g.color = Color(0, 200, 0, 160)
        panels.forEach { g.draw(it, image) }
        g.color = Color(0, 120, 255, 160)
        bubbles.forEach { g.draw(it, image) }
        g.stroke = BasicStroke(STOP_STROKE)
        g.font = Font("SansSerif", Font.BOLD, 30)
        stops.forEachIndexed { index, stop ->
            val color = stopColor(index)
            g.color = color
            val inset = (STOP_STROKE / 2).roundToInt()
            g.drawRect(
                (stop.left * image.width).roundToInt() + inset,
                (stop.top * image.height).roundToInt() + inset,
                (stop.width * image.width).roundToInt() - 2 * inset,
                (stop.height * image.height).roundToInt() - 2 * inset,
            )
            val x = (stop.left * image.width).roundToInt() + inset
            val y = (stop.top * image.height).roundToInt() + inset
            g.fillRect(x, y, BADGE_SIZE, BADGE_SIZE)
            g.color = Color.WHITE
            g.drawString((index + 1).toString(), x + 10, y + 32)
        }
        g.dispose()
        return image
    }

    private fun java.awt.Graphics2D.draw(rect: Rect, image: BufferedImage) = drawRect(
        (rect.left * image.width).roundToInt(),
        (rect.top * image.height).roundToInt(),
        (rect.width * image.width).roundToInt(),
        (rect.height * image.height).roundToInt(),
    )

    private fun stopColor(index: Int): Color = Color.getHSBColor((index * 0.17f) % 1f, 0.9f, 0.85f)

    private fun viewport(page: BufferedImage, stop: Rect, viewportSize: Size, width: Int): BufferedImage {
        val height = (width * viewportSize.height / viewportSize.width).roundToInt()
        val canvas = Size(width.toFloat(), height.toFloat())
        val view = GuidedFocus.frame(stop, PANEL_PADDING)
        val drawn = GuidedFocus.fit(view, Size(page.width.toFloat(), page.height.toFloat()), canvas)
        val pixelsPerUnitX = drawn.width / view.width
        val pixelsPerUnitY = drawn.height / view.height
        val pageLeft = drawn.left - view.left * pixelsPerUnitX
        val pageTop = drawn.top - view.top * pixelsPerUnitY
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.color = Color.BLACK
        g.fillRect(0, 0, width, height)
        g.drawImage(page, pageLeft.roundToInt(), pageTop.roundToInt(), pixelsPerUnitX.roundToInt(), pixelsPerUnitY.roundToInt(), null)
        g.color = Color(0, 0, 0, (SPOTLIGHT_DIM * 255).roundToInt())
        val left = drawn.left.roundToInt()
        val top = drawn.top.roundToInt()
        val right = drawn.right.roundToInt()
        val bottom = drawn.bottom.roundToInt()
        g.fillRect(0, 0, width, top)
        g.fillRect(0, bottom, width, height - bottom)
        g.fillRect(0, top, left, bottom - top)
        g.fillRect(right, top, width - right, bottom - top)
        g.dispose()
        return image
    }

    private fun detections(file: File): Map<String, Detections> {
        val entry = Regex(""""file": "([^"]+)".*"panels": \[(.*?)\], "bubbles": \[(.*?)\]\}""")
        val box = Regex("""\[([\d.]+), ([\d.]+), ([\d.]+), ([\d.]+)\]""")
        fun boxes(text: String) = box.findAll(text).map { b ->
            Rect(b.groupValues[1].toFloat(), b.groupValues[2].toFloat(), b.groupValues[3].toFloat(), b.groupValues[4].toFloat())
        }.toList()
        return file.readLines().mapNotNull { entry.find(it) }
            .associate { it.groupValues[1] to Detections(boxes(it.groupValues[2]), boxes(it.groupValues[3])) }
    }

    private fun contentPage(source: BufferedImage): BufferedImage {
        val decoded = subsampled(source)
        val pixels = decoded.getRGB(0, 0, decoded.width, decoded.height, null, 0, decoded.width)
        val content = MarginCrop.detect(pixels, decoded.width, decoded.height)
        if (content == FullPage) return decoded
        return decoded.getSubimage(
            (content.left * decoded.width).roundToInt(),
            (content.top * decoded.height).roundToInt(),
            (content.width * decoded.width).roundToInt(),
            (content.height * decoded.height).roundToInt(),
        )
    }

    private fun subsampled(source: BufferedImage): BufferedImage {
        var sample = 1
        while (source.width / (sample * 2) >= TARGET_WIDTH_PX) sample *= 2
        return if (sample <= 1) source else scaled(source, source.width / sample, source.height / sample)
    }

    private fun atAnalysisSize(image: BufferedImage): BufferedImage {
        val scale = ANALYSIS_SIDE / minOf(image.width, image.height).toFloat()
        if (scale >= 1f) return image
        val target = BufferedImage((image.width * scale).roundToInt(), (image.height * scale).roundToInt(), BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(image, 0, 0, target.width, target.height, null)
            dispose()
        }
        return target
    }

    private fun scaled(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val target = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        target.createGraphics().apply {
            drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
            dispose()
        }
        return target
    }
}
