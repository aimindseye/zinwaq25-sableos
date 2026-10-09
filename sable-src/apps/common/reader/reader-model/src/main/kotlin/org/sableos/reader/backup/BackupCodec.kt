package org.sableos.reader.backup

import kotlinx.serialization.json.Json
import org.sableos.reader.model.PublicationKind

/** Hard limits applied to a backup before and after parsing. Anything larger is refused, never truncated silently. */
data class BackupLimits(
    val maxChars: Int = DEFAULT_MAX_CHARS,
    val maxItems: Int = DEFAULT_MAX_ITEMS,
    val maxBookmarks: Int = DEFAULT_MAX_BOOKMARKS,
    val maxHighlights: Int = DEFAULT_MAX_HIGHLIGHTS,
    val maxCollections: Int = DEFAULT_MAX_COLLECTIONS,
    val maxTextLength: Int = DEFAULT_MAX_TEXT,
    val maxJsonLength: Int = DEFAULT_MAX_JSON,
) {
    companion object {
        const val DEFAULT_MAX_CHARS: Int = 64 * 1024 * 1024
        const val DEFAULT_MAX_ITEMS: Int = 100_000
        const val DEFAULT_MAX_BOOKMARKS: Int = 500_000
        const val DEFAULT_MAX_HIGHLIGHTS: Int = 500_000
        const val DEFAULT_MAX_COLLECTIONS: Int = 20_000
        const val DEFAULT_MAX_TEXT: Int = 1_000
        const val DEFAULT_MAX_JSON: Int = 64 * 1024
    }
}

enum class BackupRejection { NOT_A_BACKUP, NEWER_VERSION, TOO_LARGE, MALFORMED }

/** What was dropped while reading a backup, so the person can be told. */
data class BackupNotes(val droppedItems: Int = 0, val droppedRecords: Int = 0)

sealed interface BackupReadResult {
    data class Ok(val document: BackupDocument, val notes: BackupNotes) : BackupReadResult

    data class Rejected(val reason: BackupRejection) : BackupReadResult
}

/**
 * Reads and writes [BackupDocument] JSON. Reading is defensive: size and count limits first, then format and version,
 * then per-record validation (unknown kinds, malformed identities, orphans and duplicates are dropped and counted).
 */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val identity = Regex("^[0-9A-Za-z_-]{8,128}$")
    private val validKinds = PublicationKind.entries.map { it.stableValue }.toSet()

    fun encode(document: BackupDocument): String = json.encodeToString(BackupDocument.serializer(), document)

    fun decode(text: String?, limits: BackupLimits = BackupLimits()): BackupReadResult {
        val early = when {
            text.isNullOrBlank() -> BackupRejection.NOT_A_BACKUP
            text.length > limits.maxChars -> BackupRejection.TOO_LARGE
            else -> null
        }
        val parsed = if (early == null) {
            runCatching { json.decodeFromString(BackupDocument.serializer(), text.orEmpty()) }.getOrNull()
        } else {
            null
        }
        return when {
            early != null -> BackupReadResult.Rejected(early)
            parsed == null -> BackupReadResult.Rejected(BackupRejection.MALFORMED)
            else -> check(parsed, limits)
        }
    }

    private fun check(doc: BackupDocument, limits: BackupLimits): BackupReadResult = when {
        doc.format != BackupDocument.FORMAT_ID -> BackupReadResult.Rejected(BackupRejection.NOT_A_BACKUP)
        doc.version > BackupDocument.CURRENT_VERSION -> BackupReadResult.Rejected(BackupRejection.NEWER_VERSION)
        doc.version < BackupDocument.MIN_VERSION -> BackupReadResult.Rejected(BackupRejection.MALFORMED)
        exceeds(doc, limits) -> BackupReadResult.Rejected(BackupRejection.TOO_LARGE)
        else -> sanitize(migrate(doc), limits)
    }

    /** Older versions are upgraded here; version 1 is current, so this is the seam for the first future change. */
    private fun migrate(doc: BackupDocument): BackupDocument = doc

    private fun exceeds(doc: BackupDocument, limits: BackupLimits): Boolean =
        doc.items.size > limits.maxItems || doc.bookmarks.size > limits.maxBookmarks ||
            doc.highlights.size > limits.maxHighlights || doc.collections.size > limits.maxCollections

    private fun sanitize(doc: BackupDocument, limits: BackupLimits): BackupReadResult {
        val items = doc.items.filter { it.kind in validKinds && identity.matches(it.hash) }
            .distinctBy { it.hash }
            .map { it.copy(title = clean(it.title, limits), author = clean(it.author, limits)) }
            .filter { it.title.isNotEmpty() }
        val known = items.mapTo(HashSet()) { it.hash }
        val bookmarks = doc.bookmarks
            .filter { it.itemHash in known && it.id.isNotBlank() && fits(it.locatorJson, limits) }
            .distinctBy { it.id }
            .map { it.copy(label = clean(it.label, limits)) }
        val highlights = doc.highlights
            .filter { it.itemHash in known && it.id.isNotBlank() && fits(it.locatorJson, limits) }
            .distinctBy { it.id }
            .map { it.copy(text = it.text.take(limits.maxJsonLength)) }
        val collections = doc.collections.filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .map { c ->
                c.copy(name = clean(c.name, limits), itemHashes = c.itemHashes.filter { it in known }.distinct())
            }
            .filter { it.name.isNotEmpty() }
        val settings = doc.viewSettings.filter { it.itemHash in known && fits(it.settingsJson, limits) }
            .distinctBy { it.itemHash }
        val dropped = (doc.bookmarks.size - bookmarks.size) + (doc.highlights.size - highlights.size) +
            (doc.collections.size - collections.size) + (doc.viewSettings.size - settings.size)
        val clean = doc.copy(
            items = items,
            bookmarks = bookmarks,
            highlights = highlights,
            collections = collections,
            viewSettings = settings,
        )
        return BackupReadResult.Ok(clean, BackupNotes(doc.items.size - items.size, dropped))
    }

    private fun fits(json: String, limits: BackupLimits): Boolean = json.length <= limits.maxJsonLength

    /** Removes control characters, trims and truncates free text; the result is what the library will display. */
    private fun clean(text: String, limits: BackupLimits): String =
        text.filter { !it.isISOControl() }.trim().take(limits.maxTextLength)
}
