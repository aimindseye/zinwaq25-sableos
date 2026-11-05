package org.sableos.browser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInputTest {
    @Test
    fun addressesOpenAndEverythingElseSearches() {
        assertEquals("https://example.org", UrlInput.resolve(" example.org "))
        assertEquals("https://news.example.org/a?b=1", UrlInput.resolve("news.example.org/a?b=1"))
        assertEquals("http://example.org/x", UrlInput.resolve("http://example.org/x"))
        assertEquals("http://192.168.1.1", UrlInput.resolve("192.168.1.1"))
        assertEquals("http://localhost:8080/", UrlInput.resolve("localhost:8080/"))
        assertEquals("mailto:maya@example.org", UrlInput.resolve("mailto:maya@example.org"))
        assertEquals(UrlInput.SEARCH_PREFIX + "zinwa+q25+battery", UrlInput.resolve("zinwa q25 battery"))
        assertEquals(UrlInput.SEARCH_PREFIX + "q25", UrlInput.resolve("q25"))
        assertEquals(UrlInput.SEARCH_PREFIX + "c%2B%2B", UrlInput.resolve("c++"))
        assertNull(UrlInput.resolve("   "))
    }

    @Test
    fun theBarShowsAShortAddress() {
        assertEquals("example.org/page", UrlInput.display("https://www.example.org/page/"))
        assertEquals("", UrlInput.display(null))
    }

    @Test
    fun onlyWebLinksStayInThePage() {
        assertTrue(UrlInput.isWeb("HTTPS://example.org"))
        assertTrue(UrlInput.isWeb("about:blank"))
        assertFalse(UrlInput.isWeb("tel:5550100"))
        assertFalse(UrlInput.isWeb("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(UrlInput.isWeb("file:///sdcard/secret.txt"))
    }
}
