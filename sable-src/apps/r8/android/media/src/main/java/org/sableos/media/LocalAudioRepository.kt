package org.sableos.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject

internal data class LocalAudioItem(
    val uri: String,
    val sourceKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

internal class LocalAudioRepository(
    private val context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "sable_media",
            Context.MODE_PRIVATE,
        )

    fun loadItems(): List<LocalAudioItem> {
        val raw = preferences.getString(KEY_LOCAL_AUDIO, null) ?: return emptyList()
        val decoded =
            runCatching {
                val array = JSONArray(raw)
                List(array.length()) { index ->
                    decodeItem(array.optJSONObject(index))
                }.filterNotNull()
            }.getOrDefault(emptyList())

        val deduplicated =
            decoded
                .distinctBy { it.sourceKey }
                .sortedBy { it.title.lowercase() }

        if (deduplicated != decoded) {
            persist(deduplicated)
        }

        return deduplicated
    }

    fun addUris(
        current: List<LocalAudioItem>,
        uris: List<Uri>,
    ): List<LocalAudioItem> {
        val bySource =
            current
                .associateBy { it.sourceKey }
                .toMutableMap()

        uris.forEach { uri ->
            val item = inspect(uri)
            if (item.sourceKey !in bySource) {
                bySource[item.sourceKey] = item
            }
        }

        val updated =
            bySource.values
                .sortedBy { it.title.lowercase() }

        persist(updated)
        return updated
    }

    fun remove(
        current: List<LocalAudioItem>,
        uri: String,
    ): List<LocalAudioItem> {
        val updated = current.filterNot { it.uri == uri }
        persist(updated)
        return updated
    }

    private fun decodeItem(item: JSONObject?): LocalAudioItem? =
        item?.let { json ->
            val uriValue = json.optString("uri")
            val title = json.optString("title")

            if (uriValue.isBlank() || title.isBlank()) {
                null
            } else {
                val parsedUri = Uri.parse(uriValue)
                val durationMs = json.optLong("durationMs", 0L)
                val sourceKey =
                    json
                        .optString("sourceKey")
                        .takeIf { it.isNotBlank() }
                        ?: sourceKeyFor(
                            uri = parsedUri,
                            fallbackName = title,
                            fallbackSize = UNKNOWN_FILE_SIZE,
                            durationMs = durationMs,
                        )

                LocalAudioItem(
                    uri = uriValue,
                    sourceKey = sourceKey,
                    title = title,
                    artist = json.optString("artist", "Unknown artist"),
                    album = json.optString("album", "Local audio"),
                    durationMs = durationMs,
                )
            }
        }

    private fun inspect(uri: Uri): LocalAudioItem {
        val document = queryDocumentInfo(uri)
        val metadata = readMetadata(uri, document.displayName)

        return LocalAudioItem(
            uri = uri.toString(),
            sourceKey =
                sourceKeyFor(
                    uri = uri,
                    fallbackName = document.displayName ?: metadata.title,
                    fallbackSize = document.size,
                    durationMs = metadata.durationMs,
                ),
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            durationMs = metadata.durationMs,
        )
    }

    private fun queryDocumentInfo(uri: Uri): DocumentInfo {
        var displayName =
            uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.ifBlank { null }
        var size = UNKNOWN_FILE_SIZE

        context.contentResolver
            .query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        displayName =
                            cursor
                                .getString(nameIndex)
                                ?.ifBlank { displayName }
                    }

                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }

        return DocumentInfo(
            displayName = displayName,
            size = size,
        )
    }

    private fun readMetadata(
        uri: Uri,
        fallbackName: String?,
    ): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            AudioMetadata(
                title =
                    metadataValue(
                        retriever = retriever,
                        key = MediaMetadataRetriever.METADATA_KEY_TITLE,
                    ) ?: fallbackName ?: "Local audio",
                artist =
                    metadataValue(
                        retriever = retriever,
                        key = MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    ) ?: "Unknown artist",
                album =
                    metadataValue(
                        retriever = retriever,
                        key = MediaMetadataRetriever.METADATA_KEY_ALBUM,
                    ) ?: "Local audio",
                durationMs =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?: 0L,
            )
        } catch (_: RuntimeException) {
            AudioMetadata(
                title = fallbackName ?: "Local audio",
                artist = "Unknown artist",
                album = "Local audio",
                durationMs = 0L,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun metadataValue(
        retriever: MediaMetadataRetriever,
        key: Int,
    ): String? =
        retriever
            .extractMetadata(key)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun sourceKeyFor(
        uri: Uri,
        fallbackName: String,
        fallbackSize: Long,
        durationMs: Long,
    ): String {
        val documentId =
            runCatching {
                DocumentsContract.getDocumentId(uri)
            }.getOrNull()

        if (!documentId.isNullOrBlank()) {
            return "document:${uri.authority.orEmpty()}:$documentId"
        }

        return buildString {
            append("fallback:")
            append(uri.authority.orEmpty())
            append(':')
            append(fallbackName.lowercase())
            append(':')
            append(fallbackSize)
            append(':')
            append(durationMs)
        }
    }

    private fun persist(items: List<LocalAudioItem>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("uri", item.uri)
                    .put("sourceKey", item.sourceKey)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("album", item.album)
                    .put("durationMs", item.durationMs),
            )
        }

        preferences
            .edit()
            .putString(KEY_LOCAL_AUDIO, array.toString())
            .apply()
    }

    private data class DocumentInfo(
        val displayName: String?,
        val size: Long,
    )

    private data class AudioMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
    )

    private companion object {
        const val KEY_LOCAL_AUDIO = "local_audio_items"
        const val UNKNOWN_FILE_SIZE = -1L
    }
}
