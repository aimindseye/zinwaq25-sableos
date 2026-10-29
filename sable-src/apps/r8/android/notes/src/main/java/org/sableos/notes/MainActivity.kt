package org.sableos.notes

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.sableos.design.SableGlobalTheme
import org.sableos.notes.ui.NotesApp

/**
 * Sable Notes: local notes and checklists, typed on the keyboard. Notes stay in app storage; share,
 * export and import happen only when asked. Text shared from another app opens as a new note.
 */
class MainActivity : ComponentActivity() {
    private lateinit var controller: NotesController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = NotesController(this)
        controller.load()
        setContent {
            SableGlobalTheme(window = window) {
                NotesApp(controller.state, controller.actions)
            }
        }
        if (savedInstanceState == null) controller.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        controller.handleIntent(intent)
    }

    /** List and Tasks keys arrive here only when no focused view used them, so typing always wins. */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = (event.repeatCount == 0 && controller.onKey(keyCode, event)) || super.onKeyDown(keyCode, event)

    override fun onPause() {
        controller.flushAll()
        super.onPause()
    }
}
