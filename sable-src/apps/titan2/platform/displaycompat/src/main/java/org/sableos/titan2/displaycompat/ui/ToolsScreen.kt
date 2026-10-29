@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.titan2.displaycompat.android.DisplayCompatApp
import org.sableos.titan2.displaycompat.android.GatedPlatformBackend
import org.sableos.titan2.displaycompat.core.CanvasCycle
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t
import org.sableos.titan2.displaycompat.core.SizePx
import org.sableos.titan2.displaycompat.core.SystemRotation
import org.sableos.titan2.displaycompat.core.TallCanvas

/** One row of Display tools: rotation, one favourite size each, restore, back. */
private sealed interface ToolRow {
    data object Rotation : ToolRow
    data class Favourite(val canvas: TallCanvas) : ToolRow
    data object Restore : ToolRow
    data object Back : ToolRow
}

private class ToolsState(rotation: SystemRotation, favourites: Set<TallCanvas>) {
    var rotation by mutableStateOf(rotation)
    var favourites by mutableStateOf(favourites)
    var row by mutableIntStateOf(0)
    var msg by mutableStateOf<String?>(null)
}

/**
 * Display tools: the system-wide rotation, the favourite Tall canvas sizes the Alt+Shift+R hotkey
 * cycles through, and a manual restore. Up/Down choose a row, Left/Right or Enter change it, Esc
 * leaves. Changes apply at once.
 */
@Composable
fun ToolsScreen(app: DisplayCompatApp, display: SizePx, onDone: () -> Unit) {
    val settings = app.toolsSettings
    val st = remember { ToolsState(settings.systemRotation, settings.favourites) }
    val rows = remember(display) {
        listOf(ToolRow.Rotation) + TallCanvas.usable(display).map { ToolRow.Favourite(it) } +
            listOf(ToolRow.Restore, ToolRow.Back)
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    val actions = ToolsActions(app, st, display, onDone)

    Column(
        Modifier.fillMaxSize().padding(12.dp).focusRequester(fr).focusable()
            .onPreviewKeyEvent { e -> actions.onKey(e, rows) }
    ) {
        Text(t("display_tools"), color = C.text, fontSize = 18.sp)
        if (!GatedPlatformBackend.isOpen()) {
            Text(
                t("enforcement_backend_closed_these_settings"),
                color = C.warn,
                fontSize = 11.sp
            )
        }
        Text(
            t("alt_shift_r_sym_shift") +
                cycleSummary(st.favourites, display),
            color = C.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            rows.forEachIndexed { i, r ->
                ToolRowItem(title(r, display), value(r, st), i == st.row) {
                    st.row = i
                    actions.act(r, 1)
                }
            }
            Text(
                t("portrait_and_landscape_lock_the"),
                color = C.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
            st.msg?.let {
                Text(
                    it,
                    color = if (it.startsWith("Could not")) C.warn else C.ok,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Text(t("row_change_enter_change_act"), color = C.muted, fontSize = 11.sp)
    }
}

/** Row actions and keys of Display tools; created per composition so it sees the latest state. */
private class ToolsActions(
    private val app: DisplayCompatApp,
    private val st: ToolsState,
    private val display: SizePx,
    private val onDone: () -> Unit
) {
    private val settings get() = app.toolsSettings

    private companion object {
        val KEYS = setOf(
            Key.DirectionDown,
            Key.DirectionUp,
            Key.DirectionRight,
            Key.DirectionLeft,
            Key.Enter,
            Key.NumPadEnter,
            Key.Escape
        )
    }

    fun onKey(e: KeyEvent, rows: List<ToolRow>): Boolean {
        val handled = e.type == KeyEventType.KeyDown && e.key in KEYS
        if (handled) {
            when (e.key) {
                Key.DirectionDown -> st.row = (st.row + 1).coerceAtMost(rows.lastIndex)
                Key.DirectionUp -> st.row = (st.row - 1).coerceAtLeast(0)
                Key.DirectionLeft -> act(rows[st.row], -1)
                Key.Escape -> onDone()
                else -> act(rows[st.row], 1)
            }
        }
        return handled
    }

    fun act(r: ToolRow, dir: Int) {
        when (r) {
            ToolRow.Rotation -> {
                val all = SystemRotation.entries
                st.rotation = all[(all.indexOf(st.rotation) + dir + all.size) % all.size]
                settings.systemRotation = st.rotation
                app.refreshDisplay()
                st.msg = t("system_rotation", st.rotation.label)
            }

            is ToolRow.Favourite -> {
                st.favourites = if (r.canvas in st.favourites) {
                    st.favourites - r.canvas
                } else {
                    st.favourites + r.canvas
                }
                settings.favourites = st.favourites
                st.msg = cycleSummary(st.favourites, display)
            }

            ToolRow.Restore -> {
                val failed = app.tools.restoreAll()
                st.msg = if (failed.isEmpty()) {
                    t("display_restored_profiles_apply_again")
                } else {
                    t("could_not_restore_try_again", failed)
                }
            }

            ToolRow.Back -> onDone()
        }
    }
}

private fun cycleSummary(favourites: Set<TallCanvas>, display: SizePx): String {
    val order = CanvasCycle.order(favourites, display)
    return if (order.size < 2) {
        t("no_favourite_sizes_yet")
    } else {
        order.joinToString(" → ") { it.label(display) } + t("native_label")
    }
}

private fun title(r: ToolRow, display: SizePx): String = when (r) {
    ToolRow.Rotation -> t("system_rotation_2")
    is ToolRow.Favourite -> t("favourite") + r.canvas.label(display)
    ToolRow.Restore -> t("restore_the_native_display_now")
    ToolRow.Back -> t("back")
}

private fun value(r: ToolRow, st: ToolsState): String = when (r) {
    ToolRow.Rotation -> st.rotation.label
    is ToolRow.Favourite -> if (r.canvas in st.favourites) t("on") else t("off")
    ToolRow.Restore, ToolRow.Back -> ""
}

@Composable
private fun ToolRowItem(title: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .background(if (selected) C.surface else C.window, RoundedCornerShape(6.dp))
            .border(
                if (selected) 2.dp else 0.dp,
                if (selected) C.accent else C.window,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }.padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = C.text, fontSize = 14.sp)
        Text(value, color = C.accent, fontSize = 13.sp)
    }
}
