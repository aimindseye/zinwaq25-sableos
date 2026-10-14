package org.sableos.games

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.sableos.design.SableGlobalTheme
import org.sableos.design.SableHeroHeader
import org.sableos.design.SableScrollableScreen

private const val GAMES_EYEBROW = "Sable Games"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val qualification = NativeBridge.selfTest()
        check(qualification.startsWith("PASS:")) { qualification }

        setContent {
            SableGlobalTheme(window = window) {
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
