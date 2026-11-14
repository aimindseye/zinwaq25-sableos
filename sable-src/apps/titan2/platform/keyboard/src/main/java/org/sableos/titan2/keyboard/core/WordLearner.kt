package org.sableos.titan2.keyboard.core

/** A word the user has just finished, and the word before it when they were typed as one phrase. */
data class FinishedWord(val word: String, val previous: String?)

/**
 * Spots a finished word from two readings of the text before the cursor, so the physical keys and the on-screen
 * keyboard are learned from alike. A word counts as finished only when exactly one separator was typed after it;
 * pastes, expansions and accepted suggestions grow the text by more and are not learned from.
 */
object WordLearner {
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 24

    fun finished(old: String, new: String): FinishedWord? = if (oneSeparatorAdded(old, new)) {
        Tokens.currentWord(old)?.takeIf(::learnable)?.let {
            FinishedWord(it, previousOf(old, it))
        }
    } else {
        null
    }

    private fun oneSeparatorAdded(old: String, new: String): Boolean =
        new.length == old.length + 1 && new.startsWith(old) && !Tokens.isTokenChar(new.last())

    private fun previousOf(old: String, word: String): String? =
        wordBefore(old.dropLast(word.length))

    /** The word typed straight before [head]'s end plus one space, when there is one (the other half of a phrase). */
    fun wordBefore(head: String): String? =
        if (head.endsWith(" ")) Tokens.currentWord(head.dropLast(1))?.takeIf(::learnable) else null

    private fun learnable(word: String): Boolean =
        word.length in MIN_LENGTH..MAX_LENGTH && Tokens.isPlainWord(word)
}
