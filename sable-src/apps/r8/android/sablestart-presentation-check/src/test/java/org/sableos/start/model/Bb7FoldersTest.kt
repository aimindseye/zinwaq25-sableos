package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** BB7 uses the BB10 grid's folders, its 1.x tabs move into them once, and home screen shortcuts. */
class Bb7FoldersTest {
    @Test
    fun onlyFoldersWithAnInstalledAppShow() {
        val folders = listOf(GridFolder(1, "Work", listOf("a", "b")), GridFolder(2, "Games", listOf("z")))
        assertEquals(listOf(folders[0]), Bb7Folders.shown(folders, setOf("a")))
    }

    @Test
    fun oldTabsBecomeFoldersAndAnAppEndsInTheLastTab() {
        val tabs = listOf(Bb7Tab("Work", listOf("a", "b")), Bb7Tab("Play", listOf("b", "c")), Bb7Tab("Empty"))
        val folders = Bb7Folders.fromTabs(tabs, emptyList())
        assertEquals(listOf("Work", "Play"), folders.map { it.name })
        assertEquals(listOf("a"), folders[0].keys)
        assertEquals(listOf("b", "c"), folders[1].keys)
    }

    @Test
    fun aTabJoinsTheFolderWithItsName() {
        val folders = Bb7Folders.fromTabs(listOf(Bb7Tab("work", listOf("b"))), listOf(GridFolder(4, "Work", listOf("a"))))
        assertEquals(listOf(GridFolder(4, "Work", listOf("a", "b"))), folders)
    }

    @Test
    fun shortcutsToggleUpToTheLimit() {
        assertEquals(listOf("a"), Bb7Shortcuts.toggle(emptyList(), "a"))
        assertEquals(emptyList<String>(), Bb7Shortcuts.toggle(listOf("a"), "a"))
        val full = (1..Bb7Shortcuts.MAX).map { "k$it" }
        assertEquals(full, Bb7Shortcuts.toggle(full, "x"))
    }
}
