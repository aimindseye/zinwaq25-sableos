package org.vaachak.reader.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpdsUrlUtilsTest {

    @Test
    fun cleanCatalogUrl_addsHttpsAndOpds_forBareHostPath() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("mac-mini.tail1f3687.ts.net/calibre")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_addsOpds_forTrailingSlash() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("mac-mini.tail1f3687.ts.net/calibre/")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_keepsExistingHttpsOpdsUrl() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("https://mac-mini.tail1f3687.ts.net/calibre/opds")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_upgradesHttpToHttps() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("http://mac-mini.tail1f3687.ts.net/calibre/opds")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_doesNotAppendOpds_forGutendexBooksUrl() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("https://gutendex.com/books")
        assertEquals(
            "https://gutendex.com/books",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_doesNotAppendOpds_forXmlFeed() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("https://example.com/feed.xml")
        assertEquals(
            "https://example.com/feed.xml",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_doesNotAppendOpds_forDotOpdsUrl() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("https://example.com/catalog.opds")
        assertEquals(
            "https://example.com/catalog.opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_trimsWhitespace_beforeNormalizing() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("  mac-mini.tail1f3687.ts.net/calibre  ")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun cleanCatalogUrl_preservesHttps_forNonOpdsSpecificUrl() {
        val actual = OpdsUrlUtils.cleanCatalogUrl("https://example.com/library")
        assertEquals(
            "https://example.com/library/opds",
            actual
        )
    }

    @Test
    fun normalizeHttpsUrl_acceptsValidHttpsUrl() {
        val actual = OpdsUrlUtils.normalizeHttpsUrl("https://mac-mini.tail1f3687.ts.net/calibre/opds")
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            actual
        )
    }

    @Test
    fun normalizeHttpsUrl_trimsWhitespace() {
        val actual = OpdsUrlUtils.normalizeHttpsUrl("  https://example.com/opds  ")
        assertEquals(
            "https://example.com/opds",
            actual
        )
    }

    @Test
    fun normalizeHttpsUrl_rejectsHttpUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            OpdsUrlUtils.normalizeHttpsUrl("http://example.com/opds")
        }
    }

    @Test
    fun normalizeHttpsUrl_rejectsInvalidUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            OpdsUrlUtils.normalizeHttpsUrl("not a url")
        }
    }

    @Test
    fun normalizeHttpsUrl_rejectsBlankUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            OpdsUrlUtils.normalizeHttpsUrl("   ")
        }
    }
}