package org.sableos.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val CardRed = Color(0xFFD32F45)
private val CardBlack = Color(0xFF12171C)
private val CardFace = Color(0xFFF7F9FC)
private val TileFace = Color(0xFFF2EBDD)
private val SuitColors = listOf(Color(0xFF2F6FD0), Color(0xFF2E8B57), Color(0xFFC0392B))
private val SuitMarks = listOf("●", "▮", "✦")
private val MarkRight = Color(0xFF35C66B)
private val MarkElsewhere = Color(0xFFD9A441)
private const val PHONE_THINK_MS = 450L
private const val BLOCKED_ALPHA = 0.55f

/** The square board surface the four board games draw into. */
@Composable
private fun BoardSurface(
    side: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.size(side),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize().padding(4.dp)) { content() }
    }
}

// ---------------------------------------------------------------- Reversi

@Composable
internal fun ReversiScreen() {
    var game by remember { mutableStateOf(ReversiGame()) }
    var cursor by remember { mutableIntStateOf(2 * ReversiGame.SIZE + 3) }

    LaunchedEffect(game) {
        if (game.turn == ReversiGame.LIGHT && !game.over) {
            delay(PHONE_THINK_MS)
            game.phoneMove()?.let { game = game.play(it) }
        }
    }

    fun place(index: Int) {
        if (game.turn == ReversiGame.DARK) game = game.play(index)
    }

    GameKeyHandler(GameKind.Reversi) { command ->
        when (command) {
            is GameCommand.Move -> {
                val (row, col) =
                    GameKeymap.moveCursor(
                        cursor / ReversiGame.SIZE,
                        cursor % ReversiGame.SIZE,
                        command,
                        ReversiGame.SIZE,
                    )
                cursor = row * ReversiGame.SIZE + col
            }

            GameCommand.Primary -> {
                place(cursor)
            }

            GameCommand.NewGame -> {
                game = ReversiGame()
            }

            else -> {
                return@GameKeyHandler false
            }
        }
        true
    }

    val dark = game.count(ReversiGame.DARK)
    val light = game.count(ReversiGame.LIGHT)
    GameFrame(
        title = "Reversi",
        subtitle = "Outflank the phone's discs to turn them yours.",
        statusLabel = "you $dark · phone $light",
        status =
            when {
                game.over && dark > light -> "You win"
                game.over && dark < light -> "The phone wins"
                game.over -> "A draw"
                game.turn == ReversiGame.DARK -> "Your move"
                else -> "The phone is thinking"
            },
        hint = "Arrows move, Enter or Space places a disc; dots show where you can play. N starts over.",
        actions = listOf(GameAction(label = "New", key = "N") { game = ReversiGame() }),
    ) { side ->
        val legal = if (game.turn == ReversiGame.DARK) game.legalMoves(ReversiGame.DARK).toSet() else emptySet()
        val colors = MaterialTheme.colorScheme
        BoardSurface(side) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(ReversiGame.SIZE) { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(ReversiGame.SIZE) { col ->
                            val index = row * ReversiGame.SIZE + col
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .background(colors.surfaceVariant, RoundedCornerShape(4.dp))
                                        .border(
                                            if (index ==
                                                cursor
                                            ) {
                                                2.dp
                                            } else {
                                                0.dp
                                            },
                                            if (index ==
                                                cursor
                                            ) {
                                                colors.primary
                                            } else {
                                                Color.Transparent
                                            },
                                            RoundedCornerShape(4.dp),
                                        ).clickable { place(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                when (game.cells[index]) {
                                    ReversiGame.DARK -> {
                                        Disc(CardBlack, colors.outline)
                                    }

                                    ReversiGame.LIGHT -> {
                                        Disc(CardFace, colors.outline)
                                    }

                                    else -> {
                                        if (index in
                                            legal
                                        ) {
                                            Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
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
}

@Composable
private fun Disc(
    fill: Color,
    edge: Color,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(3.dp)
            .background(fill, CircleShape)
            .border(1.dp, edge, CircleShape),
    )
}

// ---------------------------------------------------------------- Solitaire

/** The pile under the Solitaire cursor: row 0 is stock, waste, a gap and the foundations; row 1 the columns. */
internal fun solitairePileAt(
    row: Int,
    col: Int,
): Int =
    when {
        row == 1 -> SolitaireGame.TABLEAU_FIRST + col
        col == 0 -> SolitaireGame.STOCK
        col <= 2 -> SolitaireGame.WASTE
        else -> SolitaireGame.FOUNDATION_FIRST + col - 3
    }

@Composable
internal fun SolitaireScreen() {
    val random = remember { Random(System.nanoTime()) }
    var game by remember { mutableStateOf(SolitaireGame.deal(random)) }
    var row by remember { mutableIntStateOf(1) }
    var col by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    fun reset() {
        game = SolitaireGame.deal(random)
        picked = null
        note = ""
    }

    fun choose(pile: Int) {
        val from = picked
        note = ""
        when {
            pile == SolitaireGame.STOCK -> {
                game = game.draw()
                picked = null
            }

            from == null -> {
                if (game.pile(pile).lastOrNull()?.up == true) picked = pile
            }

            from == pile -> {
                picked = null
            }

            else -> {
                val moved = game.move(from, pile)
                if (moved != null) game = moved else note = "Can't go there"
                picked = null
            }
        }
    }

    GameKeyHandler(GameKind.Solitaire) { command ->
        when (command) {
            is GameCommand.Move -> {
                row = (row + command.dy).coerceIn(0, 1)
                col = (col + command.dx).coerceIn(0, 6)
            }

            GameCommand.Primary -> {
                choose(solitairePileAt(row, col))
            }

            GameCommand.Draw -> {
                choose(SolitaireGame.STOCK)
            }

            GameCommand.Auto -> {
                game.toFoundation(picked ?: solitairePileAt(row, col))?.let {
                    game = it
                    picked = null
                }
            }

            GameCommand.Erase -> {
                picked = null
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
        title = "Solitaire",
        subtitle = "Build each suit from ace to king.",
        statusLabel = "moves",
        status =
            when {
                game.won -> "You won in ${game.moves} moves"
                note.isNotEmpty() -> note
                picked != null -> "${game.moves} · pick where it goes"
                else -> game.moves.toString()
            },
        hint = "Arrows move, Enter picks up and puts down, Space turns a card, A sends it home, Del drops it. N deals again.",
        actions =
            listOf(
                GameAction(label = "Turn", key = "Space") { choose(SolitaireGame.STOCK) },
                GameAction(label = "New", key = "N", onClick = ::reset),
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        val cardW = (side - 8.dp - 12.dp) / 7
        val cardH = cardW * 1.3f
        val cursorPile = solitairePileAt(row, col)
        BoardSurface(side) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0 until 7).forEach { c ->
                        if (c == 2) {
                            Spacer(Modifier.size(cardW, cardH))
                            return@forEach
                        }
                        val pile = solitairePileAt(0, c)
                        val cards = game.pile(pile)
                        CardSlot(
                            card =
                                if (pile ==
                                    SolitaireGame.STOCK
                                ) {
                                    cards.lastOrNull()?.copy(up = false)
                                } else {
                                    cards.lastOrNull()
                                },
                            width = cardW,
                            height = cardH,
                            cursor = pile == cursorPile,
                            picked = pile == picked,
                            empty = if (pile == SolitaireGame.STOCK) "↻" else "",
                        ) { choose(pile) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0 until 7).forEach { c ->
                        val pile = SolitaireGame.TABLEAU_FIRST + c
                        val cards = game.pile(pile)
                        Box(Modifier.size(cardW, side - cardH - 20.dp).clickable { choose(pile) }) {
                            if (cards.isEmpty()) {
                                CardSlot(
                                    null,
                                    cardW,
                                    cardH,
                                    cursor = pile == cursorPile,
                                    picked = false,
                                    empty = "K",
                                ) { choose(pile) }
                            }
                            var y = 0.dp
                            cards.forEachIndexed { i, card ->
                                val top = i == cards.lastIndex
                                Box(Modifier.offset(y = y)) {
                                    CardSlot(
                                        card,
                                        cardW,
                                        cardH,
                                        cursor = top && pile == cursorPile,
                                        picked =
                                            top && pile == picked,
                                        empty = "",
                                    ) { choose(pile) }
                                }
                                y += if (card.up) cardH * 0.32f else cardH * 0.14f
                            }
                        }
                    }
                }
            }
            if (game.won) {
                Text(
                    "Well done",
                    Modifier.align(Alignment.Center),
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun CardSlot(
    card: PlayCard?,
    width: Dp,
    height: Dp,
    cursor: Boolean,
    picked: Boolean,
    empty: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val edge =
        when {
            picked -> colors.primary
            cursor -> colors.primary
            else -> colors.outlineVariant
        }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(width, height),
        shape = RoundedCornerShape(4.dp),
        color =
            when {
                card == null -> colors.surfaceVariant
                !card.up -> colors.primary.copy(alpha = 0.55f)
                picked -> colors.primary.copy(alpha = 0.25f).compositeOverFace()
                else -> CardFace
            },
        border = BorderStroke(if (cursor || picked) 2.dp else 1.dp, edge),
    ) {
        Box(Modifier.padding(horizontal = 2.dp), contentAlignment = Alignment.TopStart) {
            when {
                card == null -> {
                    Text(
                        empty,
                        Modifier.align(Alignment.Center),
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }

                card.up -> {
                    Text(
                        card.label,
                        color = if (card.red) CardRed else CardBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Color.compositeOverFace(): Color =
    Color(
        red = red * alpha + CardFace.red * (1 - alpha),
        green = green * alpha + CardFace.green * (1 - alpha),
        blue = blue * alpha + CardFace.blue * (1 - alpha),
    )

// ---------------------------------------------------------------- Mahjong

@Composable
internal fun MahjongScreen() {
    val random = remember { Random(System.nanoTime()) }
    var game by remember { mutableStateOf(MahjongGame.deal(random)) }
    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<TileSpot?>(null) }
    var hint by remember { mutableStateOf<Pair<TileSpot, TileSpot>?>(null) }

    fun reset() {
        game = MahjongGame.deal(random)
        picked = null
        hint = null
    }

    fun choose(
        c: Int,
        r: Int,
    ) {
        col = c
        row = r
        val spot = game.topAt(c, r) ?: return
        if (!game.isFree(spot)) return
        val first = picked
        picked =
            when {
                first == null -> {
                    spot
                }

                first == spot -> {
                    null
                }

                else -> {
                    val matched = game.match(first, spot)
                    if (matched != null) {
                        game = matched
                        hint = null
                        null
                    } else {
                        spot
                    }
                }
            }
    }

    GameKeyHandler(GameKind.Mahjong) { command ->
        when (command) {
            is GameCommand.Move -> {
                row = (row + command.dy).coerceIn(0, MahjongGame.ROWS - 1)
                col = (col + command.dx).coerceIn(0, MahjongGame.COLUMNS - 1)
            }

            GameCommand.Primary -> {
                choose(col, row)
            }

            GameCommand.Hint -> {
                hint = game.hint()
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

    val stuck = !game.won && game.hint() == null
    GameFrame(
        title = "Mahjong",
        subtitle = "Clear the pile two matching free tiles at a time.",
        statusLabel = "tiles left",
        status =
            when {
                game.won -> "Cleared!"
                stuck -> "${game.tiles.size} · no pairs left, N deals again"
                else -> game.tiles.size.toString()
            },
        hint = "A tile is free when nothing is on it and its left or right side is open. Arrows move, Enter picks, H hints, N deals.",
        actions =
            listOf(
                GameAction(label = "Hint", key = "H") { hint = game.hint() },
                GameAction(label = "New", key = "N", onClick = ::reset),
            ),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        BoardSurface(side) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(MahjongGame.ROWS) { r ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(MahjongGame.COLUMNS) { c ->
                            val spot = game.topAt(c, r)
                            val face = spot?.let { game.tiles[it] }
                            val isCursor = c == col && r == row
                            val isPicked = spot != null && spot == picked
                            val isHint = spot != null && hint?.let { spot == it.first || spot == it.second } == true
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clickable { choose(c, r) },
                            ) {
                                if (spot != null && face != null) {
                                    MahjongTile(face, spot.layer, game.isFree(spot), isPicked, isHint)
                                }
                                if (isCursor) {
                                    Box(Modifier.fillMaxSize().border(2.dp, colors.primary, RoundedCornerShape(5.dp)))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MahjongTile(
    face: Int,
    layer: Int,
    free: Boolean,
    picked: Boolean,
    hinted: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val suit = face / 6
    Box(
        Modifier
            .fillMaxSize()
            .alpha(if (free) 1f else BLOCKED_ALPHA)
            .background(if (picked) colors.primary else TileFace, RoundedCornerShape(5.dp))
            .border(
                if (layer >
                    0
                ) {
                    3.dp
                } else {
                    1.dp
                },
                if (hinted) MarkElsewhere else Color(0xFF8A7F6A),
                RoundedCornerShape(5.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                (face % 6 + 1).toString(),
                color = if (picked) colors.onPrimary else SuitColors[suit],
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(SuitMarks[suit], color = if (picked) colors.onPrimary else SuitColors[suit], fontSize = 10.sp)
        }
    }
}

// ---------------------------------------------------------------- Words

@Composable
internal fun WordsScreen() {
    val random = remember { Random(System.nanoTime()) }
    var game by remember { mutableStateOf(WordsGame.start(random)) }

    fun reset() {
        game = WordsGame.start(random)
    }

    GameKeyHandler(GameKind.Words) { command ->
        when (command) {
            is GameCommand.Letter -> game = game.type(command.value)
            GameCommand.Erase -> game = game.erase()
            GameCommand.Primary -> if (game.over) reset() else game = game.submit()
            else -> return@GameKeyHandler false
        }
        true
    }

    val marks = game.letterMarks()
    val absent =
        marks
            .filterValues { it == LetterMark.Absent }
            .keys
            .sorted()
            .joinToString(" ")
    GameFrame(
        title = "Words",
        subtitle = "Find the five-letter word in six guesses.",
        statusLabel = "guess ${minOf(game.guesses.size + 1, WordsGame.MAX_GUESSES)} of ${WordsGame.MAX_GUESSES}",
        status =
            when {
                game.won -> "Got it! Enter plays again"
                game.over -> "It was ${game.answer} · Enter plays again"
                absent.isNotEmpty() -> "Not in it: $absent"
                else -> "Type a word, Enter guesses"
            },
        hint = "Green: right place. Amber: in the word, elsewhere. Type letters, Del erases, Enter guesses.",
        actions = listOf(GameAction(label = "New", key = "Enter", onClick = ::reset)),
    ) { side ->
        val colors = MaterialTheme.colorScheme
        BoardSurface(side) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                repeat(WordsGame.MAX_GUESSES) { r ->
                    val guess = game.guesses.getOrNull(r)
                    val text = guess ?: if (r == game.guesses.size) game.typing else ""
                    val rowMarks = guess?.let { WordsGame.marks(it, game.answer) }
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(WordsGame.LENGTH) { i ->
                            val mark = rowMarks?.get(i)
                            val fill =
                                when (mark) {
                                    LetterMark.Right -> MarkRight
                                    LetterMark.Elsewhere -> MarkElsewhere
                                    LetterMark.Absent -> colors.surfaceVariant
                                    null -> colors.surface
                                }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .background(fill, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (mark == null) colors.outline else fill, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text.getOrNull(i)?.toString().orEmpty(),
                                    color =
                                        if (mark == LetterMark.Right ||
                                            mark == LetterMark.Elsewhere
                                        ) {
                                            Color.White
                                        } else {
                                            colors.onSurface
                                        },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
