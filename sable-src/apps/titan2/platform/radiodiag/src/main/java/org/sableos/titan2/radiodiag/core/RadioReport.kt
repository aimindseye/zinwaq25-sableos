package org.sableos.titan2.radiodiag.core

data class Row(val key: String, val value: String)
data class Section(val title: String, val rows: List<Row>)

/**
 * Plain-text report. Identifiers that could identify a person or SIM are never put into it (see [Redact]); every value
 * is masked here, not only the ones a caller remembered to mask.
 */
object RadioReport {
    const val EVIDENCE_LEVEL = "PUBLIC_READ_ONLY_APP_STATE"
    const val IMS_REGISTRATION =
        "NOT_VISIBLE (needs a privileged permission; canonical interface request IR-007)"
    const val CANONICAL_EVIDENCE = "NOT_PROVIDED_BY_THIS_APP (use the canonical C3B capture)"

    /** Fixed limits block: always present, so a report can never be read as radio or IMS proof. */
    fun limits(): Section = Section(
        "Limits of this report",
        listOf(
            Row("evidence level", EVIDENCE_LEVEL),
            Row("ims registration", IMS_REGISTRATION),
            Row("canonical radio evidence", CANONICAL_EVIDENCE)
        )
    )

    fun render(
        profileLine: String,
        sections: List<Section>,
        notes: List<String> = emptyList()
    ): String = buildString {
        appendLine("Radio diagnostics (read-only, on-device)")
        appendLine(profileLine)
        appendLine("Redaction: identifiers are masked. Review before sharing.")
        sections.forEach { s -> appendSection(s) }
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine("## Consistency notes")
            notes.forEach { appendLine(Redact.text(it)) }
        }
        appendSection(limits())
    }

    private fun StringBuilder.appendSection(s: Section) {
        appendLine()
        appendLine("## ${s.title}")
        s.rows.forEach { appendLine("${it.key}: ${Redact.text(it.value).ifBlank { "-" }}") }
    }
}

object Redact {
    private const val VISIBLE_TAIL = 4
    private const val FULLY_MASKED_MAX_LENGTH = 6

    /** Keeps the last 4 characters of a long identifier (ICCID, IMSI, IMEI, number); short values are fully masked. */
    fun id(s: String?): String {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return "-"
        return if (v.length <= FULLY_MASKED_MAX_LENGTH) {
            "*".repeat(v.length)
        } else {
            "*".repeat(v.length - VISIBLE_TAIL) + v.takeLast(VISIBLE_TAIL)
        }
    }

    /** Masks any run of 11+ digits inside free text (IMEI/ICCID/IMSI/numbers in log lines). */
    fun text(s: String): String = Regex("\\d{11,}").replace(s) { m ->
        "*".repeat(m.value.length - VISIBLE_TAIL) +
            m.value.takeLast(VISIBLE_TAIL)
    }
}
