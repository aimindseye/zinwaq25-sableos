package org.sableos.reader.engine.audio

/**
 * Audiobooks are local and offline: the only playable sources are `content:` (Storage Access Framework) and `file:`
 * URIs. The Reader has no INTERNET permission; this policy makes the same rule explicit in code, in the data source and
 * in the media session so a streaming URL can never be queued.
 */
object PlaybackSourcePolicy {
    private val localSchemes = setOf("content", "file")

    fun isLocal(uri: String): Boolean {
        val scheme = uri.substringBefore(':', missingDelimiterValue = "").trim().lowercase()
        return scheme in localSchemes && uri.length > scheme.length + 1
    }
}
