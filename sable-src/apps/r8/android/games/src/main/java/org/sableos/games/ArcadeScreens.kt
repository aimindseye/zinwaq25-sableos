package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/** Shape colours for the blocks (I, O, T, S, Z, J, L) and the brick rows, chosen to read on dark and light. */
private val PieceColors =
    listOf(
        Color(0xFF3FC5F0),
        Color(0xFFFFC857),
        Color(0xFF9A63E8),
        Color(0xFF35C66B),
        Color(0xFFE34D59),
        Color(0xFF3189D8),
        Color(0xFFF08A4B),
    )

private const val PADDLE_STEP = 0.06f
private const val SWIPE_PX = 32f
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 0.05f

/** The board's frame: a square surface with the theme's outline, as in the other Sable games. */
@Composable
private fun ArcadeBoard(
    side: Dp,
    modifier: Modifier = Modifier,
    draw: DrawScope.() -> Unit,
) {
    Surface(
        modifier = modifier.size(side),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
    ) {
        Canvas(Modifier.size(side), onDraw = draw)
    }
}

/** A run state for the timed games: playing, paused by P, or finished. */
private fun runStatus(
    over: Boolean,
    paused: Boolean,
    playing: String,
): String =
    when {
        over -> "Game over · Space plays again"
        paused -> "Paused · P resumes"
        else -> playing
    }

@Composable
internal fun SnakeScreen() {
    val random = remember { Random(System.nanoTime()) }
    var game by remember { mutableStateOf(SnakeGame.start(random)) }
    var paused by remember { mutableStateOf(false) }
    var best by remember { mutableIntStateOf(0) }

    fun reset() {
        game = SnakeGame.start(random)
        paused = false
    }

    LaunchedEffect(game.alive, paused) {
        while (game.alive && !paused) {
            delay(game.stepMillis)
            game = game.step(random)
            if (game.score > best) best = game.score
        }
    }

    GameKeyHandler(GameKind.Snake) { command ->
        when (command) {
            is GameCommand.Move -> game = game.turn(command.dx, command.dy)
            GameCommand.Pause -> paused = !paused && game.alive
            GameCommand.Primary -> if (!game.alive) reset() else paused = false
            GameCommand.NewGame -> reset()
            else -> return@GameKeyHandler false
        }
        true
    }

    GameFrame(
        title = "Snake",
        subtitle = "Eat, grow and keep clear of the walls and your tail.",
        statusLabel = "score · best $best",
        status = runStatus(!game.alive, paused, game.score.toString()),
        hint = "Arrows or the trackpad turn, swipes too; P pauses, N starts over.",
        actions =
            listOf(
                GameAction(label = if (paused) "Resume" else "Pause", key = "P", active = paused) {
                    paused =
                        !paused && game.alive
                },
                GameAction(label = "New", key = "N", onClick = ::reset),
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        ArcadeBoard(
            side = side,
            modifier =
                Modifier.pointerInput(Unit) {
                    var drag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDragEnd = {
                            if (abs(drag.x) > abs(drag.y) && abs(drag.x) > SWIPE_PX) {
                                game = game.turn(if (drag.x > 0) 1 else -1, 0)
                            } else if (abs(drag.y) > SWIPE_PX) {
                                game = game.turn(0, if (drag.y > 0) 1 else -1)
                            }
                        },
                    ) { _, amount -> drag += amount }
                },
        ) {
            val cell = size.width / game.size

            fun square(
                at: Cell,
                color: Color,
            ) = drawRoundRect(
                color = color,
                topLeft = Offset(at.x * cell + 1f, at.y * cell + 1f),
                size = Size(cell - 2f, cell - 2f),
                cornerRadius = CornerRadius(cell / 4),
            )
            square(game.food, PieceColors[4])
            game.body.forEachIndexed { index, part ->
                square(
                    part,
                    if (index ==
                        0
                    ) {
                        colors.primary
                    } else {
                        colors.primary.copy(alpha = 0.6f)
                    },
                )
            }
        }
    }
}

@Composable
internal fun BlocksScreen() {
    val random = remember { Random(System.nanoTime()) }
    var game by remember { mutableStateOf(BlocksGame.start(random)) }
    var paused by remember { mutableStateOf(false) }

    fun reset() {
        game = BlocksGame.start(random)
        paused = false
    }

    LaunchedEffect(game.over, paused) {
        while (!game.over && !paused) {
            delay(game.fallMillis)
            game = game.fall(random)
        }
    }

    GameKeyHandler(GameKind.Blocks) { command ->
        if (paused && command != GameCommand.Pause && command != GameCommand.NewGame) return@GameKeyHandler true
        when (command) {
            is GameCommand.Move -> {
                game =
                    when {
                        command.dx != 0 -> game.move(command.dx)
                        command.dy < 0 -> game.rotate()
                        else -> game.fall(random)
                    }
            }

            GameCommand.Primary -> {
                if (game.over) reset() else game = game.drop(random)
            }

            GameCommand.Pause -> {
                paused = !paused && !game.over
            }

            GameCommand.NewGame -> {
                reset()
            }

            else -> {
                return@GameKeyHandler false
            }
        }
        true
    }

    GameFrame(
        title = "Blocks",
        subtitle = "Fit the falling pieces into full rows.",
        statusLabel = "score · level ${game.level}",
        status = runStatus(game.over, paused, "${game.score} · ${game.lines} rows"),
        hint = "Left/Right move, Up turns, Down drops a row, Space drops it; tap turns, swipes move. P pauses.",
        actions =
            listOf(
                GameAction(label = if (paused) "Resume" else "Pause", key = "P", active = paused) {
                    paused =
                        !paused && !game.over
                },
                GameAction(label = "New", key = "N", onClick = ::reset),
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        ArcadeBoard(
            side = side,
            modifier =
                Modifier
                    .pointerInput(Unit) { detectTapGestures { game = game.rotate() } }
                    .pointerInput(Unit) {
                        var drag = Offset.Zero
                        detectDragGestures(
                            onDragStart = { drag = Offset.Zero },
                            onDragEnd = {
                                game =
                                    when {
                                        abs(drag.x) > abs(drag.y) && abs(drag.x) > SWIPE_PX -> {
                                            game.move(
                                                if (drag.x >
                                                    0
                                                ) {
                                                    1
                                                } else {
                                                    -1
                                                },
                                            )
                                        }

                                        drag.y > SWIPE_PX -> {
                                            game.drop(random)
                                        }

                                        else -> {
                                            game
                                        }
                                    }
                            },
                        ) { _, amount -> drag += amount }
                    },
        ) {
            val cell = size.height / BlocksGame.HEIGHT
            val left = (size.width - cell * BlocksGame.WIDTH) / 2
            drawRect(colors.surfaceVariant, Offset(left, 0f), Size(cell * BlocksGame.WIDTH, size.height))

            fun block(
                x: Int,
                y: Int,
                color: Color,
            ) = drawRoundRect(
                color,
                Offset(left + x * cell + 1f, y * cell + 1f),
                Size(cell - 2f, cell - 2f),
                CornerRadius(cell / 6),
            )
            game.well.forEachIndexed { y, row ->
                row.forEachIndexed { x, v ->
                    if (v >
                        0
                    ) {
                        block(x, y, PieceColors[v - 1])
                    }
                }
            }
            game
                .ghost()
                .absolute
                .filter { it.y >= 0 }
                .forEach { block(it.x, it.y, colors.outlineVariant) }
            game.piece.absolute
                .filter { it.y >= 0 }
                .forEach { block(it.x, it.y, PieceColors[game.piece.kind]) }
            // The next piece, small, in the space right of the well.
            val small = cell * 0.6f
            BlocksGame.SHAPES[game.next].forEach {
                drawRect(
                    PieceColors[game.next],
                    Offset(
                        left + cell * BlocksGame.WIDTH + small * (it.x + 2),
                        small * (it.y + 2),
                    ),
                    Size(small - 1f, small - 1f),
                )
            }
        }
    }
}

@Composable
internal fun BricksScreen() {
    var game by remember { mutableStateOf(BricksGame()) }
    var paused by remember { mutableStateOf(false) }

    fun reset() {
        game = BricksGame()
        paused = false
    }

    LaunchedEffect(game.launched, paused, game.over) {
        var last = 0L
        while (game.launched && !paused && !game.over) {
            withFrameNanos { now ->
                if (last != 0L) {
                    game = game.step(((now - last) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_SECONDS))
                }
                last = now
            }
        }
    }

    GameKeyHandler(GameKind.Bricks) { command ->
        when (command) {
            is GameCommand.Move -> if (!paused && command.dx != 0) game = game.movePaddle(command.dx * PADDLE_STEP)
            GameCommand.Primary -> if (game.over) reset() else game = game.launch()
            GameCommand.Pause -> paused = !paused && game.launched
            GameCommand.NewGame -> reset()
            else -> return@GameKeyHandler false
        }
        true
    }

    GameFrame(
        title = "Bricks",
        subtitle = "Keep the ball in play and break every brick.",
        statusLabel = "score · lives ${game.lives} · level ${game.level}",
        status =
            when {
                game.over -> "Game over · Space plays again"
                paused -> "Paused · P resumes"
                !game.launched -> "${game.score} · Space launches"
                else -> game.score.toString()
            },
        hint = "Left/Right or the trackpad move the paddle, or drag it; Space or a tap launches. P pauses.",
        actions =
            listOf(
                GameAction(label = if (paused) "Resume" else "Pause", key = "P", active = paused) {
                    paused =
                        !paused && game.launched
                },
                GameAction(label = "New", key = "N", onClick = ::reset),
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        ArcadeBoard(
            side = side,
            modifier =
                Modifier
                    .pointerInput(Unit) { detectTapGestures { game = if (game.over) BricksGame() else game.launch() } }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> game = game.paddleTo(change.position.x / size.width) }
                    },
        ) {
            val w = size.width
            val gap = 2f
            val brickW = w / BricksGame.COLUMNS
            val brickH = w * BricksGame.BRICK_HEIGHT
            game.bricks.forEachIndexed { index, hits ->
                if (hits <= 0) return@forEachIndexed
                val row = index / BricksGame.COLUMNS
                val col = index % BricksGame.COLUMNS
                drawRoundRect(
                    PieceColors[row % PieceColors.size],
                    Offset(col * brickW + gap, w * BricksGame.WALL_TOP + row * brickH + gap),
                    Size(brickW - gap * 2, brickH - gap * 2),
                    CornerRadius(4f),
                )
            }
            val paddleW = w * BricksGame.PADDLE_WIDTH
            drawRoundRect(
                colors.primary,
                Offset(game.paddleX * w - paddleW / 2, w * BricksGame.PADDLE_Y),
                Size(paddleW, w * BricksGame.PADDLE_HEIGHT),
                CornerRadius(w * BricksGame.PADDLE_HEIGHT / 2),
            )
            drawCircle(colors.onSurface, w * BricksGame.BALL_RADIUS, Offset(game.ballX * w, game.ballY * w))
        }
    }
}
