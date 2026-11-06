package org.sableos.phone.core

/**
 * The BlackBerry 10 call list: calls grouped by day, newest first, with back-to-back calls to or from the same
 * number on the same day folded into one row that counts them. Pure, so the host tests cover the grouping.
 */
object CallHistory {
    enum class Kind { INCOMING, OUTGOING, MISSED, DECLINED, BLOCKED, VOICEMAIL }

    /** One call log entry. [day] is the local calendar day (epoch day) of [timeMillis]. */
    data class Call(
        val id: Long,
        val number: String,
        val name: String?,
        val kind: Kind,
        val timeMillis: Long,
        val durationSeconds: Long,
        val day: Long,
        val hidden: Boolean = false
    )

    /** One row: the newest call and how many calls it stands for, with their kinds newest first. */
    data class Row(val latest: Call, val calls: List<Call>) {
        val count: Int get() = calls.size
        val missed: Boolean get() = latest.kind == Kind.MISSED
    }

    enum class DayLabel { TODAY, YESTERDAY, DATE }

    data class Section(val day: Long, val label: DayLabel, val rows: List<Row>)

    /** Groups [calls] (any order) into day sections, newest first, given [today] as an epoch day. */
    fun group(calls: List<Call>, today: Long): List<Section> {
        val sorted = calls.sortedByDescending { it.timeMillis }
        val sections = mutableListOf<Section>()
        for ((day, ofDay) in sorted.groupBy { it.day }) {
            val rows = mutableListOf<Row>()
            for (call in ofDay) {
                val last = rows.lastOrNull()
                if (last != null && foldable(last.latest, call)) {
                    rows[rows.lastIndex] = last.copy(calls = last.calls + call)
                } else {
                    rows += Row(call, listOf(call))
                }
            }
            sections += Section(day, labelFor(day, today), rows)
        }
        return sections
    }

    fun labelFor(day: Long, today: Long): DayLabel = when (day) {
        today -> DayLabel.TODAY
        today - 1 -> DayLabel.YESTERDAY
        else -> DayLabel.DATE
    }

    /** Same caller, and a missed call never folds into an answered one (it must stand out). */
    private fun foldable(newer: Call, older: Call): Boolean {
        val same = if (newer.hidden || older.hidden) {
            newer.hidden && older.hidden
        } else {
            CallFormat.sameNumber(newer.number, older.number)
        }
        return same && (newer.kind == Kind.MISSED) == (older.kind == Kind.MISSED)
    }

    /** CallLog.Calls.TYPE to a [Kind]; unknown types read as incoming. */
    fun kindOf(type: Int): Kind = when (type) {
        TYPE_OUTGOING -> Kind.OUTGOING
        TYPE_MISSED -> Kind.MISSED
        TYPE_VOICEMAIL -> Kind.VOICEMAIL
        TYPE_REJECTED -> Kind.DECLINED
        TYPE_BLOCKED -> Kind.BLOCKED
        else -> Kind.INCOMING
    }

    // android.provider.CallLog.Calls TYPE values.
    const val TYPE_INCOMING = 1
    const val TYPE_OUTGOING = 2
    const val TYPE_MISSED = 3
    const val TYPE_VOICEMAIL = 4
    const val TYPE_REJECTED = 5
    const val TYPE_BLOCKED = 6
}
