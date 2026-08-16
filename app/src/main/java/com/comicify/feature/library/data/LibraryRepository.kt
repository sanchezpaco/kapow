package com.comicify.feature.library.data

import android.net.Uri
import com.comicify.feature.library.domain.LibraryComic
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    val library: Flow<List<LibraryComic>>
    val folderUri: Flow<String?>
    val grouped: Flow<Boolean>
    suspend fun setFolder(treeUri: Uri)
    suspend fun setGrouped(grouped: Boolean)
    suspend fun refresh()
    suspend fun generateMissingCovers()
    suspend fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int)
    suspend fun setRead(comicId: Long, read: Boolean)
    suspend fun setFavorite(comicId: Long, favorite: Boolean)
    suspend fun deleteComic(comicId: Long): Boolean
}
