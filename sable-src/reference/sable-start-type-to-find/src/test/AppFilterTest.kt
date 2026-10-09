package org.sableos.reference.typetofind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFilterTest {
    private val apps = listOf(
        AppEntry("Settings", "a", "A"),
        AppEntry("Sable Setup", "b", "B"),
        AppEntry("Camera", "c", "C"),
        AppEntry("Radio diagnostics", "d", "D"),
        AppEntry("Messages", "e", "E"),
        AppEntry("Phone", "f", "F")
    )

    @Test fun emptyQueryListsAllSorted() {
        assertEquals(
            listOf("Camera", "Messages", "Phone", "Radio diagnostics", "Sable Setup", "Settings"),
            AppFilter.filter(apps, "").map {
                it.label
            }
        )
    }

    @Test fun prefixBeatsWordBeatsSubstring() {
        assertEquals(
            listOf("Settings", "Sable Setup"),
            AppFilter.filter(apps, "se").map {
                it.label
            }
        )
        assertEquals(
            listOf("Sable Setup", "Settings", "Messages", "Radio diagnostics"),
            AppFilter.filter(apps, "s").map {
                it.label
            }
        )
        assertEquals("Radio diagnostics", AppFilter.filter(apps, "diag").first().label)
        assertEquals("Camera", AppFilter.filter(apps, "mer").first().label)
    }

    @Test fun caseInsensitive() {
        assertEquals("Phone", AppFilter.filter(apps, "PHO").first().label)
    }

    @Test fun noMatchIsEmpty() {
        assertEquals(0, AppFilter.filter(apps, "zzz").size)
        assertNull(AppFilter.firstMatch(apps, "zzz"))
    }

    @Test fun enterNeedsAQuery() {
        assertNull(AppFilter.firstMatch(apps, "  "))
        assertEquals("Messages", AppFilter.firstMatch(apps, "mes")?.label)
    }
}
