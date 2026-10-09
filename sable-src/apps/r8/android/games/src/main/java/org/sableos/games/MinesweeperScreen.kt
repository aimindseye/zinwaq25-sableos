package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.sableos.design.SableActionButton
import org.sableos.design.SableSpacing
import org.sableos.design.SableTile
import org.sableos.design.SableValuePanel

private val MineOne = Color(0xFF5EA8FF)
private val MineTwo = Color(0xFF52D680)
private val MineThree = Color(0xFFFF7A6B)
private val MineFour = Color(0xFFB38AFF)
private val MineFive = Color(0xFFFF6FB5)

@Composable
internal fun MinesweeperScreen() {
    var state by remember {
        mutableStateOf(
            requireOk(
                NativeBridge.minesweeperStart(
                    9,
                    9,
                    10,
                    1803L,
                ),
            ),
        )
    }
    var view by remember {
        mutableStateOf(requireOk(NativeBridge.minesweeperView(state)))
    }
    var flagMode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Clear the field") }

    fun reset() {
        state =
            requireOk(
                NativeBridge.minesweeperStart(
                    9,
                    9,
                    10,
                    1803L,
                ),
            )
        view = requireOk(NativeBridge.minesweeperView(state))
        flagMode = false
        status = "New field"
    }

    fun applyResponse(response: String) {
        val fields = response.split('|')

        if (fields.size >= 3 && fields[0] == "OK") {
            state = fields[1]
            view = requireOk(NativeBridge.minesweeperView(state))
            status = fields.drop(2).joinToString(" ")
        } else {
            status = humanize(response)
        }
    }

    fun selectCell(
        x: Int,
        y: Int,
    ) {
        applyResponse(
            if (flagMode) {
                NativeBridge.minesweeperToggleFlag(
                    state,
                    x,
                    y,
                )
            } else {
                NativeBridge.minesweeperReveal(
                    state,
                    x,
                    y,
                )
            },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        SableValuePanel(
            label = if (flagMode) "flag mode" else "reveal mode",
            value = status,
            supportingText = "9 × 9 field · 10 mines",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            SableTile(
                title = "Reveal",
                subtitle = "open a tile",
                active = !flagMode,
                modifier = Modifier.weight(1f),
                onClick = { flagMode = false },
            )
            SableTile(
                title = "Flag",
                subtitle = "mark a mine",
                active = flagMode,
                modifier = Modifier.weight(1f),
                onClick = { flagMode = true },
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                ),
            tonalElevation = 0.dp,
        ) {
            Column {
                view.chunked(9).forEachIndexed { y, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEachIndexed { x, cell ->
                            MinesweeperCell(
                                cell = cell,
                                modifier = Modifier.weight(1f),
                                onClick = { selectCell(x, y) },
                            )
                        }
                    }
                }
            }
        }

        SableActionButton(
            text = "New field",
            modifier = Modifier.fillMaxWidth(),
            primary = false,
            onClick = ::reset,
        )
    }
}

@Composable
private fun MinesweeperCell(
    cell: Char,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val hidden = cell == 'H' || cell == '#'
    val flagged = cell == 'F'
    val mine = cell == '*' || cell == 'X'

    val container =
        when {
            flagged -> MaterialTheme.colorScheme.primary
            mine -> MaterialTheme.colorScheme.error
            hidden -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        }

    val content =
        when {
            flagged -> MaterialTheme.colorScheme.onPrimary
            mine -> MaterialTheme.colorScheme.onError
            else -> minesweeperNumberColor(cell)
        }

    Surface(
        onClick = onClick,
        modifier =
            modifier
                .aspectRatio(1f),
        shape = MaterialTheme.shapes.extraSmall,
        color = container,
        contentColor = content,
        border =
            BorderStroke(
                if (hidden || flagged) 1.dp else 0.5.dp,
                if (hidden || flagged) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
        tonalElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cellLabel(cell),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
    }
}

@Composable
private fun minesweeperNumberColor(cell: Char): Color =
    when (cell) {
        '1' -> MineOne
        '2' -> MineTwo
        '3' -> MineThree
        '4' -> MineFour
        '5', '6', '7', '8' -> MineFive
        else -> MaterialTheme.colorScheme.onSurface
    }

private fun cellLabel(cell: Char): String =
    when (cell) {
        '#', 'H', '0' -> ""
        'F' -> "⚑"
        '*', 'X' -> "✦"
        else -> cell.toString()
    }
