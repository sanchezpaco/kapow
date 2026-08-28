package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_MARGIN_PX = 4
private const val MARGIN_FRACTION_OF_BOX = 0.04f
private const val CHAMFER_STRAIGHT = 3
private const val CHAMFER_DIAGONAL = 4
private const val UNREACHED = Int.MAX_VALUE / 2
private const val OPAQUE = 0xff shl 24
private const val MIN_SMOOTH_RADIUS = 3
private const val SMOOTH_RADIUS_FRACTION = 0.06f

class BubbleFill(val left: Int, val top: Int, val width: Int, val height: Int, val argb: IntArray)

object CrescentFill {

    fun of(pixels: IntArray, pageWidth: Int, pageHeight: Int, bubble: SpeechBubble): BubbleFill {
        val box = bubble.box
        val margin = max(MIN_MARGIN_PX, (MARGIN_FRACTION_OF_BOX * max(box.width * pageWidth, box.height * pageHeight)).roundToInt())
        val pad = margin + 1
        val left = max(0, floor(box.left * pageWidth).toInt() - pad)
        val top = max(0, floor(box.top * pageHeight).toInt() - pad)
        val right = min(pageWidth, ceil(box.right * pageWidth).toInt() + pad)
        val bottom = min(pageHeight, ceil(box.bottom * pageHeight).toInt() + pad)
        val w = max(1, right - left)
        val h = max(1, bottom - top)
        val inside = rasterize(bubble.outlines, left, top, w, h, pageWidth, pageHeight)
        val mask = BooleanArray(w * h)
        val toSilhouette = chamfer(inside, w, h)
        for (i in mask.indices) mask[i] = toSilhouette[i] <= margin * CHAMFER_STRAIGHT
        val known = BooleanArray(w * h) { !mask[it] }
        val order = chamfer(known, w, h)
        val argb = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) if (known[y * w + x]) argb[y * w + x] = pixels[(top + y) * pageWidth + left + x] or OPAQUE
        peel(argb, known, order, w, h)
        smooth(argb, mask, w, h, max(MIN_SMOOTH_RADIUS, (max(w, h) * SMOOTH_RADIUS_FRACTION).roundToInt()))
        for (i in argb.indices) if (!mask[i]) argb[i] = 0
        return BubbleFill(left, top, w, h, argb)
    }

    private fun rasterize(outlines: List<List<Offset>>, left: Int, top: Int, w: Int, h: Int, pageWidth: Int, pageHeight: Int): BooleanArray {
        val inside = BooleanArray(w * h)
        val crossings = ArrayList<Float>()
        for (y in 0 until h) {
            val py = top + y + 0.5f
            crossings.clear()
            outlines.forEach { outline ->
                var j = outline.lastIndex
                for (i in outline.indices) {
                    val ay = outline[i].y * pageHeight
                    val by = outline[j].y * pageHeight
                    if ((ay > py) != (by > py)) {
                        val ax = outline[i].x * pageWidth
                        val bx = outline[j].x * pageWidth
                        crossings.add(ax + (py - ay) * (bx - ax) / (by - ay))
                    }
                    j = i
                }
            }
            crossings.sort()
            var k = 0
            while (k + 1 < crossings.size) {
                val from = max(0, (crossings[k] - left).roundToInt())
                val to = min(w, (crossings[k + 1] - left).roundToInt())
                for (x in from until to) inside[y * w + x] = true
                k += 2
            }
        }
        return inside
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
            var r = 0; var g = 0; var b = 0; var n = 0
            fun at(k: Int) = if (alongRows) line * w + k else k * w + line
            fun add(k: Int, sign: Int) { val c = source[at(k)]; r += sign * ((c shr 16) and 0xff); g += sign * ((c shr 8) and 0xff); b += sign * (c and 0xff); n += sign }
            for (k in 0 until min(radius, length)) add(k, 1)
            for (k in 0 until length) {
                if (k + radius < length) add(k + radius, 1)
                if (k - radius - 1 >= 0) add(k - radius - 1, -1)
                target[at(k)] = OPAQUE or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }

    private fun peel(argb: IntArray, known: BooleanArray, order: IntArray, w: Int, h: Int) {
        val pending = (0 until w * h).filter { !known[it] && order[it] < UNREACHED }.sortedBy { order[it] }
        pending.forEach { i ->
            val x = i % w
            val y = i / w
            var r = 0; var g = 0; var b = 0; var n = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (!known[j]) continue
                val c = argb[j]
                r += (c shr 16) and 0xff; g += (c shr 8) and 0xff; b += c and 0xff; n++
            }
            if (n == 0) return@forEach
            argb[i] = OPAQUE or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            known[i] = true
        }
    }
}
