package org.sableos.start.model

/**
 * Hold-a-letter app shortcuts on the home screen: holding an assigned letter opens its app, a short press still
 * starts type-to-search (the same key handling as Phone's speed dial, see [SpeedDialKeys]). One letter per app and
 * one app per letter, so assigning moves it. Apps are kept by their launcher component, as the text written by
 * `ComponentName.flattenToString()`. Pure policy, unit tested in apps/r8/android/sablestart-presentation-check/src/test.
 */
class LetterShortcuts private constructor(
    private val byLetter: Map<Char, String>,
) {
    val letters: Set<Char> get() = byLetter.keys

    fun componentFor(letter: Char): String? = byLetter[letter.lowercaseChar()]

    fun letterOf(component: String): Char? = byLetter.entries.firstOrNull { it.value == component }?.key

    /** [letter] (a to z, any case) opens [component]; whatever either held before is released. */
    fun assign(
        letter: Char,
        component: String,
    ): LetterShortcuts {
        val key = letter.lowercaseChar()
        if (key !in 'a'..'z' || !valid(component)) return this
        return LetterShortcuts(byLetter.filterValues { it != component }.filterKeys { it != key } + (key to component))
    }

    fun remove(component: String): LetterShortcuts = LetterShortcuts(byLetter.filterValues { it != component })

    fun encode(): String = byLetter.entries.sortedBy { it.key }.joinToString(SEPARATOR) { "${it.key}=${it.value}" }

    companion object {
        val EMPTY = LetterShortcuts(emptyMap())

        private const val SEPARATOR = ";"
        private val COMPONENT = Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")

        private fun valid(component: String) = COMPONENT.matches(component)

        /** Reads what [encode] wrote; a damaged entry is dropped, never thrown. */
        fun decode(text: String?): LetterShortcuts {
            var result = EMPTY
            text.orEmpty().split(SEPARATOR).forEach { part ->
                val letter = part.substringBefore('=').singleOrNull()
                val component = part.substringAfter('=', "")
                if (letter != null) result = result.assign(letter, component)
            }
            return result
        }
    }
}
