package org.sableos.games

internal object NativeBridge {
    init {
        System.loadLibrary("sable_games_jni")
    }

    external fun selfTest(): String

    external fun sudokuStart(puzzle: String): String

    external fun sudokuSet(
        state: String,
        row: Int,
        col: Int,
        value: Int,
    ): String

    external fun sudokuBoard(state: String): String

    external fun sudokuSolved(state: String): Boolean

    external fun minesweeperStart(
        width: Int,
        height: Int,
        mines: Int,
        seed: Long,
    ): String

    external fun minesweeperReveal(
        state: String,
        x: Int,
        y: Int,
    ): String

    external fun minesweeperToggleFlag(
        state: String,
        x: Int,
        y: Int,
    ): String

    external fun minesweeperView(state: String): String

    external fun game2048Slide(
        board: IntArray,
        direction: Int,
    ): LongArray
}
