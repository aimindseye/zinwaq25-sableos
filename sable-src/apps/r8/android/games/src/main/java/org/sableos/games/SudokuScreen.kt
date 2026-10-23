package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sableos.design.SableSpacing
import org.sableos.design.SableValuePanel

private const val SUDOKU_SIZE = 9

private const val DEFAULT_SUDOKU =
    "530070000600195000098000060800060003400803001700020006060000280000419005000080079"

@Composable
internal fun SudokuScreen() {
    var state by remember {
        mutableStateOf(requireOk(NativeBridge.sudokuStart(DEFAULT_SUDOKU)))
    }
    var board by remember {
        mutableStateOf(requireOk(NativeBridge.sudokuBoard(state)))
    }
    var selectedRow by remember { mutableIntStateOf(0) }
    var selectedCol by remember { mutableIntStateOf(2) }
    var status by remember { mutableStateOf("Choose a cell") }
    val history = remember { mutableStateListOf<String>() }

    fun reset() {
        state = requireOk(NativeBridge.sudokuStart(DEFAULT_SUDOKU))
        board = requireOk(NativeBridge.sudokuBoard(state))
        selectedRow = 0
        selectedCol = 2
        history.clear()
        status = "New puzzle"
    }

    fun setValue(value: Int) {
        val response =
            NativeBridge.sudokuSet(
                state,
                selectedRow,
                selectedCol,
                value,
            )

        if (response.startsWith("OK:")) {
            history.add(state)
            state = response.removePrefix("OK:")
            board = requireOk(NativeBridge.sudokuBoard(state))
            status =
                if (NativeBridge.sudokuSolved(state)) {
                    "Solved"
                } else if (value == 0) {
                    "Cell erased"
                } else {
                    "Move accepted"
                }
        } else {
            status = humanize(response)
        }
    }

    fun undo() {
        if (history.isEmpty()) {
            status = "Nothing to undo"
            return
        }

        state = history.removeAt(history.lastIndex)
        board = requireOk(NativeBridge.sudokuBoard(state))
        status = "Last move undone"
    }

    fun erase() {
        setValue(0)
    }

    fun select(
        row: Int,
        col: Int,
    ) {
        selectedRow = row
        selectedCol = col
        status =
            if (isGiven(row, col)) {
                "Fixed clue"
            } else {
                "Row ${row + 1}, column ${col + 1}"
            }
    }

    GameKeyHandler(GameKind.Sudoku) { command ->
        when (command) {
            is GameCommand.Move -> {
                val (row, col) = GameKeymap.moveCursor(selectedRow, selectedCol, command, SUDOKU_SIZE)
                select(row, col)
            }

            is GameCommand.Digit -> {
                setValue(command.value)
            }

            GameCommand.Erase -> {
                erase()
            }

            GameCommand.Undo -> {
                undo()
            }

            GameCommand.NewGame -> {
                reset()
            }

            GameCommand.Primary, GameCommand.Flag -> {
                return@GameKeyHandler false
            }
        }
        true
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(SableSpacing.Lg),
    ) {
        SableValuePanel(
            label = "puzzle",
            value = status,
            supportingText = "Arrows move, 1-9 (or W E R S D F Z X C) fill, Del erases, U undoes, N starts over.",
        )

        SudokuBoard(
            board = board,
            selectedRow = selectedRow,
            selectedCol = selectedCol,
            onSelect = ::select,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            (1..9).forEach { value ->
                SudokuNumberKey(
                    value = value,
                    modifier = Modifier.weight(1f),
                    onClick = { setValue(value) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            SudokuBottomAction(
                symbol = "↶",
                label = "Undo",
                modifier = Modifier.weight(1f),
                onClick = ::undo,
            )
            SudokuBottomAction(
                symbol = "⌫",
                label = "Erase",
                modifier = Modifier.weight(1f),
                onClick = ::erase,
            )
            SudokuBottomAction(
                symbol = "↻",
                label = "New game",
                modifier = Modifier.weight(1f),
                onClick = ::reset,
            )
        }
    }
}

@Composable
private fun SudokuBoard(
    board: String,
    selectedRow: Int,
    selectedCol: Int,
    onSelect: (Int, Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = boardMaxSide()),
    ) {
        (0 until 3).forEach { blockRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                (0 until 3).forEach { blockCol ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RectangleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border =
                            BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.outline,
                            ),
                    ) {
                        Column {
                            (0 until 3).forEach { innerRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    (0 until 3).forEach { innerCol ->
                                        val row = blockRow * 3 + innerRow
                                        val col = blockCol * 3 + innerCol
                                        SudokuCell(
                                            row = row,
                                            col = col,
                                            cell = board[row * 9 + col],
                                            highlight = cellHighlight(row, col, selectedRow, selectedCol),
                                            onSelect = onSelect,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CellHighlight { SELECTED, PEER, NONE }

private fun cellHighlight(
    row: Int,
    col: Int,
    selectedRow: Int,
    selectedCol: Int,
): CellHighlight =
    when {
        row == selectedRow && col == selectedCol -> CellHighlight.SELECTED
        row == selectedRow || col == selectedCol -> CellHighlight.PEER
        else -> CellHighlight.NONE
    }

@Composable
private fun RowScope.SudokuCell(
    row: Int,
    col: Int,
    cell: Char,
    highlight: CellHighlight,
    onSelect: (Int, Int) -> Unit,
) {
    val given = isGiven(row, col)
    val selected = highlight == CellHighlight.SELECTED

    val container =
        when {
            selected -> {
                MaterialTheme.colorScheme.primary
            }

            highlight == CellHighlight.PEER -> {
                MaterialTheme.colorScheme.surfaceVariant
            }

            else -> {
                MaterialTheme.colorScheme.surface
            }
        }
    val content =
        when {
            selected -> {
                MaterialTheme.colorScheme.onPrimary
            }

            given -> {
                MaterialTheme.colorScheme.onSurface
            }

            else -> {
                MaterialTheme.colorScheme.primary
            }
        }

    Surface(
        onClick = { onSelect(row, col) },
        modifier =
            Modifier
                .weight(1f)
                .aspectRatio(1f),
        shape = RectangleShape,
        color = container,
        contentColor = content,
        border =
            BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        tonalElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (cell == '0') {
                        ""
                    } else {
                        cell.toString()
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight =
                    if (given) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
            )
        }
    }
}

@Composable
private fun SudokuNumberKey(
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.extraSmall,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SudokuBottomAction(
    symbol: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        tonalElevation = 0.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun isGiven(
    row: Int,
    col: Int,
): Boolean = DEFAULT_SUDOKU[row * 9 + col] != '0'
