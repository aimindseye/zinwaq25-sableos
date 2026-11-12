@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sableos.titan2.displaycompat.android.DisplayCompatApp
import org.sableos.titan2.displaycompat.android.TraitsResolver
import org.sableos.titan2.displaycompat.android.resetToPreset
import org.sableos.titan2.displaycompat.core.AppCompatText
import org.sableos.titan2.displaycompat.core.AppLayout
import org.sableos.titan2.displaycompat.core.AppPage
import org.sableos.titan2.displaycompat.core.AppSize
import org.sableos.titan2.displaycompat.core.AspectProfile
import org.sableos.titan2.displaycompat.core.DisplayCompatText.t
import org.sableos.titan2.displaycompat.core.DisplayProfile
import org.sableos.titan2.displaycompat.core.LandscapeVideo
import org.sableos.titan2.displaycompat.core.ProfileController
import org.sableos.titan2.displaycompat.core.SaveStatus
import org.sableos.titan2.displaycompat.core.SizePx

/** The page's state; every property is Compose snapshot state. */
private class PageState(initial: DisplayProfile) {
    var p by mutableStateOf(initial)
    var row by mutableStateOf(AppPage.Row.Layout)
    var msg by mutableStateOf<String?>(null)

    /** A change that needs Enter to confirm its warnings. */
    var pending by mutableStateOf<DisplayProfile?>(null)
}

/** Where the page goes: the full editor for Custom, or back to the list. */
class PageNav(val onCustom: () -> Unit, val onDone: () -> Unit)

/** Saving, reset and keys of the page; created per composition so it sees the latest state. */
private class PageActions(
    private val app: DisplayCompatApp,
    private val resolver: TraitsResolver,
    private val entry: TraitsResolver.Entry,
    private val display: SizePx,
    private val st: PageState,
    private val nav: PageNav
) {
    fun save(candidate: DisplayProfile, acknowledged: Boolean = false) {
        val outcome = app.controller.save(
            resolver.resolve(entry.pkg),
            candidate,
            display,
            advanced = candidate.profile == AspectProfile.Custom,
            acknowledged = acknowledged
        )
        st.pending = candidate.takeIf { outcome.status == SaveStatus.NEEDS_ACK }
        st.msg = when (outcome.status) {
            SaveStatus.STORED -> t("applies_next_time", entry.label)
            SaveStatus.NEEDS_ACK -> t("press_enter_to_confirm", firstAck(outcome)).trim()
            else -> outcome.message()
        }
        if (outcome.status == SaveStatus.STORED) {
            st.p = app.store.get(entry.pkg)
            app.refreshDisplay()
        }
    }

    private fun firstAck(outcome: ProfileController.Outcome): String =
        outcome.validation.issues.firstOrNull { it.needsAck }?.message.orEmpty()

    fun pick(layout: AppLayout) {
        if (layout == AppLayout.Custom) {
            nav.onCustom()
        } else {
            AppLayout.apply(st.p, layout).takeIf { it != st.p }?.let { save(it) }
        }
    }

    fun reset() {
        st.msg = app.resetToPreset(entry.pkg) { resolver.resolve(it) }
        st.p = app.store.get(entry.pkg)
        st.pending = null
    }

    /** A tap on App size: the next size, back to 100% after the smallest. */
    fun nextSize() {
        st.row = AppPage.Row.Size
        save(AppPage.sizeStep(st.p, 1).takeIf { it != st.p } ?: st.p.copy(appSize = AppSize.Full))
    }

    fun toggleVideo() {
        st.row = AppPage.Row.Video
        save(AppPage.videoStep(st.p))
    }

    fun onKey(e: KeyEvent): Boolean {
        val action: (() -> Unit)? = when (e.key) {
            Key.DirectionUp -> {
                { move(-1) }
            }

            Key.DirectionDown -> {
                { move(1) }
            }

            Key.DirectionLeft -> {
                { AppPage.step(st.p, st.row, -1)?.let { save(it) } }
            }

            Key.DirectionRight -> {
                { AppPage.step(st.p, st.row, 1)?.let { save(it) } }
            }

            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                { enter() }
            }

            Key.Escape, Key.Back -> nav.onDone

            else -> null
        }
        if (action != null && e.type == KeyEventType.KeyDown) action()
        return action != null
    }

    private fun move(dir: Int) {
        val rows = AppPage.Row.entries
        st.row = rows[(st.row.ordinal + dir).coerceIn(0, rows.size - 1)]
    }

    private fun enter() {
        val pending = st.pending
        when {
            pending != null -> save(pending, acknowledged = true)
            st.row == AppPage.Row.Layout -> nav.onCustom()
            st.row == AppPage.Row.Reset -> reset()
            else -> AppPage.step(st.p, st.row, 1)?.let { save(it) }
        }
    }
}

/**
 * One app (2.0 App Compatibility): layout, app size and landscape video. Every change is saved at
 * once and applies the next time the app comes to the front. Custom opens the full 1.0 editor.
 */
@Composable
fun AppPageScreen(
    app: DisplayCompatApp,
    resolver: TraitsResolver,
    entry: TraitsResolver.Entry,
    display: SizePx,
    nav: PageNav
) {
    val st = remember(entry.pkg) { PageState(app.store.get(entry.pkg)) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val actions = PageActions(app, resolver, entry, display, st, nav)
    Column(
        Modifier.fillMaxSize().padding(horizontal = 14.dp)
            .focusRequester(fr).focusable()
            .onPreviewKeyEvent { actions.onKey(it) }
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(entry.label, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Light)
            val source = AppCompatText.source(entry.pkg, st.p, app.presets)
            Text(source, color = C.muted, fontSize = 12.sp)
        }
        SectionLabel(t("app_layout"))
        LayoutChoices(AppLayout.of(st.p), st.row == AppPage.Row.Layout) { actions.pick(it) }
        val sizes = AppSize.entries.joinToString(" · ") { marked(it.label, it == st.p.appSize) }
        ChoiceRow(t("app_size"), t("app_size_help"), sizes, st.row == AppPage.Row.Size) {
            actions.nextSize()
        }
        SectionLabel(t("landscape_video"))
        val video = LandscapeVideo.of(st.p)
        val videos = LandscapeVideo.entries.joinToString(" · ") { marked(it.label, it == video) }
        ChoiceRow(video.label, video.help, videos, st.row == AppPage.Row.Video) {
            actions.toggleVideo()
        }
        val hasPreset = app.presets[entry.pkg]?.let { !it.isNative } == true
        val resetTitle = if (hasPreset) t("reset_to_preset") else t("reset_to_square")
        ChoiceRow(resetTitle, "", "", st.row == AppPage.Row.Reset) {
            st.row = AppPage.Row.Reset
            actions.reset()
        }
        PageMessage(st)
        Text(
            t("page_hint"),
            color = C.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

private fun marked(label: String, on: Boolean): String = if (on) "[$label]" else label

@Composable
private fun PageMessage(st: PageState) {
    val msg = st.msg ?: return
    val warn = st.pending != null || msg.contains(t("could_not"))
    Text(
        msg,
        color = if (warn) C.warn else C.muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** The little outline drawn for each layout. */
private fun shapeOf(layout: AppLayout): DpSize = when (layout) {
    AppLayout.Square -> DpSize(36.dp, 36.dp)
    AppLayout.TallPhone -> DpSize(20.dp, 40.dp)
    AppLayout.Custom -> DpSize(28.dp, 40.dp)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = C.muted,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** Square, Tall phone and Custom, each drawn with its shape. */
@Composable
private fun LayoutChoices(current: AppLayout, focused: Boolean, onPick: (AppLayout) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppLayout.entries.forEach { layout ->
            val on = layout == current
            Column(
                Modifier.weight(1f)
                    .background(C.surface)
                    .border(
                        if (on) 2.dp else 0.dp,
                        if (on &&
                            focused
                        ) {
                            C.accent
                        } else if (on) {
                            C.muted
                        } else {
                            C.surface
                        }
                    )
                    .clickable { onPick(layout) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(shapeOf(layout)).border(1.dp, C.text).background(C.window))
                }
                Text(
                    layout.label,
                    color = C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(layout.help, color = C.muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    help: String,
    value: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp)
            .border(if (focused) 2.dp else 0.dp, if (focused) C.accent else C.window)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp)
            if (help.isNotEmpty()) Text(help, color = C.muted, fontSize = 11.sp)
        }
        if (value.isNotEmpty()) {
            Text(
                value,
                color = C.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
