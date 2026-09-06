package com.comicify.feature.library.domain

import org.w3c.dom.Document
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

const val COMIC_INFO_ENTRY = "comicinfo.xml"
const val METADATA_VERSION = 1

private const val ROOT_ELEMENT = "ComicInfo"
private const val MANGA_RIGHT_TO_LEFT = "YesAndRightToLeft"
private const val MANGA_UNKNOWN = "Unknown"

object ComicInfoParser {

    fun parse(xml: String): ComicInfo? {
        val document = documentOf(xml) ?: return null
        if (document.documentElement?.tagName != ROOT_ELEMENT) return null
        return ComicInfo(
            series = document.text("Series"),
            number = document.text("Number")?.toIntOrNull(),
            year = document.text("Year")?.toIntOrNull(),
            storyTitle = document.text("Title"),
            publisher = document.text("Publisher"),
            writer = document.text("Writer"),
            penciller = document.text("Penciller"),
            inker = document.text("Inker"),
            colorist = document.text("Colorist"),
            summary = document.text("Summary"),
            readsRightToLeft = readsRightToLeft(document.text("Manga")),
        )
    }

    private fun documentOf(xml: String): Document? =
        try {
            builderFactory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: SAXException) {
            null
        }

    private fun builderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply { isExpandEntityReferences = false }

    private fun readsRightToLeft(manga: String?): Boolean? = when (manga) {
        null, MANGA_UNKNOWN -> null
        MANGA_RIGHT_TO_LEFT -> true
        else -> false
    }

    private fun Document.text(tag: String): String? =
        getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifBlank { null }
}
