package org.sableos.tools.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Where and under what name a saved report goes: Download/SableTools/sable-tools-<title>-<local time>.txt. */
object ReportFile {
    const val FOLDER = "Download/SableTools"
    const val MIME = "text/plain"
    private const val MAX_SLUG = 40
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun name(title: String, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val slug = title.lowercase().replace(
            Regex("[^a-z0-9]+"),
            "-"
        ).trim('-').take(MAX_SLUG).trim('-')
        val stamp = STAMP.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        return "sable-tools-" + slug.ifEmpty { "report" } + "-" + stamp + ".txt"
    }
}
