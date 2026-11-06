package org.sableos.assistant.core

import java.net.URI

/**
 * The address of a server on the owner's network, as people type it: "192.168.1.20",
 * "192.168.1.20:11434", "http://pc.local:11434/" or ".../v1". Plain http is only for addresses
 * that cannot be on the internet (private ranges, .local, localhost, a Tailscale address);
 * anything else must be https.
 */
object ServerAddress {
    const val OLLAMA_PORT = 11434

    /** "scheme://host[:port][/path]" without a trailing slash or /v1, or null when it is not an address. */
    fun normalize(
        input: String,
        defaultPort: Int? = null,
    ): String? {
        val text = input.trim().trimEnd('/')
        val usable = text.isNotEmpty() && text.none(Char::isWhitespace)
        val uri = if (usable) runCatching { URI(if ("://" in text) text else "http://$text") }.getOrNull() else null
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        if (uri == null || scheme !in WEB_SCHEMES || host.isNullOrEmpty()) return null
        val port = if (uri.port > 0) uri.port else defaultPort
        val path =
            uri.rawPath
                .orEmpty()
                .trimEnd('/')
                .removeSuffix("/v1")
        return buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(path)
        }
    }

    /** Whether Cosgrove may talk to [normalized]: https anywhere, plain http only on the owner's network. */
    fun allowed(normalized: String): Boolean {
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        return uri.scheme == "https" || (uri.scheme == "http" && isLocalHost(uri.host.orEmpty()))
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        return when {
            h == "localhost" || LOCAL_SUFFIXES.any(h::endsWith) -> true
            ':' in h -> h == "::1" || IPV6_LOCAL_PREFIXES.any(h::startsWith)
            else -> isPrivateIpv4(h)
        }
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.').map { it.toIntOrNull() ?: -1 }
        val valid = parts.size == IPV4_PARTS && parts.all { it in 0..IPV4_PART_MAX }
        return valid && PRIVATE_IPV4.any { (first, second) -> parts[0] == first && parts[1] in second }
    }

    private val WEB_SCHEMES = setOf("http", "https")

    /** Names only a home network or a Tailscale tailnet resolves. */
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".ts.net")

    /** IPv6 loopback is matched whole; these are unique-local and link-local. */
    private val IPV6_LOCAL_PREFIXES = listOf("fd", "fc", "fe80")

    private const val IPV4_PARTS = 4
    private const val IPV4_PART_MAX = 255

    /**
     * Private IPv4 ranges by first and second number: 10/8, 127/8 (loopback), 192.168/16,
     * 172.16/12, 169.254/16 (link-local) and 100.64/10 (carrier-grade NAT, used by Tailscale).
     */
    @Suppress("MagicNumber") // The ranges are the definition.
    private val PRIVATE_IPV4: List<Pair<Int, IntRange>> =
        listOf(
            10 to 0..255,
            127 to 0..255,
            192 to 168..168,
            172 to 16..31,
            169 to 254..254,
            100 to 64..127,
        )
}
