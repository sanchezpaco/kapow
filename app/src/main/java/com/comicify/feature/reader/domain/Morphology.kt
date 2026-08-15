package com.comicify.feature.reader.domain

fun BooleanArray.opened(width: Int, height: Int, radius: Int): BooleanArray {
    var mask = this
    repeat(radius) { mask = mask.eroded(width, height) }
    repeat(radius) { mask = mask.dilated(width, height) }
    return mask
}

private fun BooleanArray.eroded(width: Int, height: Int) = neighborhood(width, height, all = true)

private fun BooleanArray.dilated(width: Int, height: Int) = neighborhood(width, height, all = false)

private fun BooleanArray.neighborhood(width: Int, height: Int, all: Boolean): BooleanArray {
    val out = BooleanArray(size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val left = if (x == 0) all else this[i - 1]
            val right = if (x == width - 1) all else this[i + 1]
            val up = if (y == 0) all else this[i - width]
            val down = if (y == height - 1) all else this[i + width]
            out[i] = if (all) this[i] && left && right && up && down else this[i] || left || right || up || down
        }
    }
    return out
}
