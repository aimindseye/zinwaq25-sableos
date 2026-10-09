@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.titan2.displaycompat.android.DisplayCompatApp
import org.sableos.titan2.displaycompat.android.TraitsResolver
import org.sableos.titan2.displaycompat.core.BackendAvailability
import org.sableos.titan2.displaycompat.core.DisplayProfile

/** Observable list state; every property is Compose snapshot state so reads recompose as before. */
private class ListState {
    var query by mutableStateOf("")
    var sel by mutableIntStateOf(0)
    var version by mutableIntStateOf(0) // bumps after reset-all so profile badges refresh
    var confirmReset by mutableStateOf(false)
    var msg by mutableStateOf<String?>(null)
}

/** Key and reset handling of the list; created per composition so it always sees the latest parameters. */
private class ListActions(
    private val app: DisplayCompatApp,
    private val st: ListState,
    private val rows: List<TraitsResolver.Entry>,
    private val onOpen: (TraitsResolver.Entry) -> Unit,
    private val onExit: () -> Unit
) {
    fun doReset() {
        if (!st.confirmReset) {
            st.confirmReset = true
        } else {
            st.msg = app.controller.resetAllChecked().message()
            st.confirmReset = false
            st.version++
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when (e.key) {
            Key.DirectionDown -> {
                st.sel = (st.sel + 1).coerceAtMost((rows.size - 1).coerceAtLeast(0))
                true
            }

            Key.DirectionUp -> {
                st.sel = (st.sel - 1).coerceAtLeast(0)
                true
            }

            Key.Enter, Key.NumPadEnter -> {
                rows.getOrNull(st.sel)?.let(onOpen)
                true
            }

            Key.R -> ctrlReset(e)

            Key.Escape -> {
                escape()
                true
            }

            else -> false
        }
    }

    private fun ctrlReset(e: KeyEvent): Boolean {
        if (!e.isCtrlPressed) return false
        doReset()
        return true
    }

    private fun escape() {
        if (st.confirmReset) {
            st.confirmReset = false
        } else if (st.query.isNotEmpty()) {
            st.query = ""
        } else {
            onExit()
        }
    }
}

/** Keyboard-first list: type to filter (text input always wins), Up/Down move, Enter opens, Esc clears then exits. */
@Composable
fun AppListScreen(
    app: DisplayCompatApp,
    resolver: TraitsResolver,
    onOpen: (TraitsResolver.Entry) -> Unit,
    onExit: () -> Unit
) {
    val all = remember { resolver.launchable() }
    val st = remember { ListState() }
    val rows =
        remember(st.query, all) {
            all.filter {
                st.query.isBlank() || it.label.contains(st.query, true) ||
                    it.pkg.contains(st.query, true)
            }
        }
    val fr = remember { FocusRequester() }
    val list = rememberLazyListState()
    val actions = ListActions(app, st, rows, onOpen, onExit)
    LaunchedEffect(Unit) { fr.requestFocus() }
    LaunchedEffect(rows.size) { st.sel = st.sel.coerceIn(0, (rows.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(st.sel) { if (rows.isNotEmpty()) list.animateScrollToItem(st.sel) }

    Column(
        Modifier.fillMaxSize().padding(12.dp)
            .onPreviewKeyEvent { e -> actions.onKey(e) }
    ) {
        Text("App display compatibility", color = C.text, fontSize = 18.sp)
        val availability = app.controller.backendAvailability
        Text(
            availability.banner,
            color = if (availability == BackendAvailability.CLOSED) C.warn else C.muted,
            fontSize = 11.sp
        )
        FilterField(st.query, { st.query = it }, fr)
        if (rows.isEmpty()) {
            Text(
                emptyListMessage(all.isEmpty(), st.query),
                color = C.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(Modifier.weight(1f), state = list) {
            itemsIndexed(rows, key = { _, e -> e.pkg }) { i, e ->
                val p = remember(st.version, e.pkg) { app.store.get(e.pkg) }
                AppRow(e.label, p, i == st.sel)
            }
        }
        st.msg?.let { Text(it, color = messageTint(it), fontSize = 11.sp) }
        ListFooter(st.confirmReset) { actions.doReset() }
    }
}

private fun emptyListMessage(noAppsAtAll: Boolean, query: String): String = if (noAppsAtAll) {
    "No launchable apps were found for this user."
} else {
    "No apps match \"$query\"."
}

private fun messageTint(msg: String): androidx.compose.ui.graphics.Color =
    if (msg.contains("could not")) C.warn else C.ok

@Composable
private fun FilterField(query: String, onQueryChange: (String) -> Unit, fr: FocusRequester) {
    Box(
        Modifier.fillMaxWidth().padding(
            vertical = 8.dp
        ).border(1.dp, C.accent, RoundedCornerShape(6.dp)).padding(8.dp)
    ) {
        BasicTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth().focusRequester(fr),
            textStyle = TextStyle(color = C.text, fontSize = 14.sp),
            cursorBrush = SolidColor(C.accent),
            singleLine = true,
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        "Type to filter apps",
                        color = C.muted,
                        fontSize = 14.sp
                    )
                }
                inner()
            }
        )
    }
}

@Composable
private fun AppRow(label: String, p: DisplayProfile, selected: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .background(if (selected) C.surface else C.window, RoundedCornerShape(6.dp))
            .border(
                if (selected) 2.dp else 0.dp,
                if (selected) C.accent else C.window,
                RoundedCornerShape(6.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = C.text, fontSize = 14.sp)
        Text(
            if (p.isNative) "Native" else p.profile.id.replace('_', ' ') + " · stored",
            color = if (p.isNative) C.muted else C.accent,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ListFooter(confirmReset: Boolean, onReset: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("↑↓ select · Enter edit · Esc back", color = C.muted, fontSize = 11.sp)
        Text(
            if (confirmReset) {
                "Press Ctrl+R again to reset ALL profiles"
            } else {
                "Ctrl+R: reset all profiles"
            },
            color = C.warn,
            fontSize = 11.sp,
            modifier = Modifier.clickable { onReset() }
        )
    }
}
