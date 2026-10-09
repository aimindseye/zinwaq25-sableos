package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardCoreTest {
    private var t = 0L
    private val mid = FieldCtx(InputClass.Text, before = "hello", autoCapAllowed = true)

    private fun r(cfg: KeyboardConfig = KeyboardConfig()) = Resolver(cfg = cfg)
    private fun Resolver.press(
        k: Key,
        ctx: FieldCtx = mid,
        repeat: Int = 0,
        gap: Long = 50
    ): List<Out> {
        t += gap
        val d = onKey(KeyEv(k, true, t, repeat), ctx)
        t += 20
        onKey(KeyEv(k, false, t), ctx)
        return d
    }
    private fun Resolver.tap(m: Mod, gap: Long = 50) {
        t += gap
        onKey(KeyEv(Key.Modifier(m), true, t), mid)
        t +=
            30
        onKey(KeyEv(Key.Modifier(m), false, t), mid)
    }
    private fun Resolver.holdDown(m: Mod) {
        t += 50
        onKey(KeyEv(Key.Modifier(m), true, t), mid)
    }
    private fun Resolver.holdUp(m: Mod) {
        t += 50
        onKey(KeyEv(Key.Modifier(m), false, t), mid)
    }
    private fun commit(s: String) = listOf<Out>(Out.Commit(s))

    @Test fun plainLetter() = assertEquals(commit("a"), r().press(Key.Letter('a')))

    @Test fun shiftOneShotAppliesOnceThenClears() {
        val x = r()
        x.tap(Mod.Shift)
        assertEquals(ModState.OneShot, x.mods.state(Mod.Shift))
        assertEquals(commit("A"), x.press(Key.Letter('a')))
        assertEquals(commit("b"), x.press(Key.Letter('b')))
    }

    @Test fun doubleTapShiftLocksAndTapUnlocks() {
        val x = r()
        x.tap(Mod.Shift)
        x.tap(Mod.Shift, gap = 100)
        assertEquals(ModState.Lock, x.mods.state(Mod.Shift))
        assertEquals(commit("A"), x.press(Key.Letter('a')))
        assertEquals(commit("B"), x.press(Key.Letter('b')))
        x.tap(Mod.Shift)
        assertEquals(ModState.Off, x.mods.state(Mod.Shift))
        assertEquals(commit("c"), x.press(Key.Letter('c')))
    }

    @Test fun slowSecondTapDoesNotLock() {
        val x = r()
        x.tap(Mod.Shift)
        x.tap(Mod.Shift, gap = 900)
        assertEquals(ModState.OneShot, x.mods.state(Mod.Shift))
    }

    @Test fun heldShiftChordLeavesNoStrayOneShot() {
        val x = r()
        x.holdDown(Mod.Shift)
        assertEquals(commit("A"), x.press(Key.Letter('a')))
        assertEquals(commit("B"), x.press(Key.Letter('b')))
        x.holdUp(Mod.Shift)
        assertEquals(ModState.Off, x.mods.state(Mod.Shift))
        assertEquals(commit("c"), x.press(Key.Letter('c')))
    }

    @Test fun longHoldWithoutKeyCancels() {
        val x = r()
        x.holdDown(Mod.Alt)
        t += 900
        x.holdUp(Mod.Alt)
        assertEquals(ModState.Off, x.mods.state(Mod.Alt))
    }

    @Test fun altLayerAndFallback() {
        val x = r()
        x.tap(Mod.Alt)
        assertEquals(commit("1"), x.press(Key.Letter('q')))
        assertEquals(commit("q"), x.press(Key.Letter('q')))
        val y = Resolver(layout = KeyLayout.SableProvisional.copy(alt = emptyMap()))
        y.tap(Mod.Alt)
        // missing mapping never swallows the key
        assertEquals(commit("q"), y.press(Key.Letter('q')))
    }

    @Test fun symLayer() {
        val x = r()
        x.tap(Mod.Sym)
        assertEquals(commit("~"), x.press(Key.Letter('q')))
    }

    @Test fun autoCapAtSentenceStart() {
        val x = r()
        assertEquals(commit("H"), x.press(Key.Letter('h'), FieldCtx(before = "")))
        assertEquals(commit("T"), x.press(Key.Letter('t'), FieldCtx(before = "Hi. ")))
        assertEquals(commit("t"), x.press(Key.Letter('t'), FieldCtx(before = "Hi, ")))
        assertEquals(
            commit("h"),
            x.press(Key.Letter('h'), FieldCtx(before = "", autoCapAllowed = false))
        )
        assertEquals(
            commit("h"),
            x.press(Key.Letter('h'), FieldCtx(InputClass.Password, before = ""))
        )
        assertEquals(
            commit("h"),
            r(KeyboardConfig(autoCap = false)).press(Key.Letter('h'), FieldCtx(before = ""))
        )
    }

    @Test fun numericFieldsGiveDigitsWithoutAlt() {
        val x = r()
        assertEquals(commit("1"), x.press(Key.Letter('q'), FieldCtx(InputClass.Number)))
        assertEquals(commit("0"), x.press(Key.Letter('p'), FieldCtx(InputClass.Phone)))
        assertEquals(
            commit("q"),
            r(
                KeyboardConfig(numericAutoAlt = false)
            ).press(Key.Letter('q'), FieldCtx(InputClass.Number))
        )
    }

    @Test fun navModeToggleMapAndSuspension() {
        val x = r()
        x.tap(Mod.Alt)
        assertEquals(listOf<Out>(Out.NavMode(true)), x.press(Key.Space))
        assertTrue(x.navActive)
        assertEquals(listOf<Out>(Out.Send(Key.Up)), x.press(Key.Letter('i')))
        // unmapped nav key is swallowed, not typed
        assertEquals(listOf<Out>(Out.Consume), x.press(Key.Letter('q')))
        // numeric field: nav suspended, digit typed
        assertEquals(commit("1"), x.press(Key.Letter('q'), FieldCtx(InputClass.Number)))
        // text input always wins
        assertEquals(commit("a"), x.press(Key.Letter('a'), FieldCtx(InputClass.Password)))
        x.tap(Mod.Alt)
        assertEquals(listOf<Out>(Out.NavMode(false)), x.press(Key.Space))
        assertFalse(x.navActive)
        assertEquals(commit("i"), x.press(Key.Letter('i')))
    }

    @Test fun navModeDisabledByConfig() {
        val x = r(KeyboardConfig(navModeEnabled = false))
        x.tap(Mod.Alt)
        assertEquals(commit(" "), x.press(Key.Space))
        assertFalse(x.navActive)
    }

    @Test fun doubleSpacePeriod() {
        val x = r()
        assertEquals(
            listOf<Out>(Out.DeleteBefore(1), Out.Commit(". ")),
            x.press(Key.Space, FieldCtx(before = "ok "))
        )
        assertEquals(commit(" "), x.press(Key.Space, FieldCtx(before = "ok")))
        assertEquals(commit(" "), x.press(Key.Space, FieldCtx(before = " ")))
        assertEquals(
            commit(" "),
            r(KeyboardConfig(doubleSpacePeriod = false)).press(Key.Space, FieldCtx(before = "ok "))
        )
    }

    @Test fun longPressReplacesWithAltOnce() {
        val x = r()
        assertEquals(commit("q"), x.press(Key.Letter('q')))
        val lp = x.onKey(KeyEv(Key.Letter('q'), true, 900, repeat = 1), mid)
        assertEquals(listOf<Out>(Out.DeleteBefore(1), Out.Commit("1")), lp)
        assertEquals(
            listOf<Out>(Out.Consume),
            x.onKey(KeyEv(Key.Letter('q'), true, 950, repeat = 2), mid)
        )
    }

    @Test fun nonEditableFocusIsUntouched() {
        val x = r()
        val none = FieldCtx(InputClass.None)
        assertEquals(listOf<Out>(Out.Pass), x.press(Key.Letter('a'), none))
        assertEquals(listOf<Out>(Out.Pass), x.onKey(KeyEv(Key.Modifier(Mod.Alt), true, 5), none))
        assertEquals(ModState.Off, x.mods.state(Mod.Alt))
    }

    @Test fun ctrlChordsPassThrough() = assertEquals(
        listOf<Out>(Out.Pass),
        r().onKey(KeyEv(Key.Letter('c'), true, 1, ctrlHeld = true), mid)
    )

    @Test fun shiftBackspaceDeletesForward() {
        val x = r()
        x.holdDown(Mod.Shift)
        assertEquals(listOf<Out>(Out.Send(Key.Delete)), x.press(Key.Backspace))
        x.holdUp(Mod.Shift)
        assertEquals(listOf<Out>(Out.Pass), x.press(Key.Backspace))
    }

    @Test fun shiftPunctuation() {
        val x = r()
        x.holdDown(Mod.Shift)
        assertEquals(commit("?"), x.press(Key.Punct('/')))
        x.holdUp(Mod.Shift)
        assertEquals(commit("/"), x.press(Key.Punct('/')))
    }

    @Test fun oneShotTimeoutExpires() {
        val x = r(KeyboardConfig(oneShotTimeoutMs = 1000))
        x.tap(Mod.Shift)
        t += 2000
        assertEquals(commit("a"), x.press(Key.Letter('a'), gap = 0))
    }

    @Test fun layoutIsMarkedUnverified() {
        assertFalse(KeyLayout.SableProvisional.verified)
    }

    @Test fun titan2PartialLayoutMatchesStockEvidenceAndStaysUnverified() {
        val l = KeyLayout.Titan2PhotoRead
        assertEquals("0", l.alt['q'])
        assertFalse(l.verified)
        assertEquals("-", l.alt['u'])
        assertEquals("_", l.alt['i'])
    }

    @Test fun deviceCharacterMapBeatsProvisionalTable() {
        // Titan 2 stock evidence: Alt+Q yields '0', where the provisional table says '1'.
        val x = r()
        x.tap(Mod.Alt)
        val out = x.onKey(KeyEv(Key.Letter('q'), true, 500, nativeAlt = '0'), mid)
        assertEquals(commit("0"), out)
        x.tap(Mod.Alt)
        assertEquals(commit("1"), x.press(Key.Letter('q'))) // no native char: fallback table
    }

    @Test fun nativeSymAndNumericAndLongPressUseDeviceMap() {
        val x = r()
        x.tap(Mod.Sym)
        assertEquals(commit("%"), x.onKey(KeyEv(Key.Letter('q'), true, 500, nativeSym = '%'), mid))
        assertEquals(
            commit("0"),
            r().onKey(
                KeyEv(Key.Letter('q'), true, 500, nativeAlt = '0'),
                FieldCtx(InputClass.Number)
            )
        )
        val y = r()
        assertEquals(
            listOf<Out>(Out.DeleteBefore(1), Out.Commit("0")),
            y.onKey(KeyEv(Key.Letter('q'), true, 900, repeat = 1, nativeAlt = '0'), mid)
        )
    }

    @Test fun keyProbeFormatsMetaAndCharacters() {
        assertEquals("-", KeyProbeFormat.metaNames(0))
        assertEquals("ALT+ALT_R", KeyProbeFormat.metaNames(0x2 or 0x20))
        assertEquals("SYM", KeyProbeFormat.metaNames(0x4))
        assertEquals("none", KeyProbeFormat.charText(0))
        assertEquals("U+005F '_'", KeyProbeFormat.charText('_'.code))
        assertEquals("U+000A", KeyProbeFormat.charText(10))
        assertEquals(
            "DOWN KEYCODE_U(49) scan=22 meta=ALT char=U+002D '-' dev=TitanKey",
            KeyProbeFormat.line(
                "DOWN",
                KeyProbeFormat.ProbeKey("KEYCODE_U", 49, 22),
                KeyProbeFormat.ProbeChar(0x2, '-'.code),
                "TitanKey",
                0
            )
        )
    }

    @Test fun layoutFollowsProfileIdAndNeverInherits() {
        assertEquals("titan2-printed-legends-partial", KeyLayout.forProfile("titan2").id)
        assertEquals(KeyLayout.SableProvisional.id, KeyLayout.forProfile("titan2-elite").id)
        assertEquals(KeyLayout.SableProvisional.id, KeyLayout.forProfile("zinwa-q27").id)
        assertEquals(KeyLayout.SableProvisional.id, KeyLayout.forProfile(null).id)
    }

    // Every Alt character below was produced by the stock platform key map on a Titan 2 (Key Probe
    // sweep, 2026-10-01); P is photo-only.
    @Test fun altTableMatchesDeviceSweep() {
        val device = mapOf(
            'q' to "0", 'w' to "1", 'e' to "2", 'r' to "3", 't' to "(", 'y' to ")",
            'u' to "-", 'i' to "_", 'o' to "/",
            'a' to "@", 's' to "4", 'd' to "5", 'f' to "6", 'g' to "*", 'h' to "#", 'j' to "+",
            'k' to "\"", 'l' to "'",
            'z' to "!", 'x' to "7", 'c' to "8", 'v' to "9", 'b' to ".", 'n' to ",", 'm' to "?"
        )
        for ((k, v) in device) assertEquals("alt+$k", v, KeyLayout.Titan2PhotoRead.alt[k])
        assertEquals(":", KeyLayout.Titan2PhotoRead.alt['p'])
    }
}
