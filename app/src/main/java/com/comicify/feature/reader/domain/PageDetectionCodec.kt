package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

private const val ITEM_SEPARATOR = ';'
private const val OUTLINE_SEPARATOR = '/'
private const val BOX_OUTLINES_SEPARATOR = '|'
private const val VALUE_SEPARATOR = ','

object PageDetectionCodec {

    fun encodePanels(panels: List<Rect>): String = panels.joinToString(ITEM_SEPARATOR.toString(), transform = ::encodeRect)

    fun decodePanels(encoded: String): List<Rect> = split(encoded, ITEM_SEPARATOR).map(::decodeRect)

    fun encodeBubbles(bubbles: List<SpeechBubble>): String =
        bubbles.joinToString(ITEM_SEPARATOR.toString()) { bubble ->
            encodeRect(bubble.box) + BOX_OUTLINES_SEPARATOR + bubble.outlines.joinToString(OUTLINE_SEPARATOR.toString(), transform = ::encodeOutline)
        }

    fun decodeBubbles(encoded: String): List<SpeechBubble> =
        split(encoded, ITEM_SEPARATOR).map { item ->
            val (box, outlines) = item.split(BOX_OUTLINES_SEPARATOR, limit = 2)
            SpeechBubble(decodeRect(box), split(outlines, OUTLINE_SEPARATOR).map(::decodeOutline))
        }

    private fun encodeRect(rect: Rect) = listOf(rect.left, rect.top, rect.right, rect.bottom).joinToString(VALUE_SEPARATOR.toString())

    private fun decodeRect(encoded: String): Rect {
        val (left, top, right, bottom) = encoded.split(VALUE_SEPARATOR).map(String::toFloat)
        return Rect(left, top, right, bottom)
    }

    private fun encodeOutline(outline: List<Offset>) =
        outline.joinToString(VALUE_SEPARATOR.toString()) { "${it.x}$VALUE_SEPARATOR${it.y}" }

    private fun decodeOutline(encoded: String): List<Offset> =
        encoded.split(VALUE_SEPARATOR).map(String::toFloat).chunked(2) { (x, y) -> Offset(x, y) }

    private fun split(encoded: String, separator: Char): List<String> =
        if (encoded.isEmpty()) emptyList() else encoded.split(separator)
}
