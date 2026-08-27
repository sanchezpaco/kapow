package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Rect
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO

private const val PAGES_ENV = "REPRO_PAGES"
private const val DUMP_ENV = "REPRO_DUMP"
private const val ANALYSED_SUFFIX = "-analysed.png"

class DeviceOutlineRepro {

    @Test
    fun reproduceDeviceOutlines() {
        val pagesDir = System.getenv(PAGES_ENV)?.let(::File)
        val dump = System.getenv(DUMP_ENV)?.let(::File)
        assumeTrue(pagesDir != null && dump != null && dump.isFile)
        val entry = Regex(""""file":\s*"([^"]+)"""")
        val box = Regex(""""box":\s*\[([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+)\]""")
        val out = ArrayList<String>()
        dump!!.readLines().filter { entry.containsMatchIn(it) }.forEach { line ->
            val name = entry.find(line)!!.groupValues[1]
            val boxes = box.findAll(line).map { Rect(it.groupValues[1].toFloat(), it.groupValues[2].toFloat(), it.groupValues[3].toFloat(), it.groupValues[4].toFloat()) }.toList()
            val analysed = ImageIO.read(File(pagesDir, name.removeSuffix(".jpg") + ANALYSED_SUFFIX))
            val pixels = analysed.getRGB(0, 0, analysed.width, analysed.height, null, 0, analysed.width)
            val classes = PixelClasses.classify(pixels, analysed.width, analysed.height, 1)
            val bubbles = SpeechBubbles.outlined(classes, boxes)
            out.add("{\"file\": \"$name\", \"bubbles\": [" + bubbles.joinToString(", ") { b ->
                "{\"box\": [${f(b.box.left)}, ${f(b.box.top)}, ${f(b.box.right)}, ${f(b.box.bottom)}], \"outlines\": " +
                    b.outlines.joinToString(", ", "[", "]") { o -> o.joinToString(", ", "[", "]") { "[${f(it.x)}, ${f(it.y)}]" } } + "}"
            } + "]}")
        }
        File(dump.parentFile, "repro_outlines.json").writeText(out.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)
}
