package org.vaachak.reader.core.data.repository

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.domain.model.BookEntity
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val bookDao: BookDao,
    private val readiumManager: ReadiumManager,
    private val platformHelper: LibraryPlatformHelper,
    private val vaultRepository: VaultRepository // <-- 1. INJECT THE VAULT
) {

    suspend fun isBookDuplicate(title: String): Boolean {
        val profileId = vaultRepository.activeVaultId.first() // <-- 2. GET ACTIVE USER
        return bookDao.isBookExists(title, profileId)
    }

    suspend fun getLocalBookMap(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val profileId = vaultRepository.activeVaultId.first()
            bookDao.getAllBooksSortedByRecent(profileId)
                .first()
                .mapNotNull { book ->
                    book.localUri?.let { uri -> book.title to uri }
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun importBook(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val profileId = vaultRepository.activeVaultId.first()

        // DB Check by URI (Fastest fail)
        val existingUri = bookDao.getBookByUri(uri.toString(), profileId)
        if (existingUri != null) return@withContext Result.failure(Exception("This exact file is already in your library."))

        try {
            // Generate Hash
            val bookHash = platformHelper.openInputStream(uri)?.use { stream ->
                calculateMd5Hash(stream)
            } ?: return@withContext Result.failure(Exception("Could not read file to generate hash."))

            // Check Hash Duplicate
            val existingHash = bookDao.getBookByHash(bookHash, profileId)
            if (existingHash != null) {
                return@withContext Result.failure(Exception("Duplicate: '${existingHash.title}' is already in your library."))
            }

            platformHelper.takePersistableUriPermission(uri)

            val publication = readiumManager.openEpubFromUri(uri)
                ?: return@withContext Result.failure(Exception("Failed to parse book data."))

            val title = publication.metadata.title ?: "Unknown Title"

            // Duplicate Check by Title
            if (bookDao.isBookExists(title, profileId)) {
                readiumManager.closePublication()
                return@withContext Result.failure(Exception("Duplicate: A book titled '$title' is already in your library."))
            }

            val author = publication.metadata.authors.firstOrNull()?.name?.toString() ?: "Unknown Author"

            var savedCoverPath: String? = null
            try {
                val bitmap = readiumManager.getPublicationCover(publication)
                if (bitmap != null) {
                    savedCoverPath = platformHelper.saveCoverBitmapToStorage(bitmap, title)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val newBook = BookEntity(
                bookHash = bookHash,
                profileId = profileId, // <-- 3. MULTI-TENANT PRIMARY KEY
                title = title,
                author = author,
                localUri = uri.toString(),
                seriesName = null,
                language = "en",
                coverPath = savedCoverPath,
                lastCfiLocation = null,
                progress = 0.0,
                addedDate = System.currentTimeMillis(),
                lastRead = 0L,
                updatedAt = System.currentTimeMillis(),
                isDirty = true
            )

            bookDao.insertBook(newBook)
            readiumManager.closePublication()
            return@withContext Result.success("Imported '$title' successfully!")

        } catch (e: Exception) {
            readiumManager.closePublication()
            return@withContext Result.failure(Exception(e.message ?: "Failed to import book."))
        }
    }

    suspend fun addDownloadedBook(bookFile: File, title: String, author: String, coverFile: File?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val profileId = vaultRepository.activeVaultId.first()

            if (bookDao.isBookExists(title, profileId)) {
                return@withContext Result.failure(Exception("Duplicate: A book titled '$title' already exists."))
            }

            val bookHash = bookFile.inputStream().use { stream ->
                calculateMd5Hash(stream)
            }

            val existingHash = bookDao.getBookByHash(bookHash, profileId)
            if (existingHash != null) {
                return@withContext Result.failure(Exception("Duplicate file detected in your library."))
            }

            val newBook = BookEntity(
                title = title,
                author = author,
                localUri = platformHelper.getFileUriString(bookFile),
                bookHash = bookHash,
                profileId = profileId, // <-- MULTI-TENANT PRIMARY KEY
                lastCfiLocation = null,
                coverPath = coverFile?.absolutePath,
                addedDate = System.currentTimeMillis(),
                lastRead = System.currentTimeMillis(),
                progress = 0.0,
                updatedAt = System.currentTimeMillis(),
                isDirty = true
            )

            bookDao.insertBook(newBook)
            Result.success("Saved '$title' to Library.")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Failed to add downloaded book."))
        }
    }

    private fun calculateMd5Hash(stream: InputStream): String {
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("MD5")
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}