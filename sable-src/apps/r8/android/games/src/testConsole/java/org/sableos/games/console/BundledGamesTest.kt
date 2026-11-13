package org.sableos.games.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The games that come with the console parse, start and survive a long random session. */
class BundledGamesTest {
    private val root = File("src/console/assets/console")

    private fun load(
        id: String,
        store: GameStore = MemoryGameStore(),
    ): Pair<GameManifest, LuaGame> {
        val dir = File(root, id)
        val m = GameManifest.parse(File(dir, GameManifest.FILE).readText()).getOrThrow()
        assertEquals(id, m.id)
        val pictures = dir.list().orEmpty().filter { GamePictures.isPicture(it) }.toSet()
        return m to LuaGame(File(dir, m.entry).readText(), m.entry, store, pictures).also { it.start() }
    }

    @Test
    fun theFourMotionGamesAreThere() {
        val ids =
            root
                .listFiles()
                .orEmpty()
                .map { it.name }
                .toSet()
        assertEquals(setOf("tilt_maze", "motion_2048", "sokoban_tilt", "tilt_racer"), ids)
        ids.forEach { assertTrue(it, load(it).first.usesTilt) }
    }

    @Test
    fun everyGameSurvivesRandomPlay() {
        val random = Random(25)
        for (id in listOf("tilt_maze", "motion_2048", "sokoban_tilt", "tilt_racer")) {
            val (_, g) = load(id)
            repeat(3_000) { i ->
                when (random.nextInt(4)) {
                    0 -> {
                        g.tilt(TiltStep.entries[random.nextInt(4)])
                    }

                    1 -> {
                        g.key(listOf("space", "u", "enter", "n")[random.nextInt(4)])
                    }

                    else -> {
                        g.tiltX = random.nextDouble(-1.0, 1.0)
                        g.tiltY = random.nextDouble(-1.0, 1.0)
                        g.update(1.0 / 60)
                    }
                }
                if (g.over != null && i % 7 == 0) g.restart()
                assertTrue(id, g.frame().isNotEmpty())
            }
            assertTrue(id, g.status.isNotEmpty())
        }
    }

    @Test
    fun tiltRacerEndsInACrashWhenYouDoNotSteer() {
        val (_, g) = load("tilt_racer")
        repeat(60 * 120) { if (g.over == null) g.update(1.0 / 60) }
        assertNotNull(g.over)
        assertTrue((g.score ?: 0) > 0)
    }

    @Test
    fun tiltRacerDrawsTheF1CarsAndCKeepsTheChosenColour() {
        val dir = File(root, "tilt_racer")
        val pictures = dir.list().orEmpty().filter { GamePictures.isPicture(it) }.toSet()
        assertEquals(
            setOf("blue", "red", "yellow", "green", "purple", "violet").map { "f1_$it.png" }.toSet(),
            pictures,
        )
        pictures.forEach { assertTrue(it, File(dir, it).length() <= GamePictures.MAX_BYTES) }
        val store = MemoryGameStore()
        val (_, g) = load("tilt_racer", store)
        fun player() = g.frame().filterIsInstance<DrawOp.Image>().last().name
        assertEquals("f1_red.png", player())
        g.key("c")
        assertEquals("f1_blue.png", player())
        assertEquals("2", store.get("color"))
        // A new run (and a new start of the app) keeps the colour.
        val (_, again) = load("tilt_racer", store)
        assertEquals("f1_blue.png", again.frame().filterIsInstance<DrawOp.Image>().last().name)
        // Six presses go round every colour and back.
        repeat(6) { g.key("c") }
        assertEquals("f1_blue.png", player())
    }

    @Test
    fun motion2048SlidesAndScores() {
        val (_, g) = load("motion_2048")
        repeat(200) { g.tilt(TiltStep.entries[it % 4]) }
        assertTrue((g.score ?: 0) > 0)
    }
}
