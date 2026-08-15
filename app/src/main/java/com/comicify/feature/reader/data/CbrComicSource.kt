package com.comicify.feature.reader.data

import android.graphics.Bitmap
import com.comicify.core.util.naturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

class CbrComicSource private constructor(
    private val cacheFile: File,
    private val randomAccessFile: RandomAccessFile,
    private val archive: IInArchive,
    private val itemIndices: List<Int>,
) : ComicSource {

    private val extractLock = Mutex()

    override val pageCount: Int get() = itemIndices.size

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val bytes = extractLock.withLock { extractItem(itemIndices[index]) }
            decodeSampled(bytes, targetWidth)
        }

    private fun extractItem(itemIndex: Int): ByteArray {
        val output = ByteArrayOutputStream()
        archive.extract(intArrayOf(itemIndex), false, object : IArchiveExtractCallback {
            override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
                if (mode != ExtractAskMode.EXTRACT) return null
                return ISequentialOutStream { data ->
                    output.write(data)
                    data.size
                }
            }

            override fun prepareOperation(mode: ExtractAskMode?) = Unit
            override fun setOperationResult(result: ExtractOperationResult?) = Unit
            override fun setTotal(total: Long) = Unit
            override fun setCompleted(complete: Long) = Unit
        })
        return output.toByteArray()
    }

    override fun close() {
        runCatching { archive.close() }
        runCatching { randomAccessFile.close() }
        runCatching { cacheFile.delete() }
    }

    companion object {
        suspend fun fromFile(cacheFile: File): CbrComicSource =
            withContext(Dispatchers.IO) {
                val randomAccessFile = RandomAccessFile(cacheFile, "r")
                val archive = SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))
                val itemIndices = (0 until archive.numberOfItems)
                    .filter { !isFolder(archive, it) && pathOf(archive, it).hasImageExtension() }
                    .sortedWith(compareBy(naturalOrder) { pathOf(archive, it) })
                CbrComicSource(cacheFile, randomAccessFile, archive, itemIndices)
            }

        private fun isFolder(archive: IInArchive, index: Int): Boolean =
            archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false

        private fun pathOf(archive: IInArchive, index: Int): String =
            archive.getStringProperty(index, PropID.PATH).orEmpty()
    }
}
