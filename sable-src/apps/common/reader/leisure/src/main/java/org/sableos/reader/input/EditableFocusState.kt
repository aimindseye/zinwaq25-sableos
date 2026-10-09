package org.sableos.reader.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

/** Whether an in-window text field currently owns the keyboard. Reader shortcuts must yield while it does. */
@Stable
class EditableFocusState {
    var focused: Boolean by mutableStateOf(false)
}

/** Apply to every text field in a reading surface so printable keys and Space reach the field. */
@Composable
fun Modifier.reportsEditableFocus(state: EditableFocusState): Modifier {
    DisposableEffect(state) { onDispose { state.focused = false } }
    return onFocusChanged { state.focused = it.isFocused }
}
