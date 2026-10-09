package org.sableos.reader.core.comic

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import org.sableos.reader.engine.comic.BoundedRead
import org.sableos.reader.engine.comic.ComicCatalog
import org.sableos.reader.engine.comic.ComicCatalogBuilder
import org.sableos.reader.engine.comic.ComicInfo
import org.sableos.reader.engine.comic.ComicInfoParser
import org.sableos.reader.engine.comic.ComicLimits
import org.sableos.reader.engine.comic.ComicOpenException
import org.sableos.reader.engine.comic.ComicOpenFailure
import org.sableos.reader.engine.comic.ComicPageException
import org.sableos.reader.engine.comic.ComicSource
import org.sableos.reader.engine.comic.PageFailure
import org.sableos.reader.engine.comic.RawEntry

private val CHILD_COLUMNS = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
    DocumentsContract.Document.COLUMN_SIZE,
)

/**
 * A folder of page images chosen with the system folder picker (a Storage Access Framework tree). Listing uses only
 * `DocumentsContract`; the walk is bounded by [ComicLimits] and every page read is capped on actual bytes.
 */
class TreeComicSource private constructor(
    private val resolver: ContentResolver,
    override val limits: ComicLimits,
    override val catalog: ComicCatalog,
    override val info: ComicInfo?,
) : ComicSource {
    override fun readPage(index: Int): ByteArray {
        val page = catalog.pages.getOrNull(index) ?: throw ComicPageException(PageFailure.UNREADABLE, "no page $index")
        ComicCatalogBuilder.checkReadable(page, limits)
        return readUri(resolver, page.handle, limits.maxPageBytes)
    }

    override fun close() = Unit

    companion object {
        fun open(resolver: ContentResolver, tree: Uri, limits: ComicLimits = ComicLimits()): ComicSource {
            val entries = TreeWalker(resolver, tree, limits).walk()
            val catalog = ComicCatalogBuilder.build(entries.asSequence(), limits)
            val info = catalog.comicInfoHandle?.let { handle ->
                runCatching { readUri(resolver, handle, limits.maxComicInfoBytes.toLong()) }.getOrNull()
                    ?.let { ComicInfoParser.parse(it, limits) }
            }
            return TreeComicSource(resolver, limits, catalog, info)
        }

        private fun readUri(resolver: ContentResolver, uri: String, limit: Long): ByteArray {
            val stream = runCatching { resolver.openInputStream(Uri.parse(uri)) }.getOrNull()
                ?: throw ComicPageException(PageFailure.UNREADABLE, "cannot open page")
            return stream.use { BoundedRead.readAll(it, limit) }
        }
    }
}

/** Breadth-first, depth- and count-bounded listing of a document tree. */
private class TreeWalker(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val limits: ComicLimits,
) {
    private val out = ArrayList<RawEntry>()
    private val pending = ArrayDeque<Triple<String, String, Int>>()

    fun walk(): List<RawEntry> {
        pending.addLast(Triple(DocumentsContract.getTreeDocumentId(tree), "", 0))
        while (pending.isNotEmpty()) {
            val (documentId, prefix, depth) = pending.removeFirst()
            list(documentId, prefix, depth)
        }
        return out
    }

    private fun list(documentId: String, prefix: String, depth: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val cursor = runCatching { resolver.query(children, CHILD_COLUMNS, null, null, null) }.getOrNull()
            ?: throw ComicOpenException(ComicOpenFailure.UNREADABLE, "folder cannot be listed")
        cursor.use {
            while (it.moveToNext()) {
                check(out.size + pending.size)
                visit(it, prefix, depth)
            }
        }
    }

    private fun check(known: Int) {
        if (known > limits.maxEntries) throw ComicOpenException(ComicOpenFailure.TOO_MANY_ENTRIES)
    }

    private fun visit(row: Cursor, prefix: String, depth: Int) {
        val childId = row.getString(ID_COLUMN)
        val name = row.getString(NAME_COLUMN).orEmpty()
        val path = if (prefix.isEmpty()) name else "$prefix/$name"
        if (row.getString(MIME_COLUMN) == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (depth < limits.maxFolderDepth) pending.addLast(Triple(childId, path, depth + 1))
        } else {
            val size = if (row.isNull(SIZE_COLUMN)) 0L else row.getLong(SIZE_COLUMN)
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId).toString()
            out += RawEntry(path, size, 0L, uri)
        }
    }

    private companion object {
        const val ID_COLUMN = 0
        const val NAME_COLUMN = 1
        const val MIME_COLUMN = 2
        const val SIZE_COLUMN = 3
    }
}
