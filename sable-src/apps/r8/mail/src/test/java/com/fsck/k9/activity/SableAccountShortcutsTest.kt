package com.fsck.k9.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class SableAccountShortcutsTest {
    private val accounts = listOf("a" to "Work", "b" to "", "c" to "Home", "d" to "Other")

    @Test
    fun accountsWithoutALabelAreLeftOut() {
        assertEquals(listOf("a" to "Work", "c" to "Home", "d" to "Other"), SableAccountShortcuts.limit(accounts, 10))
    }

    @Test
    fun theLimitKeepsTheFirstAccounts() {
        assertEquals(listOf("a" to "Work", "c" to "Home"), SableAccountShortcuts.limit(accounts, 2))
        assertEquals(emptyList<Pair<String, String>>(), SableAccountShortcuts.limit(accounts, -1))
    }
}
