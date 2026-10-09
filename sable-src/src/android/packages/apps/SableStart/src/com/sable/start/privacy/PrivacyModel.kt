package org.sableos.start.privacy

/**
 * All Apps privacy/security model (DESIGN-KF-D part 1).
 *
 * Pure Kotlin: no android.* types, so the truthfulness rules are unit tested
 * off-device. The platform layer (PrivacyFactsReader) turns PackageManager /
 * AppOps / accessibility / device-policy state for ONE app instance in ONE
 * Android user into [PrivacyFacts]; [PrivacyEvaluator] decides what may be
 * shown.
 *
 * Rules carried here:
 *  - REQUESTED_PERMISSION_ONLY=INSUFFICIENT: a group needs an effective grant.
 *  - APP_OP_EFFECTIVE_STATE_USED_WHEN_RELEVANT: an ignored/errored app op
 *    cancels a grant; an op that could not be read makes the group Unknown.
 *  - UNKNOWN_STATE=OMIT_NOT_GUESS: Unknown groups are never shown as access.
 *  - SEPARATE_PERMISSION_DATABASE=NO: nothing here persists anything.
 */
enum class PrivacyGroup(
    val label: String,
    val special: Boolean,
) {
    // Everyday groups, in compact-row priority order (the first eleven).
    Location("Location", false),
    Camera("Camera", false),
    Microphone("Microphone", false),
    Contacts("Contacts", false),
    Phone("Phone", false),
    Messages("Messages", false),
    Calendar("Calendar", false),
    Photos("Photos", false),
    Audio("Audio", false),
    Nearby("Nearby", false),
    Notifications("Notifications", false),

    // Special access: badge + expanded detail, never crowding the compact row.
    Accessibility("Accessibility", true),
    DeviceAdmin("Device admin", true),
    InstallApps("Install apps", true),
    Overlay("Overlay", true),
    UsageAccess("Usage access", true),
    ;

    companion object {
        val everyday: List<PrivacyGroup> = entries.filterNot { it.special }
        val specialAccess: List<PrivacyGroup> = entries.filter { it.special }
    }
}

/** Effective App-op mode as read for the app's uid in its own user. */
enum class OpMode {
    Allowed,
    Foreground,
    Default,
    Ignored,
    Errored,

    /** The permission has no app op; the runtime grant alone decides. */
    NotApplicable,

    /** The op exists but could not be read (SecurityException, missing package, ...). */
    Unknown,
}

/** One requested permission of one app instance. */
data class PermissionFact(
    val granted: Boolean,
    val opMode: OpMode = OpMode.NotApplicable,
)

/**
 * Everything the platform could learn about one app in one Android user.
 *
 * Nullable special-access fields mean "this layer could not determine it";
 * they are omitted, never guessed.
 */
data class PrivacyFacts(
    val permissions: Map<String, PermissionFact>,
    val targetSdk: Int,
    val deviceSdk: Int,
    val accessibilityServiceEnabled: Boolean? = null,
    val activeDeviceAdmin: Boolean? = null,
    val installAppsOp: OpMode? = null,
    val overlayOp: OpMode? = null,
    val usageAccessOp: OpMode? = null,
)

/** What the evaluator concluded for one group. */
sealed interface GroupState {
    /** Effective access the summary may show. */
    data object Allowed : GroupState

    /** Reliable, understandable partial access ("Photos limited", "Location approximate"). */
    data class Partial(
        val label: String,
    ) : GroupState

    /** Requested but not effectively allowed. */
    data object NotAllowed : GroupState

    /** Requested but the effective state could not be read: omit, never infer. */
    data object Unknown : GroupState
}

/** Result for one app instance (package in one user). Groups absent from [groups] were not requested. */
data class AppPrivacy(
    val groups: Map<PrivacyGroup, GroupState>,
) {
    /** Groups with effective (full or partial) access, in design priority order. */
    val effective: List<PrivacyGroup>
        get() =
            PrivacyGroup.entries.filter { group ->
                when (groups[group]) {
                    GroupState.Allowed, is GroupState.Partial -> true
                    else -> false
                }
            }

    val hasUnknown: Boolean
        get() = groups.values.any { it == GroupState.Unknown }

    /** User-facing label of one effective group (partial labels included). */
    fun labelFor(group: PrivacyGroup): String =
        when (val state = groups[group]) {
            is GroupState.Partial -> state.label
            else -> group.label
        }
}

object PrivacyPermissions {
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val ACCESS_BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"
    const val CAMERA = "android.permission.CAMERA"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val WRITE_CONTACTS = "android.permission.WRITE_CONTACTS"
    const val GET_ACCOUNTS = "android.permission.GET_ACCOUNTS"
    const val READ_PHONE_STATE = "android.permission.READ_PHONE_STATE"
    const val READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
    const val CALL_PHONE = "android.permission.CALL_PHONE"
    const val ANSWER_PHONE_CALLS = "android.permission.ANSWER_PHONE_CALLS"
    const val READ_CALL_LOG = "android.permission.READ_CALL_LOG"
    const val WRITE_CALL_LOG = "android.permission.WRITE_CALL_LOG"
    const val READ_SMS = "android.permission.READ_SMS"
    const val RECEIVE_SMS = "android.permission.RECEIVE_SMS"
    const val SEND_SMS = "android.permission.SEND_SMS"
    const val RECEIVE_MMS = "android.permission.RECEIVE_MMS"
    const val RECEIVE_WAP_PUSH = "android.permission.RECEIVE_WAP_PUSH"
    const val READ_CALENDAR = "android.permission.READ_CALENDAR"
    const val WRITE_CALENDAR = "android.permission.WRITE_CALENDAR"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    const val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE"
    const val NEARBY_WIFI_DEVICES = "android.permission.NEARBY_WIFI_DEVICES"
    const val UWB_RANGING = "android.permission.UWB_RANGING"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val SYSTEM_ALERT_WINDOW = "android.permission.SYSTEM_ALERT_WINDOW"
    const val REQUEST_INSTALL_PACKAGES = "android.permission.REQUEST_INSTALL_PACKAGES"
    const val PACKAGE_USAGE_STATS = "android.permission.PACKAGE_USAGE_STATS"

    /** Runtime permissions per everyday group (the partial-access permissions are handled separately). */
    val runtimeGroups: Map<PrivacyGroup, Set<String>> =
        mapOf(
            PrivacyGroup.Camera to setOf(CAMERA),
            PrivacyGroup.Microphone to setOf(RECORD_AUDIO),
            PrivacyGroup.Contacts to setOf(READ_CONTACTS, WRITE_CONTACTS, GET_ACCOUNTS),
            PrivacyGroup.Phone to
                setOf(
                    READ_PHONE_STATE,
                    READ_PHONE_NUMBERS,
                    CALL_PHONE,
                    ANSWER_PHONE_CALLS,
                    READ_CALL_LOG,
                    WRITE_CALL_LOG,
                ),
            PrivacyGroup.Messages to
                setOf(READ_SMS, RECEIVE_SMS, SEND_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH),
            PrivacyGroup.Calendar to setOf(READ_CALENDAR, WRITE_CALENDAR),
            PrivacyGroup.Audio to setOf(READ_MEDIA_AUDIO),
            PrivacyGroup.Nearby to
                setOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES, UWB_RANGING),
        )

    /** Every permission the reader must fetch grant/op state for. */
    val all: Set<String> =
        runtimeGroups.values.flatten().toSet() +
            setOf(
                ACCESS_FINE_LOCATION,
                ACCESS_COARSE_LOCATION,
                ACCESS_BACKGROUND_LOCATION,
                READ_MEDIA_IMAGES,
                READ_MEDIA_VIDEO,
                READ_MEDIA_VISUAL_USER_SELECTED,
                READ_EXTERNAL_STORAGE,
                POST_NOTIFICATIONS,
                SYSTEM_ALERT_WINDOW,
                REQUEST_INSTALL_PACKAGES,
                PACKAGE_USAGE_STATS,
            )
}

object PrivacyEvaluator {
    const val SDK_TIRAMISU = 33
    const val SDK_UPSIDE_DOWN_CAKE = 34
    const val LABEL_PHOTOS_LIMITED = "Photos limited"
    const val LABEL_LOCATION_APPROXIMATE = "Location approximate"

    fun evaluate(facts: PrivacyFacts): AppPrivacy {
        val groups = linkedMapOf<PrivacyGroup, GroupState>()
        location(facts)?.let { groups[PrivacyGroup.Location] = it }
        PrivacyPermissions.runtimeGroups.forEach { (group, permissions) ->
            val extra =
                if (group == PrivacyGroup.Audio) legacyStorage(facts) else emptySet()
            groupOf(facts, permissions + extra)?.let { groups[group] = it }
        }
        photos(facts)?.let { groups[PrivacyGroup.Photos] = it }
        notifications(facts)?.let { groups[PrivacyGroup.Notifications] = it }
        special(facts, groups)
        return AppPrivacy(PrivacyGroup.entries.mapNotNull { g -> groups[g]?.let { g to it } }.toMap())
    }

    /** true = effective, false = not effective, null = cannot tell. Missing = not requested. */
    internal fun effective(fact: PermissionFact): Boolean? {
        if (!fact.granted) return false
        return when (fact.opMode) {
            OpMode.Allowed, OpMode.Foreground, OpMode.Default, OpMode.NotApplicable -> true
            OpMode.Ignored, OpMode.Errored -> false
            OpMode.Unknown -> null
        }
    }

    private fun groupOf(
        facts: PrivacyFacts,
        permissions: Set<String>,
    ): GroupState? {
        val requested = permissions.mapNotNull { facts.permissions[it] }
        if (requested.isEmpty()) return null
        val states = requested.map(::effective)
        return when {
            states.any { it == true } -> GroupState.Allowed
            states.any { it == null } -> GroupState.Unknown
            else -> GroupState.NotAllowed
        }
    }

    private fun stateOf(
        facts: PrivacyFacts,
        permission: String,
    ): Boolean? = facts.permissions[permission]?.let(::effective)

    private fun location(facts: PrivacyFacts): GroupState? {
        val fine = facts.permissions[PrivacyPermissions.ACCESS_FINE_LOCATION]
        val coarse = facts.permissions[PrivacyPermissions.ACCESS_COARSE_LOCATION]
        if (fine == null && coarse == null) return null
        val fineState = fine?.let(::effective)
        val coarseState = coarse?.let(::effective)
        return when {
            fineState == true -> GroupState.Allowed
            // Location access is certain but precision is not: plain "Location", no qualifier.
            coarseState == true && fine != null && fineState == null -> GroupState.Allowed
            // "approximate" only when precise is known to be off (or never requested).
            coarseState == true -> GroupState.Partial(LABEL_LOCATION_APPROXIMATE)
            fine != null && fineState == null -> GroupState.Unknown
            coarse != null && coarseState == null -> GroupState.Unknown
            else -> GroupState.NotAllowed
        }
    }

    /** READ_EXTERNAL_STORAGE still means media access for apps targeting 32 or lower. */
    private fun legacyStorage(facts: PrivacyFacts): Set<String> =
        if (facts.deviceSdk < SDK_TIRAMISU || facts.targetSdk < SDK_TIRAMISU) {
            setOf(PrivacyPermissions.READ_EXTERNAL_STORAGE)
        } else {
            emptySet()
        }

    private fun photos(facts: PrivacyFacts): GroupState? {
        val full =
            setOf(PrivacyPermissions.READ_MEDIA_IMAGES, PrivacyPermissions.READ_MEDIA_VIDEO) +
                legacyStorage(facts)
        val fullState = groupOf(facts, full)
        val selected =
            if (facts.deviceSdk >= SDK_UPSIDE_DOWN_CAKE) {
                stateOf(facts, PrivacyPermissions.READ_MEDIA_VISUAL_USER_SELECTED)
            } else {
                null
            }
        val selectedRequested =
            facts.deviceSdk >= SDK_UPSIDE_DOWN_CAKE &&
                PrivacyPermissions.READ_MEDIA_VISUAL_USER_SELECTED in facts.permissions
        return when {
            fullState == GroupState.Allowed -> GroupState.Allowed
            fullState == GroupState.Unknown -> GroupState.Unknown
            selected == true -> GroupState.Partial(LABEL_PHOTOS_LIMITED)
            selectedRequested && selected == null -> GroupState.Unknown
            fullState == null && !selectedRequested -> null
            else -> GroupState.NotAllowed
        }
    }

    /**
     * Since Android 13 the app-level notification switch IS the POST_NOTIFICATIONS
     * grant (legacy apps get it as an implicit permission), so the grant state is
     * the effective state. Before 13 the launcher cannot read another app's
     * notification switch, so nothing is shown.
     */
    private fun notifications(facts: PrivacyFacts): GroupState? {
        if (facts.deviceSdk < SDK_TIRAMISU) return null
        val fact = facts.permissions[PrivacyPermissions.POST_NOTIFICATIONS] ?: return null
        return when (effective(fact)) {
            true -> GroupState.Allowed
            false -> GroupState.NotAllowed
            null -> GroupState.Unknown
        }
    }

    /** App-op backed special access: MODE_DEFAULT defers to the permission grant. */
    private fun specialOp(
        op: OpMode?,
        permission: PermissionFact?,
    ): GroupState? {
        if (permission == null) return null
        return when (op) {
            null, OpMode.Unknown -> GroupState.Unknown
            OpMode.Allowed, OpMode.Foreground -> GroupState.Allowed
            OpMode.Default, OpMode.NotApplicable ->
                if (permission.granted) GroupState.Allowed else GroupState.NotAllowed
            OpMode.Ignored, OpMode.Errored -> GroupState.NotAllowed
        }
    }

    private fun special(
        facts: PrivacyFacts,
        groups: MutableMap<PrivacyGroup, GroupState>,
    ) {
        if (facts.accessibilityServiceEnabled == true) groups[PrivacyGroup.Accessibility] = GroupState.Allowed
        if (facts.activeDeviceAdmin == true) groups[PrivacyGroup.DeviceAdmin] = GroupState.Allowed
        specialOp(facts.installAppsOp, facts.permissions[PrivacyPermissions.REQUEST_INSTALL_PACKAGES])
            ?.let { groups[PrivacyGroup.InstallApps] = it }
        specialOp(facts.overlayOp, facts.permissions[PrivacyPermissions.SYSTEM_ALERT_WINDOW])
            ?.let { groups[PrivacyGroup.Overlay] = it }
        specialOp(facts.usageAccessOp, facts.permissions[PrivacyPermissions.PACKAGE_USAGE_STATS])
            ?.let { groups[PrivacyGroup.UsageAccess] = it }
    }
}
