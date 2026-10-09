package org.vaachak.reader.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.local.SyncVaultDao
import org.vaachak.reader.core.data.remote.SyncApi
import org.vaachak.reader.core.data.remote.dto.LoginRequest
import org.vaachak.reader.core.data.remote.dto.RegisterRequest
import org.vaachak.reader.core.data.remote.dto.SyncDto
import org.vaachak.reader.core.domain.model.SyncVaultEntity
import org.vaachak.reader.core.security.CryptoManager
import org.vaachak.reader.core.security.EncryptedPayload
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao,
    private val syncVaultDao: SyncVaultDao,
    private val cryptoManager: CryptoManager,
    private val syncApi: SyncApi,
    private val settingsRepo: SettingsRepository,
    private val vaultRepository: VaultRepository
) {

    // ==========================================
    // 1. END-TO-END ENCRYPTED VAULT SYNC LOGIC
    // ==========================================

    suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val useLocal = settingsRepo.useLocalServer.first()
            val rawUrl = if (useLocal) settingsRepo.localServerUrl.first() else settingsRepo.syncCloudUrl.first()
            val targetUrl = rawUrl.trim().removeSuffix("/")
            syncApi.setBaseUrl(targetUrl)

            val profileId = vaultRepository.activeVaultId.first()
            val deviceId = settingsRepo.deviceName.first()
            val lastSync = settingsRepo.lastSyncTimestamp.first()

            val username = settingsRepo.syncUsername.first()
            val password = settingsRepo.syncPassword.first()

            if (username.isBlank() || password.isBlank()) {
                throw IllegalStateException("Cannot sync: User is not logged in.")
            }

            // 1. Stage Local Changes (NOW PASSES CREDENTIALS)
            stageLocalChanges(profileId, username, password)

            // 2. Fetch all dirty records ready for upload
            val pendingUploads = syncVaultDao.getPendingUploads(profileId).first()

            // 3. Convert Android Entities to Cloudflare DTOs
            val networkEntries = pendingUploads.map { entity ->
                SyncDto.VaultEntry(
                    entryKey = entity.entryKey,
                    encryptedPayload = "${entity.iv}:${entity.encryptedBlob}",
                    updatedAt = entity.remoteUpdatedAt,
                    deleted = entity.isDeleted
                )
            }

            // 4. Build the Request
            val request = SyncDto.SyncRequest(
                auth = SyncDto.AuthDto(username, password),
                deviceId = deviceId,
                lastSyncTimestamp = lastSync,
                vaultEntries = networkEntries
            )

            // 5. Execute Network Call
            val response = syncApi.syncVault(request).getOrThrow()

            // 6. Push successful! Clear local 'needsPush' flags
            if (pendingUploads.isNotEmpty()) {
                syncVaultDao.markAsSynced(profileId, pendingUploads.map { it.entryKey })
            }

            // 7. Process Incoming Data from Cloudflare (NOW PASSES CREDENTIALS)
            processRemoteChanges(profileId, response.vaultEntries, username, password)

            // 8. Update sync anchor
            settingsRepo.setLastSyncTimestamp(response.newSyncTimestamp)
        }
    }

    private suspend fun stageLocalChanges(profileId: String, syncUser: String, syncPass: String) {
        val dirtyBooks = bookDao.getBooksModifiedSince(profileId, 0L).filter { it.isDirty }

        for (book in dirtyBooks) {
            val highlights = highlightDao.getHighlightsForBook(book.bookHash, profileId).first()

            val payloadObj = SyncDto.CleartextPayload(
                bookHash = book.bookHash,
                progress = book.progress,
                lastCfiLocation = book.lastCfiLocation,
                updatedAt = book.updatedAt,
                highlights = highlights
            )

            // Encrypt using the new PBKDF2 method
            val jsonString = Json.encodeToString(payloadObj)
            val encrypted = cryptoManager.encrypt(jsonString, syncPass, syncUser)

            // Stage in Queue
            val vaultEntity = SyncVaultEntity(
                profileId = profileId,
                entryKey = "book_${book.bookHash}",
                encryptedBlob = encrypted.ciphertext,
                iv = encrypted.iv,
                remoteUpdatedAt = book.updatedAt,
                needsPush = true,
                isDeleted = false
            )
            syncVaultDao.upsertSyncPayload(vaultEntity)

            // Clear dirty flag on BookDao
            bookDao.updateBookMetadataFromSync(book.bookHash, profileId, book.lastCfiLocation ?: "", book.progress, book.updatedAt)
        }
    }

    private suspend fun processRemoteChanges(
        profileId: String,
        remoteEntries: List<SyncDto.VaultEntry>,
        syncUser: String,
        syncPass: String
    ) {
        for (entry in remoteEntries) {
            val parts = entry.encryptedPayload.split(":")
            if (parts.size != 2) continue

            val iv = parts[0]
            val ciphertext = parts[1]

            val vaultEntity = SyncVaultEntity(
                profileId = profileId,
                entryKey = entry.entryKey,
                encryptedBlob = ciphertext,
                iv = iv,
                remoteUpdatedAt = entry.updatedAt,
                needsPush = false,
                isDeleted = entry.deleted
            )
            syncVaultDao.upsertSyncPayload(vaultEntity)

            if (entry.entryKey.startsWith("book_") && !entry.deleted) {
                try {
                    // Decrypt using the new PBKDF2 method
                    val decryptedJson = cryptoManager.decrypt(EncryptedPayload(ciphertext, iv), syncPass, syncUser)
                    val cleartext = Json.decodeFromString<SyncDto.CleartextPayload>(decryptedJson)

                    bookDao.updateProgressFromCloud(
                        bookHash = cleartext.bookHash,
                        profileId = profileId,
                        progress = cleartext.progress,
                        cfiLocation = cleartext.lastCfiLocation ?: "",
                        timestamp = cleartext.updatedAt
                    )

                    cleartext.highlights.forEach { highlight ->
                        highlightDao.insertHighlight(highlight.copy(profileId = profileId))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decrypt payload for ${entry.entryKey}")
                }
            }
        }
    }

    // ==========================================
    // 2. LEGACY AUTHENTICATION & ROUTING LOGIC
    // ==========================================

    suspend fun login(user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val useLocal = settingsRepo.useLocalServer.first()
            val rawUrl = if (useLocal) settingsRepo.localServerUrl.first() else settingsRepo.syncCloudUrl.first()

            val targetUrl = rawUrl.trim().removeSuffix("/")
            if (targetUrl.isBlank()) return@withContext Result.failure(Exception("Server URL is blank"))

            syncApi.setBaseUrl(targetUrl)
            val loginRequest = LoginRequest(username = user, passwordHash = pass)

            syncApi.login(loginRequest).getOrThrow()

            val deviceName = settingsRepo.deviceName.first()
            settingsRepo.updateSyncProfile(user, pass, if (deviceName.isNullOrBlank()) "My Device" else deviceName)
            settingsRepo.setLastSyncTimestamp(0L)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Login failed")
            Result.failure(e)
        }
    }

    suspend fun register(user: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val useLocal = settingsRepo.useLocalServer.first()
            val rawUrl = if (useLocal) settingsRepo.localServerUrl.first() else settingsRepo.syncCloudUrl.first()

            val targetUrl = rawUrl.trim().removeSuffix("/")
            if (targetUrl.isBlank()) return@withContext Result.failure(Exception("Server URL is blank"))

            syncApi.setBaseUrl(targetUrl)
            val registerRequest = RegisterRequest(username = user, passwordHash = pass)

            syncApi.register(registerRequest).getOrThrow()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Registration failed")
            Result.failure(e)
        }
    }

    suspend fun testConnection(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val targetUrl = url.trim().removeSuffix("/")
            if (targetUrl.isBlank()) return@withContext Result.failure(Exception("Server URL is blank"))

            syncApi.setBaseUrl(targetUrl)
            syncApi.testConnection().getOrThrow()

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Test connection failed")
            Result.failure(e)
        }
    }
}