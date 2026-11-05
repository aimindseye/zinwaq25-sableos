package org.sableos.titan2.compass.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The compass's arithmetic, kept free of Android so it can be tested on the JVM.
 *
 * Rotation matrices are 3 × 3, row-major, device to world, as SensorManager.getRotationMatrixFromVector gives
 * them: world x points east, y north, z up; column 0 is the device's right edge, column 1 its top edge and
 * column 2 the way the screen faces.
 */
object Heading {
    const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0
    private const val QUARTER_TURN = 90.0
    private const val POINTS = 8
    private const val POINT_DEG = FULL_TURN / POINTS

    // Matrix cells: top edge east/north, screen normal east/north/up.
    private const val TOP_EAST = 1
    private const val TOP_NORTH = 4
    private const val SCREEN_EAST = 2
    private const val SCREEN_NORTH = 5
    private const val SCREEN_UP = 8
    private const val MATRIX_SIZE = 9

    /** Below this the facing direction has no usable horizontal part (the phone is edge-on to the ground). */
    private const val MIN_HORIZONTAL = 0.2

    /** The thinnest the dial is drawn when the phone stands upright, as a share of its height. */
    const val MIN_SQUASH = 0.15

    /** The eight directions, from north clockwise: string keys for the words under the heading. */
    val WORDS = listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")

    /**
     * The direction the user faces, in degrees clockwise from north, or null when it cannot be told.
     *
     * Lying flat, that is where the phone's top edge points; held upright, where its back looks. Adding the two
     * (top edge plus the reverse of the screen normal) gives one direction that turns smoothly from one to the
     * other as the phone is tilted, with no jump at 45°.
     */
    fun facing(rotation: FloatArray): Double? {
        require(rotation.size >= MATRIX_SIZE) { "a 3 × 3 rotation matrix" }
        val east = rotation[TOP_EAST] - rotation[SCREEN_EAST].toDouble()
        val north = rotation[TOP_NORTH] - rotation[SCREEN_NORTH].toDouble()
        if (hypot(east, north) < MIN_HORIZONTAL) return null
        return normalize(Math.toDegrees(atan2(east, north)))
    }

    /** How far the screen is tilted from lying face up: 0 flat, 90 upright, 180 face down. */
    fun tilt(rotation: FloatArray): Double =
        Math.toDegrees(acos(rotation[SCREEN_UP].toDouble().coerceIn(-1.0, 1.0)))

    /** How tall the dial is drawn against its width, so it leans with the phone like a disc seen at an angle. */
    fun squash(tilt: Double): Double {
        val lean = if (tilt > QUARTER_TURN) HALF_TURN - tilt else tilt
        return cos(Math.toRadians(lean)).coerceIn(MIN_SQUASH, 1.0)
    }

    fun normalize(degrees: Double): Double = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN

    /** The shortest turn from [from] to [to], -180 up to 180. */
    fun turn(from: Double, to: Double): Double {
        val d = normalize(to - from)
        return if (d > HALF_TURN) d - FULL_TURN else d
    }

    /**
     * Low-pass smoothing that goes the short way round, so 359° to 1° moves 2°, not 358°. [share] is how much
     * of the gap is closed per reading, 0 to 1.
     */
    fun smooth(previous: Double?, next: Double, share: Double): Double = if (previous ==
        null
    ) {
        normalize(next)
    } else {
        normalize(previous + turn(previous, next) * share)
    }

    /** True north: magnetic heading plus the declination (east of true north is positive). */
    fun toTrue(magnetic: Double, declination: Double): Double = normalize(magnetic + declination)

    /** Whole degrees as shown: 0 to 359. */
    fun degrees(heading: Double): Int = heading.roundToInt() % FULL_TURN.toInt()

    /** The key in [WORDS] for a heading. */
    fun word(heading: Double): String =
        WORDS[((normalize(heading) + POINT_DEG / 2) / POINT_DEG).toInt() % POINTS]

    /** Whether two headings differ by more than [degrees], the short way round (used to skip redraws). */
    fun moved(a: Double, b: Double, degrees: Double): Boolean = abs(turn(a, b)) > degrees
}

/** SensorManager's magnetometer accuracy, with the line the screen shows for it. */
enum class Accuracy(val key: String) {
    UNRELIABLE("accuracy_unreliable"),
    LOW("accuracy_low"),
    MEDIUM("accuracy_medium"),
    HIGH("accuracy_high");

    /** Below medium the heading may be off by tens of degrees: the number turns to the warning colour. */
    val needsCalibration: Boolean get() = this < MEDIUM

    companion object {
        /** SENSOR_STATUS_* is 0 unreliable to 3 high; anything else is treated as unreliable. */
        fun of(status: Int): Accuracy = entries.getOrElse(status) { UNRELIABLE }
    }
}

/** The figure 8 the calibration screen traces: a lemniscate inside -1..1 on both axes. */
object Figure8 {
    private const val HEIGHT = 0.5

    /** Where the dot is at [phase], 0 to 1 for one full figure 8, as (x, y). */
    fun point(phase: Double): Pair<Double, Double> {
        val a = phase * 2 * Math.PI
        val s = sin(a)
        val c = cos(a)
        val d = 1 + s * s
        return c / d to HEIGHT * 2 * s * c / d
    }
}
