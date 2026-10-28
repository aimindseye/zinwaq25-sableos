package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import org.sableos.design.SableActionButton
import org.sableos.design.SableHeroHeader
import org.sableos.design.SableSpacing
import org.sableos.design.SableValuePanel

private const val GAMES_EYEBROW = "Sable Games"

/** A game control: the on-screen button and the key that does the same. */
internal data class GameAction(
    val label: String,
    val key: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Where the board and controls go. A window less than [TALL_RATIO] times as tall as it is wide
 * (the Q25's 720x720 square) puts the board on the left at the full height and the title, status
 * and controls in a narrow rail on the right, so the whole game shows without scrolling. A tall
 * phone keeps the header, status panel, board and buttons in one column.
 */
internal object GameLayout {
    const val TALL_RATIO = 1.3f
    val RailWidth = 88.dp
    val Gap = 8.dp
    val Edge = 8.dp

    fun isSquare(
        widthDp: Float,
        heightDp: Float,
    ): Boolean = heightDp < widthDp * TALL_RATIO

    /** The board's side in a square window: the height, or the width left beside the rail. */
    fun squareBoardSide(
        width: Dp,
        height: Dp,
    ): Dp = min(height - Edge * 2, width - Edge * 2 - RailWidth - Gap)

    /** The board's side in a tall window: the width, at most 60 % of the height. */
    fun tallBoardSide(
        width: Dp,
        height: Dp,
    ): Dp = min(width - SableSpacing.ScreenHorizontal * 2, height * 0.6f)
}

/** One game's screen, laid out for the window ([GameLayout]); colours come from the Sable theme. */
@Composable
internal fun GameFrame(
    title: String,
    subtitle: String,
    statusLabel: String,
    status: String,
    hint: String,
    actions: List<GameAction>,
    tallExtras: @Composable ColumnScope.() -> Unit = {},
    board: @Composable (side: Dp) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val width = maxWidth
            val height = maxHeight
            if (GameLayout.isSquare(width.value, height.value)) {
                SquareGame(
                    title = title,
                    statusLabel = statusLabel,
                    status = status,
                    actions = actions,
                    side = GameLayout.squareBoardSide(width, height),
                    board = board,
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = SableSpacing.ScreenHorizontal,
                                vertical = SableSpacing.ScreenVertical,
                            ),
                    verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
                ) {
                    SableHeroHeader(eyebrow = GAMES_EYEBROW, title = title, subtitle = subtitle)
                    SableValuePanel(label = statusLabel, value = status, supportingText = hint)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        board(GameLayout.tallBoardSide(width, height))
                    }
                    tallExtras()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                    ) {
                        actions.forEach { action ->
                            SableActionButton(
                                text = action.label,
                                modifier = Modifier.weight(1f),
                                primary = action.active,
                                onClick = action.onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquareGame(
    title: String,
    statusLabel: String,
    status: String,
    actions: List<GameAction>,
    side: Dp,
    board: @Composable (side: Dp) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(GameLayout.Edge),
        horizontalArrangement = Arrangement.spacedBy(GameLayout.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        board(side)
        Column(
            modifier = Modifier.width(GameLayout.RailWidth).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Xs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            actions.forEach { action -> RailButton(action) }
        }
    }
}

/** A rail control: the label, and below it the key that does the same. */
@Composable
private fun RailButton(action: GameAction) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = action.onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (action.active) colors.primary else colors.surface,
        contentColor = if (action.active) colors.onPrimary else colors.onSurface,
        border = BorderStroke(1.dp, if (action.active) colors.primary else colors.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = action.key,
                style = MaterialTheme.typography.labelSmall,
                color = if (action.active) colors.onPrimary else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
