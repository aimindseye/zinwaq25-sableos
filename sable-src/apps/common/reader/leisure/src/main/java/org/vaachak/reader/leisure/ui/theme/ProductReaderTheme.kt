package org.vaachak.reader.leisure.ui.theme

import androidx.compose.runtime.Composable
import org.sableos.design.SableTheme
import org.sableos.design.rememberGlobalSableAppearance
import org.vaachak.reader.core.domain.model.ThemeMode

/**
 * Normal Reader chrome follows the SableOS system appearance. E-Ink is an explicit, reading-only
 * high-contrast override and deliberately keeps the Reader's own theme.
 */
@Composable
fun ProductReaderTheme(
    themeMode: ThemeMode,
    contrast: Float,
    content: @Composable () -> Unit,
) {
    if (themeMode == ThemeMode.E_INK) {
        VaachakTheme(
            themeMode = themeMode,
            contrast = contrast,
            content = content,
        )
        return
    }

    SableTheme(
        appearance = rememberGlobalSableAppearance(),
        content = content,
    )
}
