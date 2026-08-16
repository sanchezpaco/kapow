package com.comicify.feature.library.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import com.comicify.core.storage.ComicDao
import com.comicify.core.storage.ComicEntity
import com.comicify.core.storage.LibraryPreferences
import com.comicify.core.storage.ReadingStateDao
import com.comicify.core.storage.ReadingStateEntity
import com.comicify.feature.library.domain.ComicNameParser
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val LIBRARY_TAG = "Library"

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val comicDao: ComicDao,
    private val readingStateDao: ReadingStateDao,
    private val preferences: LibraryPreferences,
    private val scanner: ComicScanner,
    private val coverGenerator: CoverGenerator,
) : LibraryRepository {

    override val library: Flow<List<LibraryComic>> =
        combine(comicDao.observeAll(), readingStateDao.observeAll()) { comics, states ->
            val byId = states.associateBy { it.comicId }
            LibraryCatalog.sort(comics.map { it.toLibraryComic(byId[it.id]) })
        }

    override val folderUri: Flow<String?> = preferences.folderUri

    override val grouped: Flow<Boolean> = preferences.grouped

    override suspend fun setGrouped(grouped: Boolean) {
        preferences.setGrouped(grouped)
    }

    override suspend fun setFolder(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        preferences.setFolderUri(treeUri.toString())
        refresh()
    }

    override suspend fun refresh() {
        val treeUri = preferences.folderUri.first()?.toUri() ?: return
        val discovered = scanner.scan(treeUri)
        pruneMissing(discovered.map { it.documentUri }.toSet())
        discovered.forEach { discovered ->
            if (comicDao.findByDocumentUri(discovered.documentUri) != null) return@forEach
            val parsed = ComicNameParser.parse(discovered.displayName)
            val series = parsed.series.ifBlank { discovered.folderName }.ifBlank { discovered.displayName }
            comicDao.insert(
                ComicEntity(
                    documentUri = discovered.documentUri,
                    displayName = discovered.displayName.substringBeforeLast('.', discovered.displayName),
                    series = series,
                    issueNumber = parsed.issueNumber,
                    year = parsed.year,
                    pageCount = null,
                    coverPath = null,
                    addedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun generateMissingCovers() {
        comicDao.withoutCover().forEach { comic ->
            runCatching { coverGenerator.generate(comic.id, comic.documentUri.toUri()) }
                .onSuccess { comicDao.updateCover(comic.id, it.pageCount, it.coverPath) }
        }
    }

    override suspend fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int) {
        readingStateDao.upsert(
            ReadingStateEntity(
                comicId = comicId,
                pageIndex = pageIndex,
                completed = LibraryCatalog.isCompleted(pageIndex, pageCount),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setRead(comicId: Long, read: Boolean) {
        if (!read) {
            readingStateDao.delete(comicId)
            return
        }
        val lastPage = comicDao.findById(comicId)?.pageCount?.let { it - 1 } ?: 0
        readingStateDao.upsert(
            ReadingStateEntity(
                comicId = comicId,
                pageIndex = lastPage,
                completed = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun setFavorite(comicId: Long, favorite: Boolean) {
        comicDao.setFavorite(comicId, favorite)
    }

    override suspend fun deleteComic(comicId: Long): Boolean {
        val comic = comicDao.findById(comicId) ?: return false
        val deleted = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, comic.documentUri.toUri())
        }.getOrElse { error ->
            Log.e(LIBRARY_TAG, "Failed to delete ${comic.documentUri}", error)
            false
        }
        if (!deleted) return false
        removeComic(comic)
        return true
    }

    private suspend fun pruneMissing(presentUris: Set<String>) {
        comicDao.getAll().forEach { comic ->
            if (comic.documentUri !in presentUris) removeComic(comic)
        }
    }

    private suspend fun removeComic(comic: ComicEntity) {
        comic.coverPath?.let { File(it).delete() }
        readingStateDao.delete(comic.id)
        comicDao.deleteById(comic.id)
    }

    private fun ComicEntity.toLibraryComic(state: ReadingStateEntity?): LibraryComic =
        LibraryComic(
            id = id,
            documentUri = documentUri,
            title = LibraryCatalog.title(series, issueNumber),
            series = series,
            issueNumber = issueNumber,
            coverPath = coverPath,
            pageCount = pageCount,
            pageIndex = state?.pageIndex ?: 0,
            completed = state?.completed ?: false,
            favorite = favorite,
            lastReadAt = state?.updatedAt,
        )
}
