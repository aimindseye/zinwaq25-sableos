package com.android.dialer.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure tests for the line under a contact's name (phone-contacts patches 0701/0751). */
class SableSubtitleTest {
    private val lri = "⁦"
    private val pdi = "⁩"

    @Test fun labelAndNumberAreJoinedWithTheNumberIsolated() {
        assertEquals("Mobile · ${lri}+1 555 0101$pdi", SableSubtitle.format("Mobile", "+1 555 0101"))
        assertEquals("${lri}0101$pdi", SableSubtitle.format(null, "0101"))
        assertEquals("${lri}0101$pdi", SableSubtitle.format("   ", "0101"))
    }

    @Test fun whitespaceIsCollapsedAndEmptyNumbersHideTheLine() {
        assertEquals("Work · ${lri}+1 555$pdi", SableSubtitle.format(" Work\n", "\t+1  555 "))
        assertNull(SableSubtitle.format("Mobile", null))
        assertNull(SableSubtitle.format("Mobile", "  "))
        assertNull(SableSubtitle.format("Mobile", "()-"))
        assertEquals("*86", SableSubtitle.cleanNumber(" *86 "))
    }

    @Test fun longCustomLabelsAreCut() {
        val label = SableSubtitle.cleanLabel("A very long custom label from an account")
        assertEquals(SableSubtitle.MAX_LABEL_LENGTH, label.length)
        assertEquals('…', label.last())
        assertEquals("", SableSubtitle.cleanLabel(null))
    }
}
