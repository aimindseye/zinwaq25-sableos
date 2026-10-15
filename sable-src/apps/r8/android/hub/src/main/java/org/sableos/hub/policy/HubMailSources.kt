package org.sableos.hub.policy

import org.sableos.hub.HubConversation
import org.sableos.hub.HubConversationSource

/**
 * Mail apps as Hub sources. Hub has no network access and signs into nothing: a mail app is
 * "connected" only through its own notifications, opt-in per app like any other connected app.
 * Accounts (Gmail, Outlook, Yahoo, iCloud via OAuth/IMAP) live in Sable Mail or the mail app.
 */
object HubMailSources {
    /** `android.app.Notification.CATEGORY_EMAIL`. */
    const val CATEGORY_EMAIL = "email"

    /** Popular mail apps, recognised even when a notification carries no category. */
    val KNOWN_PACKAGES: Set<String> =
        setOf(
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
        )

    /** A known mail package, or any app whose notification says it is email. */
    fun isMail(
        packageName: String?,
        category: String?,
    ): Boolean = isKnownMailPackage(packageName) || category == CATEGORY_EMAIL

    fun isKnownMailPackage(packageName: String?): Boolean = packageName != null && packageName in KNOWN_PACKAGES

    /**
     * Whether Connected apps should warn that an account signed in to two mail apps shows twice:
     * two or more distinct known mail apps are included in Hub at once.
     */
    fun duplicateAccountWarning(includedPackages: Collection<String>): Boolean =
        includedPackages.filter(::isKnownMailPackage).toSet().size >= 2

    /** Connected-app conversations from a mail source; Hub's own SMS never is. */
    fun isMail(conversation: HubConversation): Boolean =
        conversation.source == HubConversationSource.ConnectedApp &&
            isMail(conversation.sourcePackage, conversation.category)
}
