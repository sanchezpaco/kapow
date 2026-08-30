package com.comicify.feature.reader.data

import androidx.compose.ui.geometry.Rect
import com.comicify.core.storage.PageDetectionDao
import com.comicify.core.storage.PageDetectionEntity
import com.comicify.feature.reader.domain.PageDetectionCodec
import com.comicify.feature.reader.domain.SpeechBubble

private const val SPLIT_PAGES_SUFFIX = "+split"

class PageDetectionStore(
    private val dao: PageDetectionDao,
    private val documentUri: String,
    splitWidePages: Boolean,
) {

    private val version = if (splitWidePages) DETECTIONS_VERSION + SPLIT_PAGES_SUFFIX else DETECTIONS_VERSION

    suspend fun panels(pageIndex: Int): List<Rect>? = find(pageIndex)?.panels?.let(PageDetectionCodec::decodePanels)

    suspend fun bubbles(pageIndex: Int): List<SpeechBubble>? = find(pageIndex)?.bubbles?.let(PageDetectionCodec::decodeBubbles)

    suspend fun savePanels(pageIndex: Int, panels: List<Rect>) {
        dao.upsert(row(pageIndex).copy(panels = PageDetectionCodec.encodePanels(panels)))
    }

    suspend fun saveBubbles(pageIndex: Int, bubbles: List<SpeechBubble>) {
        dao.upsert(row(pageIndex).copy(bubbles = PageDetectionCodec.encodeBubbles(bubbles)))
    }

    private suspend fun find(pageIndex: Int) = dao.find(documentUri, pageIndex, version)

    private suspend fun row(pageIndex: Int) =
        find(pageIndex) ?: PageDetectionEntity(documentUri, pageIndex, version, panels = null, bubbles = null)
}
