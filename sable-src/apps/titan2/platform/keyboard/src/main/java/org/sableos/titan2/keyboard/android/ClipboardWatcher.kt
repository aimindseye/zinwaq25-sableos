package org.sableos.titan2.keyboard.android

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import org.sableos.titan2.keyboard.core.ClipboardHistory

/**
 * Records copies into the in-memory [ClipboardHistory] while the keyboard runs and the user has
 * turned history on. Only the default keyboard may read the clipboard in the background, which is
 * why history lives here. One history per process, so Settings can clear what the service holds.
 */
object ClipboardWatcher {
    val history = ClipboardHistory()

    private var manager: ClipboardManager? = null
    private var isEnabled: () -> Boolean = { false }
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { record() }

    fun start(context: Context, enabled: () -> Boolean) {
        if (manager != null) return
        isEnabled = enabled
        manager = context.getSystemService(ClipboardManager::class.java)?.also {
            it.addPrimaryClipChangedListener(listener)
        }
    }

    fun stop() {
        manager?.removePrimaryClipChangedListener(listener)
        manager = null
    }

    private fun record() {
        if (!isEnabled()) {
            history.clear()
            return
        }
        val clip =
            runCatching { manager?.primaryClip }.getOrNull()?.takeIf { it.itemCount > 0 } ?: return
        val sensitive =
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        history.add(clip.getItemAt(0).text, sensitive)
    }
}
