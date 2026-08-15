package com.comicify.feature.library.data

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import javax.inject.Inject
import javax.inject.Singleton

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

    override suspend fun setFolder(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.setFolderUri(treeUri.toString())
        refresh()
    }

    override suspend fun refresh() {
        val treeUri = preferences.folderUri.first()?.toUri() ?: return
        scanner.scan(treeUri).forEach { discovered ->
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
            lastReadAt = state?.updatedAt,
        )
}
