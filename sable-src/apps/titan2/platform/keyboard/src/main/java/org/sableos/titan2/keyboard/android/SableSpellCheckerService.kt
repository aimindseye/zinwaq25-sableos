package org.sableos.titan2.keyboard.android

import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import org.sableos.titan2.keyboard.core.SpellVerdict

/**
 * System spell checker: apps that use Android's spell check (messages, notes, mail, browsers' text areas) underline
 * unknown words and offer corrections from the same word lists as the keyboard's suggestion strip. It works
 * offline, keeps no history and sends nothing anywhere. Pick it under Settings > System > Languages & input >
 * Spell checker; Sable Keyboard never switches it for the user.
 */
class SableSpellCheckerService : SpellCheckerService() {
    override fun createSession(): Session = object : Session() {
        private var lang: String? = null

        override fun onCreate() {
            lang = Dictionaries.languageOf(locale)?.takeIf { it in Dictionaries.FULL_LANGUAGES }
        }

        override fun onGetSuggestions(textInfo: TextInfo, suggestionsLimit: Int): SuggestionsInfo {
            val list = lang?.let { Dictionaries.wordsBlocking(this@SableSpellCheckerService, it) }
            val word = textInfo.text.orEmpty()
            return SpellVerdict.of(list, word, suggestionsLimit).let {
                SuggestionsInfo(
                    it.attributes,
                    it.suggestions.toTypedArray(),
                    textInfo.cookie,
                    textInfo.sequence
                )
            }
        }
    }
}
