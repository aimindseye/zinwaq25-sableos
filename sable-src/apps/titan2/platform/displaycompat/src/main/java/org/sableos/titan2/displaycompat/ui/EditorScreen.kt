@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.titan2.displaycompat.android.DisplayCompatApp
import org.sableos.titan2.displaycompat.android.MiniSizeEvidence
import org.sableos.titan2.displaycompat.android.TraitsResolver
import org.sableos.titan2.displaycompat.core.AppTraits
import org.sableos.titan2.displaycompat.core.AspectProfile
import org.sableos.titan2.displaycompat.core.BarsPref
import org.sableos.titan2.displaycompat.core.CanvasPlan
import org.sableos.titan2.displaycompat.core.CanvasPlanner
import org.sableos.titan2.displaycompat.core.DisplayProfile
import org.sableos.titan2.displaycompat.core.Issue
import org.sableos.titan2.displaycompat.core.LetterboxBackground
import org.sableos.titan2.displaycompat.core.OrientationPref
import org.sableos.titan2.displaycompat.core.ProfileController
import org.sableos.titan2.displaycompat.core.ProfileValidator
import org.sableos.titan2.displaycompat.core.Ratio
import org.sableos.titan2.displaycompat.core.ScalingPref
import org.sableos.titan2.displaycompat.core.Severity
import org.sableos.titan2.displaycompat.core.SizePx
import org.sableos.titan2.displaycompat.core.Tri

private fun <T> cycle(values: List<T>, cur: T, dir: Int): T = values[
    (values.indexOf(cur) + dir + values.size) % values.size
]

private val customChoices =
    listOf(
        Ratio(1, 1),
        Ratio(5, 4),
        Ratio(4, 3),
        Ratio(3, 2),
        Ratio(16, 10),
        Ratio(16, 9),
        Ratio(21, 9)
    )

private val defaultLetterboxBackdrop = Color(0xFF1B2F40)

private enum class EditorRow(val title: String) {
    Profile("Profile"),
    Orientation("Orientation"),
    Scaling("Scaling"),
    Background("Letterbox background"),
    KeyboardSafe("Keyboard-safe area"),
    Bars("Status / nav bars"),
    Stretch("Stretch (distorts)"),
    Custom("Custom ratio"),
    Advanced("Advanced profiles"),
    Ack("Acknowledge warnings"),
    Save("Save profile"),
    Reset("Reset this app to Native"),
    Back("Back")
}

/** Observable editor state; every property is Compose snapshot state so reads recompose as before. */
private class EditorState(initial: DisplayProfile) {
    var p by mutableStateOf(initial)
    var advanced by mutableStateOf(false)
    var ack by mutableStateOf(false)
    var msg by mutableStateOf<String?>(null)
    var row by mutableIntStateOf(0)

    fun visibleRows(): List<EditorRow> =
        EditorRow.entries.filter { it != EditorRow.Custom || p.profile == AspectProfile.Custom }

    fun valueOf(r: EditorRow): String = when (r) {
        EditorRow.Profile -> p.profile.id.replace('_', ' ')
        EditorRow.Orientation -> p.orientation.name
        EditorRow.Scaling -> p.scaling.name
        EditorRow.Background -> p.letterboxBackground.name
        EditorRow.KeyboardSafe -> p.keyboardSafeArea.name
        EditorRow.Bars -> p.statusNav.name
        EditorRow.Stretch -> onOff(p.stretch)
        EditorRow.Custom -> p.customRatio?.toString() ?: "choose"
        EditorRow.Advanced -> onOff(advanced)
        EditorRow.Ack -> if (ack) "Yes" else "No"
        EditorRow.Save, EditorRow.Reset, EditorRow.Back -> ""
    }

    fun cycleField(r: EditorRow, dir: Int) {
        when (r) {
            EditorRow.Profile -> p = p.copy(
                profile = cycle(
                    AspectProfile.entries.filter { it != AspectProfile.Custom || advanced },
                    p.profile,
                    dir
                )
            )

            EditorRow.Orientation ->
                p =
                    p.copy(orientation = cycle(OrientationPref.entries, p.orientation, dir))

            EditorRow.Scaling -> p = p.copy(scaling = cycle(ScalingPref.entries, p.scaling, dir))

            EditorRow.Background -> p = p.copy(
                letterboxBackground = cycle(LetterboxBackground.entries, p.letterboxBackground, dir)
            )

            EditorRow.KeyboardSafe ->
                p =
                    p.copy(keyboardSafeArea = cycle(Tri.entries, p.keyboardSafeArea, dir))

            EditorRow.Bars -> p = p.copy(statusNav = cycle(BarsPref.entries, p.statusNav, dir))

            EditorRow.Stretch -> p = p.copy(stretch = !p.stretch)

            EditorRow.Custom -> p = p.copy(
                customRatio = cycle(customChoices, p.customRatio ?: customChoices[0], dir)
            )

            else -> Unit
        }
    }

    private fun onOff(b: Boolean): String = if (b) "On" else "Off"
}

/** Row actions of the editor; created per composition so it always sees the latest parameters. */
private class EditorActions(
    private val app: DisplayCompatApp,
    private val st: EditorState,
    private val traits: AppTraits,
    private val pkg: String,
    private val display: SizePx,
    private val onDone: () -> Unit
) {
    fun change(dir: Int) {
        val rows = st.visibleRows()
        when (val r = rows[st.row.coerceIn(0, rows.lastIndex)]) {
            EditorRow.Advanced -> toggleAdvanced()

            EditorRow.Ack -> st.ack = !st.ack

            EditorRow.Save ->
                st.msg =
                    app.controller.save(traits, st.p, display, st.advanced, st.ack).message()

            EditorRow.Reset -> reset()

            EditorRow.Back -> onDone()

            else -> st.cycleField(r, dir)
        }
    }

    fun onKey(e: KeyEvent, lastRow: Int): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        return when (e.key) {
            Key.DirectionDown -> {
                st.row = (st.row + 1).coerceAtMost(lastRow)
                true
            }

            Key.DirectionUp -> {
                st.row = (st.row - 1).coerceAtLeast(0)
                true
            }

            Key.DirectionRight, Key.Enter, Key.NumPadEnter -> {
                change(1)
                true
            }

            Key.DirectionLeft -> {
                change(-1)
                true
            }

            Key.Escape -> {
                onDone()
                true
            }

            else -> false
        }
    }

    private fun toggleAdvanced() {
        st.advanced = !st.advanced
        if (!st.advanced && st.p.profile == AspectProfile.Custom) {
            st.p = st.p.copy(profile = AspectProfile.Native)
        }
    }

    private fun reset() {
        val outcome = app.controller.resetChecked(pkg)
        if (outcome.storeCleared) {
            st.p = DisplayProfile.NATIVE
            st.ack = false
        }
        st.msg = outcome.message()
    }
}

/** Messages that say something was not done, or was done only partly, are not shown in the success colour. */
private fun messageColor(msg: String): Color {
    val caution =
        listOf("Not saved", "failed", "NOT enforced", "could not", "ignored", "not verified")
    return if (caution.any { msg.contains(it) }) C.warn else C.ok
}

/**
 * Per-app profile editor. Up/Down choose a row, Left/Right (or Enter) change it, Esc leaves.
 * Nothing applies until Save.
 */
@Composable
fun EditorScreen(
    app: DisplayCompatApp,
    resolver: TraitsResolver,
    entry: TraitsResolver.Entry,
    display: SizePx,
    onDone: () -> Unit
) {
    val traits = remember(entry.pkg) { resolver.resolve(entry.pkg) }
    val loaded = remember(entry.pkg) { app.controller.load(traits) }
    val st = remember { EditorState(loaded.profile).also { it.msg = loaded.message() } }
    val rows = remember(st.p.profile) { st.visibleRows() }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val validation = ProfileValidator.validate(traits, st.p, st.advanced)
    val mini = remember { MiniSizeEvidence.read(app) }
    val plan = CanvasPlanner.plan(display, st.p, mini)
    val actions = EditorActions(app, st, traits, entry.pkg, display, onDone)

    Column(
        Modifier.fillMaxSize().padding(12.dp).focusRequester(fr).focusable()
            .onPreviewKeyEvent { e -> actions.onKey(e, rows.lastIndex) }
    ) {
        Text(entry.label, color = C.text, fontSize = 18.sp)
        Text(entry.pkg, color = C.muted, fontSize = 11.sp)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Preview(plan, st.p.letterboxBackground)
            Text(plan.note, color = C.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            rows.forEachIndexed { i, r ->
                EditorRowItem(
                    row = r,
                    value = st.valueOf(r),
                    selected = i == st.row,
                    onClick = {
                        st.row = i
                        actions.change(1)
                    }
                )
            }
            IssueList(validation.issues)
            st.msg?.let {
                Text(
                    it,
                    color = messageColor(it),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        Text("↑↓ row · ←→ change · Enter change/act · Esc back", color = C.muted, fontSize = 11.sp)
    }
}

@Composable
private fun EditorRowItem(row: EditorRow, value: String, selected: Boolean, onClick: () -> Unit) {
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
        Text(row.title, color = if (row == EditorRow.Reset) C.warn else C.text, fontSize = 14.sp)
        Text(value, color = C.accent, fontSize = 13.sp)
    }
}

@Composable
private fun IssueList(issues: List<Issue>) {
    issues.forEach {
        Text(
            "${it.severity.name.uppercase()}: ${it.message}",
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
            color = when (it.severity) {
                Severity.Blocked -> C.danger
                Severity.Warning -> C.warn
                Severity.Info -> C.muted
            }
        )
    }
}

@Composable
private fun Preview(plan: CanvasPlan, bg: LetterboxBackground) {
    val side = 110.dp
    Canvas(Modifier.size(side).border(1.dp, C.muted)) {
        val sx = size.width / plan.display.w
        val sy = size.height / plan.display.h
        drawRect(
            when (bg) {
                LetterboxBackground.Dark -> Color.Black
                LetterboxBackground.Light -> Color.White
                LetterboxBackground.Accent -> C.accent
                else -> defaultLetterboxBackdrop
            }
        )
        drawRect(
            C.surface,
            Offset(plan.canvas.l * sx, plan.canvas.t * sy),
            Size(
                plan.canvas.w * sx,
                plan.canvas.h * sy
            )
        )
        drawRect(
            C.accent,
            Offset(plan.canvas.l * sx, plan.canvas.t * sy),
            Size(
                plan.canvas.w * sx,
                plan.canvas.h * sy
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2f)
        )
    }
}
