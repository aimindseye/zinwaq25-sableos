package org.sableos.browser.core

import java.net.URLEncoder

/**
 * What the address bar does with typed text: a web address opens (https unless it is a bare local
 * address, which routers often serve over http), anything else searches. Pure.
 */
object UrlInput {
    const val SEARCH_PREFIX = "https://duckduckgo.com/?q="

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val APP_SCHEME = Regex("^(mailto|tel|sms|geo|market):", RegexOption.IGNORE_CASE)
    private val HOST = Regex("^[^\\s/]+\\.[a-zA-Z]{2,}(:\\d+)?([/?#].*)?$")
    private val LOCAL = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?([/?#].*)?$")

    /** The address to load for [text], or null for blank text. */
    fun resolve(text: String): String? {
        val typed = text.trim()
        return when {
            typed.isEmpty() -> null
            typed.contains(' ') -> search(typed)
            SCHEME.containsMatchIn(typed) && typed.contains("//") -> typed
            APP_SCHEME.containsMatchIn(typed) -> typed
            LOCAL.matches(typed) -> "http://$typed"
            HOST.matches(typed) -> "https://$typed"
            else -> search(typed)
        }
    }

    fun search(query: String): String = SEARCH_PREFIX + URLEncoder.encode(query.trim(), "UTF-8")

    /** The address as the bar shows it while you are not typing: no scheme, no "www.", no last "/". */
    fun display(url: String?): String =
        url
            .orEmpty()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removeSuffix("/")

    /** Whether the page itself loads [url]; any other scheme (tel:, mailto:, intent:) goes to an app. */
    fun isWeb(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("https://") || lower.startsWith("http://") || lower == "about:blank"
    }
}
