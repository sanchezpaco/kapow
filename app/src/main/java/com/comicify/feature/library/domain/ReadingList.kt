package com.comicify.feature.library.domain

data class ReadingList(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val comicIds: List<Long> = emptyList(),
)

object ReadingListMembership {

    fun holdsAll(list: ReadingList, comicIds: List<Long>): Boolean =
        comicIds.isNotEmpty() && comicIds.all { it in list.comicIds }

    fun missing(list: ReadingList, comicIds: List<Long>): List<Long> =
        comicIds.filterNot { it in list.comicIds }
}

object ReadingListOrder {

    fun canMoveUp(comicIds: List<Long>, comicId: Long): Boolean = comicIds.indexOf(comicId) > 0

    fun canMoveDown(comicIds: List<Long>, comicId: Long): Boolean =
        comicIds.indexOf(comicId).let { it >= 0 && it < comicIds.lastIndex }

    fun moveUp(comicIds: List<Long>, comicId: Long): List<Long> = swap(comicIds, comicId, -1)

    fun moveDown(comicIds: List<Long>, comicId: Long): List<Long> = swap(comicIds, comicId, 1)

    fun remove(comicIds: List<Long>, comicId: Long): List<Long> = comicIds.filterNot { it == comicId }

    private fun swap(comicIds: List<Long>, comicId: Long, step: Int): List<Long> {
        val from = comicIds.indexOf(comicId)
        val to = from + step
        if (from < 0 || to !in comicIds.indices) return comicIds
        return comicIds.toMutableList().apply {
            this[from] = this[to].also { this[to] = this[from] }
        }
    }
}
