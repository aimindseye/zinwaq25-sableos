package org.sableos.titan2.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.setup.core.Item
import org.sableos.titan2.setup.core.Readiness
import org.sableos.titan2.setup.core.SetupModel

class SetupModelTest {
    private val items =
        listOf(
            Item("a", "A", "", Readiness.Satisfied),
            Item("b", "B", "", Readiness.ActionRequired),
            Item("c", "C", "", Readiness.Informational)
        )

    @Test fun summaryIgnoresInformationalRows() {
        assertEquals("1 of 2 confirmed · 1 action required", SetupModel.summary(items))
    }

    @Test fun readyOnlyWithoutActionRequired() {
        assertFalse(SetupModel.isReady(items))
        assertTrue(SetupModel.isReady(items.filter { it.id != "b" }))
    }

    @Test fun profileLines() {
        assertEquals("Unihertz Titan 2", SetupModel.profileLine("titan2"))
        assertEquals("Unihertz Titan 2 Elite", SetupModel.profileLine("titan2-elite"))
        assertEquals(
            "Unrecognized profile id: other-device",
            SetupModel.profileLine("other-device")
        )
    }
}
