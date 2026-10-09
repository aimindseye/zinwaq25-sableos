package org.sableos.hub.notifications

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import org.sableos.hub.ConnectedAppKey
import org.sableos.hub.HubPreferences
import org.sableos.hub.platform.DeviceAttention
import org.sableos.hub.platform.PrivacyReader
import org.sableos.hub.platform.ProfileDirectory
import org.sableos.hub.platform.ProfileState
import org.sableos.hub.policy.AndroidAlertState
import org.sableos.hub.policy.AndroidVisibility
import org.sableos.hub.policy.AttentionDecision
import org.sableos.hub.policy.AttentionDeviceProfile
import org.sableos.hub.policy.AttentionOutput
import org.sableos.hub.policy.AttentionPolicy
import org.sableos.hub.policy.AttentionSelections
import org.sableos.hub.policy.PrivacyContext

/**
 * A driver for one Sable-controlled attention output (keyboard backlight, secondary display).
 * A sink receives only [AttentionDecision.Emit] values, which are already DND-, importance- and
 * privacy-gated and carry a bounded pattern; it must not add content of its own.
 */
interface AttentionSink {
    val output: AttentionOutput

    fun signal(
        decision: AttentionDecision.Emit,
        category: String?,
    )
}

/**
 * Maps Android's effective decision for each posted notification to the profile-supported Sable
 * attention outputs (DESIGN-KF-A "Attention capability model").
 *
 * No sink ships yet: no Sable device profile has a validated Sable-driven output, and
 * unvalidated outputs must stay hidden. A validated profile adds its driver to [SINKS]; until
 * then this does no work beyond an early return.
 */
internal class AttentionDispatcher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val profile: AttentionDeviceProfile by lazy { DeviceAttention.profile(appContext) }
    private val preferences by lazy { HubPreferences(appContext) }
    private val profiles by lazy { ProfileDirectory(appContext) }
    private val privacy by lazy { PrivacyReader(appContext) }
    private val seenKeys = LinkedHashSet<String>()

    fun onPosted(
        sbn: StatusBarNotification,
        ranking: Ranking?,
    ) {
        val sinks = SINKS.filter { profile.isSupported(it.output) }
        val update = rememberAndCheckUpdate(sbn)
        val source = if (sinks.isEmpty() || ranking == null) null else profiles.forUser(sbn.user)
        if (ranking != null && source != null) {
            dispatch(sinks, sbn, ranking, source, update)
        }
    }

    private fun dispatch(
        sinks: List<AttentionSink>,
        sbn: StatusBarNotification,
        ranking: Ranking,
        source: ProfileState,
        update: Boolean,
    ) {
        val key = ConnectedAppKey(sbn.packageName, source.serial)
        val selection = AttentionSelections.effectiveFor(key, preferences.loadAttentionSelections(), profile)
        val notification = sbn.notification
        val decisions =
            AttentionPolicy.decide(
                profile = profile,
                selection = selection,
                android =
                    AndroidAlertState(
                        importance = ranking.importance,
                        matchesInterruptionFilter = ranking.matchesInterruptionFilter(),
                        alertOnlyOnceUpdate =
                            update && notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0,
                    ),
                privacy =
                    PrivacyContext(
                        deviceLocked = privacy.deviceLocked(),
                        privateMode = preferences.privateMode(),
                        profile = source.kind,
                        profileLocked = source.locked,
                        lockscreen = privacy.lockscreenPolicy(),
                        visibility =
                            AndroidVisibility.effective(
                                notification.visibility,
                                ranking.channel?.lockscreenVisibility ?: AndroidVisibility.VISIBILITY_NO_OVERRIDE,
                            ),
                    ),
            )
        decisions.filterIsInstance<AttentionDecision.Emit>().forEach { emit ->
            sinks.filter { it.output == emit.output }.forEach { sink ->
                runCatching { sink.signal(emit, notification.category) }
            }
        }
    }

    /** True when this key was already posted in this listener session (an update). */
    @Synchronized
    private fun rememberAndCheckUpdate(sbn: StatusBarNotification): Boolean {
        val seen = !seenKeys.add(sbn.key)
        if (seenKeys.size > MAX_SEEN_KEYS) {
            seenKeys.remove(seenKeys.first())
        }
        return seen
    }

    private companion object {
        const val MAX_SEEN_KEYS = 256
        val SINKS: List<AttentionSink> = emptyList()
    }
}
