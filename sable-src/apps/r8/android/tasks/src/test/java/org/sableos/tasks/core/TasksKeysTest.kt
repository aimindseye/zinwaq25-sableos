package org.sableos.tasks.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksKeysTest {
    @Test
    fun letterKeysMapAndAltLeavesThemAlone() {
        assertEquals(ScreenKey.New, TasksKeys.screen(KeyEvent.KEYCODE_C, modified = false))
        assertEquals(ScreenKey.ShowDone, TasksKeys.screen(KeyEvent.KEYCODE_H, modified = false))
        assertNull(TasksKeys.screen(KeyEvent.KEYCODE_C, modified = true))
        assertEquals(RowKey.Done, TasksKeys.row(KeyEvent.KEYCODE_SPACE, modified = false))
        assertEquals(RowKey.Tomorrow, TasksKeys.row(KeyEvent.KEYCODE_Z, modified = false))
        assertEquals(RowKey.Delete, TasksKeys.row(KeyEvent.KEYCODE_DEL, modified = false))
        assertNull(TasksKeys.row(KeyEvent.KEYCODE_Z, modified = true))
    }

    @Test
    fun hintNamesEveryKey() {
        listOf("C new", "Enter edit", "Space done", "Z tomorrow", "D delete", "H done list").forEach {
            assertTrue(it, TasksKeys.HINT.contains(it))
        }
    }
}
