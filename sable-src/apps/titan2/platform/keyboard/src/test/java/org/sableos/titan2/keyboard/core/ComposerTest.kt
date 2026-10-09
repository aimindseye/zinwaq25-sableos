package org.sableos.titan2.keyboard.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerTest {
    /** Types jamo/letters, returns everything committed plus what is still composing. */
    private fun run(c: Composer, s: String): String {
        val sb = StringBuilder()
        s.forEach { sb.append(c.type(it.toString())) }
        sb.append(c.flush())
        return sb.toString()
    }

    // ---- Hangul
    @Test fun hangulBasicSyllables() {
        assertEquals("한글", run(HangulComposer(), "ㅎㅏㄴㄱㅡㄹ"))
        assertEquals("안녕하세요", run(HangulComposer(), "ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ"))
        assertEquals("대한민국", run(HangulComposer(), "ㄷㅐㅎㅏㄴㅁㅣㄴㄱㅜㄱ"))
    }

    @Test fun hangulFinalMovesToNextSyllable() {
        assertEquals("하나", run(HangulComposer(), "ㅎㅏㄴㅏ"))
        // compound final ㄺ splits: ㄹ stays, ㄱ moves
        assertEquals("일거", run(HangulComposer(), "ㅇㅣㄹㄱㅓ"))
        assertEquals("읽어", run(HangulComposer(), "ㅇㅣㄹㄱㅇㅓ"))
    }

    @Test fun hangulCompoundVowelAndFinal() {
        assertEquals("과", run(HangulComposer(), "ㄱㅗㅏ"))
        assertEquals("닭", run(HangulComposer(), "ㄷㅏㄹㄱ"))
        assertEquals("값", run(HangulComposer(), "ㄱㅏㅂㅅ"))
        assertEquals("의", run(HangulComposer(), "ㅇㅡㅣ"))
    }

    @Test fun hangulCompoundFinalSplitsOnVowel() {
        val c = HangulComposer()
        val out = StringBuilder()
        "ㄷㅏㄹㄱㅏ".forEach { out.append(c.type(it.toString())) }
        assertEquals("달", out.toString())
        assertEquals("가", c.composing)
    }

    @Test fun hangulDoubleConsonantCannotBeFinalAndBackspaceUndoesJamo() {
        assertEquals("가ㅃ", run(HangulComposer(), "ㄱㅏㅃ"))
        val c = HangulComposer()
        "ㅎㅏㄴ".forEach { c.type(it.toString()) }
        assertEquals("한", c.composing)
        assertTrue(c.backspace())
        assertEquals("하", c.composing)
        assertTrue(c.backspace())
        assertEquals("ㅎ", c.composing)
        assertTrue(c.backspace())
        assertFalse(c.backspace())
        val w = HangulComposer()
        "ㄱㅗㅏ".forEach { w.type(it.toString()) }
        assertTrue(w.backspace())
        assertEquals("고", w.composing)
    }

    @Test fun dubeolsikMapsQwertyAndShift() {
        assertEquals('ㅎ', Dubeolsik.jamo('g', false))
        assertEquals('ㅃ', Dubeolsik.jamo('q', true))
        assertEquals('ㅒ', Dubeolsik.jamo('o', true))
        assertEquals('ㅏ', Dubeolsik.jamo('k', true))
        assertEquals(
            "한글",
            run(
                HangulComposer(),
                "gksrmf".map {
                    Dubeolsik.jamo(it, false)!!
                }.joinToString("")
            )
        )
    }

    @Test fun hangulNonJamoFlushes() {
        val c = HangulComposer()
        "ㅎㅏ".forEach { c.type(it.toString()) }
        assertEquals("하1", c.type("1"))
        assertEquals("", c.composing)
    }

    // ---- Kana
    @Test fun kanaBasics() {
        assertEquals("こんにちは", run(KanaComposer(), "konnnichiha"))
        assertEquals("にほんご", run(KanaComposer(), "nihongo"))
        assertEquals("きょう", run(KanaComposer(), "kyou"))
        assertEquals("しゃしん", run(KanaComposer(), "shashinn"))
    }

    @Test fun kanaSokuonAndN() {
        assertEquals("きっぷ", run(KanaComposer(), "kippu"))
        assertEquals("がっこう", run(KanaComposer(), "gakkou"))
        assertEquals("ほんや", run(KanaComposer(), "honya".replace("honya", "hon'ya")))
        assertEquals("さんか", run(KanaComposer(), "sanka"))
        assertEquals("ん", run(KanaComposer(), "n"))
    }

    @Test fun kanaPunctuationLongVowelSmallKana() {
        assertEquals("ラーメン".let { KanaComposer.toKatakana("らーめん") }, "ラーメン")
        assertEquals("らーめん", run(KanaComposer(), "ra-menn"))
        assertEquals("ありがとう。", run(KanaComposer(), "arigatou."))
        assertEquals("ぁ", run(KanaComposer(), "xa"))
    }

    @Test fun kanaBackspaceAndCandidates() {
        val c = KanaComposer()
        "kak".forEach { c.type(it.toString()) }
        assertEquals("かk", c.composing)
        assertTrue(c.backspace())
        assertEquals("か", c.composing)
        assertEquals(listOf("か", "カ"), c.candidates.map { it.text })
        assertEquals("カ", c.pick(1))
        assertEquals("", c.composing)
    }

    // ---- Pinyin
    private val dict = PinyinDict.parse(
        sequenceOf(
            "ni\t你\t234587", "ni\t呢\t28623", "ni\t泥\t4354", "nihao\t你好\t725", "nimen\t你们\t900",
            "hao\t好\t100000", "hao\t号\t5000", "ma\t吗\t3000", "ma\t妈\t2000", "xian\t先\t1000",
            "xian\t西安\t300", "xi\t西\t800", "an\t安\t700"
        ).sortedBy { it.substringBefore('\t') }
    )
    private fun py() = PinyinComposer { dict }

    @Test fun pinyinExactThenCompletionsThenPrefixes() {
        val c = py()
        "ni".forEach { c.type(it.toString()) }
        assertEquals(listOf("你", "呢", "泥", "你们", "你好"), c.candidates.map { it.text })
        "hao".forEach { c.type(it.toString()) }
        assertEquals("你好", c.candidates[0].text)
        assertEquals("你", c.candidates[1].text) // prefix match consumes only "ni"
        assertEquals(2, c.candidates[1].consumed)
    }

    @Test fun pinyinPickConsumesPrefixAndKeepsRemainder() {
        val c = py()
        "nihao".forEach { c.type(it.toString()) }
        assertEquals("你", c.pick(1))
        assertEquals("hao", c.composing)
        assertEquals("好", c.pick(0))
        assertEquals("", c.composing)
    }

    @Test fun pinyinSpaceTakesFirstCandidate() {
        val c = py()
        "nihao".forEach { c.type(it.toString()) }
        assertEquals(SpaceResult("你好", false), c.space())
        assertEquals(SpaceResult("", true), c.space())
    }

    @Test fun pinyinApostropheSeparatesSyllables() {
        val c = py()
        "xi'an".forEach { c.type(it.toString()) }
        assertEquals(
            listOf("先", "西安"),
            c.candidates.take(2).map {
                it.text
            }
        ) // both are the "xian" key, by ranking weight
        c.reset()
        "xi'an".forEach { c.type(it.toString()) }
        val xi = c.candidates.first { it.text == "西" }
        assertEquals(3, xi.consumed) // index into "xi'an" (the apostrophe is skipped)
        assertEquals("西", c.pick(c.candidates.indexOf(xi)))
        assertEquals("an", c.composing)
    }

    @Test fun pinyinBackspacePunctuationAndUnknown() {
        val c = py()
        "nix".forEach { c.type(it.toString()) }
        assertTrue(c.backspace())
        assertEquals("ni", c.composing)
        assertEquals("你，", c.type(",")) // commits best candidate then full-width comma
        assertEquals("", c.composing)
        val u = py()
        "zzz".forEach { u.type(it.toString()) }
        assertTrue(u.candidates.isEmpty())
        assertEquals(SpaceResult("zzz", false), u.space())
        assertEquals("好。", py().also { "hao".forEach { ch -> it.type(ch.toString()) } }.type("."))
    }

    @Test fun pinyinWithoutDictionaryStillComposesLetters() {
        val c = PinyinComposer { null }
        "ni".forEach { c.type(it.toString()) }
        assertEquals("ni", c.composing)
        assertTrue(c.candidates.isEmpty())
        assertEquals("ni", c.flush())
    }

    @Test fun composerFactory() {
        assertTrue(Composers.create(Engine.Direct) == null)
        assertTrue(Composers.create(Engine.Hangul) is HangulComposer)
        assertTrue(Composers.create(Engine.Kana) is KanaComposer)
        assertTrue(Composers.create(Engine.Pinyin) is PinyinComposer)
    }
}
