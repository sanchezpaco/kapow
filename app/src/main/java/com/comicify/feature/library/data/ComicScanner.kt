package com.comicify.feature.library.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.comicify.core.util.naturalOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val comicExtensions = setOf("cbz", "cbr", "pdf")

private val comicMimeTypes = setOf(
    "application/vnd.comicbook+zip",
    "application/vnd.comicbook-rar",
    "application/x-cbz",
    "application/x-cbr",
    "application/zip",
    "application/x-rar-compressed",
    "application/vnd.rar",
    "application/pdf",
)

class ComicScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun scan(treeUri: Uri): List<DiscoveredComic> = withContext(Dispatchers.IO) {
        val root = DocumentsContract.getTreeDocumentId(treeUri)
        val discovered = mutableListOf<DiscoveredComic>()
        walk(treeUri, root, folderName(treeUri, root), discovered)
        val comparator = compareBy(naturalOrder) { comic: DiscoveredComic -> comic.folderName }
            .thenComparing(compareBy(naturalOrder) { comic: DiscoveredComic -> comic.displayName })
        discovered.sortedWith(comparator)
    }

    private fun walk(treeUri: Uri, documentId: String, folderName: String, sink: MutableList<DiscoveredComic>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0)
                val name = cursor.getString(1).orEmpty()
                val mimeType = cursor.getString(2)
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(treeUri, childId, name, sink)
                } else if (isComic(name, mimeType)) {
                    sink += DiscoveredComic(
                        documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString(),
                        displayName = name,
                        folderName = folderName,
                    )
                }
            }
        }
    }

    private fun folderName(treeUri: Uri, documentId: String): String {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) return cursor.getString(0).orEmpty() }
        return ""
    }

    private fun isComic(name: String, mimeType: String?): Boolean =
        extensionOf(name) in comicExtensions || mimeType in comicMimeTypes

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").trim().substringBefore(' ').lowercase()
}
