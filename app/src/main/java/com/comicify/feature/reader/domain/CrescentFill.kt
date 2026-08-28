package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_MARGIN_PX = 4
private const val MARGIN_FRACTION_OF_BOX = 0.04f
private const val HALO_PX = 2
private const val MIN_SMOOTH_RADIUS = 3
private const val SMOOTH_RADIUS_FRACTION = 0.06f
private const val CHAMFER_STRAIGHT = 3
private const val CHAMFER_DIAGONAL = 4
private const val UNREACHED = Int.MAX_VALUE / 2
private const val OPAQUE = 0xff shl 24

class BubbleFill(val left: Int, val top: Int, val width: Int, val height: Int, val argb: IntArray)

object CrescentFill {

    fun of(pixels: IntArray, pageWidth: Int, pageHeight: Int, bubble: SpeechBubble, neighbours: List<SpeechBubble>): BubbleFill {
        val crop = Crop.around(bubble, pageWidth, pageHeight)
        val mask = crop.dilated(crop.rasterize(bubble.outlines), crop.margin)
        val known = crop.source(mask, bubble, neighbours)
        val argb = crop.pixels(pixels, pageWidth, known)
        peel(argb, known, chamfer(known, crop.width, crop.height), crop.width, crop.height)
        smooth(argb, mask, crop.width, crop.height, crop.smoothRadius)
        for (i in argb.indices) if (!mask[i]) argb[i] = 0
        return BubbleFill(crop.left, crop.top, crop.width, crop.height, argb)
    }

    private class Crop(val left: Int, val top: Int, val width: Int, val height: Int, val margin: Int, val pageWidth: Int, val pageHeight: Int) {
        val smoothRadius = max(MIN_SMOOTH_RADIUS, (max(width, height) * SMOOTH_RADIUS_FRACTION).roundToInt())
        val bounds = Rect(left.toFloat() / pageWidth, top.toFloat() / pageHeight, (left + width).toFloat() / pageWidth, (top + height).toFloat() / pageHeight)

        fun dilated(inside: BooleanArray, by: Int): BooleanArray {
            val distance = chamfer(inside, width, height)
            return BooleanArray(width * height) { distance[it] <= by * CHAMFER_STRAIGHT }
        }

        fun source(mask: BooleanArray, bubble: SpeechBubble, neighbours: List<SpeechBubble>): BooleanArray {
            val foreign = neighbours.filter { it !== bubble && it.box.overlaps(bounds) }.map { dilated(rasterize(it.outlines), margin + HALO_PX) }
            val beyondHalo = chamfer(mask, width, height)
            return BooleanArray(width * height) { i -> beyondHalo[i] > HALO_PX * CHAMFER_STRAIGHT && foreign.none { it[i] } }
        }

        fun pixels(page: IntArray, pageWidth: Int, known: BooleanArray): IntArray {
            val argb = IntArray(width * height)
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                if (known[i]) argb[i] = page[(top + y) * pageWidth + left + x] or OPAQUE
            }
            return argb
        }

        fun rasterize(outlines: List<List<Offset>>): BooleanArray {
            val inside = BooleanArray(width * height)
            val crossings = ArrayList<Float>()
            for (y in 0 until height) {
                crossings.clear()
                outlines.forEach { crossingsOf(it, top + y + 0.5f, crossings) }
                crossings.sort()
                var k = 0
                while (k + 1 < crossings.size) {
                    val from = max(0, (crossings[k] - left).roundToInt())
                    val to = min(width, (crossings[k + 1] - left).roundToInt())
                    for (x in from until to) inside[y * width + x] = true
                    k += 2
                }
            }
            return inside
        }

        private fun crossingsOf(outline: List<Offset>, py: Float, into: MutableList<Float>) {
            var j = outline.lastIndex
            for (i in outline.indices) {
                val ay = outline[i].y * pageHeight
                val by = outline[j].y * pageHeight
                if ((ay > py) != (by > py)) {
                    val ax = outline[i].x * pageWidth
                    val bx = outline[j].x * pageWidth
                    into.add(ax + (py - ay) * (bx - ax) / (by - ay))
                }
                j = i
            }
        }

        companion object {
            fun around(bubble: SpeechBubble, pageWidth: Int, pageHeight: Int): Crop {
                val box = bubble.box
                val margin = max(MIN_MARGIN_PX, (MARGIN_FRACTION_OF_BOX * max(box.width * pageWidth, box.height * pageHeight)).roundToInt())
                val pad = margin + 1
                val left = max(0, floor(box.left * pageWidth).toInt() - pad)
                val top = max(0, floor(box.top * pageHeight).toInt() - pad)
                val right = min(pageWidth, ceil(box.right * pageWidth).toInt() + pad)
                val bottom = min(pageHeight, ceil(box.bottom * pageHeight).toInt() + pad)
                return Crop(left, top, max(1, right - left), max(1, bottom - top), margin, pageWidth, pageHeight)
            }
        }
    }

    private fun chamfer(source: BooleanArray, w: Int, h: Int): IntArray {
        val dist = IntArray(w * h) { if (source[it]) 0 else UNREACHED }
        for (y in 0 until h) for (x in 0 until w) relax(dist, w, h, x, y, -1)
        for (y in h - 1 downTo 0) for (x in w - 1 downTo 0) relax(dist, w, h, x, y, 1)
        return dist
    }

    private fun relax(dist: IntArray, w: Int, h: Int, x: Int, y: Int, dir: Int) {
        val i = y * w + x
        var best = dist[i]
        val ny = y + dir
        if (x - dir in 0 until w) best = min(best, dist[i - dir] + CHAMFER_STRAIGHT)
        if (ny in 0 until h) {
            best = min(best, dist[ny * w + x] + CHAMFER_STRAIGHT)
            if (x - 1 >= 0) best = min(best, dist[ny * w + x - 1] + CHAMFER_DIAGONAL)
            if (x + 1 < w) best = min(best, dist[ny * w + x + 1] + CHAMFER_DIAGONAL)
        }
        dist[i] = best
    }

    private fun peel(argb: IntArray, known: BooleanArray, order: IntArray, w: Int, h: Int) {
        val pending = (0 until w * h).filter { !known[it] && order[it] < UNREACHED }.sortedBy { order[it] }
        pending.forEach { i ->
            val x = i % w
            val y = i / w
            val sum = ColorSum()
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (known[j]) sum.add(argb[j])
            }
            if (sum.n == 0) return@forEach
            argb[i] = sum.mean()
            known[i] = true
        }
    }

    private fun smooth(argb: IntArray, mask: BooleanArray, w: Int, h: Int, radius: Int) {
        val horizontal = IntArray(w * h)
        boxPass(argb, horizontal, w, h, radius, alongRows = true)
        val blurred = IntArray(w * h)
        boxPass(horizontal, blurred, w, h, radius, alongRows = false)
        for (i in argb.indices) if (mask[i]) argb[i] = blurred[i]
    }

    private fun boxPass(source: IntArray, target: IntArray, w: Int, h: Int, radius: Int, alongRows: Boolean) {
        val lines = if (alongRows) h else w
        val length = if (alongRows) w else h
        for (line in 0 until lines) {
            val sum = ColorSum()
            fun at(k: Int) = if (alongRows) line * w + k else k * w + line
            for (k in 0 until min(radius, length)) sum.add(source[at(k)])
            for (k in 0 until length) {
                if (k + radius < length) sum.add(source[at(k + radius)])
                if (k - radius - 1 >= 0) sum.remove(source[at(k - radius - 1)])
                target[at(k)] = sum.mean()
            }
        }
    }

    private class ColorSum {
        var r = 0; var g = 0; var b = 0; var n = 0

        fun add(c: Int) { r += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff; n++ }
        fun remove(c: Int) { r -= (c shr 16) and 0xff; g -= (c shr 8) and 0xff; b -= c and 0xff; n-- }
        fun mean() = OPAQUE or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
