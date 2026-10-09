package org.sableos.reader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic 20 000-title backups. Generous bounds catch accidental quadratic work, they are not benchmarks. */
class LargeBackupTest {
    private val big = BackupFixtures.large(items = 20_000, bookmarksPerItem = 3)

    private fun <T> timed(limitMs: Long, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${elapsed}ms, limit ${limitMs}ms", elapsed < limitMs)
        return result
    }

    @Test
    fun encodeDecodeAndPlanScaleToTwentyThousandTitles() {
        val text = timed(15_000) { BackupCodec.encode(big) }
        val read = timed(15_000) { BackupCodec.decode(text) as BackupReadResult.Ok }
        assertEquals(big.items.size, read.document.items.size)
        assertEquals(60_000, read.document.bookmarks.size)
        assertEquals(BackupNotes(), read.notes)
        val plan = timed(10_000) { RestorePlanner.plan(ExistingState(), read.document) }
        assertEquals(20_000, plan.newItems.size)
        assertEquals(60_000, plan.bookmarks.size)
        assertEquals(1_000, plan.newCollections.size)
        assertEquals(20_000, plan.memberships.size)
    }

    @Test
    fun aRestoreAgainstAnAlreadyRestoredLibraryIsEmptyAndFast() {
        val existing = ExistingState(
            items = big.items.map { ExistingItem(it.hash, it.lastOpenedAt, it.favorite) },
            bookmarkIds = big.bookmarks.mapTo(HashSet()) { it.id },
            collectionIds = big.collections.mapTo(HashSet()) { it.id },
            memberships = big.collections.flatMapTo(HashSet()) { c -> c.itemHashes.map { c.id to it } },
            settingsUpdatedAt = big.viewSettings.associate { it.itemHash to it.updatedAt },
        )
        assertTrue(timed(10_000) { RestorePlanner.plan(existing, big) }.isEmpty)
    }

    @Test
    fun hostileGiantInputIsRefusedBeforeParsing() {
        val started = System.nanoTime()
        val result = BackupCodec.decode("x".repeat(100), BackupLimits(maxChars = 50))
        assertEquals(BackupRejection.TOO_LARGE, (result as BackupReadResult.Rejected).reason)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000)
    }
}
