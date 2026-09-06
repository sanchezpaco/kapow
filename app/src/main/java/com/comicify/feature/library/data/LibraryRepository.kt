package com.comicify.feature.library.data

import android.net.Uri
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.ReadingList
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    val library: Flow<List<LibraryComic>>
    val folderUri: Flow<String?>
    val grouped: Flow<Boolean>
    val readingLists: Flow<List<ReadingList>>
    suspend fun setFolder(treeUri: Uri)
    suspend fun setGrouped(grouped: Boolean)
    suspend fun seedSampleIfNeeded()
    suspend fun refresh()
    suspend fun fillMissingDetails()
    suspend fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int): Boolean
    suspend fun unshelve(comicId: Long)
    suspend fun reshelve(comicId: Long)
    suspend fun setRead(comicId: Long, read: Boolean)
    suspend fun setFavorite(comicId: Long, favorite: Boolean)
    suspend fun deleteComic(comicId: Long): Boolean
    suspend fun createList(name: String): Long
    suspend fun renameList(listId: Long, name: String)
    suspend fun deleteList(listId: Long)
    suspend fun restoreList(list: ReadingList)
    suspend fun addToList(listId: Long, comicId: Long, ordering: Int? = null)
    suspend fun removeFromList(listId: Long, comicId: Long): Int?
    suspend fun reorderList(listId: Long, comicIds: List<Long>)
    fun settings(documentUri: String): Flow<ComicSettings>
    suspend fun saveSettings(documentUri: String, settings: ComicSettings)
    suspend fun clearDetections(documentUri: String)
}
