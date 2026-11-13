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
    fun theSixMotionGamesAreThere() {
        val ids =
            root
                .listFiles()
                .orEmpty()
                .map { it.name }
                .toSet()
        assertEquals(setOf("tilt_maze", "motion_2048", "sokoban_tilt", "tilt_racer", "tilt_circuit", "ball_maze"), ids)
        ids.forEach { assertTrue(it, load(it).first.usesTilt) }
    }

    @Test
    fun everyGameSurvivesRandomPlay() {
        val random = Random(25)
        for (id in listOf("tilt_maze", "motion_2048", "sokoban_tilt", "tilt_racer", "tilt_circuit", "ball_maze")) {
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
    fun tiltCircuitIsRaceableAndCountsOnlyWholeLaps() {
        val dir = File(root, "tilt_circuit")
        val pictures = dir.list().orEmpty().filter { GamePictures.isPicture(it) }.toSet()
        assertTrue(pictures.size <= GamePictures.MAX)
        pictures.forEach { assertTrue(it, File(dir, it).length() <= GamePictures.MAX_BYTES) }
        // A driver that follows the road: the script's car and track helpers made reachable.
        val source =
            File(dir, "main.lua")
                .readText()
                .replace("local car, rivals,", "car, rivals,")
                .replace("lap_start, best_lap, wrong, finished\n", "lap_start, best_lap, wrong, finished = nil\n")
                .replace("local function nearest", "function nearest")
                .replace("local function at_distance", "function at_distance") +
                """

                function bot()
                  local _, where = nearest(car.x, car.y)
                  local tx, ty = at_distance(where + 3.5)
                  local diff = (math.atan2(ty - car.y, tx - car.x) - car.a + math.pi) % (2 * math.pi) - math.pi
                  return math.max(-1, math.min(1, diff * 2))
                end
                sable.tilt = function() return bot(), 0 end
                """.trimIndent()
        val g = LuaGame(source, "main.lua", MemoryGameStore(), pictures).also { it.start() }
        var frames = 0
        while (g.over == null && frames < 60 * 120) {
            g.update(1.0 / 60)
            assertTrue(g.frame().size < 200)
            frames++
        }
        val over = g.over.orEmpty()
        assertTrue(over, over.contains("best lap"))
        assertTrue((g.score ?: 0) in 30L..90L)

        // Without steering the car leaves the road and never finishes a lap.
        val (_, idle) = load("tilt_circuit")
        repeat(60 * 60) { idle.update(1.0 / 60) }
        assertEquals(null, idle.over)
        assertTrue(idle.status, idle.status.startsWith("Lap 1/3"))
    }

    @Test
    fun ballMazeKeepsItsBallsOnEveryBoard() {
        val random = Random(13)
        for (shape in 0..1) {
            for (level in 0..3) {
                val store = MemoryGameStore()
                val (_, g) = load("ball_maze", store)
                assertTrue(g.status.contains("Space starts"))
                repeat(shape) { g.tilt(TiltStep.Right) }
                repeat(2) { g.tilt(TiltStep.Down) }
                repeat(level) { g.tilt(TiltStep.Right) }
                g.key("space")
                assertEquals("${shape + 1}", store.get("shape"))
                assertEquals("${level + 1}", store.get("level"))
                assertTrue(g.status, g.status.contains("home"))
                repeat(60 * 20) { i ->
                    if (i % 40 == 0) {
                        g.tiltX = random.nextDouble(-1.0, 1.0)
                        g.tiltY = random.nextDouble(-1.0, 1.0)
                    }
                    g.update(1.0 / 60)
                }
                val frame = g.frame()
                assertTrue(frame.size < LuaGame.MAX_OPS)
                // Each ball's body is one grey circle; every one is still on the board.
                val bodies = frame.filterIsInstance<DrawOp.Circle>().filter { it.color == "#7E878E" }
                assertEquals(level + 2, bodies.size)
                for (b in bodies) {
                    if (shape == 0) {
                        assertTrue("$level $b", (b.x - 6) * (b.x - 6) + (b.y - 6) * (b.y - 6) < 5.4 * 5.4)
                    } else {
                        assertTrue("$level $b", b.x in 0.5..11.5 && b.y in 0.5..11.5)
                    }
                }
            }
        }
    }

    @Test
    fun motion2048SlidesAndScores() {
        val (_, g) = load("motion_2048")
        repeat(200) { g.tilt(TiltStep.entries[it % 4]) }
        assertTrue((g.score ?: 0) > 0)
    }
}
