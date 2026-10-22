package org.sableos.games

import android.view.KeyEvent

/**
 * Keyboard play for the Sable games (keyboard-first, like the other Sable apps).
 *
 * The trackpad sends arrow keys, so arrows move the board cursor (Sudoku, Minesweeper) or slide the
 * tiles (2048). Numbers come from the character the key produces, so Alt+W E R S D F Z X C (the Q25
 * and Titan digit layer) and a number row both work; plain W E R S D F Z X C also enter 1-9 in Sudoku,
 * where no letter has another meaning. There is no Ctrl on the Q25: Ctrl chords are left alone.
 */
internal sealed interface GameCommand {
    data class Move(
        val dx: Int,
        val dy: Int,
    ) : GameCommand

    data class Digit(
        val value: Int,
    ) : GameCommand

    /** Space or Enter: reveal (Minesweeper) or nothing special. */
    data object Primary : GameCommand

    data object Flag : GameCommand

    data object Erase : GameCommand

    data object Undo : GameCommand

    data object NewGame : GameCommand
}

/** A key-down reduced to what the keymap needs, so the mapping is testable on the JVM. */
internal data class GameKeyPress(
    val keyCode: Int,
    /** The character the key produces with the current meta state, or null. */
    val char: Char? = null,
    val alt: Boolean = false,
    val ctrl: Boolean = false,
    val repeatCount: Int = 0,
)

internal enum class GameKind { Sudoku, Minesweeper, Game2048 }

internal object GameKeymap {
    /** BlackBerry-style digit keys: W E R S D F Z X C are 1-9. */
    private val LETTER_DIGITS =
        mapOf(
            KeyEvent.KEYCODE_W to 1,
            KeyEvent.KEYCODE_E to 2,
            KeyEvent.KEYCODE_R to 3,
            KeyEvent.KEYCODE_S to 4,
            KeyEvent.KEYCODE_D to 5,
            KeyEvent.KEYCODE_F to 6,
            KeyEvent.KEYCODE_Z to 7,
            KeyEvent.KEYCODE_X to 8,
            KeyEvent.KEYCODE_C to 9,
        )

    fun command(
        game: GameKind,
        press: GameKeyPress,
    ): GameCommand? {
        if (press.ctrl) return null
        move(press.keyCode)?.let { return it }
        // A held key repeats only cursor moves.
        if (press.repeatCount > 0) return null
        return when (game) {
            GameKind.Sudoku -> sudoku(press)
            GameKind.Minesweeper -> minesweeper(press)
            GameKind.Game2048 -> common(press)
        }
    }

    private fun move(keyCode: Int): GameCommand.Move? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> GameCommand.Move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> GameCommand.Move(0, 1)
            KeyEvent.KEYCODE_DPAD_LEFT -> GameCommand.Move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> GameCommand.Move(1, 0)
            else -> null
        }

    private fun sudoku(press: GameKeyPress): GameCommand? {
        val digit = press.char?.takeIf { it in '1'..'9' }?.digitToInt()
        return when {
            digit != null -> GameCommand.Digit(digit)

            press.char == '0' -> GameCommand.Erase

            press.keyCode == KeyEvent.KEYCODE_DEL || press.keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> GameCommand.Erase

            // Alt plus a letter types a symbol; only the digits above are taken from it.
            press.alt -> null

            press.keyCode in LETTER_DIGITS -> GameCommand.Digit(LETTER_DIGITS.getValue(press.keyCode))

            press.keyCode == KeyEvent.KEYCODE_U -> GameCommand.Undo

            else -> common(press)
        }
    }

    private fun minesweeper(press: GameKeyPress): GameCommand? =
        when {
            press.alt -> null

            press.keyCode == KeyEvent.KEYCODE_SPACE ||
                press.keyCode == KeyEvent.KEYCODE_ENTER ||
                press.keyCode == KeyEvent.KEYCODE_DPAD_CENTER -> GameCommand.Primary

            press.keyCode == KeyEvent.KEYCODE_F || press.keyCode == KeyEvent.KEYCODE_G -> GameCommand.Flag

            else -> common(press)
        }

    private fun common(press: GameKeyPress): GameCommand? =
        when {
            press.alt -> null
            press.keyCode == KeyEvent.KEYCODE_N -> GameCommand.NewGame
            else -> null
        }

    /** The cursor after [move] on a [size] x [size] board, clamped at the edges. */
    fun moveCursor(
        row: Int,
        col: Int,
        move: GameCommand.Move,
        size: Int,
    ): Pair<Int, Int> = (row + move.dy).coerceIn(0, size - 1) to (col + move.dx).coerceIn(0, size - 1)

    /** 2048 slide direction for the native bridge: 0 left, 1 right, 2 up, 3 down. */
    fun slideDirection(move: GameCommand.Move): Int =
        when {
            move.dx < 0 -> 0
            move.dx > 0 -> 1
            move.dy < 0 -> 2
            else -> 3
        }
}

/** Set by the current game screen; [MainActivity] offers it every key-down first. */
internal class GameKeyHost {
    var handler: ((GameKeyPress) -> Boolean)? = null
}

internal fun KeyEvent.toGameKeyPress(): GameKeyPress {
    val produced = unicodeChar
    return GameKeyPress(
        keyCode = keyCode,
        char = if (produced > 0) produced.toChar() else null,
        alt = isAltPressed,
        ctrl = isCtrlPressed,
        repeatCount = repeatCount,
    )
}
