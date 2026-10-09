package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for critical text entry: fields where typing must work with no on-screen help
 * (Bluetooth pairing codes, system dialogs, Setup Wizard, lockscreen PIN/password, Wi-Fi password, account sign-in).
 * This is source-level evidence only. Device runtime acceptance is a separate, pending gate.
 */
class CriticalTextEntryTest {
    private var t = 0L
    private val text = FieldCtx(InputClass.Text, before = "hello", autoCapAllowed = true)
    private val textStart = FieldCtx(InputClass.Text, before = "", autoCapAllowed = true)
    private val password = FieldCtx(InputClass.Password, before = "", autoCapAllowed = true)
    private val number = FieldCtx(InputClass.Number)
    private val numberPassword = FieldCtx(InputClass.NumberPassword)
    private val phone = FieldCtx(InputClass.Phone)

    private fun resolver(
        lang: Language = Languages.English,
        cfg: KeyboardConfig = KeyboardConfig()
    ) = Resolver(cfg = cfg).also { it.language = lang }

    private fun Resolver.press(k: Key, ctx: FieldCtx, repeat: Int = 0): List<Out> {
        t += 50
        val d = onKey(KeyEv(k, true, t, repeat), ctx)
        t += 20
        onKey(KeyEv(k, false, t), ctx)
        return d
    }

    private fun Resolver.tap(m: Mod, ctx: FieldCtx) {
        t += 50
        onKey(KeyEv(Key.Modifier(m), true, t), ctx)
        t += 30
        onKey(KeyEv(Key.Modifier(m), false, t), ctx)
    }

    private fun commit(s: String) = listOf<Out>(Out.Commit(s))
    private val chinese get() = Languages.forTag("zh_CN")
    private val korean get() = Languages.forTag("ko_KR")

    // ---- field classification (EditorInfo.inputType -> InputClass)

    @Test fun inputTypeBitsMatchTheAospValues() {
        assertEquals(0x0000000f, InputTypeBits.MASK_CLASS)
        assertEquals(0x00000ff0, InputTypeBits.MASK_VARIATION)
        assertEquals(0x1, InputTypeBits.CLASS_TEXT)
        assertEquals(0x2, InputTypeBits.CLASS_NUMBER)
        assertEquals(0x3, InputTypeBits.CLASS_PHONE)
        assertEquals(0x80, InputTypeBits.TEXT_VARIATION_PASSWORD)
        assertEquals(0x90, InputTypeBits.TEXT_VARIATION_VISIBLE_PASSWORD)
        assertEquals(0xe0, InputTypeBits.TEXT_VARIATION_WEB_PASSWORD)
        assertEquals(0x10, InputTypeBits.NUMBER_VARIATION_PASSWORD)
        assertEquals(0x4000, InputTypeBits.TEXT_FLAG_CAP_SENTENCES)
    }

    @Test fun classifiesNullAndTypeNullAsNone() {
        assertEquals(InputClass.None, FieldPolicy.classify(null))
        assertEquals(InputClass.None, FieldPolicy.classify(0))
    }

    @Test fun classifiesPlainTextAndTextVariationsAsText() {
        assertEquals(InputClass.Text, FieldPolicy.classify(0x1))
        assertEquals(InputClass.Text, FieldPolicy.classify(0x1 or 0x4000))
        assertEquals(InputClass.Text, FieldPolicy.classify(0x21)) // email address
        assertEquals(InputClass.Text, FieldPolicy.classify(0x11)) // URI
        assertEquals(InputClass.Text, FieldPolicy.classify(0x4)) // date/time falls back to Text
    }

    @Test fun classifiesAllThreeTextPasswordVariationsAsPassword() {
        assertEquals(InputClass.Password, FieldPolicy.classify(0x1 or 0x80))
        assertEquals(InputClass.Password, FieldPolicy.classify(0x1 or 0x90))
        assertEquals(InputClass.Password, FieldPolicy.classify(0x1 or 0xe0))
        // with the no-suggestions flag
        assertEquals(InputClass.Password, FieldPolicy.classify(0x1 or 0x80 or 0x20000))
    }

    @Test fun classifiesNumberNumericPasswordAndPhone() {
        assertEquals(InputClass.Number, FieldPolicy.classify(0x2))
        // decimal + signed
        assertEquals(InputClass.Number, FieldPolicy.classify(0x2 or 0x2000 or 0x1000))
        assertEquals(InputClass.NumberPassword, FieldPolicy.classify(0x2 or 0x10))
        assertEquals(InputClass.Phone, FieldPolicy.classify(0x3))
    }

    @Test fun sentenceCapsOnlyForTextClassWithTheFlag() {
        assertTrue(FieldPolicy.wantsSentenceCaps(0x1 or 0x4000))
        assertFalse(FieldPolicy.wantsSentenceCaps(0x1))
        assertFalse(FieldPolicy.wantsSentenceCaps(0x2 or 0x4000))
        assertFalse(FieldPolicy.wantsSentenceCaps(null))
    }

    // ---- software fallback policy

    @Test fun softFallbackIsOfferedForDigitEntryFields() {
        for (c in listOf(InputClass.Number, InputClass.Phone, InputClass.NumberPassword)) {
            assertTrue(
                "$c",
                FieldPolicy.softKeyboardWanted(
                    c,
                    forceSoft = false,
                    softToggled = false,
                    softForNumeric = true
                )
            )
        }
    }

    @Test fun softFallbackIsNotOfferedJustBecauseATextFieldGainedFocus() {
        for (c in listOf(InputClass.Text, InputClass.Password, InputClass.None)) {
            assertFalse(
                "$c",
                FieldPolicy.softKeyboardWanted(
                    c,
                    forceSoft = false,
                    softToggled = false,
                    softForNumeric = true
                )
            )
        }
    }

    @Test fun softFallbackHonoursTheUserPreferenceAndForcing() {
        assertFalse(
            FieldPolicy.softKeyboardWanted(InputClass.Number, false, false, softForNumeric = false)
        )
        assertTrue(
            FieldPolicy.softKeyboardWanted(
                InputClass.Text,
                forceSoft = true,
                softToggled = false,
                softForNumeric = false
            )
        )
        assertTrue(
            FieldPolicy.softKeyboardWanted(
                InputClass.Password,
                forceSoft = false,
                softToggled = true,
                softForNumeric = false
            )
        )
    }

    // ---- text

    @Test fun textFieldTypesLettersAndAutoCapsAtStart() {
        val r = resolver()
        assertEquals(commit("h"), r.press(Key.Letter('h'), text))
        assertEquals(commit("H"), r.press(Key.Letter('h'), textStart))
    }

    @Test fun textFieldDoubleSpaceMakesAPeriod() {
        val ctx = FieldCtx(InputClass.Text, before = "ok ")
        assertEquals(
            listOf(Out.DeleteBefore(1), Out.Commit(". ")),
            resolver().press(Key.Space, ctx)
        )
    }

    // ---- password

    @Test fun passwordKeepsExactCaseAndNeverAutoCapsOrDoubleSpaces() {
        val r = resolver()
        assertEquals(commit("a"), r.press(Key.Letter('a'), password)) // field start: no auto-cap
        r.tap(Mod.Shift, password)
        assertEquals(commit("A"), r.press(Key.Letter('a'), password))
        assertEquals(commit(" "), r.press(Key.Space, FieldCtx(InputClass.Password, before = "a ")))
    }

    @Test fun passwordLettersAreNotTurnedIntoDigits() {
        assertEquals(commit("q"), resolver().press(Key.Letter('q'), password))
    }

    @Test fun passwordSuspendsNavMode() {
        val r = resolver()
        val navLetter = r.layout.navMap.keys.first()
        r.setNav(true)
        assertTrue(
            r.press(Key.Letter(navLetter), text).any {
                it is Out.Send
            }
        ) // control: nav works in text
        assertEquals(commit(navLetter.toString()), r.press(Key.Letter(navLetter), password))
    }

    @Test fun passwordInAComposingLanguageBypassesComposition() {
        // control: the same key composes in a text field
        assertTrue(resolver(chinese).press(Key.Letter('n'), text).single() is Out.Compose)
        assertEquals(commit("n"), resolver(chinese).press(Key.Letter('n'), password))
        assertEquals(commit("g"), resolver(korean).press(Key.Letter('g'), password))
    }

    // ---- number, numeric password, phone

    @Test fun numberFieldTypesDigitsFromLetterKeys() {
        val r = resolver()
        assertEquals(commit(r.layout.alt.getValue('q')), r.press(Key.Letter('q'), number))
        assertEquals(commit("7"), r.press(Key.Digit('7'), number))
    }

    @Test fun numericPasswordTypesDigitsAndNeverComposesAndSuspendsNav() {
        val r = resolver(chinese)
        assertEquals(commit(r.layout.alt.getValue('q')), r.press(Key.Letter('q'), numberPassword))
        val nav = resolver()
        nav.setNav(true)
        val navLetter = nav.layout.navMap.keys.first()
        assertEquals(
            commit(nav.layout.alt.getValue(navLetter)),
            nav.press(Key.Letter(navLetter), numberPassword)
        )
    }

    @Test fun phoneFieldTypesDigitsAndSuspendsNav() {
        val r = resolver()
        r.setNav(true)
        val l = r.layout.navMap.keys.first()
        assertEquals(commit(r.layout.alt.getValue(l)), r.press(Key.Letter(l), phone))
    }

    @Test fun numericAutoAltCanBeDisabled() {
        val r = resolver(cfg = KeyboardConfig(numericAutoAlt = false))
        assertEquals(commit("q"), r.press(Key.Letter('q'), number))
    }

    @Test fun nonEditableFocusPassesEverythingThrough() {
        val r = resolver()
        assertEquals(listOf<Out>(Out.Pass), r.press(Key.Letter('a'), FieldCtx(InputClass.None)))
        assertEquals(
            listOf<Out>(Out.Pass),
            r.press(Key.Modifier(Mod.Alt), FieldCtx(InputClass.None))
        )
    }

    // ---- composition flush and backspace

    private fun pinyin(): PinyinComposer {
        val dict = PinyinDict.parse(sequenceOf("hao\t好\t100", "ni\t你\t200", "ni\t泥\t10"))
        return PinyinComposer { dict }
    }

    @Test fun backspaceEditsTheCompositionThenFallsThroughToARealBackspace() {
        val c = pinyin()
        c.type("n")
        c.type("i")
        assertEquals("ni", c.composing)
        assertTrue(c.backspace())
        assertEquals("n", c.composing)
        assertTrue(c.backspace())
        assertEquals("", c.composing)
        assertFalse(c.backspace()) // empty: caller must send a real Backspace
    }

    @Test fun flushCommitsTheTypedLettersAsIsAndClears() {
        val c = pinyin()
        c.type("n")
        c.type("i")
        assertEquals("ni", c.flush())
        assertEquals("", c.composing)
        assertEquals("", c.flush())
    }

    @Test fun resetDropsTheCompositionWithoutCommitting() {
        val c = pinyin()
        c.type("h")
        c.reset()
        assertEquals("", c.composing)
        assertTrue(c.candidates.isEmpty())
    }

    @Test fun nonLetterInputFlushesTheCompositionBeforeThePunctuation() {
        val c = pinyin()
        c.type("n")
        c.type("i")
        assertEquals("你，", c.type(","))
        assertEquals("", c.composing)
    }

    @Test fun hangulBackspaceAndFlushKeepTheSyllable() {
        val c = HangulComposer()
        c.type("ㅎ")
        c.type("ㅏ")
        c.type("ㄴ")
        assertTrue(c.backspace())
        assertEquals("하", c.composing)
        assertEquals("하", c.flush())
        assertEquals("", c.composing)
    }

    // ---- Alt / Sym state

    @Test fun altOneShotAppliesOnceThenClears() {
        val r = resolver()
        r.tap(Mod.Alt, text)
        assertEquals(ModState.OneShot, r.mods.state(Mod.Alt))
        assertEquals(commit(r.layout.alt.getValue('q')), r.press(Key.Letter('q'), text))
        assertEquals(ModState.Off, r.mods.state(Mod.Alt))
        assertEquals(commit("q"), r.press(Key.Letter('q'), text))
    }

    @Test fun altLockPersistsUntilTappedAgain() {
        val r = resolver()
        r.tap(Mod.Alt, text)
        t += 100
        r.tap(Mod.Alt, text)
        assertEquals(ModState.Lock, r.mods.state(Mod.Alt))
        assertEquals(commit(r.layout.alt.getValue('w')), r.press(Key.Letter('w'), text))
        assertEquals(commit(r.layout.alt.getValue('e')), r.press(Key.Letter('e'), text))
        r.tap(Mod.Alt, text)
        assertEquals(ModState.Off, r.mods.state(Mod.Alt))
    }

    @Test fun altAndSymAreStillConsumedButNeverEatenOutsideEditableFields() {
        val r = resolver()
        assertEquals(listOf<Out>(Out.Consume), r.press(Key.Modifier(Mod.Sym), text))
        assertEquals(
            listOf<Out>(Out.Pass),
            r.press(Key.Modifier(Mod.Sym), FieldCtx(InputClass.None))
        )
    }

    @Test fun deviceCharacterMapAltWinsOverTheProvisionalTable() {
        val r = resolver()
        r.tap(Mod.Alt, text)
        t += 50
        assertEquals(commit("0"), r.onKey(KeyEv(Key.Letter('q'), true, t, nativeAlt = '0'), text))
    }

    @Test fun missingSymMappingFallsBackToThePlainCharacter() {
        val r = Resolver(layout = KeyLayout.Titan2PhotoRead).also {
            it.language = Languages.English
        }
        r.tap(Mod.Sym, text)
        assertEquals(commit("q"), r.press(Key.Letter('q'), text))
    }

    @Test fun altAndSymStateIsClearedByAFreshInput() {
        val r = resolver()
        r.tap(Mod.Alt, text)
        r.tap(Mod.Sym, text)
        r.mods.reset()
        assertEquals(ModState.Off, r.mods.state(Mod.Alt))
        assertEquals(ModState.Off, r.mods.state(Mod.Sym))
    }
}
