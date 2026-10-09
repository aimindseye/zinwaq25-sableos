package org.sableos.hub.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.Vibrator
import org.sableos.hub.policy.AttentionDeviceProfile
import org.sableos.hub.policy.PlatformAttentionFacts

/**
 * The running device's attention capabilities: the device profile's own declaration
 * (`ro.sable.attention.outputs`, matched against `ro.sable.profile.id`) plus Android's audio and
 * vibrator facts. No device-model branching: a profile without a declaration gets no Sable output.
 */
object DeviceAttention {
    fun profile(context: Context): AttentionDeviceProfile {
        val vibrator = context.getSystemService(Vibrator::class.java)
        return AttentionDeviceProfile.parse(
            profileId = SysProps.get(AttentionDeviceProfile.PROPERTY_PROFILE_ID),
            declaration = SysProps.get(AttentionDeviceProfile.PROPERTY_ATTENTION_OUTPUTS),
            platform =
                PlatformAttentionFacts(
                    hasAudioOutput = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT),
                    hasVibrator = vibrator?.hasVibrator() == true,
                ),
        )
    }
}
