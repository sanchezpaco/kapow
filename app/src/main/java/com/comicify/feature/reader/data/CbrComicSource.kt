package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.core.util.naturalOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

class CbrComicSource private constructor(
    private val cacheFile: File,
    private val extractDir: File,
    private val randomAccessFile: RandomAccessFile,
    private val archive: IInArchive,
    private val itemIndices: List<Int>,
) : ComicSource {

    private val extractedFiles: Map<Int, CompletableDeferred<File>> =
        itemIndices.associateWith { CompletableDeferred() }

    private var closed = false
    private var extractionFinished = false

    override val pageCount: Int get() = itemIndices.size

    init {
        CoroutineScope(Dispatchers.IO).launch { extractAllItems() }
    }

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val file = extractedFiles.getValue(itemIndices[index]).await()
            decodeSampled(file.readBytes(), targetWidth)
        }

    private fun extractAllItems() {
        try {
            if (itemIndices.isNotEmpty()) {
                archive.extract(itemIndices.sorted().toIntArray(), false, ItemToFileCallback())
            }
        } catch (e: SevenZipException) {
            failPendingItems(ComicSourceException.ReadFailure(e))
        } finally {
            failPendingItems(ComicSourceException.ReadFailure(IOException("Archive ended before item was extracted")))
            synchronized(this) {
                extractionFinished = true
                if (closed) release()
            }
        }
    }

    private fun failPendingItems(cause: ComicSourceException) {
        extractedFiles.values.forEach { it.completeExceptionally(cause) }
    }

    private fun isClosed(): Boolean = synchronized(this) { closed }

    private inner class ItemToFileCallback : IArchiveExtractCallback {
        private var currentIndex = -1
        private var currentStream: OutputStream? = null

        override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
            if (mode != ExtractAskMode.EXTRACT) return null
            if (isClosed()) throw SevenZipException("Source closed during extraction")
            currentIndex = index
            val stream = itemFile(index).outputStream().buffered()
            currentStream = stream
            return ISequentialOutStream { data ->
                stream.write(data)
                data.size
            }
        }

        override fun setOperationResult(result: ExtractOperationResult?) {
            currentStream?.close()
            currentStream = null
            val deferred = extractedFiles[currentIndex] ?: return
            if (result == ExtractOperationResult.OK) {
                deferred.complete(itemFile(currentIndex))
            } else {
                deferred.completeExceptionally(
                    ComicSourceException.ReadFailure(IOException("Extracting item $currentIndex failed: $result")),
                )
            }
        }

        override fun prepareOperation(mode: ExtractAskMode?) = Unit
        override fun setTotal(total: Long) = Unit
        override fun setCompleted(complete: Long) = Unit
    }

    private fun itemFile(itemIndex: Int): File = File(extractDir, itemIndex.toString())

    override fun close() {
        synchronized(this) {
            closed = true
            if (extractionFinished) release()
        }
    }

    private fun release() {
        runCatching { archive.close() }
        runCatching { randomAccessFile.close() }
        runCatching { cacheFile.delete() }
        runCatching { extractDir.deleteRecursively() }
    }

    companion object {
        private const val EXTRACT_DIR_SUFFIX = "_pages"

        suspend fun fromFile(cacheFile: File): CbrComicSource =
            withContext(Dispatchers.IO) {
                val extractDir = freshExtractDir(cacheFile)
                val randomAccessFile = RandomAccessFile(cacheFile, "r")
                val archive = SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))
                val itemIndices = (0 until archive.numberOfItems)
                    .filter { !isFolder(archive, it) && pathOf(archive, it).hasImageExtension() }
                    .sortedWith(compareBy(naturalOrder) { pathOf(archive, it) })
                CbrComicSource(cacheFile, extractDir, randomAccessFile, archive, itemIndices)
            }

        private fun freshExtractDir(cacheFile: File): File {
            val dir = File(cacheFile.parentFile, cacheFile.nameWithoutExtension + EXTRACT_DIR_SUFFIX)
            dir.deleteRecursively()
            if (!dir.mkdirs()) throw ComicSourceException.ReadFailure(IOException("Cannot create $dir"))
            return dir
        }

        private fun isFolder(archive: IInArchive, index: Int): Boolean =
            archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false

        private fun pathOf(archive: IInArchive, index: Int): String =
            archive.getStringProperty(index, PropID.PATH).orEmpty()
    }
}
