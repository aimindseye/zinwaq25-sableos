package org.sableos.hub.ui

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.sableos.design.LocalSableFlatPanels
import org.sableos.design.SableGlobalTheme

/**
 * Sable Hub's screens in the BlackBerry 10 look (Sable 2.0): the global Sable theme, with every
 * panel drawn as a flat list section on black instead of a card.
 */
@Composable
internal fun HubTheme(
    window: Window? = null,
    content: @Composable () -> Unit,
) {
    SableGlobalTheme(window = window) {
        CompositionLocalProvider(LocalSableFlatPanels provides true, content = content)
    }
}
