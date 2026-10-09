package org.sableos.reader.core.importing

import android.graphics.Bitmap
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sableos.reader.core.pdf.PdfDocumentOpener
import org.sableos.reader.core.pdf.PdfOpenResult
import org.sableos.reader.model.PublicationKind
import org.sableos.reader.model.PublicationTypes
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.LibraryPlatformHelper
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity

/** Adds a PDF to the unified library: identity hash, title, page count and a cover rendered from page one. */
@Singleton
class PdfImporter @Inject constructor(
    private val bookDao: BookDao,
    private val vaultRepository: VaultRepository,
    private val platformHelper: LibraryPlatformHelper,
    private val opener: PdfDocumentOpener,
) {
    suspend fun import(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()
        if (bookDao.getBookByUri(uri.toString(), profileId) != null) {
            return@withContext Result.failure(IllegalStateException(ALREADY_IMPORTED))
        }
        val hash = platformHelper.openInputStream(uri)?.use { ContentHash.md5(it) }
            ?: return@withContext Result.failure(IllegalStateException(UNREADABLE))
        val existing = bookDao.getBookByHash(hash, profileId)
        if (existing != null) {
            return@withContext Result.failure(IllegalStateException(duplicateMessage(existing.title)))
        }
        when (val opened = opener.open(uri)) {
            is PdfOpenResult.Failed -> Result.failure(IllegalStateException(failureMessage(opened.reason.name)))
            is PdfOpenResult.Opened -> opened.session.use { session ->
                insert(uri, hash, profileId, session.page(0, COVER_WIDTH_PX, COVER_HEIGHT_PX))
            }
        }
    }

    private fun duplicateMessage(title: String) = "Duplicate: '$title' is already in your library."

    private fun failureMessage(reason: String) = "Cannot open PDF (${reason.lowercase()})."

    private fun titleFor(uri: Uri): String {
        val name = platformHelper.displayName(uri) ?: return UNTITLED
        return name.substringBeforeLast('.', name).ifBlank { UNTITLED }
    }

    private suspend fun insert(uri: Uri, hash: String, profileId: String, cover: Bitmap): Result<String> {
        val title = titleFor(uri)
        val now = System.currentTimeMillis()
        bookDao.insertBook(
            BookEntity(
                bookHash = hash,
                profileId = profileId,
                title = title,
                author = UNKNOWN_AUTHOR,
                localUri = uri.toString(),
                coverPath = platformHelper.saveCoverBitmapToStorage(cover, title),
                progress = 0.0,
                addedDate = now,
                lastRead = 0L,
                updatedAt = now,
                kind = PublicationKind.PDF.stableValue,
                format = PublicationTypes.PDF_MIME,
            )
        )
        platformHelper.takePersistableUriPermission(uri)
        return Result.success("Imported '$title' successfully!")
    }

    private companion object {
        const val COVER_WIDTH_PX = 400
        const val COVER_HEIGHT_PX = 560
        const val UNKNOWN_AUTHOR = "Unknown Author"
        const val UNTITLED = "Untitled PDF"
        const val ALREADY_IMPORTED = "This exact file is already in your library."
        const val UNREADABLE = "Could not read file to generate hash."
    }
}
