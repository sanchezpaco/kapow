package com.comicify.feature.reader.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PageDetectionCodecTest {

    @Test
    fun panelsRoundTrip() {
        val panels = listOf(Rect(0f, 0.1f, 0.5f, 0.45f), Rect(0.5f, 0.1f, 1f, 0.45f))
        assertEquals(panels, PageDetectionCodec.decodePanels(PageDetectionCodec.encodePanels(panels)))
    }

    @Test
    fun bubblesRoundTrip() {
        val bubbles = listOf(
            SpeechBubble(Rect(0.1f, 0.2f, 0.3f, 0.4f), listOf(listOf(Offset(0.1f, 0.2f), Offset(0.3f, 0.2f), Offset(0.2f, 0.4f)))),
            SpeechBubble(Rect(0.5f, 0.6f, 0.7f, 0.8f), listOf(listOf(Offset(0.5f, 0.6f), Offset(0.7f, 0.8f)), listOf(Offset(0.6f, 0.7f)))),
        )
        assertEquals(bubbles, PageDetectionCodec.decodeBubbles(PageDetectionCodec.encodeBubbles(bubbles)))
    }

    @Test
    fun emptyListsRoundTrip() {
        assertEquals(emptyList<Rect>(), PageDetectionCodec.decodePanels(PageDetectionCodec.encodePanels(emptyList())))
        assertEquals(emptyList<SpeechBubble>(), PageDetectionCodec.decodeBubbles(PageDetectionCodec.encodeBubbles(emptyList())))
    }
}
