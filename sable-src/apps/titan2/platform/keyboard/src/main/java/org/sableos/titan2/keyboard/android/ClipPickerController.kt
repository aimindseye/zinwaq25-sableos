package org.sableos.titan2.keyboard.android

import android.app.KeyguardManager
import android.content.Context
import android.view.KeyEvent
import org.sableos.titan2.keyboard.core.ClipAction
import org.sableos.titan2.keyboard.core.ClipPicker
import org.sableos.titan2.keyboard.core.KeyboardText.t

/** The clipboard history picker (Alt+Shift+V): [selected] is null while closed, else the entry in focus. */
class ClipPickerController(
    private val context: Context,
    private val enabled: () -> Boolean,
    private val paste: (String) -> Unit,
    private val refresh: () -> Unit,
    private val markChord: () -> Unit,
    private val toast: (String) -> Unit
) {
    var selected: Int? = null
        private set

    private val history get() = ClipboardWatcher.history

    fun chips(): List<String>? =
        selected?.let { ClipPicker.chips(history.entries, it, history.pinned) }

    fun close() {
        selected = null
    }

    fun chipTapped(chip: Int) {
        selected?.let { ClipPicker.entryForChip(chip, it) }?.let(::pasteAt)
    }

    /** Alt+Shift+V (held) opens the picker, or says why it can't. True when the key was this chord. */
    fun open(keyCode: Int, event: KeyEvent): Boolean {
        val opens = event.repeatCount == 0 && ClipPicker.opens(
            keyCode,
            event.isAltPressed,
            event.isShiftPressed,
            event.isCtrlPressed || event.isMetaPressed
        )
        if (!opens) return false
        // The held Alt and Shift were a chord: their release must not leave a one-shot.
        markChord()
        when {
            !enabled() -> toast(t("turn_on_clipboard_history_in"))

            context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true ->
                toast(t("unlock_to_use_clipboard_history"))

            else -> selected = 0
        }
        return true
    }

    /** A key while the picker is open; false when it is closed or the key should type as usual. */
    fun key(keyCode: Int, event: KeyEvent): Boolean {
        val sel = selected ?: return false
        val count = history.entries.size
        val action = ClipPicker.action(keyCode, event.isShiftPressed)
        when (action) {
            ClipAction.Next -> selected = ClipPicker.move(sel, 1, count)

            ClipAction.Previous -> selected = ClipPicker.move(sel, -1, count)

            ClipAction.Paste -> pasteAt(sel)

            ClipAction.TogglePin -> selected = history.togglePin(sel)

            ClipAction.Remove -> {
                history.remove(sel)
                selected = sel.coerceAtMost((count - 2).coerceAtLeast(0))
            }

            ClipAction.Close, ClipAction.CloseAndPass -> selected = null

            ClipAction.Ignore -> Unit
        }
        if (action == ClipAction.CloseAndPass) refresh()
        return action != ClipAction.Ignore && action != ClipAction.CloseAndPass
    }

    private fun pasteAt(index: Int) {
        val text = history.entries.getOrNull(index)
        selected = null
        if (text != null) paste(text)
        refresh()
    }
}
