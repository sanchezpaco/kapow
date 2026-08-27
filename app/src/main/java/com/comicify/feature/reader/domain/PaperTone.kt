package com.comicify.feature.reader.domain

import kotlin.math.abs

private const val BRIGHT_CELL_SHARE = 0.4f
private const val TINT_SAMPLE_DEPTH = 12
private const val MIN_TOLERANCE = 14
private const val TOLERANCE_SLACK = 8
private const val TOLERANCE_DARKNESS_SHARE = 0.5f
private const val MAX_RED_GREEN_DRIFT = 12
private const val MAX_GREEN_BLUE_DRIFT = 16
private const val CLEAN_PAPER_LUMINANCE = 236
private const val MIN_PAPER_LUMINANCE = 200
private const val FULL_LUMINANCE = 255

class PaperTone private constructor(val luminance: Int, private val redGreen: Int, private val greenBlue: Int) {
    private val tolerance = maxOf(MIN_TOLERANCE, ((FULL_LUMINANCE - luminance) * TOLERANCE_DARKNESS_SHARE).toInt() + TOLERANCE_SLACK)

    val isScannedPaper: Boolean get() = luminance in MIN_PAPER_LUMINANCE until CLEAN_PAPER_LUMINANCE

    fun matches(color: Int): Boolean =
        luminance(color) >= luminance - tolerance &&
            abs(red(color) - green(color) - redGreen) <= MAX_RED_GREEN_DRIFT &&
            abs(green(color) - blue(color) - greenBlue) <= MAX_GREEN_BLUE_DRIFT

    companion object {
        fun estimate(colors: IntArray, box: Box, width: Int): PaperTone {
            val inside = IntArray(box.area) { colors[(box.top + it / box.width) * width + box.left + it % box.width] }
            val luminances = inside.map(::luminance).sorted()
            val brightest = luminances.drop((luminances.size * (1 - BRIGHT_CELL_SHARE)).toInt())
            val paper = brightest.median()
            val paperCells = inside.filter { luminance(it) >= paper - TINT_SAMPLE_DEPTH }
            return PaperTone(paper, paperCells.map { red(it) - green(it) }.median(), paperCells.map { green(it) - blue(it) }.median())
        }

        private fun List<Int>.median(): Int = sorted()[size / 2]
    }
}
