package org.sableos.reader.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.backup.BackupFixtures.document
import org.sableos.reader.backup.BackupFixtures.hash
import org.sableos.reader.backup.BackupFixtures.item

class RestorePlannerTest {
    private fun state(vararg items: ExistingItem) = ExistingState(items = items.toList())

    @Test
    fun unknownTitlesAreAddedAndKnownOnesAreNotDuplicated() {
        val plan = RestorePlanner.plan(state(ExistingItem(hash(1), 0, false)), document(listOf(item(1), item(2))))
        assertEquals(listOf(hash(2)), plan.newItems.map { it.hash })
    }

    @Test
    fun theMoreRecentlyOpenedSideWinsProgress() {
        val backup = document(
            listOf(item(1) { it.copy(lastOpenedAt = 100, progress = 0.9) }, item(2) { it.copy(lastOpenedAt = 5) }),
        )
        val local = state(ExistingItem(hash(1), 50, false), ExistingItem(hash(2), 50, false))
        val plan = RestorePlanner.plan(local, backup)
        assertEquals(listOf(hash(1)), plan.progressUpdates.map { it.hash })
    }

    @Test
    fun equalOpenTimesKeepTheLibraryAsIs() {
        val backup = document(listOf(item(1) { it.copy(lastOpenedAt = 50) }))
        val plan = RestorePlanner.plan(state(ExistingItem(hash(1), 50, false)), backup)
        assertTrue(plan.progressUpdates.isEmpty())
    }

    @Test
    fun favoritesAreAUnionAndNeverRemoved() {
        val backup = document(
            listOf(
                item(1) { it.copy(favorite = true) },
                item(2) { it.copy(favorite = false) },
                item(3) { it.copy(favorite = true) },
            ),
        )
        val plan = RestorePlanner.plan(
            state(ExistingItem(hash(1), 0, false), ExistingItem(hash(2), 0, true), ExistingItem(hash(3), 0, true)),
            backup,
        )
        assertEquals(listOf(hash(1)), plan.favoriteHashes)
    }

    @Test
    fun onlyMissingBookmarksHighlightsAndCollectionsAreAdded() {
        val backup = document(listOf(item(1))) {
            it.copy(
                bookmarks = listOf(BackupBookmark("b1", hash(1), "{}", "x"), BackupBookmark("b2", hash(1), "{}", "y")),
                highlights = listOf(
                    BackupHighlight("h1", hash(1), "{}", "t"),
                    BackupHighlight("h2", hash(1), "{}", "u"),
                ),
                collections = listOf(
                    BackupCollection("c1", "A", itemHashes = listOf(hash(1))),
                    BackupCollection("c2", "B", itemHashes = listOf(hash(1))),
                ),
            )
        }
        val existing = ExistingState(
            items = listOf(ExistingItem(hash(1), 0, false)),
            bookmarkIds = setOf("b1"),
            highlightIds = setOf("h1"),
            collectionIds = setOf("c1"),
            memberships = setOf("c1" to hash(1)),
        )
        val plan = RestorePlanner.plan(existing, backup)
        assertEquals(listOf("b2"), plan.bookmarks.map { it.id })
        assertEquals(listOf("h2"), plan.highlights.map { it.id })
        assertEquals(listOf("c2"), plan.newCollections.map { it.id })
        assertEquals(listOf("c2" to hash(1)), plan.memberships)
    }

    @Test
    fun viewSettingsFollowTheNewerTimestamp() {
        val backup = document(listOf(item(1), item(2), item(3))) {
            it.copy(
                viewSettings = listOf(
                    BackupViewSettings(hash(1), "{}", 10),
                    BackupViewSettings(hash(2), "{}", 10),
                    BackupViewSettings(hash(3), "{}", 10),
                ),
            )
        }
        val existing = ExistingState(
            items = (1..3).map { ExistingItem(hash(it), 0, false) },
            settingsUpdatedAt = mapOf(hash(1) to 5L, hash(2) to 10L),
        )
        assertEquals(listOf(hash(1), hash(3)), RestorePlanner.plan(existing, backup).viewSettings.map { it.itemHash })
    }

    /** A tiny in-memory library that applies a plan the way the real restore does. */
    private class Library {
        val items = LinkedHashMap<String, ExistingItem>()
        val bookmarkIds = HashSet<String>()
        val highlightIds = HashSet<String>()
        val collectionIds = HashSet<String>()
        val memberships = HashSet<Pair<String, String>>()
        val settings = HashMap<String, Long>()

        fun state() =
            ExistingState(items.values.toList(), bookmarkIds, highlightIds, collectionIds, memberships, settings)

        fun apply(plan: RestorePlan) {
            plan.newItems.forEach { items[it.hash] = ExistingItem(it.hash, it.lastOpenedAt, it.favorite) }
            plan.progressUpdates.forEach {
                items[it.hash] = items.getValue(it.hash).copy(lastOpenedAt = it.lastOpenedAt)
            }
            plan.favoriteHashes.forEach { items[it] = items.getValue(it).copy(favorite = true) }
            bookmarkIds += plan.bookmarks.map { it.id }
            highlightIds += plan.highlights.map { it.id }
            collectionIds += plan.newCollections.map { it.id }
            memberships += plan.memberships
            plan.viewSettings.forEach { settings[it.itemHash] = it.updatedAt }
        }
    }

    @Test
    fun applyingAPlanTwiceChangesNothingTheSecondTime() {
        val backup = BackupFixtures.large(200)
        val library = Library()
        library.items[hash(0)] = ExistingItem(hash(0), 10_000, false)
        val first = RestorePlanner.plan(library.state(), backup)
        assertTrue(!first.isEmpty)
        library.apply(first)
        val second = RestorePlanner.plan(library.state(), backup)
        assertTrue("second plan: $second", second.isEmpty)
        assertEquals(200, library.items.size)
    }

    @Test
    fun aPlanNeverDeletesAnything() {
        val library = Library()
        library.items["keep-me-0001"] = ExistingItem("keep-me-0001", 1, true)
        library.bookmarkIds += "local-bookmark"
        val plan = RestorePlanner.plan(library.state(), document(listOf(item(1))))
        library.apply(plan)
        assertTrue("keep-me-0001" in library.items)
        assertTrue("local-bookmark" in library.bookmarkIds)
        assertEquals(2, library.items.size)
    }

    @Test
    fun relinkingNeedsTheSameContentHash() {
        assertTrue(Relink.sameContent("ABCDEF0123456789ABCDEF0123456789", "abcdef0123456789abcdef0123456789"))
        assertTrue(!Relink.sameContent(hash(1), hash(2)))
        assertTrue(Relink.isFolder("inode/directory"))
        assertTrue(!Relink.isFolder("application/pdf"))
        assertTrue(!Relink.isFolder(null))
    }
}
