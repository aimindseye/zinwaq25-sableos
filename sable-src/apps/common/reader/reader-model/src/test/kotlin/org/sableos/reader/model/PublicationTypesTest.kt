package org.sableos.reader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicationTypesTest {
    @Test
    fun knownMediaTypesWin() {
        assertEquals(PublicationKind.EPUB, PublicationTypes.detect("application/epub+zip", "x.bin"))
        assertEquals(PublicationKind.PDF, PublicationTypes.detect("application/pdf", null))
        assertEquals(PublicationKind.PDF, PublicationTypes.detect("Application/PDF; charset=binary", null))
    }

    @Test
    fun genericOrMissingTypesFallBackToTheExtension() {
        assertEquals(PublicationKind.PDF, PublicationTypes.detect("application/octet-stream", "Book.PDF"))
        assertEquals(PublicationKind.EPUB, PublicationTypes.detect(null, "novel.epub"))
        assertEquals(PublicationKind.EPUB, PublicationTypes.detect("", "a.b.epub"))
    }

    @Test
    fun unrelatedTypesAreNotOwnedByTheReader() {
        assertNull(PublicationTypes.detect("text/plain", "notes.pdf"))
        assertNull(PublicationTypes.detect(null, "notes.txt"))
        assertNull(PublicationTypes.detect(null, null))
        assertNull(PublicationTypes.detect("application/octet-stream", "archive.zip"))
    }

    @Test
    fun comicArchivesAreRecognisedByTypeOrCbzExtension() {
        assertEquals(PublicationKind.COMIC, PublicationTypes.detect("application/vnd.comicbook+zip", null))
        assertEquals(PublicationKind.COMIC, PublicationTypes.detect("application/x-cbz", "x.bin"))
        assertEquals(PublicationKind.COMIC, PublicationTypes.detect("application/octet-stream", "Vol 1.CBZ"))
        assertEquals(PublicationKind.COMIC, PublicationTypes.detect("application/zip", "vol.cbz"))
        assertNull(PublicationTypes.detect("application/zip", "backup.zip"))
        assertNull(PublicationTypes.detect("application/zip", null))
    }

    @Test
    fun audioFilesAreAudiobooksByTypeOrExtension() {
        assertEquals(PublicationKind.AUDIOBOOK, PublicationTypes.detect("audio/mpeg", null))
        assertEquals(PublicationKind.AUDIOBOOK, PublicationTypes.detect("audio/mp4", "x.bin"))
        assertEquals(PublicationKind.AUDIOBOOK, PublicationTypes.detect("Audio/X-M4B", null))
        assertEquals(PublicationKind.AUDIOBOOK, PublicationTypes.detect("application/octet-stream", "Book.M4B"))
        assertEquals(PublicationKind.AUDIOBOOK, PublicationTypes.detect(null, "track.opus"))
        assertNull(PublicationTypes.detect("video/mp4", "clip.mp4"))
        assertNull(PublicationTypes.detect("application/octet-stream", "movie.mkv"))
    }

    @Test
    fun defaultFormatsAreStable() {
        assertEquals("application/vnd.comicbook+zip", PublicationTypes.defaultFormat(PublicationKind.COMIC))
        assertEquals("application/pdf", PublicationTypes.defaultFormat(PublicationKind.PDF))
        assertEquals("application/epub+zip", PublicationTypes.defaultFormat(PublicationKind.EPUB))
        assertNull(PublicationTypes.defaultFormat(PublicationKind.AUDIOBOOK))
    }
}
