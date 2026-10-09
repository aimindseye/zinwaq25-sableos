package org.sableos.reader.library

import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.ReadingProgress

/**
 * The typed progress of a stored library row. Rows written by the v2 engines carry a typed locator; EPUBs last read
 * before Sable Reader v2 only have the legacy fraction and Readium location, which are wrapped as an EPUB locator so
 * every format shows progress through one model.
 */
object LegacyProgress {
    fun of(progressJson: String?, fraction: Double, legacyLocation: String?, lastReadAt: Long): ReadingProgress? {
        val typed = LocatorCodec.decode(progressJson)?.let { ReadingProgress(it, lastReadAt) }
        val legacy = fraction.takeIf { it > 0.0 }
            ?.let { ReadingProgress.epub(legacyLocation.orEmpty(), it.coerceIn(0.0, 1.0), lastReadAt) }
        return typed ?: legacy
    }
}
