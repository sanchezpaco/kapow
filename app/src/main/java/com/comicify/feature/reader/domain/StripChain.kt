package com.comicify.feature.reader.domain

data class StripLink(val title: String, val pageAspects: List<Float>)

sealed interface StripItem {
    val link: Int

    data class Page(override val link: Int, val page: Int, val aspect: Float) : StripItem
    data class Boundary(override val link: Int, val finished: String, val next: String) : StripItem
}

object StripChain {

    fun items(links: List<StripLink>): List<StripItem> = links.flatMapIndexed { index, link ->
        val pages = link.pageAspects.mapIndexed { page, aspect -> StripItem.Page(index, page, aspect) }
        val follower = links.getOrNull(index + 1) ?: return@flatMapIndexed pages
        pages + StripItem.Boundary(index, link.title, follower.title)
    }

    fun indexOfPage(items: List<StripItem>, link: Int, page: Int): Int =
        items.indexOfFirst { it is StripItem.Page && it.link == link && it.page == page }
}
