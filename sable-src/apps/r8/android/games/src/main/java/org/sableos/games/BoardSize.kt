package org.sableos.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp

/**
 * Share of the window height a square board may take. On the Q25's square 411 dp screen a full-width
 * board is as tall as the whole window, which pushes the controls and part of the board below the
 * fold; capping it keeps the board and its main controls visible together.
 */
private const val BOARD_HEIGHT_SHARE = 0.6f

/** The largest side a square game board may have in this window; full width still applies when smaller. */
@Composable
internal fun boardMaxSide(): Dp {
    val height = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { (height * BOARD_HEIGHT_SHARE).toDp() }
}
