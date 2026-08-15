package com.comicify.core.util

import java.util.Locale

val naturalOrder: Comparator<String> = Comparator { left, right ->
    val a = left.lowercase(Locale.ROOT)
    val b = right.lowercase(Locale.ROOT)
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            val startA = i
            while (i < a.length && a[i].isDigit()) i++
            val startB = j
            while (j < b.length && b[j].isDigit()) j++
            val runA = a.substring(startA, i)
            val runB = b.substring(startB, j)
            val valueCmp = compareNumericValue(runA, runB)
            if (valueCmp != 0) return@Comparator valueCmp
            if (runA.length != runB.length) return@Comparator runA.length - runB.length
        } else {
            if (ca != cb) return@Comparator ca - cb
            i++
            j++
        }
    }
    a.length - b.length
}

private fun compareNumericValue(runA: String, runB: String): Int {
    val a = runA.trimStart('0')
    val b = runB.trimStart('0')
    if (a.length != b.length) return a.length - b.length
    return a.compareTo(b)
}
