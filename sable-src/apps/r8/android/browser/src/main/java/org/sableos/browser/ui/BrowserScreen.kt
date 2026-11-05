package org.sableos.browser.ui

import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import org.sableos.browser.core.BrowserText.t
import org.sableos.browser.core.UrlInput
import kotlin.math.roundToInt

/**
 * Sable Browser on the Q25's square screen: the address bar, a thin progress line, the page with
 * the trackpad pointer over it, and one line of keys at the bottom.
 */
@Composable
fun BrowserScreen(
    state: BrowserState,
    webView: WebView,
    onGo: (String) -> Unit,
    onLeaveAddress: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background)) {
        AddressBar(state, onGo, onLeaveAddress)
        if (state.progress < BrowserState.FULL) {
            LinearProgressIndicator(
                progress = { state.progress / BrowserState.FULL.toFloat() },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (state.pointerShown) Pointer(state)
            if (state.failed) {
                Text(
                    text = t("page_failed"),
                    color = colors.onSurface,
                    modifier = Modifier.align(Alignment.Center).background(colors.surface).padding(16.dp),
                )
            }
        }
        KeyLine(state)
    }
}

@Composable
private fun AddressBar(
    state: BrowserState,
    onGo: (String) -> Unit,
    onLeaveAddress: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var value by remember { mutableStateOf(TextFieldValue()) }
    LaunchedEffect(state.url, state.addressFocused) {
        if (!state.addressFocused) value = TextFieldValue(UrlInput.display(state.url))
    }
    LaunchedEffect(state.addressRequest) {
        if (state.addressRequest == 0) return@LaunchedEffect
        // L selects the whole address, so typing replaces it.
        val text = state.url?.takeIf { it != BLANK }.orEmpty()
        value = TextFieldValue(text, TextRange(0, text.length))
        runCatching { focus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (value.text.isEmpty()) {
            Text(t("address_hint"), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo(value.text) }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { state.addressFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val escape = event.key == Key.Escape
                        if (escape) onLeaveAddress()
                        escape
                    }.semantics { contentDescription = t("address_hint") },
        )
    }
}

/** An arrow at the pointer; over a link it gets the accent ring, so you see what a click opens. */
@Composable
private fun Pointer(state: BrowserState) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .offset { IntOffset(state.pointerX.roundToInt(), state.pointerY.roundToInt()) }
            .size(POINTER_SIZE),
    ) {
        val w = size.width
        val arrow =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, w * ARROW_LENGTH)
                lineTo(w * ARROW_NOTCH_X, w * ARROW_NOTCH_Y)
                lineTo(w * ARROW_TIP, w * ARROW_TIP)
                close()
            }
        if (state.overLink) drawCircle(accent, radius = w / 2, center = Offset.Zero, style = Stroke(w * RING))
        drawPath(arrow, Color.White)
        drawPath(arrow, Color.Black, style = Stroke(w * OUTLINE))
    }
}

/** The keys, or for a moment a status (pointer on or off, a download, a link no app opens). */
@Composable
private fun KeyLine(state: BrowserState) {
    val status = state.status
    LaunchedEffect(status) {
        if (status != null) {
            delay(STATUS_MS)
            state.status = null
        }
    }
    Text(
        text = status ?: t("keys_hint"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private const val BLANK = "about:blank"
private const val STATUS_MS = 2_500L
private val POINTER_SIZE = 18.dp
private const val ARROW_LENGTH = 1f
private const val ARROW_NOTCH_X = 0.3f
private const val ARROW_NOTCH_Y = 0.72f
private const val ARROW_TIP = 0.7f
private const val RING = 0.12f
private const val OUTLINE = 0.08f
