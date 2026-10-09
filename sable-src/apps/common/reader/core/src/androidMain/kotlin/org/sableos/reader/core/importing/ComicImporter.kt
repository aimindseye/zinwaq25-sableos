package org.sableos.reader.core.importing

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sableos.reader.core.comic.ComicBitmapDecoder
import org.sableos.reader.core.comic.ComicOpenOutcome
import org.sableos.reader.core.comic.ComicOpener
import org.sableos.reader.core.comic.PageDecode
import org.sableos.reader.backup.Relink
import org.sableos.reader.engine.comic.ComicInfo
import org.sableos.reader.engine.comic.ComicLimits
import org.sableos.reader.engine.comic.ComicOpenFailure
import org.sableos.reader.engine.comic.ComicSource
import org.sableos.reader.model.PublicationKind
import org.sableos.reader.model.PublicationTypes
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.LibraryPlatformHelper
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity

/**
 * Adds comics to the unified library. A CBZ is copied once into app-private storage (a zip needs random access, which
 * a content URI cannot give) under its content hash; an image folder keeps its picked tree URI with a persisted
 * read grant. Either way the container is opened with the hostile-input limits before anything is stored.
 */
@Singleton
class ComicImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val vaultRepository: VaultRepository,
    private val platformHelper: LibraryPlatformHelper,
    private val opener: ComicOpener,
) {
    suspend fun importArchive(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val staged = stage(uri) ?: return@withContext Result.failure(IllegalStateException(UNREADABLE))
        val profileId = vaultRepository.activeVaultId.first()
        val existing = bookDao.getBookByHash(staged.hash, profileId)
        if (existing != null) {
            staged.file.delete()
            return@withContext Result.failure(IllegalStateException(duplicateMessage(existing.title)))
        }
        val owned = File(comicsDir(context), "${staged.hash}.$EXTENSION")
        if (!staged.file.renameTo(owned)) {
            staged.file.delete()
            return@withContext Result.failure(IllegalStateException(UNREADABLE))
        }
        val name = fallbackName(uri)
        val result = register(Target(Uri.fromFile(owned), staged.hash, profileId, PublicationTypes.COMIC_MIME, name))
        if (result.isFailure) owned.delete()
        result
    }

    /** Attaches a chosen CBZ to a restored title: same content hash required, copied into private storage. */
    suspend fun relinkArchive(uri: Uri, bookHash: String): Result<String> = withContext(Dispatchers.IO) {
        val staged = stage(uri) ?: return@withContext Result.failure(IllegalStateException(UNREADABLE))
        val owned = File(comicsDir(context), "${staged.hash}.$EXTENSION")
        val sameFile = Relink.sameContent(bookHash, staged.hash)
        val placed = sameFile && staged.file.renameTo(owned)
        if (!placed) staged.file.delete()
        when {
            !sameFile -> Result.failure(IllegalStateException(NOT_THE_SAME_FILE))
            !placed -> Result.failure(IllegalStateException(UNREADABLE))
            else -> attach(owned, bookHash)
        }
    }

    private suspend fun attach(owned: File, bookHash: String): Result<String> {
        val profileId = vaultRepository.activeVaultId.first()
        val outcome = opener.open(Uri.fromFile(owned).toString())
        return if (outcome is ComicOpenOutcome.Opened) {
            val cover = outcome.source.use { renderCover(it, it.info?.coverPage ?: 0) }
            bookDao.relinkSource(bookHash, profileId, Uri.fromFile(owned).toString())
            cover?.let { bookDao.setCover(bookHash, profileId, platformHelper.saveCoverBitmapToStorage(it, bookHash)) }
            Result.success("Linked the comic.")
        } else {
            owned.delete()
            Result.failure(IllegalStateException(UNREADABLE))
        }
    }

    suspend fun importFolder(tree: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()
        if (bookDao.getBookByUri(tree.toString(), profileId) != null) {
            return@withContext Result.failure(IllegalStateException(ALREADY_IMPORTED))
        }
        platformHelper.takePersistableUriPermission(tree)
        val hash = md5Hex(tree.toString().toByteArray())
        register(Target(tree, hash, profileId, FOLDER_FORMAT, fallbackName(tree)))
    }

    /** Removes the app-private copy behind a deleted library item. Anything outside the comics directory is ignored. */
    fun deleteOwnedCopy(localUri: String) {
        val file = Uri.parse(localUri).takeIf { it.scheme == "file" }?.path?.let(::File) ?: return
        if (file.canonicalFile.parentFile == comicsDir(context).canonicalFile) file.delete()
    }

    private class Target(
        val uri: Uri,
        val hash: String,
        val profileId: String,
        val format: String,
        val fallback: String,
    )

    private suspend fun register(target: Target): Result<String> =
        when (val outcome = opener.open(target.uri.toString())) {
            is ComicOpenOutcome.Failed -> Result.failure(IllegalStateException(failureMessage(outcome.failure)))
            is ComicOpenOutcome.Opened -> outcome.source.use { source -> insert(source, target) }
        }

    private suspend fun insert(source: ComicSource, target: Target): Result<String> {
        val info = source.info
        val title = titleOf(info, target.fallback)
        val now = System.currentTimeMillis()
        val cover = renderCover(source, info?.coverPage ?: 0)
        val book = BookEntity(
            bookHash = target.hash,
            profileId = target.profileId,
            title = title,
            author = info?.writer ?: UNKNOWN_AUTHOR,
            seriesName = info?.series,
            localUri = target.uri.toString(),
            coverPath = cover?.let { platformHelper.saveCoverBitmapToStorage(it, title) },
            addedDate = now,
            lastRead = 0L,
            updatedAt = now,
            kind = PublicationKind.COMIC.stableValue,
            seriesIndex = info?.seriesIndex,
            format = target.format,
        )
        bookDao.insertBook(book)
        return Result.success("Imported '$title' successfully!")
    }

    private fun renderCover(source: ComicSource, preferred: Int): Bitmap? {
        val index = preferred.takeIf { it in 0 until source.pageCount } ?: 0
        val bytes = runCatching { source.readPage(index) }.getOrNull() ?: return null
        return (ComicBitmapDecoder.decode(bytes, COVER_WIDTH_PX, COVER_HEIGHT_PX) as? PageDecode.Decoded)?.bitmap
    }

    private fun titleOf(info: ComicInfo?, fallback: String): String {
        val series = info?.series
        val number = info?.number
        return when {
            !info?.title.isNullOrBlank() && series.isNullOrBlank() -> info?.title.orEmpty()
            !series.isNullOrBlank() && !number.isNullOrBlank() -> "$series #$number"
            !series.isNullOrBlank() -> series
            else -> fallback
        }
    }

    private fun fallbackName(uri: Uri): String {
        val name = platformHelper.displayName(uri) ?: return UNTITLED
        return name.substringBeforeLast('.', name).ifBlank { UNTITLED }
    }

    private class Staged(val file: File, val hash: String)

    private fun stage(uri: Uri): Staged? {
        val dir = comicsDir(context).apply { mkdirs() }
        val temp = File(dir, "${UUID.randomUUID()}.part")
        val hash = runCatching { platformHelper.openInputStream(uri)?.use { copyHashing(it, temp) } }.getOrNull()
        if (hash == null) temp.delete()
        return hash?.let { Staged(temp, it) }
    }

    private fun copyHashing(source: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        target.outputStream().use { out ->
            while (true) {
                val n = source.read(buffer)
                if (n < 0) break
                total += n
                if (total > ComicLimits.DEFAULT_MAX_TOTAL_BYTES) throw IOException("comic archive too large")
                digest.update(buffer, 0, n)
                out.write(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun failureMessage(failure: ComicOpenFailure): String = when (failure) {
        ComicOpenFailure.ENCRYPTED -> "This comic is password protected."
        ComicOpenFailure.NO_PAGES -> "No readable pages (jpg, png, webp, gif, bmp) were found."
        ComicOpenFailure.TOO_MANY_ENTRIES, ComicOpenFailure.TOO_MANY_PAGES, ComicOpenFailure.ARCHIVE_TOO_LARGE ->
            "This comic is larger than Sable Reader will open safely."
        ComicOpenFailure.CORRUPT -> "This comic archive is damaged."
        ComicOpenFailure.NOT_FOUND, ComicOpenFailure.UNREADABLE -> UNREADABLE
    }

    private fun duplicateMessage(title: String) = "Duplicate: '$title' is already in your library."

    companion object {
        const val EXTENSION = "cbz"
        const val FOLDER_FORMAT = "inode/directory"
        private const val COMICS_DIR = "comics"
        private const val COVER_WIDTH_PX = 400
        private const val COVER_HEIGHT_PX = 560
        private const val COPY_BUFFER = 64 * 1024
        private const val UNKNOWN_AUTHOR = "Unknown Author"
        private const val UNTITLED = "Untitled comic"
        private const val UNREADABLE = "Could not read this comic."
        private const val ALREADY_IMPORTED = "This folder is already in your library."
        private const val NOT_THE_SAME_FILE = "That is not the same file as the one in the backup."

        fun comicsDir(context: Context): File = File(context.filesDir, COMICS_DIR)

        private fun md5Hex(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
