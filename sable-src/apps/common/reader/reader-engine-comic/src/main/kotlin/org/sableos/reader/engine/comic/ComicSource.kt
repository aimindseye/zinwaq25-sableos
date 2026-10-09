package org.sableos.reader.engine.comic

import java.io.Closeable

/** An opened comic container (CBZ file, image folder, SAF tree). All page access is bounded by [limits]. */
interface ComicSource : Closeable {
    val limits: ComicLimits
    val catalog: ComicCatalog

    /** Parsed `ComicInfo.xml`, or null when absent or rejected. A bad ComicInfo never prevents reading. */
    val info: ComicInfo?

    val pageCount: Int get() = catalog.pages.size

    /** Raw encoded bytes of page [index]. Throws [ComicPageException] for pages that cannot be read safely. */
    fun readPage(index: Int): ByteArray
}
