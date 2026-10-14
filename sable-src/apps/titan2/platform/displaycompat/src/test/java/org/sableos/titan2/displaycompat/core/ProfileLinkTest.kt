package org.sableos.titan2.displaycompat.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileLinkTest {
    private fun open(action: String?, scheme: String?, part: String?) =
        ProfileLink.packageFrom(action, scheme, part)

    @Test fun packageUriOpensThatApp() {
        assertEquals("org.example.app", open(ProfileLink.ACTION, "package", "org.example.app"))
    }

    @Test fun otherActionsAndSchemesOpenTheList() {
        assertNull(open("android.intent.action.MAIN", "package", "org.example.app"))
        assertNull(open(null, null, null))
        assertNull(open(ProfileLink.ACTION, "https", "org.example.app"))
        assertNull(open(ProfileLink.ACTION, "package", null))
    }

    @Test fun malformedPackageNamesAreRefused() {
        val bad = listOf(
            "",
            "app",
            "../etc",
            "org.example/app",
            "1org.example",
            "org..example",
            "a".repeat(300) + ".b"
        )
        bad.forEach { assertNull(it, open(ProfileLink.ACTION, "package", it)) }
    }
}
