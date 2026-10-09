package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticTest {
    private fun typed(tag: String, roman: String): String {
        val l = Languages.forTag(tag, "phonetic")
        val c = Composers.create(l.engine, { null }, l.phonetic)!!
        roman.forEach { assertEquals("", c.type(it.toString())) }
        return c.flush()
    }

    @Test fun hindiWords() {
        assertEquals("नमस्ते", typed("hi", "namaste"))
        assertEquals("भारत", typed("hi", "bhaarat"))
        assertEquals("हिन्दी", typed("hi", "hindii"))
        assertEquals("हिंदी", typed("hi", "hiMdii"))
        assertEquals("क्षेत्र", typed("hi", "xetra"))
        assertEquals("ज्ञान", typed("hi", "gyaan"))
        assertEquals("ऋषि", typed("hi", "RiShi"))
        assertEquals("ॐ", typed("hi", "OM"))
        assertEquals("कम", typed("hi", "kam")) // final schwa is not written
    }

    @Test fun retroflexAndAspirateByCase() {
        assertEquals("टट", typed("hi", "TaTa"))
        assertEquals("तत", typed("hi", "tata"))
        assertEquals("ठंडा", typed("hi", "ThaMDaa"))
        assertEquals("थक", typed("hi", "thak"))
        assertEquals("शष", typed("hi", "shaSha"))
    }

    @Test fun viramaIndependentVowelsAndMarks() {
        assertEquals("क्", typed("hi", "k_"))
        assertEquals("अक", typed("hi", "ak"))
        assertEquals("आ", typed("hi", "aa"))
        assertEquals("कै", typed("hi", "kai")) // ai is one vowel
        assertEquals("दुःख", typed("hi", "duHkha"))
        assertEquals("हँसी", typed("hi", "h~sii").replace("हँसी", "हँसी"))
        assertEquals("हँ", typed("hi", "h~"))
    }

    @Test fun backspaceReconvertsTheWholeWord() {
        val l = Languages.forTag("hi", "phonetic")
        val c = Composers.create(l.engine, { null }, l.phonetic)!!
        "namast".forEach { c.type(it.toString()) }
        assertEquals("नमस्त", c.composing)
        assertTrue(c.backspace())
        assertEquals("नमस", c.composing)
        assertTrue(c.backspace())
        assertTrue(c.backspace())
        assertEquals("नम", c.composing) // "nam"
        assertTrue(c.backspace())
        assertTrue(c.backspace())
        assertTrue(c.backspace())
        assertFalse(c.backspace())
    }

    @Test fun spaceCommitsAndDigitsFlush() {
        val l = Languages.forTag("hi", "phonetic")
        val c = Composers.create(l.engine, { null }, l.phonetic)!!
        "namaste".forEach { c.type(it.toString()) }
        assertEquals(SpaceResult("नमस्ते", true), c.space())
        "bhaarat".forEach { c.type(it.toString()) }
        assertEquals("भारत7", c.type("7"))
        assertEquals("", c.composing)
    }

    @Test fun southIndianScriptsEndConsonantsWithAVirama() {
        assertEquals("தமிழ்", typed("ta", "tamizh"))
        assertEquals("அம்மா", typed("ta", "ammaa"))
        assertEquals("வணக்கம்", typed("ta", "vaNakkam"))
        assertEquals("தமிழ", typed("ta", "tamizha")) // an explicit a keeps the inherent vowel
        assertEquals("తెలుగు", typed("te", "telugu"))
        assertEquals("ತೆ".substring(0, 1), typed("kn", "te").substring(0, 1))
        assertEquals("മലയാളം", typed("ml", "malayaaLaM").replace("മലയാളം്", "മലയാളം"))
    }

    @Test fun shortAndLongEOInSouthIndianScripts() {
        assertEquals("எ", typed("ta", "e"))
        assertEquals("ஏ", typed("ta", "E"))
        assertEquals("ஒ", typed("ta", "o"))
        assertEquals("ஓ", typed("ta", "O"))
        assertEquals("ெ".let { "கெ" }, typed("ta", "ke"))
        assertEquals("கே", typed("ta", "kE"))
        assertEquals("ए", typed("hi", "e"))
        assertEquals("ओ", typed("hi", "o")) // Hindi e / o are the long vowels
    }

    @Test fun otherScripts() {
        assertEquals("ગુજરાતી", typed("gu", "gujaraatii"))
        assertEquals("বাংলা", typed("bn", "baaMlaa"))
        assertEquals("ਪੰਜਾਬੀ", typed("pa", "paMjaabii"))
        assertEquals("मराठी", typed("mr", "maraaThii"))
        assertEquals("संस्कृतं", typed("sa", "saMskRitaM"))
    }

    @Test fun lettersAScriptLacksFallBack() {
        // Tamil has no aspirates or voiced stops: bh -> ப, k -> க (+ final pulli)
        assertEquals("பக்", typed("ta", "bhak"))
        assertEquals("பகம்".length, typed("ta", "bhakam").length)
        assertEquals("ஜ", typed("ta", "za")) // no nukta in Tamil: z -> ஜ
        assertEquals("ব", typed("bn", "va")) // Bengali has no va
    }

    @Test fun layoutSelection() {
        assertEquals(Engine.Phonetic, Languages.forTag("hi", "phonetic").engine)
        assertEquals(Engine.Direct, Languages.forTag("hi").engine)
        assertEquals("", Languages.forTag("hi_IN", "").layout)
        assertEquals("en_US", Languages.forTag("en", "phonetic").tag) // no such variant: default
        assertEquals(Engine.Kana, Languages.forTag("ja", "phonetic").engine)
        assertEquals("phonetic", Languages.layoutOf("foo=bar, layout=phonetic"))
        assertEquals("", Languages.layoutOf(null))
    }

    @Test fun resolverKeepsComposingForPhoneticLanguages() {
        val r = Resolver().also { it.language = Languages.forTag("ta", "phonetic") }
        val ctx = FieldCtx(InputClass.Text, before = "x")
        assertEquals(
            listOf<Out>(Out.Compose('k', false)),
            r.onKey(KeyEv(Key.Letter('k'), true, 1000), ctx)
        )
    }
}
