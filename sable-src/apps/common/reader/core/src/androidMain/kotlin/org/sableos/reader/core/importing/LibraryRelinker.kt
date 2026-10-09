package org.sableos.reader.core.importing

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sableos.reader.backup.Relink
import org.sableos.reader.core.audio.AudiobookSourceLoader
import org.sableos.reader.model.PublicationKind
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.LibraryPlatformHelper
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity

/**
 * Attaches a file or folder to a title restored from a backup ("needs file"). A file must have the same content hash as
 * the one in the backup; a folder must hold the same kind of content. Nothing is changed unless the check passes.
 */
@Singleton
class LibraryRelinker @Inject constructor(
    private val bookDao: BookDao,
    private val vaultRepository: VaultRepository,
    private val platformHelper: LibraryPlatformHelper,
    private val comicImporter: ComicImporter,
    private val audioLoader: AudiobookSourceLoader,
    private val folderKindProbe: FolderKindProbe,
) {
    suspend fun relink(bookHash: String, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()
        val book = bookDao.getBookByHash(bookHash, profileId)
        when {
            book == null -> Result.failure(IllegalStateException("That title is no longer in your library."))
            Relink.isFolder(book.format) -> relinkFolder(book, uri)
            PublicationKind.fromStableValue(book.kind) == PublicationKind.COMIC ->
                comicImporter.relinkArchive(uri, bookHash)
            else -> relinkFile(book, uri)
        }
    }

    private suspend fun relinkFile(book: BookEntity, uri: Uri): Result<String> {
        val actual = runCatching { platformHelper.openInputStream(uri)?.use { ContentHash.md5(it) } }.getOrNull()
        return when {
            actual == null -> Result.failure(IllegalStateException("Could not read that file."))
            !Relink.sameContent(book.bookHash, actual) ->
                Result.failure(IllegalStateException("That is not the same file as the one in the backup."))
            else -> attach(book, uri)
        }
    }

    private suspend fun relinkFolder(book: BookEntity, uri: Uri): Result<String> {
        platformHelper.takePersistableUriPermission(uri)
        val kind = PublicationKind.fromStableValue(book.kind)
        return if (folderKindProbe.probe(uri) == kind) {
            attach(book, uri)
        } else {
            Result.failure(IllegalStateException("That folder does not hold the same kind of content."))
        }
    }

    private suspend fun attach(book: BookEntity, uri: Uri): Result<String> {
        platformHelper.takePersistableUriPermission(uri)
        bookDao.relinkSource(book.bookHash, book.profileId, uri.toString())
        val isAudio = PublicationKind.fromStableValue(book.kind) == PublicationKind.AUDIOBOOK
        if (isAudio) audioLoader.deleteCache(book.bookHash)
        return Result.success("Linked '${book.title}'.")
    }
}
