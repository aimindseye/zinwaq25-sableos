package org.sableos.titan2.keyboard.core

/** A word replaced on Space: what was typed and what it became. */
data class Correction(val typed: String, val replacement: String)

/**
 * Auto-correct on Space, for the word that ends the text before the cursor. Deliberately cautious: a wrong silent
 * change costs more than a missed one. It acts only on a plain word of three letters or more that neither the word
 * list nor the user's own words know, when the best fix is a single edit away and clearly more common than the next
 * one. A capital letter inside or at the start of a word that is not at the start of a sentence (a name) is left
 * alone, and so is an all-capitals word. Pure, so it is unit-tested on a JVM.
 */
object AutoCorrect {
    private const val MIN_LENGTH = 3

    /** How much more frequent (word list scale 0 to 255) the best fix must be than the runner-up. */
    private const val DOMINANCE = 10

    fun fix(before: String, words: WordList, user: UserWords?): Correction? =
        Tokens.currentWord(before)?.let { typed ->
            fixFor(before, typed, words, user)?.let { Correction(typed, it) }
        }

    private fun fixFor(before: String, typed: String, words: WordList, user: UserWords?): String? {
        if (!eligible(before, typed, words, user)) return null
        val fixes = words.corrections(typed, 2)
        val best = fixes.firstOrNull()
        return best?.takeIf { oneEdit(typed, it) && dominates(words, it, fixes.getOrNull(1)) }
            ?.let { matchCase(typed, it) }
    }

    private fun eligible(
        before: String,
        typed: String,
        words: WordList,
        user: UserWords?
    ): Boolean = checkable(typed) && !words.contains(typed) && user?.isKnown(typed) != true &&
        casingOk(before, typed)

    private fun checkable(typed: String): Boolean =
        typed.length >= MIN_LENGTH && Tokens.isPlainWord(typed)

    // a name or an acronym keeps its capitals; a capital at the start of a sentence is only the sentence's
    private fun casingOk(before: String, typed: String): Boolean {
        val rest = typed.drop(1)
        val capitalised = typed.first().isUpperCase() && rest == rest.lowercase()
        return typed == typed.lowercase() ||
            (capitalised && Resolver.atSentenceStart(before.dropLast(typed.length)))
    }

    private fun oneEdit(typed: String, fix: String): Boolean =
        WordList.editDistance(typed.lowercase(), fix.lowercase(), 1) == 1

    private fun dominates(words: WordList, best: String, next: String?): Boolean =
        next == null || words.frequency(best) - words.frequency(next) >= DOMINANCE

    private fun matchCase(typed: String, fix: String): String =
        if (typed.first().isUpperCase()) fix.replaceFirstChar { it.uppercase() } else fix
}
