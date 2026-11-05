package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Folders on the BB10 app grid. */
class GridFoldersTest {
    private val installed = listOf("a", "b", "c", "d", "e")

    @Test
    fun aNewFolderTakesTheAppsPlaceAndTheAppLeavesTheGrid() {
        val (folders, order) = GridFolders.create(emptyList(), installed, "b", "Games") ?: error("not created")
        assertEquals(listOf("a", "folder:1", "c", "d", "e"), order)
        val items = GridFolders.layout(order, emptyList(), installed, folders)
        assertEquals(GridItem.Folder(folders.single(), listOf("b")), items[1])
        assertEquals(5, items.size)
    }

    @Test
    fun addingMovesAnAppIntoTheFolderAndOutOfAnyOther() {
        val (one, order) = GridFolders.create(emptyList(), installed, "a", "One") ?: error("not created")
        val (two, order2) = GridFolders.create(one, order, "c", "Two") ?: error("not created")
        val moved = GridFolders.add(two, id = 1, key = "c")
        assertEquals(listOf("a", "c"), moved.single().keys)
        // Folder Two has no app left, so it goes.
        val items = GridFolders.layout(order2, emptyList(), installed, moved)
        assertEquals(listOf("folder:1", "b", "d", "e"), GridFolders.order(items))
    }

    @Test
    fun removingPutsTheAppAfterTheFolderAndAnEmptyFolderGoes() {
        val (folders, order) = GridFolders.create(emptyList(), installed, "b", "Games") ?: error("not created")
        val both = GridFolders.add(folders, 1, "d")
        val (left, order2) = GridFolders.remove(both, order.filterNot { it == "d" }, "d")
        assertEquals(listOf("a", "folder:1", "d", "c", "e"), order2)
        val (none, order3) = GridFolders.remove(left, order2, "b")
        assertTrue(none.isEmpty())
        assertEquals(listOf("a", "b", "d", "c", "e"), order3)
    }

    @Test
    fun ungroupPutsTheAppsBackWhereTheFolderWas() {
        val (folders, order) = GridFolders.create(emptyList(), installed, "b", "Games") ?: error("not created")
        val both = GridFolders.add(folders, 1, "e")
        val (none, order2) = GridFolders.ungroup(both, order.filterNot { it == "e" }, 1)
        assertTrue(none.isEmpty())
        assertEquals(listOf("a", "b", "e", "c", "d"), order2)
    }

    @Test
    fun namesAreCleanAndUnique() {
        assertNull(GridFolders.create(emptyList(), installed, "a", "  "))
        val (folders, order) = GridFolders.create(emptyList(), installed, "a", "Games") ?: error("not created")
        assertNull(GridFolders.create(folders, order, "b", "games"))
        val (two, _) = GridFolders.create(folders, order, "b", "Tools") ?: error("not created")
        assertEquals("Tools", GridFolders.rename(two, 2, "Games").first { it.id == 2 }.name)
        assertEquals("Work", GridFolders.rename(two, 2, " Work ").first { it.id == 2 }.name)
    }

    @Test
    fun uninstalledAppsAndNewAppsAreHandled() {
        val folders = listOf(GridFolder(4, "Old", listOf("gone")), GridFolder(5, "Kept", listOf("gone", "c")))
        val items = GridFolders.layout(listOf("folder:4", "a", "folder:5"), emptyList(), installed, folders)
        assertEquals(listOf("a", "folder:5", "b", "d", "e"), GridFolders.order(items))
        assertEquals(GridItem.Folder(folders[1], listOf("c")), items[1])
    }

    @Test
    fun encodeAndDecodeRoundTripAndDropDamage() {
        val folders = listOf(GridFolder(1, "Games", listOf("0:x/y", "0:z/w")), GridFolder(3, "Tools"))
        assertEquals(folders, GridFolders.decode(GridFolders.encode(folders)))
        val damaged = "1\tGames\ta,b\nbad line\n2\tgames\tc\n0\tZero\td\n3\tMore\tb,e"
        assertEquals(
            listOf(GridFolder(1, "Games", listOf("a", "b")), GridFolder(3, "More", listOf("e"))),
            GridFolders.decode(damaged),
        )
        assertTrue(GridFolders.decode(null).isEmpty())
    }
}
