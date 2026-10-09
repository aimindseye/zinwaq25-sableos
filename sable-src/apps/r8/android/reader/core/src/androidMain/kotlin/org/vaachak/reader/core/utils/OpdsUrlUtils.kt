package org.vaachak.reader.core.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object OpdsUrlUtils {

    fun cleanCatalogUrl(url: String): String {
        var clean = url.trim()
        val lower = clean.lowercase()

        val isSpecific = lower.endsWith(".xml") || lower.endsWith(".opds") || lower.contains("/opds")
        val isGutendex = lower.contains("/books")

        if (!isSpecific && !isGutendex) {
            clean = if (clean.endsWith("/")) "${clean}opds" else "${clean}/opds"
        }

        clean = when {
            clean.startsWith("https://", ignoreCase = true) -> clean
            clean.startsWith("http://", ignoreCase = true) -> {
                "https://${clean.substring("http://".length)}"
            }
            else -> "https://$clean"
        }

        return clean
    }

    fun normalizeHttpsUrl(urlStr: String): String {
        val parsed = urlStr.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Invalid URL: $urlStr")

        if (parsed.scheme != "https") {
            throw IllegalArgumentException(
                "Only HTTPS OPDS endpoints are supported. " +
                        "Configure Calibre/Audiobookshelf behind HTTPS and use the HTTPS hostname."
            )
        }

        return parsed.toString()
    }
}

