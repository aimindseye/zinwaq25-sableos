package org.sableos.start.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DESIGN-KF-D compact row: max 3 inline labels, +N, no raw names, badge for special access. */
class PrivacySummaryPolicyTest {
    private fun privacy(vararg groups: PrivacyGroup) = AppPrivacy(groups.associateWith { GroupState.Allowed })

    @Test
    fun noSensitiveAccess() {
        val row = PrivacySummaryPolicy.compact(AppPrivacy(mapOf(PrivacyGroup.Camera to GroupState.NotAllowed)))
        assertEquals(PrivacyRowState.NoneSensitive, row.state)
        assertEquals("permissions · none sensitive", row.text)
        assertEquals("permissions · none sensitive", PrivacySummaryPolicy.compact(AppPrivacy(emptyMap())).text)
    }

    @Test
    fun unknownStateNeverClaimsNoneSensitive() {
        val row = PrivacySummaryPolicy.compact(AppPrivacy(mapOf(PrivacyGroup.Camera to GroupState.Unknown)))
        assertEquals(PrivacyRowState.NotConfirmed, row.state)
        assertFalse(row.text.contains("none"))
        assertEquals("permissions · unavailable", PrivacySummaryPolicy.compact(null).text)
    }

    @Test
    fun oneAndThreeLabels() {
        assertEquals("permissions · Camera", PrivacySummaryPolicy.compact(privacy(PrivacyGroup.Camera)).text)
        assertEquals(
            "permissions · Location · Camera · Microphone",
            PrivacySummaryPolicy.compact(privacy(PrivacyGroup.Microphone, PrivacyGroup.Camera, PrivacyGroup.Location)).text,
        )
    }

    @Test
    fun moreThanThreeFoldsIntoPlusN() {
        val maps = privacy(PrivacyGroup.Location, PrivacyGroup.Contacts, PrivacyGroup.Notifications, PrivacyGroup.Nearby)
        val row = PrivacySummaryPolicy.compact(maps)
        assertEquals("permissions · Location · Contacts · Nearby · +1", row.text)
        assertEquals(3, row.labels.size)
        assertEquals(1, row.overflowCount)
        // maxInline can never exceed the design's 3.
        assertEquals(3, PrivacySummaryPolicy.compact(maps, maxInline = 10).labels.size)
    }

    @Test
    fun narrowWidthDropsWholeLabelsNeverMidWord() {
        val maps = privacy(PrivacyGroup.Location, PrivacyGroup.Contacts, PrivacyGroup.Notifications, PrivacyGroup.Photos)
        val row = PrivacySummaryPolicy.compact(maps, fits = { it.length <= "permissions · Location · Contacts · +2".length })
        assertEquals("permissions · Location · Contacts · +2", row.text)
        val tiny = PrivacySummaryPolicy.compact(maps, fits = { false })
        assertEquals(1, tiny.labels.size) // at least one label; the view may ellipsize
        assertEquals(3, tiny.overflowCount)
        // Every inline label is a whole group label.
        val all = PrivacyGroup.entries.map { it.label }
        row.labels.forEach { assertTrue(it in all) }
    }

    @Test
    fun specialAccessIsBadgedWithAccessibleText() {
        val p = privacy(PrivacyGroup.Camera, PrivacyGroup.Overlay, PrivacyGroup.Accessibility)
        val row = PrivacySummaryPolicy.compact(p)
        assertEquals("permissions · Camera", row.text)
        assertTrue(row.warning)
        assertEquals(listOf("Accessibility", "Overlay"), row.badgeLabels)
        val spoken = row.accessibleText(p)
        assertTrue(spoken.contains("Special access: Accessibility, Overlay"))
        assertTrue(spoken.contains("Permissions: Camera"))
    }

    @Test
    fun specialAccessGoesInlineWhenNothingEverydayIsAllowed() {
        val row = PrivacySummaryPolicy.compact(privacy(PrivacyGroup.DeviceAdmin))
        assertEquals("permissions · Device admin", row.text)
        assertTrue(row.warning)
        assertTrue(row.badgeLabels.isEmpty())
    }

    @Test
    fun partialLabelsAndDetail() {
        val p =
            AppPrivacy(
                mapOf(
                    PrivacyGroup.Location to GroupState.Partial("Location approximate"),
                    PrivacyGroup.Photos to GroupState.Partial("Photos limited"),
                    PrivacyGroup.UsageAccess to GroupState.Allowed,
                    PrivacyGroup.Camera to GroupState.Unknown,
                ),
            )
        assertEquals("permissions · Location approximate · Photos limited", PrivacySummaryPolicy.compact(p).text)
        val detail = PrivacySummaryPolicy.detail(p)
        assertEquals(listOf("Location approximate", "Photos limited", "Usage access"), detail.map { it.label })
        assertTrue(detail.last().special)
        assertTrue(PrivacySummaryPolicy.detail(null).isEmpty())
    }

    @Test
    fun accessibleTextSpeaksFoldedLabels() {
        val p = privacy(PrivacyGroup.Location, PrivacyGroup.Contacts, PrivacyGroup.Notifications, PrivacyGroup.Calendar)
        val row = PrivacySummaryPolicy.compact(p)
        assertTrue(row.text.endsWith("+1"))
        assertTrue(row.accessibleText(p).contains("Notifications"))
    }

    @Test
    fun privacySearchMatchesGroupWordsAndAliases() {
        val p = privacy(PrivacyGroup.Microphone, PrivacyGroup.Nearby)
        assertTrue(PrivacySummaryPolicy.matchesPrivacyQuery(p, "mic"))
        assertTrue(PrivacySummaryPolicy.matchesPrivacyQuery(p, "blue"))
        assertTrue(PrivacySummaryPolicy.matchesPrivacyQuery(p, "devices"))
        assertFalse(PrivacySummaryPolicy.matchesPrivacyQuery(p, "cam"))
        assertFalse(PrivacySummaryPolicy.matchesPrivacyQuery(p, "mi")) // too short to be a privacy query
        assertFalse(PrivacySummaryPolicy.matchesPrivacyQuery(null, "mic"))
        val denied = AppPrivacy(mapOf(PrivacyGroup.Camera to GroupState.NotAllowed))
        assertFalse(PrivacySummaryPolicy.matchesPrivacyQuery(denied, "camera"))
    }

    @Test
    fun rowTextNeverContainsRawPermissionNames() {
        val p = AppPrivacy(PrivacyGroup.entries.associateWith { GroupState.Allowed })
        val row = PrivacySummaryPolicy.compact(p)
        assertFalse(row.text.contains("android."))
        assertFalse(row.text.contains("permission."))
        assertNull(row.labels.firstOrNull { it.contains('_') })
    }
}
