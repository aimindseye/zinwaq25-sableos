package org.sableos.reader.core.importing

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sableos.reader.core.audio.AudioMetadataReader
import org.sableos.reader.core.audio.AudiobookLoadException
import org.sableos.reader.core.audio.AudiobookLoadFailure
import org.sableos.reader.core.audio.AudiobookSourceLoader
import org.sableos.reader.engine.audio.AudioManifest
import org.sableos.reader.model.PublicationKind
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.LibraryPlatformHelper
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity

/**
 * Adds audiobooks to the unified library. Nothing is copied: the picked file or folder keeps its URI with a persisted
 * read grant, and playback reads it in place. Import builds (and thereby validates) the manifest before anything is
 * stored.
 */
@Singleton
class AudiobookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val vaultRepository: VaultRepository,
    private val platformHelper: LibraryPlatformHelper,
    private val loader: AudiobookSourceLoader,
    private val metadata: AudioMetadataReader,
) {
    suspend fun importFile(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()
        if (bookDao.getBookByUri(uri.toString(), profileId) != null) {
            return@withContext Result.failure(IllegalStateException(ALREADY_IMPORTED))
        }
        val hash = runCatching { platformHelper.openInputStream(uri)?.use { ContentHash.md5(it) } }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException(UNREADABLE))
        val existing = bookDao.getBookByHash(hash, profileId)
        if (existing != null) return@withContext Result.failure(IllegalStateException(duplicateMessage(existing.title)))
        platformHelper.takePersistableUriPermission(uri)
        register(Target(uri, hash, profileId, fallbackName(uri), context.contentResolver.getType(uri)))
    }

    suspend fun importFolder(tree: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()
        if (bookDao.getBookByUri(tree.toString(), profileId) != null) {
            return@withContext Result.failure(IllegalStateException(ALREADY_IMPORTED))
        }
        platformHelper.takePersistableUriPermission(tree)
        register(Target(tree, md5Hex(tree.toString()), profileId, fallbackName(tree), FOLDER_FORMAT))
    }

    private class Target(val uri: Uri, val hash: String, val profileId: String, val name: String, val format: String?)

    private suspend fun register(target: Target): Result<String> {
        val manifest = try {
            loader.build(target.uri.toString(), target.name, null)
        } catch (e: AudiobookLoadException) {
            return Result.failure(IllegalStateException(failureMessage(e.failure), e))
        }
        val book = bookEntity(target, manifest)
        bookDao.insertBook(book)
        return Result.success("Imported '${book.title}' successfully!")
    }

    private fun bookEntity(target: Target, manifest: AudioManifest): BookEntity {
        val now = System.currentTimeMillis()
        val first = Uri.parse(manifest.tracks.first().uri)
        val cover = runCatching { metadata.read(first, wantCover = true)?.cover }.getOrNull()
        return BookEntity(
            bookHash = target.hash,
            profileId = target.profileId,
            title = manifest.title,
            author = manifest.author ?: UNKNOWN_AUTHOR,
            localUri = target.uri.toString(),
            coverPath = cover?.let { platformHelper.saveCoverBitmapToStorage(it, manifest.title) },
            addedDate = now,
            lastRead = 0L,
            updatedAt = now,
            kind = PublicationKind.AUDIOBOOK.stableValue,
            format = target.format,
        )
    }

    private fun fallbackName(uri: Uri): String {
        val name = platformHelper.displayName(uri) ?: return UNTITLED
        return name.substringBeforeLast('.', name).ifBlank { UNTITLED }
    }

    private fun failureMessage(failure: AudiobookLoadFailure): String = when (failure) {
        AudiobookLoadFailure.NO_AUDIO ->
            "No playable audio files (mp3, m4a, m4b, aac, ogg, opus, flac, wav) were found."
        AudiobookLoadFailure.TOO_MANY_FILES -> "This folder has more audio files than Sable Reader will open."
        AudiobookLoadFailure.NOT_LOCAL -> "Only audiobooks stored on this device can be played."
        AudiobookLoadFailure.UNREADABLE -> UNREADABLE
    }

    private fun duplicateMessage(title: String) = "Duplicate: '$title' is already in your library."

    private fun md5Hex(text: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val FOLDER_FORMAT = "inode/directory"
        const val UNKNOWN_AUTHOR = "Unknown Author"
        const val UNTITLED = "Untitled audiobook"
        const val UNREADABLE = "Could not read this audiobook."
        const val ALREADY_IMPORTED = "This audiobook is already in your library."
    }
}
