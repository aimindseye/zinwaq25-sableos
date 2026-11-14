package org.sableos.titan2.keyboard.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSuggesterTest {
    private val assets = File("src/main/assets")
    private val words = WordList.parse(
        sequenceOf(
            "the\t200", "they\t150", "there\t140", "then\t130", "thin\t60", "think\t120",
            "thing\t110", "hello\t100", "world\t90", "Ramirez\t80", "don't\t95", "love\t70",
            "receive\t65", "separate\t64"
        )
    )
    private val emoji = EmojiIndex.parse(
        sequenceOf(
            "😄\tsmile happy joy",
            "❤\theart love red",
            "👍\t+1 thumbsup approve",
            "😀\tgrinning smile"
        )
    )
    private val s = WordSuggester(words, emoji)

    private fun texts(before: String) = s.suggest(before).map { it.text }

    @Test fun completesByFrequency() {
        assertEquals(listOf("the", "they", "there", "then"), texts("th").take(4))
    }

    @Test fun completionKeepsTypedCase() {
        assertEquals("The", texts("Th").first())
        assertEquals("THEY", texts("THE").first())
        assertEquals("Ramirez", texts("ram").first())
    }

    @Test fun suggestsOnlyAtTheEndOfAWord() {
        assertTrue(texts("hello ").isEmpty())
        assertTrue(texts("").isEmpty())
        assertTrue(texts("abc123").isEmpty())
    }

    @Test fun fixesATypoByFrequency() {
        val r = s.suggest("teh")
        assertEquals("the", r.first().text)
        assertEquals(SuggestionKind.Correction, r.first().kind)
        assertEquals(3, r.first().replaces)
        assertEquals("receive", texts("recieve").first())
        assertEquals("separate", texts("seperate").first())
    }

    @Test fun aKnownWordIsNotCorrected() {
        assertTrue(s.suggest("world").none { it.kind == SuggestionKind.Correction })
        assertTrue(words.contains("WORLD"))
    }

    @Test fun emojiForAKeyword() {
        val r = s.suggest("smile")
        assertEquals(
            listOf("😄", "😀"),
            r.filter {
                it.kind == SuggestionKind.Emoji
            }.map { it.text }
        )
        assertEquals(5, r.first().replaces)
    }

    @Test fun shortcodeSearchTakesTheColon() {
        val r = s.suggest("hi :smi")
        assertEquals("😄", r.first().text)
        assertEquals(4, r.first().replaces)
        assertTrue(s.suggest(":").isEmpty())
    }

    @Test fun emojiSitsAfterTheFirstWords() {
        val r = s.suggest("lov")
        assertTrue(r.none { it.kind == SuggestionKind.Emoji })
        val love = s.suggest("love")
        assertEquals("❤", love.first { it.kind == SuggestionKind.Emoji }.text)
    }

    @Test fun acceptingAWordAddsASpaceAndEmojiDoNot() {
        assertEquals(
            "hello ",
            WordSuggester.accept(Suggestion("hello", 3, SuggestionKind.Completion))
        )
        assertEquals("😄", WordSuggester.accept(Suggestion("😄", 5, SuggestionKind.Emoji)))
    }

    @Test fun apostropheIsPartOfTheWord() {
        assertEquals("don't", texts("don'").first())
        assertEquals("don't", WordSuggester.currentWord("say don't"))
        assertNull(WordSuggester.currentWord("say "))
    }

    @Test fun noDictionaryMeansNoWordSuggestions() {
        assertTrue(WordSuggester(null, null).suggest("th").isEmpty())
        assertEquals(
            listOf("😄", "😀"),
            WordSuggester(null, emoji).suggest("smile").map {
                it.text
            }
        )
    }

    @Test fun editDistanceIsBoundedAndCountsSwaps() {
        assertEquals(1, WordList.editDistance("teh", "the", 2))
        assertEquals(3, WordList.editDistance("abcdef", "uvwxyz", 2))
    }

    @Test fun twoEditCorrectionsForLongerWords() {
        assertEquals("receive", words.corrections("recieeve", 1).first())
        assertTrue(words.corrections("zzzzzz", 3).isEmpty())
    }

    @Test fun spellVerdict() {
        assertEquals(SpellVerdict.IN_DICTIONARY, SpellVerdict.of(words, "hello", 3).attributes)
        assertEquals(SpellVerdict.IN_DICTIONARY, SpellVerdict.of(words, "NASA", 3).attributes)
        assertEquals(SpellVerdict.IN_DICTIONARY, SpellVerdict.of(words, "abc123", 3).attributes)
        assertEquals(SpellVerdict.IN_DICTIONARY, SpellVerdict.of(null, "zzzz", 3).attributes)
        val bad = SpellVerdict.of(words, "helo", 3)
        assertEquals(SpellVerdict.LOOKS_LIKE_TYPO, bad.attributes)
        assertEquals("hello", bad.suggestions.first())
    }

    // ---- the shipped data -----------------------------------------------------------------------

    @Test fun shippedListsLoadAndKnowCommonWords() {
        val common = mapOf(
            "en" to "the", "es" to "de", "fr" to "le", "de" to "der", "pt" to "de",
            "it" to "di", "nl" to "de", "sv" to "och", "pl" to "nie", "tr" to "bir"
        )
        common.forEach { (lang, w) ->
            val file = File(assets, "words_$lang.tsv")
            assertTrue("$lang list missing", file.isFile)
            val list = WordList.parse(file.useLines { it.toList().asSequence() })
            assertTrue("$lang has few words", list.size > 30_000)
            assertTrue("$lang lacks $w", list.contains(w))
        }
    }

    @Test fun shippedEnglishFixesCommonTypos() {
        val list = WordList.parse(
            File(assets, "words_en.tsv").useLines {
                it.toList().asSequence()
            }
        )
        assertEquals("the", list.corrections("teh", 1).first())
        assertEquals("definitely", list.corrections("definately", 1).first())
        assertEquals("because", list.corrections("becuase", 1).first())
        assertFalse(list.contains("asdfgh"))
    }

    @Test fun shippedEmojiFindsTheObviousOnes() {
        val index = EmojiIndex.parse(
            File(assets, "emoji_en.tsv").useLines {
                it.toList().asSequence()
            }
        )
        assertTrue(index.size > 1500)
        assertTrue("😄" in index.search("smile", 5))
        assertTrue("❤️" in index.forWord("heart", 10) || "❤" in index.forWord("heart", 10))
        assertTrue("🎉" in index.search("tada", 3))
    }
}
