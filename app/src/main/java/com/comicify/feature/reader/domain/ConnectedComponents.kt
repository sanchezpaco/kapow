package com.comicify.feature.reader.domain

const val NO_LABEL = -1

class Component(val label: Int, val box: Box, val pixels: Int, val touchesBorder: Boolean) {
    val fill: Float get() = pixels / box.area.toFloat()
}

class Segmentation(val components: List<Component>, val labels: IntArray)

fun BooleanArray.segment(width: Int, height: Int, minPixels: Int): Segmentation {
    val labels = IntArray(size) { NO_LABEL }
    val stack = IntArray(size)
    val found = ArrayList<Component>()
    var nextLabel = 0
    for (start in indices) {
        if (!this[start] || labels[start] != NO_LABEL) continue
        val label = nextLabel++
        var top = 0
        stack[top++] = start
        labels[start] = label
        var x0 = width; var y0 = height; var x1 = -1; var y1 = -1; var count = 0
        while (top > 0) {
            val p = stack[--top]
            val px = p % width
            val py = p / width
            count++
            if (px < x0) x0 = px
            if (py < y0) y0 = py
            if (px > x1) x1 = px
            if (py > y1) y1 = py
            if (px > 0 && this[p - 1] && labels[p - 1] == NO_LABEL) { labels[p - 1] = label; stack[top++] = p - 1 }
            if (px < width - 1 && this[p + 1] && labels[p + 1] == NO_LABEL) { labels[p + 1] = label; stack[top++] = p + 1 }
            if (py > 0 && this[p - width] && labels[p - width] == NO_LABEL) { labels[p - width] = label; stack[top++] = p - width }
            if (py < height - 1 && this[p + width] && labels[p + width] == NO_LABEL) { labels[p + width] = label; stack[top++] = p + width }
        }
        if (count < minPixels) continue
        val touchesBorder = x0 == 0 || y0 == 0 || x1 == width - 1 || y1 == height - 1
        found.add(Component(label, Box(x0, y0, x1 + 1, y1 + 1), count, touchesBorder))
    }
    return Segmentation(found, labels)
}
