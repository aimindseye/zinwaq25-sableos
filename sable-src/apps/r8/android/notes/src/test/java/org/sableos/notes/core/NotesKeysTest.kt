package org.sableos.notes.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesKeysTest {
    @Test
    fun listKeysLeaveTheSharedListMovesAlone() {
        assertEquals(ListKey.New, NotesKeys.list(KeyEvent.KEYCODE_C, ctrl = false, chord = false))
        assertEquals(ListKey.New, NotesKeys.list(KeyEvent.KEYCODE_N, ctrl = true, chord = true))
        assertEquals(ListKey.Search, NotesKeys.list(KeyEvent.KEYCODE_S, ctrl = false, chord = false))
        assertEquals(ListKey.Tasks, NotesKeys.list(KeyEvent.KEYCODE_K, ctrl = false, chord = false))
        listOf(KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_SPACE)
            .forEach { assertNull(NotesKeys.list(it, ctrl = false, chord = false)) }
        // Alt+letter types a digit or symbol on the Q25.
        assertNull(NotesKeys.list(KeyEvent.KEYCODE_C, ctrl = false, chord = true))
    }

    @Test
    fun editorKeysNeverTakePlainTyping() {
        assertNull(NotesKeys.editor(KeyEvent.KEYCODE_S, ctrl = false, alt = false))
        assertNull(NotesKeys.editor(KeyEvent.KEYCODE_ENTER, ctrl = false, alt = false))
        assertEquals(EditorKey.Menu, NotesKeys.editor(KeyEvent.KEYCODE_MENU, ctrl = false, alt = false))
        assertEquals(EditorKey.ToggleItem, NotesKeys.editor(KeyEvent.KEYCODE_ENTER, ctrl = false, alt = true))
        assertEquals(EditorKey.ToggleItem, NotesKeys.editor(KeyEvent.KEYCODE_ENTER, ctrl = true, alt = false))
        assertEquals(EditorKey.Find, NotesKeys.editor(KeyEvent.KEYCODE_F, ctrl = true, alt = false))
        assertEquals(EditorKey.Close, NotesKeys.editor(KeyEvent.KEYCODE_ESCAPE, ctrl = false, alt = false))
    }

    @Test
    fun menuLettersAreDistinct() {
        assertEquals(
            EditorMenu.entries.size,
            EditorMenu.entries
                .map { it.key }
                .toSet()
                .size,
        )
        assertEquals(EditorMenu.Delete, EditorMenu.forKey(KeyEvent.KEYCODE_D))
        assertNull(EditorMenu.forKey(KeyEvent.KEYCODE_Z))
    }
}
