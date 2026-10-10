package org.sableos.hub.policy

import org.sableos.hub.ConnectedAppKey
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Sable Attention selection for one Android user/profile + package: which Sable-driven outputs
 * (keyboard backlight, secondary display) may signal its notifications. It is not delivery policy:
 * it never changes importance, and Android-owned outputs cannot be selected here.
 */
data class AttentionSelection(
    val key: ConnectedAppKey,
    val outputs: Set<AttentionOutput>,
) {
    init {
        require(outputs.all { it.owner == OutputOwner.Sable }) {
            "Only Sable-driven outputs have a Sable Attention selection"
        }
    }

    /** Outputs this profile actually supports; selections for hidden outputs are inert. */
    fun effective(profile: AttentionDeviceProfile): Set<AttentionOutput> {
        val supported = profile.selectableOutputs().toSet()
        return outputs.intersect(supported)
    }
}

object AttentionSelections {
    /** Explicit selection when the user made one, otherwise the conservative default. */
    fun effectiveFor(
        key: ConnectedAppKey,
        stored: Map<ConnectedAppKey, AttentionSelection>,
        profile: AttentionDeviceProfile,
    ): Set<AttentionOutput> = stored[key]?.effective(profile) ?: AttentionDefaults.defaultSelection(profile)
}

object AttentionSelectionCodec {
    private const val VERSION = "1"
    private const val FIELD_COUNT = 4
    private const val FIELD_VERSION = 0
    private const val FIELD_USER_SERIAL = 1
    private const val FIELD_PACKAGE = 2
    private const val FIELD_OUTPUTS = 3
    private const val SEPARATOR = "|"
    private const val OUTPUT_SEPARATOR = ","

    fun encode(selection: AttentionSelection): String =
        listOf(
            VERSION,
            selection.key.userSerial.toString(),
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(selection.key.packageName.toByteArray(StandardCharsets.UTF_8)),
            selection.outputs
                .map(AttentionOutput::token)
                .sorted()
                .joinToString(OUTPUT_SEPARATOR),
        ).joinToString(SEPARATOR)

    fun decode(encoded: String): AttentionSelection? =
        runCatching {
            val fields = encoded.split(SEPARATOR)
            require(fields.size == FIELD_COUNT && fields[FIELD_VERSION] == VERSION)
            val packageName = String(Base64.getUrlDecoder().decode(fields[FIELD_PACKAGE]), StandardCharsets.UTF_8)
            val outputs =
                fields[FIELD_OUTPUTS]
                    .split(OUTPUT_SEPARATOR)
                    .filter(String::isNotEmpty)
                    .map { requireNotNull(AttentionOutput.fromToken(it)) }
                    .toSet()
            AttentionSelection(ConnectedAppKey(packageName, fields[FIELD_USER_SERIAL].toLong()), outputs)
        }.getOrNull()
}
