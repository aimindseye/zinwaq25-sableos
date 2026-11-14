package org.sableos.titan2.keyboard.android

import android.content.Context
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.sableos.titan2.keyboard.core.EmojiIndex
import org.sableos.titan2.keyboard.core.WordList

/**
 * Word lists and the emoji index, read once from assets and shared by the keyboard and the spell checker service
 * (they run in one process). Loading is lazy and off the main thread; until it finishes a lookup returns null and
 * the caller simply shows no suggestions.
 */
object Dictionaries {
    private val lists = ConcurrentHashMap<String, WordList>()
    private val loading = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var emojiIndex: EmojiIndex? = null
    @Volatile private var emojiLoading = false

    /** Languages that ship a word list (assets/words_<language>.tsv). */
    val LANGUAGES = setOf("en", "es", "fr", "de", "pt", "it", "nl", "sv", "pl", "tr")

    /** Two-letter language of a locale tag such as en_US or pt-BR, or null when none ships. */
    fun languageOf(tag: String?): String? = tag?.substringBefore('_')?.substringBefore('-')?.lowercase(Locale.ROOT)
        ?.takeIf { it in LANGUAGES }

    /** The list for [lang], or null while it is still loading (loading starts here) or when none ships. */
    fun words(context: Context, lang: String, onReady: (() -> Unit)? = null): WordList? {
        lists[lang]?.let { return it }
        if (lang !in LANGUAGES || !loading.add(lang)) return null
        val app = context.applicationContext
        Thread {
            try {
                app.assets.open("words_$lang.tsv").bufferedReader(Charsets.UTF_8).use {
                    lists[lang] = WordList.parse(it.lineSequence(), Locale.forLanguageTag(lang))
                }
                onReady?.invoke()
            } catch (_: java.io.IOException) {
                // leave it unloaded; suggestions stay off for this language
            } finally {
                loading.remove(lang)
            }
        }.start()
        return null
    }

    /** Blocking variant for the spell checker, which already runs on its own thread. */
    fun wordsBlocking(context: Context, lang: String): WordList? {
        lists[lang]?.let { return it }
        if (lang !in LANGUAGES) return null
        return synchronized(this) {
            lists[lang] ?: runCatching {
                context.applicationContext.assets.open("words_$lang.tsv").bufferedReader(Charsets.UTF_8).use {
                    WordList.parse(it.lineSequence(), Locale.forLanguageTag(lang))
                }
            }.getOrNull()?.also { lists[lang] = it }
        }
    }

    fun emoji(context: Context, onReady: (() -> Unit)? = null): EmojiIndex? {
        emojiIndex?.let { return it }
        if (emojiLoading) return null
        emojiLoading = true
        val app = context.applicationContext
        Thread {
            try {
                app.assets.open("emoji_en.tsv").bufferedReader(Charsets.UTF_8).use {
                    emojiIndex = EmojiIndex.parse(it.lineSequence())
                }
                onReady?.invoke()
            } catch (_: java.io.IOException) {
                emojiLoading = false
            }
        }.start()
        return null
    }
}
