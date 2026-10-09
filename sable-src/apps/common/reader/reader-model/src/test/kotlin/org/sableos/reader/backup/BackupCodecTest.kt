package org.sableos.reader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.backup.BackupFixtures.document
import org.sableos.reader.backup.BackupFixtures.hash
import org.sableos.reader.backup.BackupFixtures.item

class BackupCodecTest {
    private fun ok(text: String?, limits: BackupLimits = BackupLimits()) =
        (BackupCodec.decode(text, limits) as? BackupReadResult.Ok)

    private fun rejected(text: String?, limits: BackupLimits = BackupLimits()) =
        (BackupCodec.decode(text, limits) as? BackupReadResult.Rejected)?.reason

    @Test
    fun aBackupRoundTripsAndCarriesItsVersion() {
        val original = document(listOf(item(1) { it.copy(favorite = true, progressJson = "{\"type\":\"page\"}") })) {
            it.copy(
                bookmarks = listOf(BackupBookmark("b1", hash(1), "{}", "Page 2", 5)),
                highlights = listOf(BackupHighlight("h1", hash(1), "{}", "quote", -256, "tag", 1, 2)),
                collections = listOf(BackupCollection("c1", "Shelf", 1, 0, listOf(hash(1)))),
                viewSettings = listOf(BackupViewSettings(hash(1), "{\"mode\":\"PAGED_RTL\"}", 9)),
            )
        }
        val text = BackupCodec.encode(original)
        assertTrue(text.contains("\"version\":1"))
        assertTrue(text.contains("\"format\":\"sable-reader-backup\""))
        val read = ok(text)
        assertEquals(original, read?.document)
        assertEquals(BackupNotes(), read?.notes)
    }

    @Test
    fun notBackupsAreRefusedWithAReason() {
        listOf(null, "", "   ").forEach { assertEquals(BackupRejection.NOT_A_BACKUP, rejected(it)) }
        assertEquals(BackupRejection.NOT_A_BACKUP, rejected("{\"format\":\"something-else\",\"version\":1}"))
        listOf("not json", "[1,2]", "{", "{\"version\":\"x\"}", BackupCodec.encode(document()).dropLast(5)).forEach {
            assertEquals(it, BackupRejection.MALFORMED, rejected(it))
        }
    }

    @Test
    fun newerVersionsAreRefusedAndOlderOnesAreNotInvented() {
        val future = BackupCodec.encode(document()).replace("\"version\":1", "\"version\":2")
        assertEquals(BackupRejection.NEWER_VERSION, rejected(future))
        val ancient = BackupCodec.encode(document()).replace("\"version\":1", "\"version\":0")
        assertEquals(BackupRejection.MALFORMED, rejected(ancient))
    }

    @Test
    fun sizeAndCountLimitsRefuseInsteadOfTruncating() {
        val text = BackupCodec.encode(document(List(5) { item(it) }))
        assertEquals(BackupRejection.TOO_LARGE, rejected(text, BackupLimits(maxChars = 100)))
        assertEquals(BackupRejection.TOO_LARGE, rejected(text, BackupLimits(maxItems = 4)))
        val marks = document(listOf(item(1))) {
            it.copy(bookmarks = List(3) { n -> BackupBookmark("b$n", hash(1), "{}", "x") })
        }
        assertEquals(BackupRejection.TOO_LARGE, rejected(BackupCodec.encode(marks), BackupLimits(maxBookmarks = 2)))
        assertTrue(ok(text, BackupLimits(maxItems = 5)) != null)
    }

    @Test
    fun invalidRecordsAreDroppedAndCounted() {
        val doc = document(
            listOf(
                item(1),
                item(2) { it.copy(kind = "TEXT") },
                item(3) { it.copy(hash = "short") },
                item(1) { it.copy(title = "Duplicate") },
                item(4) { it.copy(title = "   ") },
                item(5) { it.copy(title = "A\u0000B\u0007C  ", author = "x".repeat(5000)) },
            ),
        ) {
            it.copy(
                bookmarks = listOf(
                    BackupBookmark("ok", hash(1), "{}", "fine"),
                    BackupBookmark("ok", hash(1), "{}", "duplicate id"),
                    BackupBookmark("orphan", hash(99), "{}", "no such item"),
                    BackupBookmark("", hash(1), "{}", "blank id"),
                ),
                collections = listOf(BackupCollection("c", "Shelf", itemHashes = listOf(hash(1), hash(99), hash(1)))),
            )
        }
        val read = ok(BackupCodec.encode(doc))!!
        assertEquals(listOf(hash(1), hash(5)), read.document.items.map { it.hash })
        assertEquals("ABC", read.document.items.last().title)
        assertEquals(BackupLimits.DEFAULT_MAX_TEXT, read.document.items.last().author.length)
        assertEquals(listOf("ok"), read.document.bookmarks.map { it.id })
        assertEquals(listOf(hash(1)), read.document.collections.single().itemHashes)
        assertEquals(4, read.notes.droppedItems)
        assertEquals(3, read.notes.droppedRecords)
    }

    @Test
    fun unknownKeysFromANewerWriterWithinTheSameVersionAreIgnored() {
        val text = BackupCodec.encode(document(listOf(item(1)))).replaceFirst("{", "{\"futureField\":{\"a\":1},")
        assertEquals(1, ok(text)?.document?.items?.size)
    }

    @Test
    fun theFormatCannotCarryFilesLocationsOrCredentials() {
        val fields = listOf(
            BackupItem::class, BackupCollection::class, BackupBookmark::class, BackupHighlight::class,
            BackupViewSettings::class, BackupDocument::class,
        ).flatMap { type -> type.java.declaredFields.map { it.name.lowercase() } }
        val forbidden = listOf("uri", "path", "url", "token", "password", "secret", "cover", "file", "key", "account")
        forbidden.forEach { word ->
            assertFalse("a backup field mentions '$word'", fields.any { it.contains(word) })
        }
    }
}
