package org.sableos.titan2.theme

import java.util.Locale

/**
 * Translatable text for a platform app. [english] holds every user-visible line by key, in Android format syntax
 * (`%1$s`), and is what pure tests read. On a device [lookup] is bound to the app's string resources (see
 * [bindTo]), so a `values-<lang>/strings.xml` translation is used when the phone's language has one and English
 * otherwise. `values/strings.xml` repeats [english]; each app's tests keep the two equal.
 *
 * One copy for every platform app: each module adds `../sable-theme/src/main/java` as a source dir.
 */
class SableTexts(val english: Map<String, String>) {
    @Volatile
    var lookup: (String) -> String? = { null }

    /** The line [key] in the current language, with [args] filled in. An unknown key is a programming error. */
    operator fun invoke(key: String, vararg args: Any?): String {
        val template = lookup(key) ?: english.getValue(key)
        return if (args.isEmpty()) template else String.format(Locale.getDefault(), template, *args)
    }
}
