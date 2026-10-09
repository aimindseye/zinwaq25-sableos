package org.sableos.reader.capability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.model.PublicationKind

class ReaderCapabilitiesTest {
    @Test
    fun epubSupportsTheFullTextFeatureSet() {
        val c = ReaderCapabilities.of(PublicationKind.EPUB)
        assertTrue(c.textSearch && c.textHighlight && c.textToSpeech && c.bookmarks)
        assertTrue(c.contents && c.appearance && c.pageNavigation && c.progress && c.dictionaryLookup)
    }

    @Test
    fun pdfVersionTwoHasPageFeaturesOnly() {
        val c = ReaderCapabilities.of(PublicationKind.PDF)
        assertTrue(c.bookmarks && c.progress && c.pageNavigation && c.contents)
        assertFalse("PDF_TEXT_SEARCH=NO", c.textSearch)
        assertFalse("PDF_TEXT_HIGHLIGHT=NO", c.textHighlight)
        assertFalse("PDF_TTS=NO", c.textToSpeech)
        assertFalse(c.dictionaryLookup)
    }

    @Test
    fun comicsAndAudiobooksHaveNoTextFeatures() {
        listOf(PublicationKind.COMIC, PublicationKind.AUDIOBOOK).forEach {
            val c = ReaderCapabilities.of(it)
            assertFalse("$it search", c.textSearch)
            assertFalse("$it highlight", c.textHighlight)
            assertFalse("$it tts", c.textToSpeech)
        }
    }

    @Test
    fun everyKindHasBookmarksAndProgress() {
        PublicationKind.entries.forEach {
            val c = ReaderCapabilities.of(it)
            assertTrue("$it bookmarks", c.bookmarks)
            assertTrue("$it progress", c.progress)
        }
    }

    @Test
    fun onlyEpubDeclaresAnyTextFeature() {
        val withText = PublicationKind.entries.filter {
            val c = ReaderCapabilities.of(it)
            c.textSearch || c.textHighlight || c.textToSpeech
        }
        assertEquals(listOf(PublicationKind.EPUB), withText)
    }
}
