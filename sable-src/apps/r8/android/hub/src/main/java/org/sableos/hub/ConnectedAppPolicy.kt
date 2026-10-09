package org.sableos.hub

import java.nio.charset.StandardCharsets
import java.util.Base64

data class ConnectedAppKey(
    val packageName: String,
    val userSerial: Long,
) {
    init {
        require(packageName.isNotBlank())
        require(userSerial >= 0L)
    }
}

private const val ONE_DAY_RETENTION = 1
private const val SEVEN_DAY_RETENTION = 7
private const val THIRTY_DAY_RETENTION = 30

/**
 * Optional bounded local history preference (DESIGN-KF-A "Hub configuration ownership").
 *
 * Hub history is a derived cache of notifications Android already delivered, never the
 * notification history of record (Android owns that). Every option is therefore time-bounded;
 * the Panther V1 "Until deleted" option was removed and old records that still name it decode to
 * the longest bounded option. Count bounds live in [org.sableos.hub.policy.HubHistoryBounds].
 */
enum class HistoryRetention(
    val days: Int,
    val label: String,
) {
    OneDay(ONE_DAY_RETENTION, "1 day"),
    SevenDays(SEVEN_DAY_RETENTION, "7 days"),
    ThirtyDays(THIRTY_DAY_RETENTION, "30 days"),
    ;

    fun next(): HistoryRetention =
        when (this) {
            OneDay -> SevenDays
            SevenDays -> ThirtyDays
            ThirtyDays -> OneDay
        }

    companion object {
        /** Panther V1 stored `UntilDeleted`; it now maps to the longest bounded option. */
        private const val LEGACY_UNBOUNDED = "UntilDeleted"

        fun fromStored(name: String): HistoryRetention =
            if (name == LEGACY_UNBOUNDED) {
                ThirtyDays
            } else {
                valueOf(name)
            }
    }
}

/**
 * Hub preview preference. It only narrows what Hub shows; it never widens what Android's
 * lockscreen, private-mode or source policy allows (see [org.sableos.hub.policy.PrivacyPosture]).
 */
enum class HubPreviewPolicy(
    val label: String,
) {
    /** Sender and message text, when the device and source allow it. */
    ShowContent("Sender and message"),

    /** Sender only; message text hidden. */
    SenderOnly("Sender only"),

    /** App name and count only. */
    SourceOnly("App and count only"),
    ;

    fun next(): HubPreviewPolicy = entries[(ordinal + 1) % entries.size]
}

/**
 * Hub-owned state for one Android user/profile + package (DESIGN-KF-A `HUB_KEY`).
 *
 * This is deliberately not notification policy: it holds no importance, sound, vibration,
 * heads-up, badge, lockscreen-visibility, snooze or Do Not Disturb field. Android owns those and
 * Hub links to Android Settings for them. Field-to-concept ownership is checked by
 * `NotificationOwnershipTest`.
 *
 * - [includeInMessages]: "Include in Hub".
 * - [favorite]: "Hub priority"; ordering/aggregation only, never a DND exception.
 * - [allowQuickReply]: whether Hub may offer a source-provided RemoteInput reply.
 * - [hideFromLauncher]: Sable Start hides the app while Hub represents it (Panther V1).
 * - [retention]: bounded local derived history.
 * - [previewPolicy]: privacy-safe preview preference.
 */
data class ConnectedAppPolicy(
    val key: ConnectedAppKey,
    val includeInMessages: Boolean = false,
    val allowQuickReply: Boolean = false,
    val hideFromLauncher: Boolean = false,
    val retention: HistoryRetention = HistoryRetention.ThirtyDays,
    val favorite: Boolean = false,
    val previewPolicy: HubPreviewPolicy = HubPreviewPolicy.ShowContent,
) {
    fun normalized(): ConnectedAppPolicy =
        if (includeInMessages) {
            this
        } else {
            copy(
                allowQuickReply = false,
                hideFromLauncher = false,
            )
        }

    fun isDefaultDisabled(): Boolean {
        val value = normalized()
        return !value.includeInMessages &&
            !value.allowQuickReply &&
            !value.hideFromLauncher &&
            value.retention == HistoryRetention.ThirtyDays &&
            !value.favorite &&
            value.previewPolicy == HubPreviewPolicy.ShowContent
    }
}

object ConnectedAppPolicyCodec {
    private const val VERSION = "3"
    private const val VERSION_1 = "1"
    private const val VERSION_2 = "2"
    private const val VERSION_1_FIELD_COUNT = 7
    private const val VERSION_2_FIELD_COUNT = 8
    private const val VERSION_3_FIELD_COUNT = 9
    private const val USER_SERIAL_INDEX = 1
    private const val PACKAGE_INDEX = 2
    private const val INCLUDE_INDEX = 3
    private const val REPLY_INDEX = 4
    private const val HIDE_INDEX = 5
    private const val RETENTION_INDEX = 6
    private const val FAVORITE_INDEX = 7
    private const val PREVIEW_INDEX = 8
    private const val SEPARATOR = "|"

    fun encode(policy: ConnectedAppPolicy): String {
        val value = policy.normalized()
        return listOf(
            VERSION,
            value.key.userSerial.toString(),
            encodeText(value.key.packageName),
            value.includeInMessages.asDigit(),
            value.allowQuickReply.asDigit(),
            value.hideFromLauncher.asDigit(),
            value.retention.name,
            value.favorite.asDigit(),
            value.previewPolicy.name,
        ).joinToString(SEPARATOR)
    }

    fun decode(encoded: String): ConnectedAppPolicy? =
        runCatching {
            val fields = encoded.split(SEPARATOR)
            val version = fields.firstOrNull().orEmpty()
            require(
                (version == VERSION_1 && fields.size == VERSION_1_FIELD_COUNT) ||
                    (version == VERSION_2 && fields.size == VERSION_2_FIELD_COUNT) ||
                    (version == VERSION && fields.size == VERSION_3_FIELD_COUNT),
            )

            ConnectedAppPolicy(
                key =
                    ConnectedAppKey(
                        packageName = requireNotNull(decodeText(fields[PACKAGE_INDEX])),
                        userSerial = fields[USER_SERIAL_INDEX].toLong(),
                    ),
                includeInMessages = parseBooleanDigit(fields[INCLUDE_INDEX]),
                allowQuickReply = parseBooleanDigit(fields[REPLY_INDEX]),
                hideFromLauncher = parseBooleanDigit(fields[HIDE_INDEX]),
                retention = HistoryRetention.fromStored(fields[RETENTION_INDEX]),
                favorite =
                    version != VERSION_1 &&
                        parseBooleanDigit(fields[FAVORITE_INDEX]),
                previewPolicy =
                    if (version == VERSION) {
                        HubPreviewPolicy.valueOf(fields[PREVIEW_INDEX])
                    } else {
                        HubPreviewPolicy.ShowContent
                    },
            ).normalized()
        }.getOrNull()

    private fun encodeText(value: String): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeText(value: String): String? =
        runCatching {
            String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()

    private fun Boolean.asDigit(): String = if (this) "1" else "0"

    private fun parseBooleanDigit(value: String): Boolean =
        when (value) {
            "1" -> true
            "0" -> false
            else -> error("Invalid boolean digit")
        }
}
