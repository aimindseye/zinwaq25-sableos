package org.sableos.reader.ui.epub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.sableos.reader.input.EpubKeyCommand
import org.sableos.reader.input.EpubKeyPolicy
import org.sableos.reader.input.LocalEditableFocus
import org.sableos.reader.input.LocalReaderKeyDispatcher

/**
 * Registers the EPUB key handler with the Activity-level dispatcher for as long as the screen is composed.
 * Page turns go straight to the navigator; every other command is the screen's to interpret via [onCommand].
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun EpubKeyboardEffect(
    scrollMode: Boolean,
    overlayOpen: Boolean,
    navigator: () -> EpubNavigatorFragment?,
    onCommand: (EpubKeyCommand) -> Unit,
) {
    val dispatcher = LocalReaderKeyDispatcher.current
    val editable = LocalEditableFocus.current
    val scope = rememberCoroutineScope()
    val latestScroll = rememberUpdatedState(scrollMode)
    val latestOverlay = rememberUpdatedState(overlayOpen)
    val latestNavigator = rememberUpdatedState(navigator)
    val latestCommand = rememberUpdatedState(onCommand)
    DisposableEffect(dispatcher, editable) {
        val unregister = dispatcher.register { input ->
            val command = EpubKeyPolicy.decide(input, latestScroll.value, editable.focused, latestOverlay.value)
            command?.let {
                execute(it, latestNavigator.value(), latestCommand.value) { block -> scope.launch { block() } }
            }
            command != null
        }
        onDispose(unregister)
    }
}

@OptIn(ExperimentalReadiumApi::class)
private fun execute(
    command: EpubKeyCommand,
    navigator: EpubNavigatorFragment?,
    onCommand: (EpubKeyCommand) -> Unit,
    launch: (suspend () -> Unit) -> Unit,
) {
    when (command) {
        EpubKeyCommand.TURN_LEFT -> launch { navigator?.goPhysicalLeft() }
        EpubKeyCommand.TURN_RIGHT -> launch { navigator?.goPhysicalRight() }
        EpubKeyCommand.FORWARD -> launch { navigator?.goForward(animated = false) }
        EpubKeyCommand.BACKWARD -> launch { navigator?.goBackward(animated = false) }
        else -> onCommand(command)
    }
}

private fun EpubNavigatorFragment.goPhysicalLeft(): Boolean =
    when (overflow.value.readingProgression) {
        ReadingProgression.LTR -> goBackward(animated = false)
        ReadingProgression.RTL -> goForward(animated = false)
    }

private fun EpubNavigatorFragment.goPhysicalRight(): Boolean =
    when (overflow.value.readingProgression) {
        ReadingProgression.LTR -> goForward(animated = false)
        ReadingProgression.RTL -> goBackward(animated = false)
    }
