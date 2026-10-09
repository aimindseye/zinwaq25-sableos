package org.sableos.reader.core.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.sableos.reader.backup.BackupBookmark
import org.sableos.reader.backup.BackupCodec
import org.sableos.reader.backup.BackupCollection
import org.sableos.reader.backup.BackupDocument
import org.sableos.reader.backup.BackupHighlight
import org.sableos.reader.backup.BackupLimits
import org.sableos.reader.backup.BackupReadResult
import org.sableos.reader.backup.BackupRejection
import org.sableos.reader.backup.BackupViewSettings
import org.sableos.reader.backup.ExistingItem
import org.sableos.reader.backup.ExistingState
import org.sableos.reader.backup.IdScope
import org.sableos.reader.backup.RestorePlan
import org.sableos.reader.backup.RestorePlanner
import org.sableos.reader.core.data.local.CollectionDao
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.core.domain.model.CollectionEntity
import org.sableos.reader.core.domain.model.CollectionItemEntity
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.sableos.reader.core.library.toBackupItem
import org.sableos.reader.core.library.toUnlinkedEntity
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.HighlightEntity

/** Counts shown after a backup is written. */
data class BackupSummary(val items: Int, val bookmarks: Int, val highlights: Int, val collections: Int)

/** Counts shown after a restore. [needFile] titles were added without a file and must be relinked. */
data class RestoreReport(
    val addedItems: Int,
    val needFile: Int,
    val progressUpdated: Int,
    val bookmarks: Int,
    val highlights: Int,
    val collections: Int,
    val viewSettings: Int,
    val dropped: Int,
)

/** Every table a backup reads and a restore writes, grouped so the repository has one dependency for them. */
class BackupStores @Inject constructor(
    val bookDao: BookDao,
    val highlightDao: HighlightDao,
    val collectionDao: CollectionDao,
    val bookmarkDao: ItemBookmarkDao,
    val settingsDao: ItemViewSettingsDao,
)

class BackupException(val rejection: BackupRejection?, message: String) : Exception(message)

/**
 * Versioned, metadata-only library backup through the Storage Access Framework: the person picks where the file is
 * written or read; the app never chooses a location, never uploads and never touches the network. A restore only adds
 * or moves data forward (see `RestorePlanner`), can be repeated safely, and adds titles without files as "needs file".
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    stores: BackupStores,
    private val vaultRepository: VaultRepository,
) {
    private val bookDao = stores.bookDao
    private val highlightDao = stores.highlightDao
    private val collectionDao = stores.collectionDao
    private val bookmarkDao = stores.bookmarkDao
    private val settingsDao = stores.settingsDao

    suspend fun export(target: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val profileId = vaultRepository.activeVaultId.first()
            val document = snapshot(profileId)
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw BackupException(null, "Could not write the backup file.")
            stream.use { it.write(BackupCodec.encode(document).toByteArray(Charsets.UTF_8)) }
            BackupSummary(
                document.items.size,
                document.bookmarks.size,
                document.highlights.size,
                document.collections.size,
            )
        }.recoverCatching { failure -> throw describe(failure) }
    }

    suspend fun restore(source: Uri): Result<RestoreReport> = withContext(Dispatchers.IO) {
        runCatching {
            val profileId = vaultRepository.activeVaultId.first()
            val result = BackupCodec.decode(readText(source))
            val read = result as? BackupReadResult.Ok
            if (read == null) {
                val reason = (result as BackupReadResult.Rejected).reason
                throw BackupException(reason, rejectionText(reason))
            }
            val plan = RestorePlanner.plan(existing(profileId), read.document)
            apply(plan, profileId)
            report(plan, read.notes.droppedItems + read.notes.droppedRecords)
        }.recoverCatching { failure -> throw describe(failure) }
    }

    private suspend fun snapshot(profileId: String): BackupDocument {
        val books = bookDao.getAllBooksOnce(profileId)
        val memberships = collectionDao.getMembershipsOnce(profileId).groupBy({ it.collectionId }, { it.bookHash })
        return BackupDocument(
            createdAt = System.currentTimeMillis(),
            items = books.map { it.toBackupItem() },
            collections = collectionDao.getCollectionsOnce(profileId).map {
                BackupCollection(
                    id = IdScope.canonical(profileId, it.collectionId),
                    name = it.name,
                    createdAt = it.createdAt,
                    sortOrder = it.sortOrder,
                    itemHashes = memberships[it.collectionId].orEmpty(),
                )
            },
            bookmarks = bookmarkDao.getAllOnce(profileId).map {
                val id = IdScope.canonical(profileId, it.bookmarkId)
                BackupBookmark(id, it.bookHash, it.locatorJson, it.label, it.createdAt)
            },
            highlights = highlightDao.getAllOnce(profileId).filter { !it.isDeleted }.map {
                BackupHighlight(
                    id = IdScope.canonical(profileId, it.id),
                    itemHash = it.bookHashId,
                    locatorJson = it.locatorJson,
                    text = it.text,
                    color = it.color,
                    tag = it.tag,
                    created = it.created,
                    updatedAt = it.updatedAt,
                )
            },
            viewSettings = settingsDao.getAllOnce(profileId).map {
                BackupViewSettings(it.bookHash, it.settingsJson, it.updatedAt)
            },
        )
    }

    private suspend fun existing(profileId: String): ExistingState = ExistingState(
        items = bookDao.getAllBooksOnce(profileId).map { ExistingItem(it.bookHash, it.lastRead, it.favorite) },
        bookmarkIds = bookmarkDao.getAllOnce(profileId).mapTo(HashSet()) {
            IdScope.canonical(profileId, it.bookmarkId)
        },
        highlightIds = highlightDao.getAllOnce(profileId).mapTo(HashSet()) { IdScope.canonical(profileId, it.id) },
        collectionIds = collectionDao.getCollectionsOnce(profileId).mapTo(HashSet()) {
            IdScope.canonical(profileId, it.collectionId)
        },
        memberships = collectionDao.getMembershipsOnce(profileId).mapTo(HashSet()) {
            IdScope.canonical(profileId, it.collectionId) to it.bookHash
        },
        settingsUpdatedAt = settingsDao.getAllOnce(profileId).associate { it.bookHash to it.updatedAt },
    )

    /**
     * Applies the plan in dependency order. Every step is an upsert or an ignore, so an interrupted restore can simply
     * run again.
     */
    private suspend fun apply(plan: RestorePlan, profileId: String) {
        plan.newItems.forEach { bookDao.upsertItem(it.toUnlinkedEntity(profileId)) }
        plan.progressUpdates.forEach {
            bookDao.updateTypedProgress(it.hash, profileId, it.progress, it.progressJson, it.finished, it.lastOpenedAt)
            it.lastLocation?.let { location -> bookDao.setLastLocation(it.hash, profileId, location) }
        }
        plan.favoriteHashes.forEach { bookDao.setFavorite(it, profileId, true) }
        plan.newCollections.forEach {
            collectionDao.upsertCollection(
                CollectionEntity(IdScope.scoped(profileId, it.id), profileId, it.name, it.createdAt, it.sortOrder),
            )
        }
        plan.memberships.forEach { (collection, hash) ->
            collectionDao.addItem(CollectionItemEntity(IdScope.scoped(profileId, collection), hash, profileId))
        }
        plan.bookmarks.forEach {
            val id = IdScope.scoped(profileId, it.id)
            val entity = ItemBookmarkEntity(id, it.itemHash, profileId, it.locatorJson, it.label, it.createdAt)
            bookmarkDao.upsertBookmark(entity)
        }
        plan.highlights.forEach {
            highlightDao.insertHighlight(
                HighlightEntity(
                    id = IdScope.scoped(profileId, it.id),
                    bookHashId = it.itemHash,
                    profileId = profileId,
                    locatorJson = it.locatorJson,
                    text = it.text,
                    color = it.color,
                    tag = it.tag,
                    created = it.created,
                    updatedAt = it.updatedAt,
                ),
            )
        }
        plan.viewSettings.forEach {
            settingsDao.upsert(ItemViewSettingsEntity(it.itemHash, profileId, it.settingsJson, it.updatedAt))
        }
    }

    private fun report(plan: RestorePlan, dropped: Int) = RestoreReport(
        addedItems = plan.newItems.size,
        needFile = plan.newItems.size,
        progressUpdated = plan.progressUpdates.size,
        bookmarks = plan.bookmarks.size,
        highlights = plan.highlights.size,
        collections = plan.newCollections.size,
        viewSettings = plan.viewSettings.size,
        dropped = dropped,
    )

    /** Reads the picked file as UTF-8 text, refusing anything beyond the backup size limit. */
    private fun readText(source: Uri): String {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw BackupException(null, "Could not read the backup file.")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(READ_CHUNK)
            while (true) {
                val n = reader.read(buffer)
                if (n < 0) break
                out.append(buffer, 0, n)
                if (out.length > BackupLimits.DEFAULT_MAX_CHARS) {
                    throw BackupException(BackupRejection.TOO_LARGE, TOO_LARGE_TEXT)
                }
            }
            out.toString()
        }
    }

    private fun rejectionText(reason: BackupRejection): String = when (reason) {
        BackupRejection.NEWER_VERSION ->
            "This backup was made by a newer version of Sable Reader. Update the app to restore it."
        BackupRejection.TOO_LARGE -> TOO_LARGE_TEXT
        BackupRejection.MALFORMED -> "This backup file is damaged and cannot be read."
        BackupRejection.NOT_A_BACKUP -> "This is not a Sable Reader backup."
    }

    private fun describe(failure: Throwable): Throwable = when (failure) {
        is BackupException -> failure
        is IOException ->
            BackupException(null, "The backup file could not be read or written: ${failure.message.orEmpty()}")
        else -> failure
    }

    private companion object {
        const val READ_CHUNK = 64 * 1024
        const val TOO_LARGE_TEXT = "This backup is larger than Sable Reader will restore."
    }
}
