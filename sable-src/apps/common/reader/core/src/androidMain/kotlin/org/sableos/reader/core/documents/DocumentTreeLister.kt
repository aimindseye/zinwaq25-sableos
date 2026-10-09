package org.sableos.reader.core.documents

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract

/** One file found in a picked document tree: its path inside the tree, size, and the document URI that opens it. */
data class TreeFile(val path: String, val sizeBytes: Long, val uri: String)

/** A tree that could not be listed (permission lost, provider error) or that is larger than the limits allow. */
class TreeListingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Bounded breadth-first listing of a Storage Access Framework tree using only `DocumentsContract`. Directories are not
 * returned; symlinks do not exist in SAF. The walk stops with [TreeListingException] past [maxEntries] and does not
 * descend deeper than [maxDepth].
 */
class DocumentTreeLister(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val maxEntries: Int,
    private val maxDepth: Int,
) {
    private val files = ArrayList<TreeFile>()
    private val pending = ArrayDeque<Triple<String, String, Int>>()

    fun list(): List<TreeFile> {
        pending.addLast(Triple(DocumentsContract.getTreeDocumentId(tree), "", 0))
        while (pending.isNotEmpty()) {
            val (documentId, prefix, depth) = pending.removeFirst()
            listChildren(documentId, prefix, depth)
        }
        return files
    }

    private fun listChildren(documentId: String, prefix: String, depth: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val cursor = runCatching { resolver.query(children, COLUMNS, null, null, null) }.getOrNull()
            ?: throw TreeListingException("folder cannot be listed")
        cursor.use {
            while (it.moveToNext()) {
                if (files.size + pending.size > maxEntries) throw TreeListingException("folder has too many entries")
                visit(it, prefix, depth)
            }
        }
    }

    private fun visit(row: Cursor, prefix: String, depth: Int) {
        val childId = row.getString(ID_COLUMN)
        val name = row.getString(NAME_COLUMN).orEmpty()
        val path = if (prefix.isEmpty()) name else "$prefix/$name"
        if (row.getString(MIME_COLUMN) == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (depth < maxDepth) pending.addLast(Triple(childId, path, depth + 1))
        } else {
            val size = if (row.isNull(SIZE_COLUMN)) 0L else row.getLong(SIZE_COLUMN)
            files += TreeFile(path, size, DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString())
        }
    }

    private companion object {
        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        const val ID_COLUMN = 0
        const val NAME_COLUMN = 1
        const val MIME_COLUMN = 2
        const val SIZE_COLUMN = 3
    }
}
