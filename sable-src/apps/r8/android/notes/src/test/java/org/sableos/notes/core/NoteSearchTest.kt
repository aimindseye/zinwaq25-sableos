package org.sableos.notes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteSearchTest {
    private val shop = Note("a", "Shopping\n- [ ] Milk and bread", 1, 1)
    private val work = Note("b", "Work\nCall the bank about the loan", 2, 2)

    @Test
    fun everyWordMustMatchIgnoringCase() {
        assertEquals(listOf("a"), NoteSearch.search(listOf(shop, work), "milk BREAD").map { it.note.id })
        assertEquals(2, NoteSearch.search(listOf(shop, work), "  ").size)
        assertEquals(emptyList<NoteSearch.Hit>(), NoteSearch.search(listOf(shop), "milk loan"))
    }

    @Test
    fun hitContextIsShownOnlyForBodyMatches() {
        assertNull(NoteSearch.search(listOf(work), "work").single().context)
        assertEquals("Work Call the bank about the loan", NoteSearch.search(listOf(work), "bank").single().context)
    }

    @Test
    fun findInNoteWraps() {
        val body = "one two one"
        assertEquals(0..2, NoteSearch.nextMatch(body, "ONE", 0))
        assertEquals(8..10, NoteSearch.nextMatch(body, "one", 1))
        assertEquals(0..2, NoteSearch.nextMatch(body, "one", 11))
        assertNull(NoteSearch.nextMatch(body, "three", 0))
        assertNull(NoteSearch.nextMatch(body, "", 0))
    }
}
