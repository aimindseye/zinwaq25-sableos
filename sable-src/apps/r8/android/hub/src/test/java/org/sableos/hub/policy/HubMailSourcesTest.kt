package org.sableos.hub.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

class HubMailSourcesTest {
    @Test
    fun knownMailPackagesAreMailWithoutACategory() {
        listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "ch.protonmail.android",
            "com.yahoo.mobile.client.android.mail",
            "com.readdle.spark",
            "eu.faircode.email",
            "com.fsck.k9",
            "net.thunderbird.android",
            "com.samsung.android.email.provider",
            "me.bluemail.mail",
            "de.tutao.tutanota",
            "com.fastmail.app",
            "org.sableos.mail",
            "com.android.email",
        ).forEach { packageName ->
            assertTrue(packageName, HubMailSources.isMail(packageName, null))
        }
        assertEquals(14, HubMailSources.KNOWN_PACKAGES.size)
    }

    @Test
    fun unknownPackageWithEmailCategoryIsMail() {
        assertTrue(HubMailSources.isMail("org.example.newmail", "email"))
        assertTrue(HubMailSources.isMail("org.example.newmail", HubMailSources.CATEGORY_EMAIL))
    }

    @Test
    fun unknownPackageWithOtherCategoryIsNotMail() {
        assertFalse(HubMailSources.isMail("org.thoughtcrime.securesms", "msg"))
        assertFalse(HubMailSources.isMail("org.thoughtcrime.securesms", null))
        assertFalse(HubMailSources.isMail(null, null))
        assertFalse(HubMailSources.isMail("org.example.app", "EMAIL"))
    }

    @Test
    fun onlyConnectedConversationsAreMail() {
        val connected =
            HubConversation(
                threadId = -1L,
                address = "inbox",
                displayName = "Ana",
                lastBody = "Invoice",
                lastDateMillis = 1L,
                unreadCount = 0,
                source = HubConversationSource.ConnectedApp,
                sourcePackage = "org.example.newmail",
                sourceUserSerial = 0L,
                category = "email",
            )
        assertTrue(HubMailSources.isMail(connected))
        assertTrue(HubMailSources.isMail(connected.copy(category = null, sourcePackage = "com.google.android.gm")))
        assertFalse(HubMailSources.isMail(connected.copy(category = "msg")))
        // An SMS row is never mail, whatever its fields say.
        assertFalse(HubMailSources.isMail(connected.copy(source = HubConversationSource.Sms)))
    }

    @Test
    fun duplicateWarningNeedsTwoDistinctKnownMailApps() {
        assertFalse(HubMailSources.duplicateAccountWarning(emptyList()))
        assertFalse(HubMailSources.duplicateAccountWarning(listOf("com.google.android.gm")))
        // The same app in two profiles is one app.
        assertFalse(HubMailSources.duplicateAccountWarning(listOf("com.google.android.gm", "com.google.android.gm")))
        // Non-mail apps do not count.
        val withChat = listOf("com.google.android.gm", "org.thoughtcrime.securesms")
        assertFalse(HubMailSources.duplicateAccountWarning(withChat))
        assertTrue(HubMailSources.duplicateAccountWarning(listOf("com.google.android.gm", "org.sableos.mail")))
        assertTrue(
            HubMailSources.duplicateAccountWarning(
                listOf("ch.protonmail.android", "com.microsoft.office.outlook", "com.fsck.k9"),
            ),
        )
    }
}
