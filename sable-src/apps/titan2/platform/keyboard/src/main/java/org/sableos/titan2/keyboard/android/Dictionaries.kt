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
    val LANGUAGES = setOf(
        "en", "es", "fr", "de", "pt", "it", "nl", "sv", "pl", "tr",
        "hi", "bn", "ta", "te", "ml", "ur"
    )

    /**
     * The full dictionaries (40 000 words): enough to tell a typo from a word, so the only ones that
     * auto-correct and the system spell checker use.
     */
    val FULL_LANGUAGES = setOf("en", "es", "fr", "de", "pt", "it", "nl", "sv", "pl", "tr")

    /** Two-letter language of a locale tag such as en_US or pt-BR, or null when none ships. */
    fun languageOf(tag: String?): String? =
        tag?.substringBefore('_')?.substringBefore('-')?.lowercase(Locale.ROOT)
            ?.takeIf { it in LANGUAGES }

    /** The list for [lang], or null while it is still loading (loading starts here) or when none ships. */
    fun words(context: Context, lang: String, onReady: (() -> Unit)? = null): WordList? {
        val ready = lists[lang]
        if (ready == null && lang in LANGUAGES && loading.add(lang)) {
            val app = context.applicationContext
            Thread {
                val list = readList(app, lang)
                if (list != null) lists[lang] = list
                loading.remove(lang)
                if (list != null) onReady?.invoke()
            }.start()
        }
        return ready
    }

    private fun readList(app: Context, lang: String): WordList? = try {
        app.assets.open("words_$lang.tsv").bufferedReader(Charsets.UTF_8).use {
            WordList.parse(it.lineSequence(), Locale.forLanguageTag(lang))
        }
    } catch (_: java.io.IOException) {
        null
    }

    /** Blocking variant for the spell checker, which already runs on its own thread. */
    fun wordsBlocking(context: Context, lang: String): WordList? =
        lists[lang] ?: if (lang in LANGUAGES) {
            synchronized(this) {
                lists[lang] ?: readList(context.applicationContext, lang)?.also { lists[lang] = it }
            }
        } else {
            null
        }

    fun emoji(context: Context, onReady: (() -> Unit)? = null): EmojiIndex? {
        val ready = emojiIndex
        if (ready == null && !emojiLoading) {
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
        }
        return ready
    }
}
