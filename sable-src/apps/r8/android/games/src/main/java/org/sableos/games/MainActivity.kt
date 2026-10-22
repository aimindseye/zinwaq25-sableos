package org.sableos.games

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableHeroHeader
import org.sableos.design.SableScrollableScreen

private const val GAMES_EYEBROW = "Sable Games"

/** The key host of the running game; screens install their handler in it. */
internal val LocalGameKeyHost = staticCompositionLocalOf { GameKeyHost() }

class MainActivity : ComponentActivity() {
    private val keyHost = GameKeyHost()

    /** Key codes whose key-down was taken by the game, so their key-up is swallowed too. */
    private val consumedKeyCodes = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qualification = NativeBridge.selfTest()
        check(qualification.startsWith("PASS:")) { qualification }

        setContent {
            SableGlobalTheme(window = window) {
                CompositionLocalProvider(LocalGameKeyHost provides keyHost) {
                    SableScrollableScreen {
                        when (BuildConfig.SABLE_GAME) {
                            "sudoku" -> {
                                SableHeroHeader(
                                    eyebrow = GAMES_EYEBROW,
                                    title = "sudoku",
                                    subtitle = "A focused number puzzle.",
                                )
                                SudokuScreen()
                            }

                            "minesweeper" -> {
                                SableHeroHeader(
                                    eyebrow = GAMES_EYEBROW,
                                    title = "minesweeper",
                                    subtitle = "Clear the field without touching a mine.",
                                )
                                MinesweeperScreen()
                            }

                            "2048" -> {
                                SableHeroHeader(
                                    eyebrow = GAMES_EYEBROW,
                                    title = "2048",
                                    subtitle = "Slide, merge and build the highest tile.",
                                )
                                Game2048Screen()
                            }

                            else -> {
                                error(
                                    "Unsupported Sable game flavor: ${BuildConfig.SABLE_GAME}",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ComponentActivity.dispatchKeyEvent is a public framework override; lint flags it as androidx-restricted.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val taken =
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    (keyHost.handler?.invoke(event.toGameKeyPress()) == true).also { taken ->
                        if (taken) consumedKeyCodes += event.keyCode
                    }
                }

                KeyEvent.ACTION_UP -> {
                    consumedKeyCodes.remove(event.keyCode)
                }

                else -> {
                    false
                }
            }
        return taken || super.dispatchKeyEvent(event)
    }
}
