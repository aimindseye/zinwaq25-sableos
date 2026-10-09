package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactStripTest {
    private val en = Languages.English
    private val es = Languages.all.first { it.tag == "es_ES" }
    private val layout = KeyLayout.SableProvisional
    private val titan2 = KeyLayout.forProfile("titan2")

    private val base = StripInputs(
        field = InputClass.Text, softKeyboardShown = false, enabled = true, language = en,
        shift = ModState.Off, alt = ModState.Off, sym = ModState.Off, navOn = false,
        navEffective = false, candidates = emptyList(), variation = null, layout = layout,
        nowMs = 10_000L
    )

    private val cands = listOf(Cand("你", 1), Cand("尼", 1), Cand("泥", 1))

    // ---- visibility / mode -------------------------------------------------------------------

    @Test fun noEditableFieldMeansNoStrip() {
        val m = CompactStrip.build(base.copy(field = InputClass.None, candidates = cands))
        assertEquals(StripMode.Hidden, m.mode)
        assertFalse(m.visible)
    }

    @Test fun hiddenSoftKeyboardShowsTheCompactStrip() {
        val m = CompactStrip.build(base)
        assertEquals(StripMode.Compact, m.mode)
        assertTrue(m.visible)
    }

    @Test fun softKeyboardShownKeepsTheOldCandidatesOnlyBehaviour() {
        assertEquals(StripMode.Hidden, CompactStrip.build(base.copy(softKeyboardShown = true)).mode)
        val m = CompactStrip.build(base.copy(softKeyboardShown = true, candidates = cands))
        assertEquals(StripMode.CandidatesOnly, m.mode)
        assertTrue(m.mods.isEmpty())
        assertEquals(StripContent.Candidates(cands), m.content)
    }

    @Test fun disablingTheStripFallsBackToCandidatesOnly() {
        assertEquals(StripMode.Hidden, CompactStrip.build(base.copy(enabled = false)).mode)
        assertEquals(
            StripMode.CandidatesOnly,
            CompactStrip.build(base.copy(enabled = false, candidates = cands)).mode
        )
    }

    // ---- language ----------------------------------------------------------------------------

    @Test fun languageChipComesFromTheTagAndLayout() {
        assertEquals("EN", CompactStrip.languageChip(en))
        assertEquals("ES", CompactStrip.languageChip(es))
        val phonetic = Languages.all.firstOrNull { it.layout == "phonetic" }
        if (phonetic != null) {
            assertTrue(CompactStrip.languageChip(phonetic).contains("·ph"))
        }
        assertEquals("English (US)", CompactStrip.build(base).languageDescription)
    }

    @Test fun everyShippedLanguageGetsANonEmptyChip() {
        for (l in Languages.all) assertTrue(l.tag, CompactStrip.languageChip(l).isNotBlank())
    }

    // ---- modifier state ----------------------------------------------------------------------

    @Test fun modifierChipsFollowTheModifierMachineStates() {
        val m = CompactStrip.build(
            base.copy(shift = ModState.OneShot, alt = ModState.Lock, sym = ModState.Held)
        )
        assertEquals(listOf("shift", "alt", "sym", "nav"), m.mods.map { it.id })
        assertEquals(ChipState.Once, m.mods[0].state)
        assertEquals(ChipState.Locked, m.mods[1].state)
        assertEquals(ChipState.Held, m.mods[2].state)
        assertEquals(ChipState.Off, m.mods[3].state)
    }

    @Test fun allOffShowsEveryChipOff() {
        val m = CompactStrip.build(base)
        assertTrue(m.mods.all { it.state == ChipState.Off })
        assertEquals(listOf("Shift", "Alt", "Sym", "Nav"), m.mods.map { it.label })
    }

    @Test fun navShowsActiveOrSuspendedHonestly() {
        assertEquals(
            ChipState.Active,
            CompactStrip.build(base.copy(navOn = true, navEffective = true)).mods[3].state
        )
        // Nav is on but the field class suspends it (password/number): never shown as active
        assertEquals(
            ChipState.Suspended,
            CompactStrip.build(base.copy(field = InputClass.Password, navOn = true)).mods[3].state
        )
        assertEquals(ChipState.Off, CompactStrip.build(base).mods[3].state)
    }

    @Test fun resolverReportsNavEffectiveSoTheStripCannotOverclaim() {
        val r = Resolver()
        r.setNav(true)
        assertTrue(r.navEffective(InputClass.Text))
        assertFalse(r.navEffective(InputClass.Password))
        assertFalse(r.navEffective(InputClass.Number))
        assertFalse(r.navEffective(InputClass.Phone))
        r.setNav(false)
        assertFalse(r.navEffective(InputClass.Text))
    }

    // ---- candidates --------------------------------------------------------------------------

    @Test fun existingCandidatesAreShownBounded() {
        val many = (1..30).map { Cand("c$it", 1) }
        val m = CompactStrip.build(base.copy(candidates = many))
        val c = m.content as StripContent.Candidates
        assertEquals(StripPolicy.MAX_CANDIDATES, c.items.size)
        assertEquals(many.first(), c.items.first())
    }

    @Test fun candidatesBeatVariationAndSymbols() {
        val v = VariationInfo('a', listOf("á", "à"), 0, 9_900L)
        val m = CompactStrip.build(
            base.copy(candidates = cands, variation = v, alt = ModState.OneShot)
        )
        assertTrue(m.content is StripContent.Candidates)
    }

    // ---- variation ---------------------------------------------------------------------------

    @Test fun variationIsShortLived() {
        val v = VariationInfo('a', listOf("á", "à", "â"), 1, 9_000L)
        val shown = CompactStrip.build(base.copy(language = es, variation = v, nowMs = 9_500L))
        assertEquals(StripContent.Variation('a', listOf("á", "à", "â"), 1), shown.content)
        val justBefore = 9_000L + StripPolicy.VARIATION_TTL_MS - 1
        assertTrue(
            CompactStrip.build(
                base.copy(variation = v, nowMs = justBefore)
            ).content is StripContent.Variation
        )
        val gone = CompactStrip.build(
            base.copy(
                variation = v,
                nowMs =
                    9_000L + StripPolicy.VARIATION_TTL_MS
            )
        )
        assertEquals(StripContent.None, gone.content)
    }

    @Test fun variationNeedsAtLeastTwoOptionsAndAClockThatDidNotGoBackwards() {
        val one = VariationInfo('n', listOf("ñ"), 0, 9_900L)
        assertEquals(StripContent.None, CompactStrip.build(base.copy(variation = one)).content)
        val future = VariationInfo('a', listOf("á", "à"), 0, 20_000L)
        assertEquals(StripContent.None, CompactStrip.build(base.copy(variation = future)).content)
    }

    @Test fun variationIndexIsClamped() {
        val v = VariationInfo('a', listOf("á", "à"), 7, 9_900L)
        val c = CompactStrip.build(base.copy(variation = v)).content as StripContent.Variation
        assertEquals(1, c.selected)
    }

    @Test fun resolverReportsTheExistingAccentCycle() {
        val r = Resolver()
        r.language = es
        val ctx = FieldCtx(InputClass.Text)
        assertNull(r.variation())
        r.onKey(KeyEv(Key.Modifier(Mod.Sym), true, 100L), ctx)
        r.onKey(KeyEv(Key.Letter('u'), true, 150L), ctx)
        val v1 = r.variation()!!
        assertEquals('u', v1.base)
        assertEquals(0, v1.index)
        assertEquals(150L, v1.atMs)
        assertEquals(es.variants('u'), v1.options)
        r.onKey(KeyEv(Key.Letter('u'), true, 400L), ctx)
        assertEquals(1, r.variation()!!.index)
        assertEquals(400L, r.variation()!!.atMs)
    }

    @Test fun anyOtherKeyEndsTheVariation() {
        val r = Resolver()
        r.language = es
        val ctx = FieldCtx(InputClass.Text)
        r.onKey(KeyEv(Key.Modifier(Mod.Sym), true, 100L), ctx)
        r.onKey(KeyEv(Key.Letter('a'), true, 150L), ctx)
        assertTrue(r.variation() != null)
        r.onKey(KeyEv(Key.Modifier(Mod.Sym), false, 160L), ctx)
        r.onKey(KeyEv(Key.Space, true, 200L), ctx)
        assertNull(r.variation())
    }

    @Test fun clearVariationResets() {
        val r = Resolver()
        r.language = es
        val ctx = FieldCtx(InputClass.Text)
        r.onKey(KeyEv(Key.Modifier(Mod.Sym), true, 100L), ctx)
        r.onKey(KeyEv(Key.Letter('a'), true, 150L), ctx)
        r.clearVariation()
        assertNull(r.variation())
    }

    // ---- symbols: only existing layout data --------------------------------------------------

    @Test fun symbolsAppearOnlyWhileAnAffordingModifierIsArmed() {
        assertEquals(StripContent.None, CompactStrip.build(base).content)
        val alt = CompactStrip.build(
            base.copy(alt = ModState.OneShot)
        ).content as StripContent.Symbols
        assertEquals("Alt", alt.source)
        val sym = CompactStrip.build(base.copy(sym = ModState.Lock)).content as StripContent.Symbols
        assertEquals("Sym", sym.source)
    }

    @Test fun symbolsAreBoundedDistinctAndTakenFromTheLayoutTables() {
        val s = CompactStrip.build(
            base.copy(alt = ModState.OneShot)
        ).content as StripContent.Symbols
        assertTrue(s.items.size <= StripPolicy.MAX_SYMBOLS)
        assertEquals(s.items.size, s.items.toSet().size)
        assertTrue(layout.alt.values.containsAll(s.items))
        val y = CompactStrip.build(
            base.copy(sym = ModState.OneShot)
        ).content as StripContent.Symbols
        assertTrue(layout.sym.values.containsAll(y.items))
    }

    @Test fun anUnconfirmedSymLayerIsNeverGuessed() {
        // Titan 2 has no confirmed Sym legends: Sym alone offers nothing, Alt offers its printed legends
        assertTrue(titan2.sym.isEmpty())
        assertEquals(
            StripContent.None,
            CompactStrip.build(base.copy(sym = ModState.OneShot, layout = titan2)).content
        )
        val alt = CompactStrip.build(base.copy(alt = ModState.OneShot, layout = titan2)).content
        assertTrue((alt as StripContent.Symbols).items.containsAll(listOf("0", "1", "@")))
    }

    @Test fun noEmojiIsOfferedBecauseNoProvenancedEmojiDataExists() {
        val all = listOf(layout, titan2).flatMap { it.alt.values + it.sym.values }
        assertTrue(all.none { s -> s.codePoints().anyMatch { it >= 0x1F000 } })
        for (m in listOf(ModState.OneShot, ModState.Lock)) {
            val c = CompactStrip.build(base.copy(alt = m, sym = m)).content as StripContent.Symbols
            assertTrue(c.items.none { s -> s.codePoints().anyMatch { it >= 0x1F000 } })
        }
    }

    // ---- privacy: secret and numeric fields --------------------------------------------------

    @Test fun secretAndNumericFieldsShowNoCandidatesVariationOrSymbols() {
        val v = VariationInfo('a', listOf("á", "à"), 0, 9_900L)
        for (f in listOf(
            InputClass.Password,
            InputClass.NumberPassword,
            InputClass.Number,
            InputClass.Phone
        )) {
            val m = CompactStrip.build(
                base.copy(
                    field = f,
                    candidates = cands,
                    variation = v,
                    alt = ModState.OneShot,
                    sym = ModState.OneShot
                )
            )
            assertEquals(StripMode.Compact, m.mode)
            assertEquals(f.name, StripContent.None, m.content)
            assertEquals(4, m.mods.size) // modifier state is still useful when typing a PIN
        }
    }

    // ---- geometry: vertical space and safe areas ---------------------------------------------

    @Test fun stripUsesSubstantiallyLessHeightThanTheFullKeyboard() {
        assertEquals(48, StripGeometry.HEIGHT_DP)
        assertEquals(220, SoftKeyboardGeometry.minHeightDp())
        assertTrue(StripGeometry.HEIGHT_DP * 4 <= SoftKeyboardGeometry.minHeightDp())
    }

    @Test fun squareDisplaysAddNoCornerInset() {
        assertEquals(0, StripGeometry.cornerInset(0, 0))
        assertEquals(0, StripGeometry.cornerInset(-5, 10))
    }

    @Test fun cornerInsetFollowsTheCircleAndVanishesAboveIt() {
        assertEquals(100, StripGeometry.cornerInset(100, 0))
        assertEquals(0, StripGeometry.cornerInset(100, 100))
        assertEquals(0, StripGeometry.cornerInset(100, 150))
        val low = StripGeometry.cornerInset(100, 20)
        val high = StripGeometry.cornerInset(100, 60)
        assertTrue(low > high && high > 0)
    }

    @Test fun contentPaddingKeepsTheStripInsideCutoutsCornersAndAboveTheNavBar() {
        val p = StripGeometry.contentPadding(
            8,
            StripGeometry.Insets(
                cutoutRight = 40,
                cornerLeftRadius = 100,
                cornerRightRadius = 100,
                navBottom = 60
            )
        )
        assertEquals(60, p.bottom)
        assertEquals(8 + StripGeometry.cornerInset(100, 60), p.left)
        assertEquals(8 + maxOf(40, StripGeometry.cornerInset(100, 60)), p.right)
    }

    @Test fun contentPaddingNeverGoesNegative() {
        val p = StripGeometry.contentPadding(8, StripGeometry.Insets(navBottom = -5))
        assertEquals(StripGeometry.Padding(8, 8, 0), p)
    }

    @Test fun eachDeviceGetsItsOwnInsetsNoProfileGeometryIsHardCoded() {
        // Titan 2 (square) and an Elite-like rounded display differ only through the insets passed in
        val square = StripGeometry.contentPadding(8, StripGeometry.Insets())
        val rounded = StripGeometry.contentPadding(
            8,
            StripGeometry.Insets(cornerLeftRadius = 80, cornerRightRadius = 80)
        )
        assertEquals(8, square.left)
        assertTrue(rounded.left > square.left)
    }
}
