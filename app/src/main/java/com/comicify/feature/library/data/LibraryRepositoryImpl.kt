package com.comicify.feature.library.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.comicify.R
import com.comicify.core.storage.ComicDao
import com.comicify.core.storage.ComicEntity
import com.comicify.core.storage.ComicSettingsDao
import com.comicify.core.storage.ComicSettingsEntity
import com.comicify.core.storage.KapowDatabase
import com.comicify.core.storage.LibraryPreferences
import com.comicify.core.storage.PageDetectionDao
import com.comicify.core.storage.ReadingListDao
import com.comicify.core.storage.ReadingListEntity
import com.comicify.core.storage.ReadingListEntryEntity
import com.comicify.core.storage.ReadingStateDao
import com.comicify.core.storage.ReadingStateEntity
import com.comicify.domain.model.ReadingDirection
import com.comicify.feature.library.domain.ArrivingComic
import com.comicify.feature.library.domain.ComicInfoParser
import com.comicify.feature.library.domain.ComicNameParser
import com.comicify.feature.library.domain.ComicSettings
import com.comicify.feature.library.domain.EditedComicMetadata
import com.comicify.feature.library.domain.METADATA_VERSION
import com.comicify.feature.library.domain.LibraryCatalog
import com.comicify.feature.library.domain.LibraryComic
import com.comicify.feature.library.domain.LibraryReconciler
import com.comicify.feature.library.domain.MissingComic
import com.comicify.feature.library.domain.ParsedComicName
import com.comicify.feature.library.domain.ReadingList
import com.comicify.feature.library.domain.mergeComicMetadata
import com.comicify.feature.reader.domain.PageLook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val LIBRARY_TAG = "Library"
private const val SAMPLE_ASSET = "sample.cbz"
private const val SAMPLE_DIRECTORY = "sample"

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: KapowDatabase,
    private val comicDao: ComicDao,
    private val readingStateDao: ReadingStateDao,
    private val comicSettingsDao: ComicSettingsDao,
    private val pageDetectionDao: PageDetectionDao,
    private val readingListDao: ReadingListDao,
    private val preferences: LibraryPreferences,
    private val scanner: ComicScanner,
    private val hasher: ComicHasher,
    private val coverGenerator: CoverGenerator,
) : LibraryRepository {

    override val library: Flow<List<LibraryComic>> =
        combine(comicDao.observeAll(), readingStateDao.observeAll()) { comics, states ->
            val byId = states.associateBy { it.comicId }
            LibraryCatalog.sort(comics.map { it.toLibraryComic(byId[it.id]) })
        }

    override val folderUri: Flow<String?> = preferences.folderUri

    override val grouped: Flow<Boolean> = preferences.grouped

    override val readingLists: Flow<List<ReadingList>> =
        combine(readingListDao.observeLists(), readingListDao.observeEntries()) { lists, entries ->
            val byList = entries.sortedBy { it.ordering }.groupBy { it.listId }
            lists.map { list ->
                ReadingList(
                    id = list.id,
                    name = list.name,
                    createdAt = list.createdAt,
                    comicIds = byList[list.id].orEmpty().map { it.comicId },
                )
            }
        }

    override suspend fun setGrouped(grouped: Boolean) {
        preferences.setGrouped(grouped)
    }

    override suspend fun createList(name: String): Long =
        readingListDao.insertList(ReadingListEntity(name = name, createdAt = System.currentTimeMillis()))

    override suspend fun renameList(listId: Long, name: String) {
        readingListDao.renameList(listId, name)
    }

    override suspend fun deleteList(listId: Long) {
        readingListDao.deleteList(listId)
    }

    override suspend fun restoreList(list: ReadingList) {
        database.withTransaction {
            readingListDao.insertList(ReadingListEntity(id = list.id, name = list.name, createdAt = list.createdAt))
            readingListDao.insertEntries(list.comicIds.toEntries(list.id))
        }
    }

    override suspend fun addToList(listId: Long, comicId: Long, ordering: Int?) {
        val position = ordering ?: ((readingListDao.maxOrdering(listId) ?: -1) + 1)
        readingListDao.insertEntry(ReadingListEntryEntity(listId = listId, comicId = comicId, ordering = position))
    }

    override suspend fun removeFromList(listId: Long, comicId: Long): Int? {
        val ordering = readingListDao.findEntry(listId, comicId)?.ordering ?: return null
        readingListDao.deleteEntry(listId, comicId)
        return ordering
    }

    override suspend fun reorderList(listId: Long, comicIds: List<Long>) {
        readingListDao.updateEntries(comicIds.toEntries(listId))
    }

    private fun List<Long>.toEntries(listId: Long): List<ReadingListEntryEntity> =
        mapIndexed { index, comicId -> ReadingListEntryEntity(listId = listId, comicId = comicId, ordering = index) }

    override suspend fun seedSampleIfNeeded() {
        if (preferences.sampleSeeded.first()) return
        val file = copySampleAsset()
        val title = context.getString(R.string.sample_comic_title)
        comicDao.insert(
            ComicEntity(
                documentUri = Uri.fromFile(file).toString(),
                displayName = title,
                series = title,
                issueNumber = null,
                year = null,
                pageCount = null,
                coverPath = null,
                addedAt = System.currentTimeMillis(),
            ),
        )
        preferences.setSampleSeeded()
    }

    private fun copySampleAsset(): File {
        val directory = File(context.filesDir, SAMPLE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, SAMPLE_ASSET)
        context.assets.open(SAMPLE_ASSET).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
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
        val known = comicDao.getAll()
        val knownById = known.associateBy(ComicEntity::id)
        val reconciliation = LibraryReconciler.reconcile(arrivals(discovered, known), missing(discovered, known))
        reconciliation.relinks.forEach { relink -> relinkComic(knownById.getValue(relink.comicId), relink.comic) }
        reconciliation.additions.forEach { addComic(it) }
        reconciliation.removals.forEach { removeComic(knownById.getValue(it)) }
    }

    private suspend fun arrivals(discovered: List<DiscoveredComic>, known: List<ComicEntity>): List<ArrivingComic> {
        val registered = known.mapTo(mutableSetOf(), ComicEntity::documentUri)
        return discovered.filterNot { it.documentUri in registered }.map { comic ->
            ArrivingComic(
                documentUri = comic.documentUri,
                displayName = comic.displayName,
                folderName = comic.folderName,
                contentHash = hasher.hash(comic.documentUri.toUri()),
            )
        }
    }

    private fun missing(discovered: List<DiscoveredComic>, known: List<ComicEntity>): List<MissingComic> {
        val present = discovered.mapTo(mutableSetOf(), DiscoveredComic::documentUri)
        return known.filterNot { isManagedLocally(it) || it.documentUri in present }
            .map { MissingComic(it.id, it.contentHash) }
    }

    private suspend fun addComic(arrival: ArrivingComic) {
        val parsed = ComicNameParser.parse(arrival.displayName)
        comicDao.insert(
            ComicEntity(
                documentUri = arrival.documentUri,
                displayName = arrival.title(),
                series = arrival.series(parsed),
                issueNumber = parsed.issueNumber,
                year = parsed.year,
                pageCount = null,
                coverPath = null,
                addedAt = System.currentTimeMillis(),
                contentHash = arrival.contentHash,
            ),
        )
    }

    private suspend fun relinkComic(comic: ComicEntity, arrival: ArrivingComic) {
        val parsed = ComicNameParser.parse(arrival.displayName)
        val edited = comic.editedMetadata()
        database.withTransaction {
            comicDao.relink(
                id = comic.id,
                documentUri = arrival.documentUri,
                displayName = arrival.title(),
                series = edited?.series ?: arrival.series(parsed),
                issueNumber = if (edited != null) edited.issueNumber else parsed.issueNumber,
                year = parsed.year,
            )
            comicSettingsDao.relink(comic.documentUri, arrival.documentUri)
            pageDetectionDao.relink(comic.documentUri, arrival.documentUri)
        }
        Log.i(LIBRARY_TAG, "Relinked comic ${comic.id} to ${arrival.documentUri}")
    }

    private fun ArrivingComic.title(): String = displayName.substringBeforeLast('.', displayName)

    private fun ArrivingComic.series(parsed: ParsedComicName): String =
        parsed.series.ifBlank { folderName }.ifBlank { displayName }

    override suspend fun fillMissingDetails() {
        comicDao.getAll().forEach { comic ->
            backfillContentHash(comic)
            if (!comic.needsCoverOrMetadata()) return@forEach
            runCatching { coverGenerator.generate(comic.id, comic.documentUri.toUri(), comic.coverPage) }
                .onSuccess { generated ->
                    comicDao.updateCover(comic.id, generated.pageCount, generated.coverPage, generated.coverPath, generated.ambient)
                    saveMetadata(comic, comic.parsedName(), generated.comicInfoXml)
                }
        }
    }

    override suspend fun setCoverPage(comicId: Long, page: Int): Boolean {
        val comic = comicDao.findById(comicId) ?: return false
        val generated = runCatching { coverGenerator.generate(comic.id, comic.documentUri.toUri(), page) }
            .onFailure { Log.e(LIBRARY_TAG, "Page $page could not become the cover of ${comic.documentUri}", it) }
            .getOrNull() ?: return false
        comicDao.updateCover(comic.id, generated.pageCount, generated.coverPage, generated.coverPath, generated.ambient)
        comic.coverPath?.takeIf { it != generated.coverPath }?.let { File(it).delete() }
        return true
    }

    private suspend fun backfillContentHash(comic: ComicEntity) {
        if (comic.contentHash != null) return
        val hash = hasher.hash(comic.documentUri.toUri()) ?: return
        comicDao.setContentHash(comic.id, hash)
    }

    private suspend fun saveMetadata(comic: ComicEntity, parsed: ParsedComicName, comicInfoXml: String?) {
        val metadata = mergeComicMetadata(
            info = comicInfoXml?.let(ComicInfoParser::parse),
            parsed = parsed,
            edited = comic.editedMetadata(),
        )
        comicDao.updateMetadata(
            id = comic.id,
            series = metadata.series,
            issueNumber = metadata.issueNumber,
            year = metadata.year,
            storyTitle = metadata.storyTitle,
            publisher = metadata.publisher,
            writer = metadata.writer,
            penciller = metadata.penciller,
            inker = metadata.inker,
            colorist = metadata.colorist,
            summary = metadata.summary,
            readsRightToLeft = metadata.readsRightToLeft,
            metadataVersion = METADATA_VERSION,
        )
    }

    override suspend fun saveProgress(comicId: Long, pageIndex: Int, pageCount: Int): Boolean {
        val wasCompleted = readingStateDao.find(comicId)?.completed == true
        val completed = LibraryCatalog.completedAfter(wasCompleted, pageIndex, pageCount)
        readingStateDao.upsert(
            ReadingStateEntity(
                comicId = comicId,
                pageIndex = pageIndex,
                completed = completed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return completed && !wasCompleted
    }

    override suspend fun unshelve(comicId: Long) {
        readingStateDao.unshelve(comicId)
    }

    override suspend fun reshelve(comicId: Long) {
        readingStateDao.reshelve(comicId)
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

    override suspend fun setRating(comicId: Long, rating: Int) {
        comicDao.setRating(comicId, rating)
    }

    override suspend fun saveEditedMetadata(comicId: Long, series: String, issueNumber: Int?, storyTitle: String?) {
        comicDao.saveEditedMetadata(comicId, series.trim(), issueNumber, storyTitle?.trim()?.ifBlank { null })
    }

    override suspend fun resetMetadata(comicId: Long) {
        comicDao.clearMetadataEdit(comicId)
        val comic = comicDao.findById(comicId) ?: return
        runCatching { coverGenerator.generate(comic.id, comic.documentUri.toUri(), comic.coverPage) }
            .onSuccess { generated ->
                comicDao.updateCover(comic.id, generated.pageCount, generated.coverPage, generated.coverPath, generated.ambient)
                saveMetadata(comic, ComicNameParser.parse(comic.displayName), generated.comicInfoXml)
            }
    }

    override suspend fun deleteComic(comicId: Long): Boolean {
        val comic = comicDao.findById(comicId) ?: return false
        val deleted = if (isManagedLocally(comic)) {
            deleteLocalFile(comic.documentUri.toUri())
        } else {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, comic.documentUri.toUri())
            }.getOrElse { error ->
                Log.e(LIBRARY_TAG, "Failed to delete ${comic.documentUri}", error)
                false
            }
        }
        if (!deleted) return false
        removeComic(comic)
        return true
    }

    override fun settings(documentUri: String): Flow<ComicSettings> =
        comicSettingsDao.observe(documentUri).map { it?.toComicSettings() ?: ComicSettings.Default }

    override suspend fun saveSettings(documentUri: String, settings: ComicSettings) {
        val splitSuggested = comicSettingsDao.find(documentUri)?.splitSuggested ?: false
        if (settings == ComicSettings.Default && !splitSuggested) {
            comicSettingsDao.delete(documentUri)
            return
        }
        comicSettingsDao.upsert(
            ComicSettingsEntity(
                documentUri = documentUri,
                splitSuggested = splitSuggested,
                rightToLeft = settings.direction?.let { it == ReadingDirection.RightToLeft },
                coverAlone = settings.coverAlone,
                bubblesEnlarged = settings.bubblesEnlarged,
                guided = settings.guided,
                bubbleScale = settings.bubbleScale,
                splitWidePages = settings.splitWidePages,
                verticalScroll = settings.verticalScroll,
                pageLook = settings.pageLook.name,
                fitWidth = settings.fitWidth,
            ),
        )
    }

    override suspend fun clearDetections(documentUri: String) {
        pageDetectionDao.deleteAll(documentUri)
    }

    private fun ComicSettingsEntity.toComicSettings(): ComicSettings =
        ComicSettings(
            direction = rightToLeft?.let { if (it) ReadingDirection.RightToLeft else ReadingDirection.LeftToRight },
            coverAlone = coverAlone,
            bubblesEnlarged = bubblesEnlarged,
            guided = guided,
            bubbleScale = bubbleScale,
            splitWidePages = splitWidePages,
            verticalScroll = verticalScroll,
            pageLook = PageLook.named(pageLook),
            fitWidth = fitWidth,
        )

    private fun deleteLocalFile(uri: Uri): Boolean {
        val path = uri.path ?: return false
        val file = File(path)
        return !file.exists() || file.delete()
    }

    private fun isManagedLocally(comic: ComicEntity): Boolean =
        comic.documentUri.toUri().scheme == ContentResolver.SCHEME_FILE

    private suspend fun removeComic(comic: ComicEntity) {
        coverGenerator.deleteCovers(comic.id)
        readingStateDao.delete(comic.id)
        comicSettingsDao.delete(comic.documentUri)
        pageDetectionDao.deleteAll(comic.documentUri)
        comicDao.deleteById(comic.id)
    }

    private fun ComicEntity.toLibraryComic(state: ReadingStateEntity?): LibraryComic =
        LibraryComic(
            id = id,
            documentUri = documentUri,
            displayName = displayName,
            title = LibraryCatalog.title(series, issueNumber),
            series = series,
            issueNumber = issueNumber,
            coverPath = coverPath,
            coverPage = coverPage,
            coverAmbient = coverAmbient,
            pageCount = pageCount,
            pageIndex = state?.pageIndex ?: 0,
            completed = state?.completed ?: false,
            favorite = favorite,
            lastReadAt = state?.updatedAt,
            shelved = state?.shelved ?: true,
            storyTitle = storyTitle,
            publisher = publisher,
            writer = writer,
            penciller = penciller,
            inker = inker,
            colorist = colorist,
            summary = summary,
            year = year,
            addedAt = addedAt,
            rating = rating,
            metadataEdited = metadataEdited,
        )
}

private fun ComicEntity.parsedName(): ParsedComicName =
    ParsedComicName(series = series, issueNumber = issueNumber, year = year)

private fun ComicEntity.editedMetadata(): EditedComicMetadata? =
    if (metadataEdited) EditedComicMetadata(series = series, issueNumber = issueNumber, storyTitle = storyTitle) else null

private fun ComicEntity.needsCoverOrMetadata(): Boolean =
    coverMissing() || metadataVersion < METADATA_VERSION

private fun ComicEntity.coverMissing(): Boolean = coverPath?.let { !File(it).exists() } ?: true
