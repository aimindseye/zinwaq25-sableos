package org.sableos.media

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

internal class AudioFolderScanner(
    private val context: Context,
) {
    fun scan(treeUri: Uri): List<Uri> {
        val root = rootDocumentUri(treeUri) ?: return emptyList()
        val pending = ArrayDeque<Uri>().apply { add(root) }
        val audio = mutableListOf<Uri>()

        while (pending.isNotEmpty() && audio.size < MAX_AUDIO_FILES) {
            scanDirectory(
                treeUri = treeUri,
                directory = pending.removeFirst(),
                pending = pending,
                audio = audio,
            )
        }

        return audio
    }

    private fun rootDocumentUri(treeUri: Uri): Uri? =
        runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull()?.let { rootId ->
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                rootId,
            )
        }

    private fun scanDirectory(
        treeUri: Uri,
        directory: Uri,
        pending: ArrayDeque<Uri>,
        audio: MutableList<Uri>,
    ) {
        val directoryId =
            runCatching {
                DocumentsContract.getDocumentId(directory)
            }.getOrNull() ?: return

        val children =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                directoryId,
            )

        runCatching {
            context.contentResolver.query(
                children,
                PROJECTION,
                null,
                null,
                null,
            )
        }.getOrNull()?.use { cursor ->
            scanChildren(
                treeUri = treeUri,
                cursor = cursor,
                pending = pending,
                audio = audio,
            )
        }
    }

    private fun scanChildren(
        treeUri: Uri,
        cursor: Cursor,
        pending: ArrayDeque<Uri>,
        audio: MutableList<Uri>,
    ) {
        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        if (idIndex < 0 || mimeIndex < 0) {
            return
        }

        while (cursor.moveToNext() && audio.size < MAX_AUDIO_FILES) {
            val documentId = cursor.getString(idIndex)
            if (documentId != null) {
                collectDocument(
                    treeUri = treeUri,
                    documentId = documentId,
                    mimeType = cursor.getString(mimeIndex).orEmpty(),
                    pending = pending,
                    audio = audio,
                )
            }
        }
    }

    private fun collectDocument(
        treeUri: Uri,
        documentId: String,
        mimeType: String,
        pending: ArrayDeque<Uri>,
        audio: MutableList<Uri>,
    ) {
        val documentUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                documentId,
            )

        when {
            mimeType == DocumentsContract.Document.MIME_TYPE_DIR -> {
                pending.add(documentUri)
            }

            mimeType.startsWith(AUDIO_MIME_PREFIX) -> {
                audio.add(documentUri)
            }
        }
    }

    private companion object {
        const val MAX_AUDIO_FILES = 2_000
        const val AUDIO_MIME_PREFIX = "audio/"
        val PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
    }
}
