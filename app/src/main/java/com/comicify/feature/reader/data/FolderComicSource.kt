package com.comicify.feature.reader.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import com.comicify.core.util.naturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FolderComicSource private constructor(
    private val context: Context,
    private val pageUris: List<Uri>,
) : ComicSource {

    override val pageCount: Int get() = pageUris.size

    override suspend fun decodePage(index: Int, targetWidth: Int): Bitmap =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(pageUris[index]).use {
                requireNotNull(it) { "Cannot open page stream" }.readBytes()
            }
            decodeSampled(bytes, targetWidth)
        }

    override suspend fun pageAspect(index: Int): Float =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(pageUris[index]).use {
                decodeAspect(requireNotNull(it) { "Cannot open page stream" })
            }
        }

    override fun close() = Unit

    companion object {
        suspend fun fromTree(context: Context, treeUri: Uri): FolderComicSource =
            withContext(Dispatchers.IO) {
                val pageUris = listChildDocuments(context, treeUri)
                    .inImageReadingOrder { it.name }
                    .map { it.uri }
                FolderComicSource(context.applicationContext, pageUris)
            }

        private fun listChildDocuments(context: Context, treeUri: Uri): List<FolderDocument> {
            val treeId = DocumentsContract.getTreeDocumentId(treeUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            val documents = mutableListOf<FolderDocument>()
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    documents += FolderDocument(name, uri)
                }
            }
            return documents
        }
    }
}

internal data class FolderDocument(val name: String, val uri: Uri)

internal fun <T> List<T>.inImageReadingOrder(name: (T) -> String): List<T> =
    filter { name(it).hasImageExtension() }
        .sortedWith(compareBy(naturalOrder, name))
