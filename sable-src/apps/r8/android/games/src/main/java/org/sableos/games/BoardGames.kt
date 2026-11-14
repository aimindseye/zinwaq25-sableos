package org.sableos.games

import kotlin.random.Random

/** Snakes & Ladders on an 8 x 8 board, two to four players; you are player 0. Roll a six and roll again. */
internal data class SnakesGame(
    val names: List<String>,
    val pos: List<Int>,
    val turn: Int = 0,
    val roll: Int = 0,
    val winner: Int? = null,
    val note: String = "",
) {
    val over: Boolean get() = winner != null
    val needsStep: Boolean get() = !over && turn != HUMAN

    /** One roll for the player whose turn it is. */
    fun roll(random: Random): SnakesGame = move(random.nextInt(1, 7))

    fun move(dice: Int): SnakesGame {
        if (over) return this
        val p = turn
        val target = pos[p] + dice
        var note = ""
        var land = pos[p]
        if (target <= LAST) {
            land = target
            val jump = JUMPS[land]
            if (jump != null) {
                note =
                    if (jump >
                        land
                    ) {
                        "${names[p]} climbs a ladder: $land to $jump"
                    } else {
                        "${names[p]} slips on a snake: $land to $jump"
                    }
                land = jump
            }
        } else {
            note = "${names[p]} needs exactly ${LAST - pos[p]}"
        }
        val next = pos.mapIndexed { i, v -> if (i == p) land else v }
        val won = land == LAST
        val again = dice == 6 && !won
        return copy(
            pos = next,
            roll = dice,
            turn = if (again) p else (p + 1) % pos.size,
            winner = if (won) p else null,
            note = note.ifEmpty { "${names[p]} rolls $dice" },
        )
    }

    companion object {
        const val HUMAN = 0
        const val SIDE = 8
        const val LAST = SIDE * SIDE
        val LADDERS = mapOf(4 to 25, 14 to 45, 33 to 56, 40 to 60)
        val SNAKES = mapOf(58 to 19, 47 to 26, 37 to 8)
        private val JUMPS = LADDERS + SNAKES

        fun start(names: List<String>): SnakesGame = SnakesGame(names, List(names.size) { 0 })
    }
}

/**
 * Ludo for four colours (you are red, player 0). Roll a six to bring a token out; a six, a capture or
 * a token reaching home gives another roll. Three sixes in a row lose the turn. A token goes round the
 * 52-square track and up its own home column; it needs the exact roll to reach home. Landing on another
 * colour's token sends it back to its yard, except on a start square or a star.
 */
internal data class LudoGame(
    val names: List<String>,
    /** For each colour, the four tokens' progress: -1 in the yard, 0-50 on the track, 51-55 in the home column, 56 home. */
    val tokens: List<List<Int>>,
    val turn: Int = 0,
    val roll: Int? = null,
    val sixes: Int = 0,
    val winner: Int? = null,
    val note: String = "",
) {
    val over: Boolean get() = winner != null
    val needsStep: Boolean get() = !over && turn != HUMAN

    /** Tokens of [player] that can move with the current roll. */
    fun movable(player: Int = turn): List<Int> {
        val r = roll ?: return emptyList()
        return tokens[player].indices.filter { t ->
            val p = tokens[player][t]
            if (p < 0) r == 6 else p + r <= HOME
        }
    }

    fun rollDice(random: Random): LudoGame {
        if (over || roll != null) return this
        val r = random.nextInt(1, 7)
        val six = if (r == 6) sixes + 1 else 0
        if (six >=
            3
        ) {
            return copy(
                roll = null,
                sixes = 0,
                turn = (turn + 1) % PLAYERS,
                note = "${names[turn]} rolled three sixes and loses the turn",
            )
        }
        val withRoll = copy(roll = r, sixes = six, note = "${names[turn]} rolls $r")
        return if (withRoll.movable().isEmpty()) {
            withRoll.copy(
                roll = null,
                turn = (turn + 1) % PLAYERS,
                sixes = 0,
                note = "${names[turn]} rolls $r and cannot move",
            )
        } else {
            withRoll
        }
    }

    /** Moves token [token] of the player whose turn it is. */
    fun move(token: Int): LudoGame {
        if (over || token !in movable()) return this
        val r = roll!!
        val p = turn
        val from = tokens[p][token]
        val to = if (from < 0) 0 else from + r
        var bumped =
            tokens.mapIndexed { c, list ->
                if (c ==
                    p
                ) {
                    list.mapIndexed { i, v -> if (i == token) to else v }
                } else {
                    list
                }
            }
        var captured = false
        if (to in 0..LAST_TRACK && !isSafe(to, p)) {
            val square = square(p, to)
            bumped =
                bumped.mapIndexed { c, list ->
                    if (c == p) {
                        list
                    } else {
                        list.map { v ->
                            if (v in 0..LAST_TRACK && square(c, v) == square) {
                                captured = true
                                -1
                            } else {
                                v
                            }
                        }
                    }
                }
        }
        val home = to == HOME
        val won = bumped[p].all { it == HOME }
        val again = (r == 6 || captured || home) && !won
        val text =
            when {
                won -> "${names[p]} wins!"
                captured -> "${names[p]} sends a token home"
                home -> "${names[p]} gets a token home"
                else -> "${names[p]} moves $r"
            }
        return copy(
            tokens = bumped,
            roll = null,
            turn = if (again) p else (p + 1) % PLAYERS,
            sixes = if (again && r == 6) sixes else 0,
            winner = if (won) p else null,
            note = text,
        )
    }

    /** One phone move: roll, or choose a token. */
    fun step(random: Random): LudoGame {
        if (!needsStep) return this
        if (roll == null) return rollDice(random)
        val options = movable()
        val p = turn
        val r = roll
        val pick =
            options.maxBy { t ->
                val from = tokens[p][t]
                val to = if (from < 0) 0 else from + r
                var score = to
                if (from < 0) score += 60
                if (to == HOME) score += 80
                if (to in 0..LAST_TRACK && !isSafe(to, p) && captures(p, to)) score += 100
                if (from in 0..LAST_TRACK && endangered(p, from)) score += 25
                if (to in 0..LAST_TRACK && isSafe(to, p)) score += 15
                score
            }
        return move(pick)
    }

    private fun captures(
        player: Int,
        progress: Int,
    ): Boolean {
        val sq = square(player, progress)
        return tokens.withIndex().any { (c, list) ->
            c != player &&
                list.any { it in 0..LAST_TRACK && square(c, it) == sq }
        }
    }

    /** Another colour has a token a roll or two behind this one. */
    private fun endangered(
        player: Int,
        progress: Int,
    ): Boolean {
        if (isSafe(progress, player)) return false
        val sq = square(player, progress)
        return tokens.withIndex().any { (c, list) ->
            c != player && list.any { v -> v in 0..LAST_TRACK && (sq - square(c, v) + TRACK) % TRACK in 1..6 }
        }
    }

    companion object {
        const val HUMAN = 0
        const val PLAYERS = 4
        const val HOME = 56
        const val LAST_TRACK = 50
        const val TRACK = 52

        /** The track square (0-51) a token of [color] stands on at [progress] 0-50. */
        fun square(
            color: Int,
            progress: Int,
        ): Int = (color * 13 + progress) % TRACK

        /** Start squares and the star squares eight steps on cannot be captured on. */
        fun isSafe(
            progress: Int,
            color: Int,
        ): Boolean = progress in 0..LAST_TRACK && square(color, progress) % 13 in listOf(0, 8)

        fun start(names: List<String>): LudoGame = LudoGame(names, List(PLAYERS) { List(4) { -1 } })
    }
}
