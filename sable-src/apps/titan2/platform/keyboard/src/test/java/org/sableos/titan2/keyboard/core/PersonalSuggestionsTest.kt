package org.sableos.titan2.keyboard.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalSuggestionsTest {
    private val words = WordList.parse(
        sequenceOf(
            "the\t200", "they\t150", "there\t140", "hello\t100", "world\t90", "receive\t120",
            "separate\t110", "definitely\t100", "tree\t60", "trek\t50", "treat\t55"
        )
    )

    // ---- learning ------------------------------------------------------------------------------

    @Test fun finishedWordNeedsExactlyOneSeparator() {
        assertEquals(FinishedWord("sable", null), WordLearner.finished("sable", "sable "))
        assertEquals(
            FinishedWord("sable", "hello"),
            WordLearner.finished("hello sable", "hello sable ")
        )
        assertEquals(FinishedWord("sable", "hi"), WordLearner.finished("hi sable", "hi sable."))
        assertNull(WordLearner.finished("sable", "sable  "))
        assertNull(WordLearner.finished("sabl", "sable"))
        assertNull(WordLearner.finished("sable", "sable and"))
        assertNull(WordLearner.finished("a", "a "))
        assertNull(WordLearner.finished("abc123", "abc123 "))
    }

    @Test fun anUnknownWordIsLearnedAfterTwoSightings() {
        val user = UserWords()
        user.learnWord("Sable")
        assertFalse(user.isKnown("sable"))
        user.learnWord("sable")
        assertTrue(user.isKnown("SABLE"))
        assertEquals(listOf("sable"), user.complete("sab", 3))
    }

    @Test fun learnedWordsAreCompletedAndNotCorrected() {
        val user = UserWords().apply {
            learnWord("zinwa")
            learnWord("zinwa")
        }
        val s = WordSuggester(words, null, user)
        assertEquals("zinwa", s.suggest("zin").first().text)
        assertTrue(s.suggest("zinwa").none { it.kind == SuggestionKind.Correction })
        assertTrue(WordSuggester(words, null, UserWords()).suggest("zinwa").isEmpty())
    }

    @Test fun nextWordComesFromTheUsersOwnPhrases() {
        val user = UserWords()
        repeat(3) { user.learnPair("good", "morning") }
        repeat(2) { user.learnPair("good", "night") }
        user.learnPair("good", "luck")
        val s = WordSuggester(words, null, user)
        assertEquals(listOf("morning", "night"), s.suggest("say good ").map { it.text })
        assertEquals(0, s.suggest("say good ").first().replaces)
        assertEquals(SuggestionKind.NextWord, s.suggest("good ").first().kind)
        assertTrue(s.suggest("good").none { it.kind == SuggestionKind.NextWord })
        assertTrue(s.suggest("good. ").isEmpty())
        assertTrue(s.suggest("good  ").isEmpty())
        assertEquals("morning ", WordSuggester.accept(s.suggest("good ").first()))
    }

    @Test fun learningRoundTripsThroughText() {
        val user = UserWords()
        repeat(2) {
            user.learnWord("zinwa")
            user.learnPair("my", "zinwa")
        }
        val again = UserWords().apply { load(user.serialize()) }
        assertTrue(again.isKnown("zinwa"))
        assertEquals(listOf("zinwa"), again.next("my", 2))
        assertFalse(again.dirty)
        again.clear()
        assertEquals(0, again.size)
    }

    @Test fun tablesAreCappedByDroppingTheLeastUsed() {
        val user = UserWords(maxWords = 20, maxPairs = 20)
        repeat(3) { user.learnWord("keeper") }
        for (i in 0 until 60) user.learnWord("w" + "abcdefghij"[i % 10] + "klmnopqrst"[i / 10])
        assertTrue(user.size <= 22)
        assertTrue(user.isKnown("keeper"))
    }

    @Test fun badLinesInTheStoreAreSkipped() {
        val user = UserWords().apply {
            load("W\tgood\t3\nnonsense\nW\tonly\nP\ta\tb\tx\nP\tx\ty\t4\n")
        }
        assertTrue(user.isKnown("good"))
        assertEquals(listOf("y"), user.next("x", 2))
        assertEquals(2, user.size)
    }

    // ---- auto-correct --------------------------------------------------------------------------

    @Test fun autoCorrectFixesAnObviousTypo() {
        assertEquals(Correction("teh", "the"), AutoCorrect.fix("say teh", words, null))
        assertEquals(Correction("recieve", "receive"), AutoCorrect.fix("recieve", words, null))
        assertEquals(Correction("seperate", "separate"), AutoCorrect.fix("a seperate", words, null))
    }

    @Test fun autoCorrectLeavesAlone() {
        assertNull(AutoCorrect.fix("hello", words, null))
        assertNull(AutoCorrect.fix("ab", words, null))
        assertNull(AutoCorrect.fix("say ", words, null))
        assertNull(AutoCorrect.fix("abc123", words, null))
        assertNull(AutoCorrect.fix("zzzzzz", words, null))
        // a name mid-sentence and an acronym keep their capitals
        assertNull(AutoCorrect.fix("met Teh", words, null))
        assertNull(AutoCorrect.fix("THE", words, null))
        assertNull(AutoCorrect.fix("TEH", words, null))
        // two fixes of similar frequency are not a clear call
        assertNull(AutoCorrect.fix("trea", words, null))
    }

    @Test fun autoCorrectKeepsSentenceCase() {
        assertEquals(Correction("Teh", "The"), AutoCorrect.fix("Teh", words, null))
        assertEquals(Correction("Teh", "The"), AutoCorrect.fix("Done. Teh", words, null))
    }

    @Test fun autoCorrectSkipsTheUsersOwnWords() {
        val user = UserWords().apply {
            learnWord("teh")
            learnWord("teh")
        }
        assertNull(AutoCorrect.fix("teh", words, user))
    }

    // ---- Indic text ------------------------------------------------------------------------------

    @Test fun marksStayInsideAWord() {
        // Devanagari vowel signs and the virama are marks, not letters
        assertEquals("नमस्ते", Tokens.currentWord("कहो नमस्ते"))
        assertEquals("তুমি", Tokens.currentWord("আমি তুমি"))
        assertNull(Tokens.currentWord("नमस्ते "))
        assertNull(Tokens.currentWord("नमस्ते।"))
        assertTrue(Tokens.isPlainWord("नमस्ते"))
        assertFalse(Tokens.isPlainWord("नम1"))
    }

    @Test fun shippedIndicListsCompleteAndSpotTypos() {
        val assets = File("src/main/assets")
        mapOf(
            "hi" to "है",
            "bn" to "আমি",
            "ta" to "நான்",
            "te" to "మీరు",
            "ml" to "ഒരു",
            "ur" to "ہے"
        ).forEach { (lang, w) ->
            val list = WordList.parse(
                File(assets, "words_$lang.tsv").useLines {
                    it.toList().asSequence()
                }
            )
            assertTrue("$lang has few words", list.size > 3_000)
            assertTrue("$lang lacks $w", list.contains(w))
        }
        val hi = WordList.parse(File(assets, "words_hi.tsv").useLines { it.toList().asSequence() })
        assertTrue(WordSuggester(hi, null).suggest("न").isNotEmpty())
    }
}
