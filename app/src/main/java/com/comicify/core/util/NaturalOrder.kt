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
            var endA = i
            while (endA < a.length && a[endA].isDigit()) endA++
            var endB = j
            while (endB < b.length && b[endB].isDigit()) endB++
            val numA = a.substring(i, endA).trimStart('0').ifEmpty { "0" }
            val numB = b.substring(j, endB).trimStart('0').ifEmpty { "0" }
            val cmp = if (numA.length != numB.length) {
                numA.length - numB.length
            } else {
                numA.compareTo(numB)
            }
            if (cmp != 0) return@Comparator cmp
            i = endA
            j = endB
        } else {
            if (ca != cb) return@Comparator ca - cb
            i++
            j++
        }
    }
    a.length - b.length
}
