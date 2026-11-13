package org.sableos.games.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LuaGameTest {
    private fun game(
        source: String,
        store: GameStore = MemoryGameStore(),
    ) = LuaGame(source, "main.lua", store).also { it.start() }

    private fun error(block: () -> Unit): String =
        try {
            block()
            fail("expected the game to stop")
            ""
        } catch (e: GameScriptError) {
            e.message.orEmpty()
        }

    @Test
    fun aGameDrawsReportsAndSaves() {
        val store = MemoryGameStore()
        val g =
            game(
                """
                local n = 0
                function init() sable.grid(4, 3); n = tonumber(sable.load("n") or "0") end
                function tilt(step) if step == "right" then n = n + 1 end; sable.save("n", tostring(n)) end
                function update(dt) local x = sable.tilt(); sable.status("n=" .. n .. " x=" .. x); sable.score(n) end
                function key(k) if k == "space" then sable.over("done") end end
                function draw() sable.rect(0, 0, 1, 1, "wall"); sable.circle(1, 1, 0.5, "#FF0000"); sable.text(2, 2, "hi") end
                """.trimIndent(),
                store,
            )
        assertEquals(4, g.cols)
        assertEquals(3, g.rows)
        g.tilt(TiltStep.Right)
        g.tiltX = 0.5
        g.update(0.016)
        assertEquals("n=1 x=0.5", g.status)
        assertEquals(1L, g.score)
        assertEquals("1", store.get("n"))
        val ops = g.frame()
        assertEquals(DrawOp.Rect(0.0, 0.0, 1.0, 1.0, "wall"), ops[0])
        assertEquals(DrawOp.Circle(1.0, 1.0, 0.5, "#FF0000"), ops[1])
        assertEquals(DrawOp.Text(2.0, 2.0, "hi", 1.0, "text"), ops[2])
        assertTrue(g.key("space"))
        assertEquals("done", g.over)
        g.restart()
        assertNull(g.over)
    }

    @Test
    fun theSandboxHasNoFilesJavaOrLoading() {
        for (name in listOf(
            "io",
            "os",
            "luajava",
            "require",
            "load",
            "loadstring",
            "dofile",
            "loadfile",
            "debug",
            "package",
            "coroutine",
            "print",
        )) {
            val probe = game("function update() if $name ~= nil then sable.status('open') end end")
            probe.update(0.0)
            assertEquals(name, "", probe.status)
        }
    }

    @Test
    fun aLoopThatNeverEndsIsStoppedEvenUnderPcall() {
        val g = game("function update() while true do end end")
        assertTrue(error { g.update(0.0) }.contains("too long"))
        val p = game("function update() while true do pcall(function() while true do end end) end end")
        assertTrue(error { p.update(0.0) }.contains("too long"))
    }

    @Test
    fun scriptErrorsNameTheLine() {
        assertTrue(error { LuaGame("x = = 1", "main.lua", MemoryGameStore()) }.contains("main.lua:1"))
        val g = game("function update()\n  local t = nil\n  return t.x\nend")
        assertTrue(error { g.update(0.0) }.contains("main.lua:3"))
    }

    @Test
    fun drawingAndTextAreBounded() {
        val g =
            game(
                "function draw() for i = 1, 10000 do sable.rect(0, 0, 1, 1) end end function init() sable.grid(9999, 0) end",
            )
        assertEquals(LuaGame.MAX_OPS, g.frame().size)
        assertEquals(LuaGame.MAX_GRID, g.cols)
        assertEquals(1, g.rows)
    }
}
