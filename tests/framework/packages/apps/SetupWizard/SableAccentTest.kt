package org.lineageos.setupwizard.sable

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure tests for the Sable accent on the Theme step carried in patches/framework/packages/apps/SetupWizard/0406. */
class SableAccentTest {
    @Test fun presetsInSettingsOrder() {
        // Same order as SableAppearancePolicy.ACCENTS (Settings 0101) and the label array.
        assertEquals(5, SableAccent.count())
        listOf("blue", "green", "purple", "orange", "slate").forEachIndexed { i, v ->
            assertEquals(i, SableAccent.indexOf(v))
        }
    }

    @Test fun unknownOrMissingShowsNoAccent() {
        assertEquals(-1, SableAccent.indexOf(null))
        assertEquals(-1, SableAccent.indexOf(""))
        assertEquals(-1, SableAccent.indexOf("Blue"))
        assertEquals(-1, SableAccent.indexOf("red"))
        assertEquals(0, SableAccent.indexOf(" blue "))
    }

    @Test fun readsTheSettingsAuthorityAndLinksToItsAccentRow() {
        assertEquals("content://org.sableos.appearance/appearance", SableAccent.CONTENT_URI)
        assertEquals("accent", SableAccent.COLUMN_ACCENT)
        assertEquals("sable_accent", SableAccent.SETTINGS_PREFERENCE_KEY)
        assertEquals(":settings:fragment_args_key", SableAccent.EXTRA_FRAGMENT_ARG_KEY)
        assertEquals(":settings:show_fragment_args", SableAccent.EXTRA_SHOW_FRAGMENT_ARGUMENTS)
    }
}
