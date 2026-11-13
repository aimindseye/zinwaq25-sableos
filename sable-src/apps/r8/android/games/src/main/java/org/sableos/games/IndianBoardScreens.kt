package org.sableos.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private val TOKEN_COLORS = listOf(Color(0xFFD93A3A), Color(0xFF2E9A55), Color(0xFFE8B923), Color(0xFF2A6FD6))
private val SNAKE_GREEN = Color(0xFF2E8B57)
private val LADDER_BROWN = Color(0xFF8B5A2B)

// ---------------------------------------------------------------- Snakes & Ladders

/** The centre of square [n] (1 to 64) on a board of cells [cell] wide; square 0 sits just off the first square. */
private fun snakeCentre(
    n: Int,
    cell: Float,
): Offset {
    if (n <= 0) return Offset(cell * 0.2f, cell * (SnakesGame.SIDE - 0.25f))
    val row = (n - 1) / SnakesGame.SIDE
    val col = if (row % 2 == 0) (n - 1) % SnakesGame.SIDE else SnakesGame.SIDE - 1 - (n - 1) % SnakesGame.SIDE
    return Offset((col + 0.5f) * cell, (SnakesGame.SIDE - 1 - row + 0.5f) * cell)
}

@Composable
internal fun SnakesScreen(random: Random) {
    var players by remember { mutableIntStateOf(3) }
    var names by remember { mutableStateOf(listOf("You") + PlayerNames.pick(random, 3)) }
    var game by remember { mutableStateOf(SnakesGame.start(names.take(3))) }

    fun restart(count: Int) {
        players = count.coerceIn(2, 4)
        game = SnakesGame.start(names.take(players))
    }

    LaunchedEffect(game) {
        if (game.needsStep) {
            delay(750)
            game = game.roll(random)
        }
    }

    GameKeyHandler(GameKind.Indian) { command ->
        when (command) {
            GameCommand.Primary, GameCommand.Select -> {
                if (game.over) {
                    restart(players)
                } else if (game.turn ==
                    0
                ) {
                    game = game.roll(random)
                }
            }

            GameCommand.NewGame -> {
                restart(players)
            }

            is GameCommand.Digit -> {
                if (command.value in 2..4) restart(command.value) else return@GameKeyHandler false
            }

            else -> {
                return@GameKeyHandler false
            }
        }
        true
    }

    GameFrame(
        title = "Snakes & Ladders",
        subtitle = "Climb ladders, dodge snakes, land exactly on 64",
        statusLabel = if (game.roll > 0) "rolled" else "turn",
        status =
            if (game.over) {
                "${game.names[game.winner!!]} wins"
            } else if (game.roll > 0) {
                "${game.roll}"
            } else {
                "you"
            },
        hint = "Space or Enter rolls · 2 3 4 set the number of players · N starts over",
        actions =
            listOf(
                GameAction(if (game.over) "Again" else "Roll", "Space", active = game.turn == 0 && !game.over) {
                    if (game.over) {
                        restart(players)
                    } else if (game.turn == 0) {
                        game = game.roll(random)
                    }
                },
                GameAction("Players $players", "2 3 4") { restart(if (players == 4) 2 else players + 1) },
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        val measurer = rememberTextMeasurer()
        val light = colors.surface
        val dark = colors.surfaceVariant
        val ink = colors.onSurface
        Column(Modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Canvas(Modifier.size(side)) {
                val cell = size.width / SnakesGame.SIDE
                for (n in 1..SnakesGame.LAST) {
                    val c = snakeCentre(n, cell)
                    val row = (n - 1) / SnakesGame.SIDE
                    val col = (c.x / cell).toInt()
                    drawRect(
                        if ((row + col) % 2 ==
                            0
                        ) {
                            light
                        } else {
                            dark
                        },
                        Offset(c.x - cell / 2, c.y - cell / 2),
                        Size(cell, cell),
                    )
                    drawText(
                        measurer,
                        n.toString(),
                        Offset(c.x - cell / 2 + 3f, c.y - cell / 2 + 1f),
                        TextStyle(
                            fontSize =
                                (
                                    cell /
                                        5.2f /
                                        density *
                                        1f
                                ).sp,
                            color = ink.copy(alpha = 0.55f),
                        ),
                    )
                }
                SnakesGame.LADDERS.forEach { (from, to) ->
                    drawLadder(snakeCentre(from, cell), snakeCentre(to, cell), cell)
                }
                SnakesGame.SNAKES.forEach { (from, to) ->
                    drawSnake(snakeCentre(from, cell), snakeCentre(to, cell), cell)
                }
                game.pos.forEachIndexed { i, square ->
                    val base = snakeCentre(square, cell)
                    val spot = Offset(base.x + (i % 2 - 0.5f) * cell * 0.34f, base.y + (i / 2 - 0.5f) * cell * 0.34f)
                    drawCircle(TOKEN_COLORS[i], cell * 0.17f, spot)
                    drawCircle(
                        if (i == game.turn &&
                            !game.over
                        ) {
                            ink
                        } else {
                            Color.White
                        },
                        cell * 0.17f,
                        spot,
                        style =
                            Stroke(
                                if (i ==
                                    game.turn
                                ) {
                                    3f
                                } else {
                                    1.5f
                                },
                            ),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawLadder(
    from: Offset,
    to: Offset,
    cell: Float,
) {
    val dx = to.y - from.y
    val dy = from.x - to.x
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
    val ox = dx / len * cell * 0.14f
    val oy = dy / len * cell * 0.14f
    drawLine(LADDER_BROWN, Offset(from.x - ox, from.y - oy), Offset(to.x - ox, to.y - oy), strokeWidth = cell * 0.07f)
    drawLine(LADDER_BROWN, Offset(from.x + ox, from.y + oy), Offset(to.x + ox, to.y + oy), strokeWidth = cell * 0.07f)
    val rungs = (len / (cell * 0.45f)).toInt().coerceAtLeast(2)
    for (i in 1 until rungs) {
        val t = i / rungs.toFloat()
        val x = from.x + (to.x - from.x) * t
        val y = from.y + (to.y - from.y) * t
        drawLine(LADDER_BROWN, Offset(x - ox, y - oy), Offset(x + ox, y + oy), strokeWidth = cell * 0.05f)
    }
}

private fun DrawScope.drawSnake(
    head: Offset,
    tail: Offset,
    cell: Float,
) {
    val path = Path()
    val steps = 24
    val dx = tail.y - head.y
    val dy = head.x - tail.x
    val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val wave = sin(t * 6.2f * 1.5f) * cell * 0.22f
        val x = head.x + (tail.x - head.x) * t + dx / len * wave
        val y = head.y + (tail.y - head.y) * t + dy / len * wave
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, SNAKE_GREEN, style = Stroke(cell * 0.2f))
    drawCircle(SNAKE_GREEN, cell * 0.2f, head)
    drawCircle(Color.White, cell * 0.05f, Offset(head.x - cell * 0.07f, head.y - cell * 0.05f))
    drawCircle(Color.White, cell * 0.05f, Offset(head.x + cell * 0.07f, head.y - cell * 0.05f))
}

// ---------------------------------------------------------------- Ludo

/** Where things go on Ludo's 15 x 15 grid: (row, column). */
internal object LudoGeometry {
    val PATH: List<Pair<Int, Int>> =
        buildList {
            for (c in 1..5) add(6 to c)
            for (r in 5 downTo 0) add(r to 6)
            add(0 to 7)
            add(0 to 8)
            for (r in 1..5) add(r to 8)
            for (c in 9..14) add(6 to c)
            add(7 to 14)
            add(8 to 14)
            for (c in 13 downTo 9) add(8 to c)
            for (r in 9..14) add(r to 8)
            add(14 to 7)
            add(14 to 6)
            for (r in 13 downTo 9) add(r to 6)
            for (c in 5 downTo 0) add(8 to c)
            add(7 to 0)
            add(6 to 0)
        }

    private val YARDS = listOf(0 to 0, 0 to 9, 9 to 9, 9 to 0)

    /** The centre (x, y) in cell units of token [token] of [color] at [progress]. */
    fun centre(
        color: Int,
        token: Int,
        progress: Int,
    ): Pair<Float, Float> =
        when {
            progress < 0 -> {
                val (r, c) = YARDS[color]
                (c + 2 + (token % 2) * 2) to (r + 2 + (token / 2) * 2)
            }

            progress <= LudoGame.LAST_TRACK -> {
                val (r, c) = PATH[LudoGame.square(color, progress)]
                (c + 0.5f) to (r + 0.5f)
            }

            progress < LudoGame.HOME -> {
                val k = progress - 51
                val (r, c) =
                    when (color) {
                        0 -> 7 to 1 + k
                        1 -> 1 + k to 7
                        2 -> 7 to 13 - k
                        else -> 13 - k to 7
                    }
                (c + 0.5f) to (r + 0.5f)
            }

            else -> {
                (7.5f + (token % 2 - 0.5f) * 0.7f) to (7.5f + (token / 2 - 0.5f) * 0.7f)
            }
        }.let { (x, y) -> x.toFloat() to y.toFloat() }
}

@Composable
internal fun LudoScreen(random: Random) {
    val names = remember { listOf("You") + PlayerNames.pick(random, 3) }
    var game by remember { mutableStateOf(LudoGame.start(names)) }
    var choice by remember { mutableIntStateOf(0) }

    LaunchedEffect(game) {
        if (game.needsStep) {
            delay(650)
            game = game.step(random)
        } else if (!game.over && game.turn == 0 && game.roll != null) {
            val options = game.movable()
            if (options.size == 1) {
                delay(450)
                game = game.move(options.first())
            } else {
                choice = options.firstOrNull() ?: 0
            }
        }
    }

    fun act() {
        if (game.over) {
            game = LudoGame.start(names)
        } else if (game.turn == 0) {
            if (game.roll ==
                null
            ) {
                game = game.rollDice(random)
            } else {
                game.movable().let {
                    if (choice in
                        it
                    ) {
                        game = game.move(choice)
                    }
                }
            }
        }
    }

    GameKeyHandler(GameKind.Indian) { command ->
        when (command) {
            GameCommand.Primary, GameCommand.Select -> {
                act()
            }

            is GameCommand.Move -> {
                if (game.turn == 0 && game.roll != null) {
                    val options = game.movable()
                    if (options.isNotEmpty()) {
                        val at = options.indexOf(choice).coerceAtLeast(0)
                        val step = if (command.dx != 0) command.dx else command.dy
                        choice = options[(at + step + options.size) % options.size]
                    }
                }
            }

            GameCommand.NewGame -> {
                game = LudoGame.start(names)
            }

            else -> {
                return@GameKeyHandler false
            }
        }
        true
    }

    GameFrame(
        title = "Ludo",
        subtitle = "You are red. Roll a six to bring a token out",
        statusLabel = if (game.over) "winner" else game.names[game.turn],
        status =
            if (game.over) {
                game.names[game.winner!!]
            } else {
                game.roll?.let { "rolled $it" }
                    ?: if (game.turn == 0) "your roll" else "rolling"
            },
        hint = "Space or Enter rolls and moves · arrows choose a token · N starts over",
        actions =
            listOf(
                GameAction(
                    if (game.over) {
                        "Again"
                    } else if (game.roll ==
                        null
                    ) {
                        "Roll"
                    } else {
                        "Move"
                    },
                    "Space",
                    active = game.turn == 0 && !game.over,
                ) { act() },
                GameAction("New", "N") { game = LudoGame.start(names) },
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        val boardLight = colors.surface
        val line = colors.outline
        val ink = colors.onSurface
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.size(side)) {
            val cell = size.width / 15f
            drawRect(boardLight, Offset.Zero, size)

            fun square(
                r: Int,
                c: Int,
                color: Color,
            ) {
                drawRect(color, Offset(c * cell, r * cell), Size(cell, cell))
                drawRect(line, Offset(c * cell, r * cell), Size(cell, cell), style = Stroke(1f))
            }
            listOf(0 to 0, 0 to 9, 9 to 9, 9 to 0).forEachIndexed { i, (r0, c0) ->
                drawRect(TOKEN_COLORS[i].copy(alpha = 0.85f), Offset(c0 * cell, r0 * cell), Size(cell * 6, cell * 6))
                drawRect(boardLight, Offset((c0 + 1) * cell, (r0 + 1) * cell), Size(cell * 4, cell * 4))
            }
            LudoGeometry.PATH.forEach { (r, c) -> square(r, c, boardLight) }
            for (k in 0 until 5) {
                square(7, 1 + k, TOKEN_COLORS[0])
                square(1 + k, 7, TOKEN_COLORS[1])
                square(7, 13 - k, TOKEN_COLORS[2])
                square(13 - k, 7, TOKEN_COLORS[3])
            }
            for (i in 0 until 4) {
                val (r, c) = LudoGeometry.PATH[i * 13]
                square(r, c, TOKEN_COLORS[i].copy(alpha = 0.7f))
                val (sr, sc) = LudoGeometry.PATH[i * 13 + 8]
                drawText(
                    measurer,
                    "★",
                    Offset(sc * cell + cell * 0.1f, sr * cell),
                    TextStyle(
                        fontSize =
                            (
                                cell / density *
                                    0.7f
                            ).sp,
                        color = ink,
                    ),
                )
            }
            val c = Offset(7.5f * cell, 7.5f * cell)
            listOf(
                listOf(Offset(6f, 6f), Offset(9f, 6f), c),
                listOf(Offset(9f, 6f), Offset(9f, 9f), c),
                listOf(Offset(9f, 9f), Offset(6f, 9f), c),
                listOf(Offset(6f, 9f), Offset(6f, 6f), c),
            ).forEachIndexed { i, pts ->
                val p = Path()
                p.moveTo(pts[0].x * cell, pts[0].y * cell)
                p.lineTo(pts[1].x * cell, pts[1].y * cell)
                p.lineTo(pts[2].x, pts[2].y)
                p.close()
                drawPath(p, TOKEN_COLORS[i])
            }
            val movable = if (game.turn == 0 && game.roll != null) game.movable() else emptyList()
            game.tokens.forEachIndexed { color, list ->
                list.forEachIndexed { token, progress ->
                    val (x, y) = LudoGeometry.centre(color, token, progress)
                    val at = Offset(x * cell, y * cell)
                    drawCircle(TOKEN_COLORS[color], cell * 0.36f, at)
                    drawCircle(Color.White, cell * 0.36f, at, style = Stroke(2.5f))
                    if (color == 0 &&
                        token in movable
                    ) {
                        drawCircle(
                            ink,
                            cell * 0.46f,
                            at,
                            style =
                                Stroke(
                                    if (token ==
                                        choice
                                    ) {
                                        5f
                                    } else {
                                        2f
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}
