package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Area
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
private const val SILHOUETTE_TRUTH_FILE = "../.claude/ml-spike-kit/gt/silhouettes.json"
private const val SILHOUETTE_MATCH_IOU = 0.1f
private const val GOOD_SILHOUETTE_IOU = 0.9f
private const val BOX_FALLBACK_FILL = 0.97f

private class SilhouetteTruth(val series: String, val box: Rect, val polygon: List<Offset>)
private class SilhouetteScore(val series: String, val iou: Float?, val boxFallback: Boolean)

class SpeechBubbleVisualizer {

    @Test
    fun renderEnlargedBubbles() {
        val dir = System.getenv(PAGES_DIR_ENV)?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val out = File(dir, "out").apply { mkdirs() }
        val pages = ArrayList<String>()
        val mlBoxes = mlBoxes(File(dir!!, ML_BOXES_FILE))
        val truths = silhouetteTruths(File(SILHOUETTE_TRUTH_FILE))
        val scores = ArrayList<SilhouetteScore>()
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
            val layoutStarted = System.nanoTime()
            val enlarged = BubbleLayout.enlarge(bubbles, BUBBLE_ENLARGE_SCALE)
            val layoutMillis = (System.nanoTime() - layoutStarted) / 1_000_000
            val pageScores = truths[file.name].orEmpty().map { silhouetteScore(it, bubbles, content, page.width, page.height) }
            scores.addAll(pageScores)
            val preview = scaled(page, PREVIEW_WIDTH, PREVIEW_WIDTH * page.height / page.width)
            ImageIO.write(withEnlarged(preview, page, enlarged), "png", File(out, file.nameWithoutExtension + "-enlarged.png"))
            ImageIO.write(withOutlines(preview, bubbles), "png", File(out, file.nameWithoutExtension + "-bubbles.png"))
            ImageIO.write(page, "png", File(out, file.nameWithoutExtension + "-page.png"))
            pages.add(pageMetrics(file.name, page.width, page.height, content, pool, millis, layoutMillis, enlarged, pageScores))
            println("${file.name}: ${bubbles.size} bubbles in ${millis}ms, layout ${layoutMillis}ms")
        }
        File(out, "metrics.json").writeText(pages.joinToString(",\n", "[\n", "\n]\n"))
        if (scores.isNotEmpty()) File(out, "silhouettes.json").writeText(silhouetteSummary(scores))
    }

    private fun silhouetteSummary(scores: List<SilhouetteScore>): String {
        val bySeries = scores.groupBy { it.series } + ("all" to scores)
        return bySeries.entries.joinToString(",\n", "{\n", "\n}\n") { (series, items) ->
            val matched = items.mapNotNull { it.iou }
            val line = "\"$series\": {\"count\": ${items.size}, \"unmatched\": ${items.size - matched.size}, " +
                "\"meanIou\": ${f(matched.average().toFloat())}, \"good\": ${matched.count { it >= GOOD_SILHOUETTE_IOU }}, " +
                "\"boxFallbacks\": ${items.count { it.boxFallback }}}"
            println("silhouettes $line")
            "  $line"
        }
    }

    private fun silhouetteScore(truth: SilhouetteTruth, bubbles: List<SpeechBubble>, content: Rect, width: Int, height: Int): SilhouetteScore {
        val box = inContent(truth.box, content)
        val bubble = bubbles.maxByOrNull { iou(it.box, box) }?.takeIf { iou(it.box, box) >= SILHOUETTE_MATCH_IOU }
            ?: return SilhouetteScore(truth.series, null, false)
        val expected = Area(shape(listOf(truth.polygon.map { inContent(it, content) }), width, height) { it })
        val actual = Area(shape(bubble.outlines, width, height) { it })
        val intersection = Area(expected).apply { intersect(actual) }
        val union = Area(expected).apply { add(actual) }
        val boxPixels = bubble.box.width * width * bubble.box.height * height
        val fallback = rasterArea(actual, width, height) >= boxPixels * BOX_FALLBACK_FILL
        return SilhouetteScore(truth.series, rasterArea(intersection, width, height) / rasterArea(union, width, height).toFloat(), fallback)
    }

    private fun iou(a: Rect, b: Rect): Float {
        val overlap = a.intersect(b)
        if (overlap.width <= 0f || overlap.height <= 0f) return 0f
        val shared = overlap.width * overlap.height
        return shared / (a.width * a.height + b.width * b.height - shared)
    }

    private fun silhouetteTruths(file: File): Map<String, List<SilhouetteTruth>> {
        if (!file.isFile) return emptyMap()
        val entry = Regex("""\{"file":"([^"]+)","series":"([^"]+)","box":\[([^\]]+)\],"polygon":\[(.*?\])\]\}""")
        val point = Regex("""\[([\d.]+),([\d.]+)\]""")
        return entry.findAll(file.readText()).map { m ->
            val box = m.groupValues[3].split(",").map { it.trim().toFloat() }
            val polygon = point.findAll(m.groupValues[4]).map { Offset(it.groupValues[1].toFloat(), it.groupValues[2].toFloat()) }.toList()
            unescaped(m.groupValues[1]) to SilhouetteTruth(m.groupValues[2], Rect(box[0], box[1], box[2], box[3]), polygon)
        }.groupBy({ it.first }, { it.second })
    }

    private fun pageMetrics(name: String, width: Int, height: Int, content: Rect, pool: Int, millis: Long, layoutMillis: Long, enlarged: List<EnlargedBubble>, scores: List<SilhouetteScore>): String {
        val stuck = enlarged.count { it.scale <= 1f + STUCK_SCALE_EPSILON }
        val constrained = enlarged.count { it.scale < BUBBLE_ENLARGE_SCALE - STUCK_SCALE_EPSILON }
        val uncovered = uncoveredAreas(enlarged, width, height)
        val bubbles = enlarged.mapIndexed { i, b ->
            val box = b.bubble.box
            val t = b.target
            "{\"scale\": ${f(b.scale)}, \"box\": [${f(box.left)}, ${f(box.top)}, ${f(box.right)}, ${f(box.bottom)}], " +
                "\"target\": [${f(t.left)}, ${f(t.top)}, ${f(t.right)}, ${f(t.bottom)}], \"coversOriginal\": ${covers(b)}, " +
                "\"uncovered\": ${f(uncovered[i])}, \"outlines\": ${outlines(b.bubble)}}"
        }.joinToString(", ")
        return "  {\"page\": \"$name\", \"width\": $width, \"height\": $height, \"content\": [${f(content.left)}, ${f(content.top)}, ${f(content.right)}, ${f(content.bottom)}], \"pool\": $pool, \"ms\": $millis, \"layoutMs\": $layoutMillis, " +
            "\"count\": ${enlarged.size}, \"stuckAtOne\": $stuck, \"constrained\": $constrained, " +
            "\"uncovered\": ${f(uncovered.sum())}, \"silhouettes\": [${silhouettes(scores)}], \"bubbles\": [$bubbles]}"
    }

    private fun silhouettes(scores: List<SilhouetteScore>) = scores.joinToString(", ") {
        "{\"iou\": ${it.iou?.let(::f)}, \"boxFallback\": ${it.boxFallback}}"
    }

    private fun uncoveredAreas(enlarged: List<EnlargedBubble>, width: Int, height: Int): List<Float> {
        val copies = Area()
        enlarged.filter { it.scale > 1f }.forEach { copies.add(Area(shape(it.bubble.outlines, width, height, it::map))) }
        val pageArea = width.toFloat() * height
        return enlarged.map { item ->
            if (item.scale <= 1f) return@map 0f
            val hole = Area(shape(item.bubble.outlines, width, height) { it }).apply { subtract(copies) }
            rasterArea(hole, width, height) / pageArea
        }
    }

    private fun rasterArea(area: Area, width: Int, height: Int): Int {
        val mask = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        mask.createGraphics().apply { color = Color.WHITE; fill(area); dispose() }
        val raster = mask.raster
        var count = 0
        for (y in 0 until height) for (x in 0 until width) if (raster.getSample(x, y, 0) != 0) count++
        return count
    }

    private fun covers(b: EnlargedBubble): Boolean {
        val box = b.bubble.box
        val t = b.target
        return t.left <= box.left + 1e-3f && t.top <= box.top + 1e-3f && t.right >= box.right - 1e-3f && t.bottom >= box.bottom - 1e-3f
    }

    private fun outlines(bubble: SpeechBubble) = bubble.outlines.joinToString(", ", "[", "]") { outline ->
        outline.joinToString(", ", "[", "]") { "[${f(it.x)}, ${f(it.y)}]" }
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
            unescaped(page.groupValues[1]) to box.findAll(page.groupValues[2]).map { b ->
                Rect(b.groupValues[1].toFloat(), b.groupValues[2].toFloat(), b.groupValues[3].toFloat(), b.groupValues[4].toFloat())
            }.toList()
        }
    }

    private fun unescaped(name: String) = Regex("""\\u([0-9a-fA-F]{4})""").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun inContent(point: Offset, content: Rect) = Offset((point.x - content.left) / content.width, (point.y - content.top) / content.height)

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
