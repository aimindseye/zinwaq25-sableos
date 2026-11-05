package org.sableos.titan2.printtogo.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Print To Go policy, after BlackBerry 10's Print To Go: the phone shows up as a printer on the Wi-Fi only while
 * the user has it on, everything printed to it is saved as a PDF in Documents/Print To Go, and it turns itself
 * off after [IDLE_MS] with no print. Pure, unit tested.
 */
object PrintToGo {
    /** Above 1024, so no special rights; announced over DNS-SD, so clients never need to know it. */
    const val PORT = 8631
    const val PATH = "/ipp/print"
    const val SERVICE_TYPE = "_ipp._tcp"

    /** AirPrint looks for this subtype. */
    const val AIRPRINT_SUBTYPE = "_universal"
    const val FOLDER = "Documents/Print To Go"
    const val IDLE_MS = 10 * 60 * 1000L

    private const val MAX_NAME = 60
    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+")
    private val EXTENSION =
        Regex("\\.(pdf|docx?|xlsx?|pptx?|txt|html?|png|jpe?g|rtf|odt)$", RegexOption.IGNORE_CASE)
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")
    private const val SNIFF_BYTES = 8

    /** The saved file's name: the job's name made safe, then the time, so prints never overwrite each other. */
    fun fileName(jobName: String, atMillis: Long, zone: ZoneId): String {
        val base =
            jobName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(EXTENSION, "")
                .replace(UNSAFE, " ")
                .trim()
                .take(MAX_NAME)
                .trim()
                .ifEmpty { "Print" }
        return "$base ${STAMP.format(Instant.ofEpochMilli(atMillis).atZone(zone))}.pdf"
    }

    /** The document's real format from its first bytes; the declared one when they say nothing. */
    fun sniff(head: ByteArray, declared: String): String? {
        fun starts(magic: ByteArray) =
            head.size >= magic.size && head.copyOf(magic.size).contentEquals(magic)
        return when {
            starts("%PDF".toByteArray(Charsets.US_ASCII)) -> IppPrinter.PDF
            starts(RasterReader.PWG_SYNC) -> IppPrinter.PWG_RASTER
            starts(RasterReader.URF_SYNC.copyOf(SNIFF_BYTES)) -> IppPrinter.URF_RASTER
            declared == IppPrinter.OCTET_STREAM -> null
            else -> declared
        }
    }

    /** The DNS-SD TXT record IPP Everywhere and AirPrint clients read before they print. */
    fun txtRecord(name: String, uuid: String): Map<String, String> = linkedMapOf(
        "txtvers" to "1",
        "qtotal" to "1",
        "rp" to PATH.removePrefix("/"),
        "ty" to name,
        "product" to "(${IppPrinter.MAKE_AND_MODEL})",
        "pdl" to
            listOf(
                IppPrinter.PDF,
                IppPrinter.PWG_RASTER,
                IppPrinter.URF_RASTER
            ).joinToString(","),
        "URF" to IppPrinter.URF.joinToString(","),
        "Color" to "T",
        "Duplex" to "F",
        "kind" to "document",
        "PaperMax" to "legal-A4",
        "UUID" to uuid.removePrefix("urn:uuid:"),
        "priority" to "50"
    )

    /** Milliseconds before Print To Go turns itself off, counted from the last print (or from turning it on). */
    fun remaining(lastActivityMs: Long, nowMs: Long): Long =
        (lastActivityMs + IDLE_MS - nowMs).coerceAtLeast(0)
}
