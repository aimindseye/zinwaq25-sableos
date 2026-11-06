package org.sableos.phone.core

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.phone.core.CallHistory.Kind

class CallHistoryTest {
    private var nextId = 0L

    private fun call(number: String, kind: Kind, time: Long, day: Long, hidden: Boolean = false) =
        CallHistory.Call(nextId++, number, null, kind, time, 0, day, hidden)

    @Test
    fun groupsByDayNewestFirstWithLabels() {
        val sections = CallHistory.group(
            listOf(
                call("1", Kind.INCOMING, 10, 99),
                call("2", Kind.OUTGOING, 30, 100),
                call("3", Kind.OUTGOING, 1, 90)
            ),
            today = 100
        )
        assertEquals(listOf(100L, 99L, 90L), sections.map { it.day })
        assertEquals(
            listOf(
                CallHistory.DayLabel.TODAY,
                CallHistory.DayLabel.YESTERDAY,
                CallHistory.DayLabel.DATE
            ),
            sections.map { it.label }
        )
    }

    @Test
    fun foldsBackToBackCallsWithTheSameNumber() {
        val rows = CallHistory.group(
            listOf(
                call("+1 555 010 1222", Kind.OUTGOING, 50, 1),
                call("(555) 010-1222", Kind.INCOMING, 40, 1),
                call("777", Kind.INCOMING, 30, 1),
                call("5550101222", Kind.OUTGOING, 20, 1)
            ),
            today = 1
        ).single().rows
        assertEquals(listOf(2, 1, 1), rows.map { it.count })
        assertEquals(50L, rows.first().latest.timeMillis)
    }

    @Test
    fun aMissedCallNeverFoldsIntoAnAnsweredOne() {
        val rows = CallHistory.group(
            listOf(
                call("555", Kind.MISSED, 50, 1),
                call("555", Kind.INCOMING, 40, 1),
                call("555", Kind.INCOMING, 30, 1)
            ),
            today = 1
        ).single().rows
        assertEquals(listOf(1, 2), rows.map { it.count })
        assertEquals(true, rows.first().missed)
    }

    @Test
    fun hiddenNumbersFoldOnlyWithHiddenNumbers() {
        val rows = CallHistory.group(
            listOf(
                call("", Kind.INCOMING, 50, 1, hidden = true),
                call("", Kind.INCOMING, 40, 1, hidden = true),
                call("1", Kind.INCOMING, 30, 1)
            ),
            today = 1
        ).single().rows
        assertEquals(listOf(2, 1), rows.map { it.count })
    }

    @Test
    fun callLogTypes() {
        assertEquals(Kind.OUTGOING, CallHistory.kindOf(CallHistory.TYPE_OUTGOING))
        assertEquals(Kind.MISSED, CallHistory.kindOf(CallHistory.TYPE_MISSED))
        assertEquals(Kind.DECLINED, CallHistory.kindOf(CallHistory.TYPE_REJECTED))
        assertEquals(Kind.BLOCKED, CallHistory.kindOf(CallHistory.TYPE_BLOCKED))
        assertEquals(Kind.INCOMING, CallHistory.kindOf(99))
    }
}
