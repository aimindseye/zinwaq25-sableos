package org.sableos.reader.engine.comic

import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses `ComicInfo.xml` with the platform Java XML parser, never with a third-party library. The file comes from an
 * untrusted archive, so it is checked as bytes first: a DOCTYPE or entity declaration (the carrier of XXE and entity
 * expansion attacks) is refused outright, whatever the encoding, and the size is capped. Parser features that
 * further harden external access are applied where the platform supports them. Any problem yields null.
 */
object ComicInfoParser {
    private const val DOCTYPE = "<!doctype"
    private const val ENTITY = "<!entity"
    private const val FRONT_COVER = "frontcover"
    private const val MAX_FIELD_LENGTH = 4_000
    private val hardeningFeatures = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        XMLConstants.FEATURE_SECURE_PROCESSING to true,
    )

    fun parse(bytes: ByteArray, limits: ComicLimits = ComicLimits()): ComicInfo? {
        if (bytes.isEmpty() || bytes.size > limits.maxComicInfoBytes || containsDeclaration(bytes)) return null
        return runCatching { read(bytes) }.getOrNull()
    }

    /** NUL bytes are dropped first so UTF-16 encoded declarations are caught by the same ASCII scan. */
    private fun containsDeclaration(bytes: ByteArray): Boolean {
        val ascii = String(bytes.filter { it != 0.toByte() }.toByteArray(), Charsets.ISO_8859_1).lowercase()
        return ascii.contains(DOCTYPE) || ascii.contains(ENTITY)
    }

    private fun read(bytes: ByteArray): ComicInfo? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            isValidating = false
        }
        hardeningFeatures.forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
        val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> null } }
        val root = builder.parse(ByteArrayInputStream(bytes)).documentElement
        return if (root?.tagName == "ComicInfo") toInfo(root) else null
    }

    private fun toInfo(root: Element): ComicInfo {
        return ComicInfo(
            title = root.text("Title"),
            series = root.text("Series"),
            number = root.text("Number"),
            volume = root.text("Volume")?.toIntOrNull(),
            writer = root.text("Writer"),
            publisher = root.text("Publisher"),
            summary = root.text("Summary"),
            year = root.text("Year")?.toIntOrNull(),
            pageCount = root.text("PageCount")?.toIntOrNull()?.takeIf { it > 0 },
            format = root.text("Format"),
            manga = mangaOf(root.text("Manga")),
            coverPage = coverPageOf(root),
        )
    }

    private fun Element.text(tag: String): String? {
        var child: Node? = firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == tag) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_FIELD_LENGTH)
            }
            child = child.nextSibling
        }
        return null
    }

    private fun mangaOf(value: String?): MangaFlag = when (value?.lowercase()) {
        "yesandrighttoleft" -> MangaFlag.YES_RIGHT_TO_LEFT
        "yes" -> MangaFlag.YES
        "no" -> MangaFlag.NO
        else -> MangaFlag.UNKNOWN
    }

    private fun coverPageOf(root: Element): Int? {
        val pages = root.getElementsByTagName("Page")
        for (i in 0 until pages.length) {
            val page = pages.item(i) as? Element ?: continue
            if (page.getAttribute("Type").lowercase() == FRONT_COVER) return page.getAttribute("Image").toIntOrNull()
        }
        return null
    }

}
