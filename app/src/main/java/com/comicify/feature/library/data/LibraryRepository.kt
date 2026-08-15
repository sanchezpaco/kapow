package com.comicify.feature.library.data

import android.net.Uri
import com.comicify.feature.library.domain.LibraryComic
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    val library: Flow<List<LibraryComic>>
    val folderUri: Flow<String?>
    suspend fun setFolder(treeUri: Uri)
    suspend fun refresh()
    suspend fun generateMissingCovers()
    suspend fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int)
}
