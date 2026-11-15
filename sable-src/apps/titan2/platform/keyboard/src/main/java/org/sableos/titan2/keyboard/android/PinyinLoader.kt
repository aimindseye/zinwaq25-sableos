package org.sableos.titan2.keyboard.android

import android.content.Context
import org.sableos.titan2.keyboard.core.PinyinDict

/** Loads the Pinyin dictionary asset once, off the main thread, and calls [onLoaded] when it is ready. */
class PinyinLoader(private val context: Context, private val onLoaded: () -> Unit) {
    @Volatile var dict: PinyinDict? = null
        private set
    private var loading = false

    fun loadOnce() {
        if (dict != null || loading) return
        loading = true
        Thread {
            try {
                dict = context.assets.open("pinyin_zh.tsv").bufferedReader(Charsets.UTF_8).use {
                    PinyinDict.parse(it.lineSequence())
                }
                onLoaded()
            } catch (_: Exception) {
                loading = false
            }
        }.start()
    }
}
