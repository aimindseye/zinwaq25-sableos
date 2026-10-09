package org.vaachak.reader.core.utils

expect object SecurityUtils {
    /**
     * Converts a plain-text PIN (like "1234") into an irreversible SHA-256 hash.
     */
    fun hashPin(pin: String): String
}

