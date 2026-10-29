package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.sableos.design.SableSpacing
import org.sableos.games.GamesText.t
import kotlin.math.abs

private val Tile2 = Color(0xFF26384B)
private val Tile4 = Color(0xFF315A78)
private val Tile8 = Color(0xFFF08A4B)
private val Tile16 = Color(0xFFEE5D8A)
private val Tile32 = Color(0xFF9A63E8)
private val Tile64 = Color(0xFFE34D59)
private val Tile128 = Color(0xFFD9A441)
private val Tile256 = Color(0xFF32B67A)
private val Tile512 = Color(0xFF3189D8)
private val Tile1024 = Color(0xFF8E5CE6)
private val Tile2048 = Color(0xFFFFC857)

@Composable
internal fun Game2048Screen() {
    var board by remember { mutableStateOf(initial2048Board()) }
    var score by remember { mutableLongStateOf(0L) }

    fun slide(direction: Int) {
        val result =
            NativeBridge.game2048Slide(
                board,
                direction,
            )

        check(result.size == 17)

        board =
            IntArray(16) { index ->
                result[index].toInt()
            }
        score += result[16]
    }

    fun reset() {
        board = initial2048Board()
        score = 0L
    }

    GameKeyHandler(GameKind.Game2048) { command ->
        when (command) {
            is GameCommand.Move -> slide(GameKeymap.slideDirection(command))
            GameCommand.NewGame -> reset()
            else -> return@GameKeyHandler false
        }
        true
    }

    GameFrame(
        title = "2048",
        subtitle = t("slide_merge_and_build_the"),
        statusLabel = "score",
        status = score.toString(),
        hint = t("swipe_the_board_or_use"),
        actions = listOf(GameAction(label = t("new_label"), key = "N", onClick = ::reset)),
        tallExtras = {
            Text(
                text = t("swipe_left_right_up_or"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) { side ->
        Game2048Board(
            side = side,
            board = board,
            onSlide = ::slide,
        )
    }
}

@Composable
private fun Game2048Board(
    side: Dp,
    board: IntArray,
    onSlide: (Int) -> Unit,
) {
    var dragX = 0f
    var dragY = 0f

    Surface(
        modifier =
            Modifier
                .size(side)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragEnd = {
                            val threshold = 32f
                            if (
                                abs(dragX) < threshold &&
                                abs(dragY) < threshold
                            ) {
                                return@detectDragGestures
                            }

                            if (abs(dragX) > abs(dragY)) {
                                onSlide(if (dragX < 0f) 0 else 1)
                            } else {
                                onSlide(if (dragY < 0f) 2 else 3)
                            }
                        },
                        onDrag = { _, dragAmount ->
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        },
                    )
                },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border =
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(SableSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
        ) {
            board.toList().chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SableSpacing.Sm),
                ) {
                    row.forEach { value ->
                        Tile2048(
                            value = value,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile2048(
    value: Int,
    modifier: Modifier = Modifier,
) {
    val container = if (value == 0) MaterialTheme.colorScheme.surfaceVariant else tileColor(value)
    val content =
        if (value >= 128) {
            Color(0xFF071016)
        } else {
            Color.White
        }

    Surface(
        modifier =
            modifier
                .aspectRatio(1f),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border =
            BorderStroke(
                1.dp,
                if (value == 0) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    container.copy(alpha = 0.9f)
                },
            ),
        tonalElevation = 0.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (value == 0) "" else value.toString(),
                style =
                    if (value >= 1024) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                fontWeight = FontWeight.Bold,
                color = content,
            )
        }
    }
}

private fun tileColor(value: Int): Color =
    when (value) {
        2 -> Tile2
        4 -> Tile4
        8 -> Tile8
        16 -> Tile16
        32 -> Tile32
        64 -> Tile64
        128 -> Tile128
        256 -> Tile256
        512 -> Tile512
        1024 -> Tile1024
        else -> Tile2048
    }

private fun initial2048Board(): IntArray =
    intArrayOf(
        2,
        2,
        4,
        4,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
    )
