package org.sableos.tools.core

/**
 * A piece of hardware or platform support a tool needs. The Android layer probes these at runtime
 * (SensorManager, PackageManager features, ConsumerIrManager); the pure model never guesses them.
 */
enum class Hardware {
    ACCELEROMETER,
    MAGNETOMETER,
    GYROSCOPE,
    STEP_COUNTER,
    CAMERA_BACK,
    CAMERA_FLASH,
    MICROPHONE,
    LOCATION_GPS,
    IR_EMITTER,
    IR_RECEIVER,
    FM_TUNER,
    SUBSCREEN
}

/** What a runtime probe found for one [Hardware] fact. NOT_PROBED means the platform has no public way to ask. */
enum class Probe { PRESENT, ABSENT, NOT_PROBED }

/**
 * A capability that gates one or more tools. [needsAll] must all be present; when [needsAny] is not empty, at least
 * one of it must be present too (pedometer: a step counter, or an accelerometer fallback).
 */
enum class Capability(
    val needsAll: Set<Hardware>,
    val needsAny: Set<Hardware> = emptySet(),
    /** True when the tool can be calibrated (enables the local C command). */
    val calibratable: Boolean = false
) {
    COMPASS(setOf(Hardware.ACCELEROMETER, Hardware.MAGNETOMETER), calibratable = true),
    BUBBLE_LEVEL(setOf(Hardware.ACCELEROMETER), calibratable = true),
    PLUMB_BOB(setOf(Hardware.ACCELEROMETER), calibratable = true),
    PROTRACTOR(setOf(Hardware.ACCELEROMETER), calibratable = true),
    PICTURE_HANGING(setOf(Hardware.ACCELEROMETER), calibratable = true),
    FLASHLIGHT(setOf(Hardware.CAMERA_FLASH)),
    MAGNIFIER(setOf(Hardware.CAMERA_BACK)),
    HEIGHT_ESTIMATE(setOf(Hardware.ACCELEROMETER), calibratable = true),
    NOISE_METER(setOf(Hardware.MICROPHONE)),
    SPEEDOMETER(setOf(Hardware.LOCATION_GPS)),
    PEDOMETER(emptySet(), needsAny = setOf(Hardware.STEP_COUNTER, Hardware.ACCELEROMETER)),
    IR_REMOTE(setOf(Hardware.IR_EMITTER)),

    /** Android has no public IR receive API; this stays off unless a profile proves a platform-owned path. */
    IR_LEARNING(setOf(Hardware.IR_EMITTER, Hardware.IR_RECEIVER)),

    /** Diagnostic status only: FM playback is owned by Sable Media. */
    FM_RADIO(setOf(Hardware.FM_TUNER)),
    SUBSCREEN_COMPANION(setOf(Hardware.SUBSCREEN)),

    /** The vendor factory-test bridge behind the dialer code. Always engineering-gated. */
    FACTORY_TEST_BRIDGE(emptySet());

    /** ABSENT for any required fact (or for every alternative) means the device cannot back this capability. */
    fun runtime(probes: Map<Hardware, Probe>): Probe {
        val all = needsAll.map { probes[it] ?: Probe.NOT_PROBED }
        val any = needsAny.map { probes[it] ?: Probe.NOT_PROBED }
        return when {
            Probe.ABSENT in all -> Probe.ABSENT
            any.isNotEmpty() && any.all { it == Probe.ABSENT } -> Probe.ABSENT
            Probe.NOT_PROBED in all -> Probe.NOT_PROBED
            any.isNotEmpty() && Probe.PRESENT !in any -> Probe.NOT_PROBED
            else -> Probe.PRESENT
        }
    }
}

/** What a device profile declares for a capability (DESIGN-KF-C "Capability gating"). */
enum class Declared {
    /** No evidence. Hidden; listed only in developer capability status. */
    UNKNOWN,

    /** The device does not have it. Hidden everywhere except the diagnostic capability inventory. */
    UNSUPPORTED,

    /** Present but not proven for users: developer/service mode only (used while qualifying a device). */
    DIAGNOSTIC_ONLY,

    /** Proven for this profile. Visible when the runtime probe does not contradict it. */
    SUPPORTED
}

/** One profile entry: the declared state and where the evidence for it lives. */
data class Declaration(val state: Declared, val evidence: String)
