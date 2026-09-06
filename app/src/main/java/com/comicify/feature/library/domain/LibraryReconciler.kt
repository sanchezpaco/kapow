package com.comicify.feature.library.domain

data class ArrivingComic(
    val documentUri: String,
    val displayName: String,
    val folderName: String,
    val contentHash: String?,
)

data class MissingComic(val id: Long, val contentHash: String?)

data class ComicRelink(val comicId: Long, val comic: ArrivingComic)

data class LibraryReconciliation(
    val relinks: List<ComicRelink>,
    val additions: List<ArrivingComic>,
    val removals: List<Long>,
)

object LibraryReconciler {

    fun reconcile(arrivals: List<ArrivingComic>, missing: List<MissingComic>): LibraryReconciliation {
        val byHash = missing.filter { it.contentHash != null }
            .groupBy(MissingComic::contentHash)
            .mapValues { (_, rows) -> ArrayDeque(rows) }
        val relinks = mutableListOf<ComicRelink>()
        val additions = mutableListOf<ArrivingComic>()
        arrivals.forEach { arrival ->
            val row = arrival.contentHash?.let { byHash[it]?.removeFirstOrNull() }
            if (row == null) additions += arrival else relinks += ComicRelink(row.id, arrival)
        }
        val relinked = relinks.mapTo(mutableSetOf(), ComicRelink::comicId)
        return LibraryReconciliation(
            relinks = relinks,
            additions = additions,
            removals = missing.map(MissingComic::id).filterNot { it in relinked },
        )
    }
}
