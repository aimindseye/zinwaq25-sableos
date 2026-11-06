package org.sableos.design

import android.view.KeyEvent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch

/**
 * BlackBerry scroll keys for plain scrolling reader screens: T top, B bottom, Space page down and Shift+Space page
 * up. Same contract as the shared design library; plain keys only, and only a key nothing inside took.
 */
object SableScrollKeys {
    fun destination(
        keyCode: Int,
        shift: Boolean,
        value: Int,
        max: Int,
        viewport: Int,
    ): Int? {
        val page = viewport * PAGE_NUMERATOR / PAGE_DENOMINATOR
        val to =
            when (keyCode) {
                KeyEvent.KEYCODE_T -> if (shift) return null else 0
                KeyEvent.KEYCODE_B -> if (shift) return null else max
                KeyEvent.KEYCODE_SPACE -> if (shift) value - page else value + page
                else -> return null
            }
        return to.coerceIn(0, max.coerceAtLeast(0)).takeIf { it != value }
    }

    private const val PAGE_NUMERATOR = 9
    private const val PAGE_DENOMINATOR = 10
}

/** Use in place of `verticalScroll(rememberScrollState())`. */
@Composable
fun Modifier.sableVerticalScroll(): Modifier {
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()
    var viewport by remember { mutableIntStateOf(0) }
    return onSizeChanged { viewport = it.height }
        .onKeyEvent { event ->
            val native = event.nativeKeyEvent
            val chord = native.isAltPressed || native.isCtrlPressed || native.isMetaPressed
            val to =
                if (chord) {
                    null
                } else {
                    SableScrollKeys.destination(native.keyCode, native.isShiftPressed, state.value, state.maxValue, viewport)
                }
            if (to == null) {
                false
            } else {
                if (event.type == KeyEventType.KeyDown) scope.launch { state.animateScrollTo(to) }
                true
            }
        }.verticalScroll(state)
}
