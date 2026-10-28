package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTextTest {
    private val at = AutoText()
    private fun hit(before: String, english: Boolean = true) =
        at.match(before, english)?.replacement

    @Test fun builtInsFixContractionsAndTypos() {
        assertEquals("don't", hit("I dont"))
        assertEquals("I'm", hit("im"))
        assertEquals("the", hit("see teh"))
        assertEquals("I", hit("so i"))
    }

    @Test fun caseFollowsTheTypedWord() {
        assertEquals("Don't", hit("Dont"))
        assertEquals("DON'T", hit("DONT"))
        assertEquals("I'm", hit("IM"))
        assertNull("already right", hit("I"))
    }

    @Test fun onlyWholeProseWords() {
        assertNull(hit("hello"))
        assertNull("part of a word", hit("abcdont"))
        assertNull("after an apostrophe word", hit("o'teh"))
        assertNull("email", hit("me@im"))
        assertNull("path", hit("/usr/lt"))
        assertNull("hashtag", hit("#dont"))
        assertNull("digits only", hit("123"))
        assertNull("nothing typed", hit(""))
    }

    @Test fun builtInsAreEnglishOnlyButUserEntriesAreNot() {
        assertNull(hit("i", english = false))
        val user = AutoText(mapOf("BRB" to "be right back", "i" to "y"))
        assertEquals("be right back", user.match("brb", english = false)?.replacement)
        assertEquals("y", user.match("i", english = true)?.replacement)
    }

    @Test fun variablesExpandAndUnknownOnesLeaveTheWord() {
        val vars = { c: Char -> if (c == 'D') "Wednesday, October 28, 2026" else null }
        assertEquals("Wednesday, October 28, 2026", at.match("ld", true, vars)?.replacement)
        assertNull("no time source", at.match("lt", true, vars))
        val user = AutoText(mapOf("pct" to "100%%"))
        assertEquals("100%", user.match("pct", true)?.replacement)
    }

    @Test fun storedListRoundTripsAndSkipsBadLines() {
        val parsed = AutoText.parse(
            "brb = be right back\nno equals\n=x\ntwo words=x\nempty=\nomw=on my way"
        )
        assertEquals(mapOf("brb" to "be right back", "omw" to "on my way"), parsed)
        assertEquals("brb=be right back\nomw=on my way", AutoText.format(parsed))
        assertEquals(parsed, AutoText.parse(AutoText.format(parsed)))
        assertTrue(AutoText.validShortcut("y'all"))
    }

    @Test fun everyBuiltInIsAValidShortcut() {
        AutoText.BUILT_IN.keys.forEach {
            assertTrue(
                it,
                AutoText.validShortcut(it) && it == it.lowercase()
            )
        }
    }

    // --- in the resolver ---

    private var t = 0L
    private fun Resolver.down(
        k: Key,
        before: String,
        cls: InputClass = InputClass.Text,
        autoText: Boolean = true
    ) = onKey(KeyEv(k, true, ++t), FieldCtx(cls, before, autoText = autoText))

    @Test fun spaceAndPunctuationExpand() {
        val r = Resolver()
        assertEquals(listOf(Out.DeleteBefore(4), Out.Commit("don't ")), r.down(Key.Space, "I dont"))
        assertEquals(listOf(Out.DeleteBefore(3), Out.Commit("the.")), r.down(Key.Punct('.'), "teh"))
    }

    @Test fun altLayerPunctuationExpands() {
        // Alt+M types '.' on the BlackBerry layout
        val r = Resolver(layout = KeyLayout.ZinwaQ25Kcm)
        r.onKey(
            KeyEv(Key.Modifier(Mod.Alt), true, ++t),
            FieldCtx(InputClass.Text, "dont", autoText = true)
        )
        assertEquals(
            listOf(Out.DeleteBefore(4), Out.Commit("don't.")),
            r.down(Key.Letter('m'), "dont")
        )
    }

    @Test fun backspaceRightAfterUndoesOnce() {
        val r = Resolver()
        r.down(Key.Space, "dont")
        assertEquals(
            listOf(Out.DeleteBefore(6), Out.Commit("dont ")),
            r.down(Key.Backspace, "don't ")
        )
        assertEquals(listOf(Out.Pass), r.down(Key.Backspace, "dont "))
    }

    @Test fun anotherKeyEndsTheUndo() {
        val r = Resolver()
        r.down(Key.Space, "dont")
        r.down(Key.Letter('a'), "don't ")
        assertEquals(listOf(Out.Pass), r.down(Key.Backspace, "don't a"))
    }

    @Test fun offWhereItDoesNotBelong() {
        val r = Resolver()
        val plain = listOf(Out.Commit(" "))
        assertEquals("field said no", plain, r.down(Key.Space, "dont", autoText = false))
        assertEquals("password", plain, r.down(Key.Space, "dont", InputClass.Password))
        val off = Resolver(cfg = KeyboardConfig(autoText = false))
        assertEquals("switched off", plain, off.down(Key.Space, "dont"))
        val pinyin = Resolver().apply {
            language =
                Languages.all.first { it.engine != Engine.Direct }
        }
        assertEquals("composing language", plain, pinyin.down(Key.Space, "dont"))
    }

    @Test fun fieldPolicyKeepsAutoTextToProse() {
        val text = InputTypeBits.CLASS_TEXT
        assertTrue(FieldPolicy.autoTextAllowed(text or InputTypeBits.TEXT_FLAG_CAP_SENTENCES))
        listOf(
            null,
            InputTypeBits.CLASS_NUMBER,
            text or InputTypeBits.TEXT_VARIATION_PASSWORD,
            text or InputTypeBits.TEXT_VARIATION_URI,
            text or InputTypeBits.TEXT_VARIATION_EMAIL_ADDRESS,
            text or InputTypeBits.TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            text or InputTypeBits.TEXT_VARIATION_FILTER,
            text or InputTypeBits.TEXT_FLAG_NO_SUGGESTIONS
        ).forEach { assertTrue("$it", !FieldPolicy.autoTextAllowed(it)) }
    }
}
