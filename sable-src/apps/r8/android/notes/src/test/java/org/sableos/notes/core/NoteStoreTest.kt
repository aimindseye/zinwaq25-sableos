package org.sableos.notes.core

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NoteStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun savesListsAndDeletes() {
        val store = NoteStore(folder.root.resolve("notes"))
        val a = Note(NoteStore.newId(), "A", 1, 1)
        val b = Note(NoteStore.newId(), "B", 2, 5, NoteLabel.PINK)
        store.save(a)
        store.save(b)
        store.save(a.copy(body = "A2", updatedAt = 3))
        folder.root.resolve("notes/stray.txt").writeText("not a note")
        assertEquals(listOf("B", "A2"), store.list().map { it.body })
        store.delete(b.id)
        assertEquals(listOf(a.id), store.list().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathIds() {
        NoteStore(folder.root).delete("../escape")
    }
}
