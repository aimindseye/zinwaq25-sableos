package org.sableos.reader.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/** Window-size based layout profile (never a device name). */
@Composable
fun rememberReaderLayoutProfile(): ReaderLayoutProfile {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        ReaderLayoutProfile(configuration.screenWidthDp.toFloat(), configuration.screenHeightDp.toFloat())
    }
}
