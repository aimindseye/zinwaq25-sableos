package org.sableos.hub.policy

import org.sableos.hub.HubPreviewPolicy

/** Android user/profile kind of a notification's source (DESIGN-KF-A "Multi-user and work profile"). */
enum class ProfileKind {
    Personal,
    Work,

    /** Android private space. */
    Private,

    /** Any other secondary profile Android reports. */
    Other,
}

/**
 * Effective Android lockscreen visibility for one notification: the most restrictive of the
 * notification's own visibility and its channel's lockscreen visibility.
 */
enum class AndroidVisibility {
    Public,
    Private,
    Secret,
    ;

    companion object {
        // android.app.Notification.VISIBILITY_* and NotificationManager.VISIBILITY_NO_OVERRIDE.
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_PRIVATE = 0
        const val VISIBILITY_SECRET = -1
        const val VISIBILITY_NO_OVERRIDE = -1000

        fun fromAndroid(value: Int): AndroidVisibility? =
            when (value) {
                VISIBILITY_PUBLIC -> Public
                VISIBILITY_PRIVATE -> Private
                VISIBILITY_SECRET -> Secret
                else -> null
            }

        /** Channel override wins only when it is more restrictive, as in SystemUI. */
        fun effective(
            notificationVisibility: Int,
            channelLockscreenVisibility: Int,
        ): AndroidVisibility {
            val own = fromAndroid(notificationVisibility) ?: Private
            val channel = fromAndroid(channelLockscreenVisibility) ?: return own
            return if (channel.ordinal > own.ordinal) channel else own
        }
    }
}

/** Android's per-user lockscreen notification settings, read, never written, by Sable. */
data class LockscreenNotificationPolicy(
    val showNotifications: Boolean,
    val allowPrivateContent: Boolean,
)

/** Everything privacy-relevant about one notification/conversation at one moment. */
data class PrivacyContext(
    val deviceLocked: Boolean,
    val privateMode: Boolean = false,
    val profile: ProfileKind = ProfileKind.Personal,
    val profileLocked: Boolean = false,
    val sourceRestricted: Boolean = false,
    val lockscreen: LockscreenNotificationPolicy =
        LockscreenNotificationPolicy(showNotifications = true, allowPrivateContent = false),
    val visibility: AndroidVisibility = AndroidVisibility.Private,
)

/** What a Hub surface may show, most permissive first. */
enum class HubContentLevel {
    Full,
    SenderOnly,
    SourceAndCount,
    GenericProfile,
    Hidden,
}

/** What a hardware attention output may indicate, most permissive first. */
enum class AttentionContent {
    Full,
    CategoryCount,
    Generic,
    None,
}

object PrivacyPosture {
    /**
     * DESIGN-KF-A privacy table, Hub column. The Hub preview preference can only narrow the result.
     *
     * | State | Hub |
     * | Unlocked | allowed previews |
     * | Locked | redacted by default (source/count) |
     * | Private mode | body/sender redaction |
     * | Work profile locked | work content hidden |
     * | Source restricted | source/count/action only |
     */
    fun hubLevel(
        context: PrivacyContext,
        preview: HubPreviewPolicy,
    ): HubContentLevel {
        val levels = mutableListOf(previewLevel(preview))
        if (context.profileLocked) {
            levels +=
                if (context.profile == ProfileKind.Private) {
                    HubContentLevel.Hidden
                } else {
                    HubContentLevel.GenericProfile
                }
        }
        if (context.deviceLocked || context.privateMode || context.sourceRestricted) {
            levels += HubContentLevel.SourceAndCount
        }
        return levels.maxBy(HubContentLevel::ordinal)
    }

    /** DESIGN-KF-A privacy table, Attention column. */
    fun attentionContent(context: PrivacyContext): AttentionContent {
        val levels = mutableListOf(AttentionContent.Full)
        if (context.profileLocked) {
            levels +=
                if (context.profile == ProfileKind.Private) {
                    AttentionContent.None
                } else {
                    AttentionContent.Generic
                }
        }
        if (context.privateMode || context.sourceRestricted) {
            levels += AttentionContent.Generic
        }
        if (context.deviceLocked) {
            levels += lockedAttention(context)
        }
        return levels.maxBy(AttentionContent::ordinal)
    }

    /** Reply from a surface needs the device and the source profile unlocked unless Android says otherwise. */
    fun requiresUnlockForActions(context: PrivacyContext): Boolean = context.deviceLocked || context.profileLocked

    private fun lockedAttention(context: PrivacyContext): AttentionContent =
        when {
            !context.lockscreen.showNotifications -> AttentionContent.None
            context.visibility == AndroidVisibility.Secret -> AttentionContent.None
            context.visibility == AndroidVisibility.Public -> AttentionContent.Full
            context.lockscreen.allowPrivateContent -> AttentionContent.Full
            else -> AttentionContent.CategoryCount
        }

    private fun previewLevel(preview: HubPreviewPolicy): HubContentLevel =
        when (preview) {
            HubPreviewPolicy.ShowContent -> HubContentLevel.Full
            HubPreviewPolicy.SenderOnly -> HubContentLevel.SenderOnly
            HubPreviewPolicy.SourceOnly -> HubContentLevel.SourceAndCount
        }
}
