package org.sableos.phone.core

/** What a contact's round avatar shows when the contact has no photo (from TenSeven's Phone, 2026-11-12). */
object Avatar {
    private const val MAX_LETTERS = 2

    /** Up to two initials from the name's first words; "#" when the name has no letter (a bare number). */
    fun initials(name: String): String = name
        .trim()
        .split(Regex("\\s+"))
        .mapNotNull { word -> word.firstOrNull { it.isLetter() }?.uppercaseChar() }
        .take(MAX_LETTERS)
        .joinToString("")
        .ifEmpty { "#" }
}
