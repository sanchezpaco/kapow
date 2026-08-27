package com.comicify.feature.reader.data

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comicify.feature.reader.domain.SpeechBubble
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private const val PAGES_DIR = "mlspike"
private const val READER_TARGET_WIDTH = 2160
private const val ANALYSIS_SIDE = 1000

@RunWith(AndroidJUnit4::class)
class BubbleOutlineDump {

    @Test
    fun dumpOutlines() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, PAGES_DIR)
        assumeTrue(dir.isDirectory)
        val detector = PanelDetector.forContext(context)
        val rows = dir.listFiles { f -> f.extension == "jpg" }!!.sorted().map { file ->
            val bitmap = decodeSampled(file.readBytes(), READER_TARGET_WIDTH)
            val bubbles = detector.bubbles(bitmap)
            File(dir, file.nameWithoutExtension + "-analysed.png").outputStream().use { bitmap.atAnalysisSize().compress(Bitmap.CompressFormat.PNG, 100, it) }
            "{\"file\": \"${file.name}\", \"width\": ${bitmap.width}, \"height\": ${bitmap.height}, \"bubbles\": ${bubbles(bubbles)}}"
        }
        File(dir, "outlines.json").writeText(rows.joinToString(",\n", "[\n", "\n]\n"))
    }

    private fun bubbles(bubbles: List<SpeechBubble>) = bubbles.joinToString(", ", "[", "]") { b ->
        "{\"box\": [${f(b.box.left)}, ${f(b.box.top)}, ${f(b.box.right)}, ${f(b.box.bottom)}], \"outlines\": ${outlines(b.outlines)}}"
    }

    private fun outlines(outlines: List<List<Offset>>) = outlines.joinToString(", ", "[", "]") { outline ->
        outline.joinToString(", ", "[", "]") { "[${f(it.x)}, ${f(it.y)}]" }
    }

    private fun Bitmap.atAnalysisSize(): Bitmap {
        val scale = ANALYSIS_SIDE / kotlin.math.min(width, height).toFloat()
        if (scale >= 1f) return this
        return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
    }

    private fun f(value: Float) = String.format(Locale.US, "%.4f", value)
}
