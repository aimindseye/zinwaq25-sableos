package org.sableos.tools.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Android sensor vector in device coordinates (x right, y up the screen, z out of the screen). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    val norm: Double get() = sqrt(x * x + y * y + z * z)
}

private fun deg(rad: Double) = Math.toDegrees(rad)

/** Normalises any angle to [0, 360). */
private const val FULL_TURN = 360.0
private const val HALF_TURN = 180.0

fun normDeg(a: Double): Double = ((a % FULL_TURN) + FULL_TURN) % FULL_TURN

/** Normalises any angle to (-180, 180]. */
fun signedDeg(a: Double): Double {
    val n = normDeg(a)
    return if (n > HALF_TURN) n - FULL_TURN else n
}

/** Tilt readings from the accelerometer/gravity vector. Pure trigonometry, testable without a device. */
object Tilt {
    /** Phone lying on a surface: tilt around each axis, 0/0 when flat. */
    data class Surface(val xDeg: Double, val yDeg: Double, val totalDeg: Double)

    fun surface(g: Vec3): Surface {
        val n = g.norm
        if (n == 0.0) return Surface(0.0, 0.0, 0.0)
        return Surface(
            xDeg = deg(atan2(g.x, sqrt(g.y * g.y + g.z * g.z))),
            yDeg = deg(atan2(g.y, sqrt(g.x * g.x + g.z * g.z))),
            totalDeg = deg(acos((g.z / n).coerceIn(-1.0, 1.0)))
        )
    }

    /**
     * Phone standing on an edge (picture hanging, protractor, plumb bob): rotation within the screen plane, 0 when
     * the phone is upright, positive when rotated clockwise as seen from the front.
     */
    fun screenRotation(g: Vec3): Double = deg(atan2(g.x, g.y))

    /** Elevation of the phone's top edge above the horizon (aiming along the top edge), in degrees. */
    fun topEdgeElevation(g: Vec3): Double {
        val n = g.norm
        return if (n == 0.0) 0.0 else deg(asin((g.y / n).coerceIn(-1.0, 1.0)))
    }

    const val LEVEL_TOLERANCE_DEG = 0.5

    fun isLevel(angleDeg: Double, tolerance: Double = LEVEL_TOLERANCE_DEG) =
        abs(angleDeg) <= tolerance

    /** Rounds to 0.1 degree for display; finer digits would be false precision for a phone accelerometer. */
    fun display(angleDeg: Double): String = "%.1f°".format(angleDeg)
}

/**
 * User zero-point for a tilt tool (C = calibrate on a known level surface). Stored locally by the app; it is a
 * measurement offset, not a device setting.
 */
data class TiltCalibration(
    val xDeg: Double = 0.0,
    val yDeg: Double = 0.0,
    val rotationDeg: Double = 0.0
) {
    fun apply(s: Tilt.Surface) = s.copy(xDeg = s.xDeg - xDeg, yDeg = s.yDeg - yDeg)

    fun applyRotation(r: Double) = signedDeg(r - rotationDeg)

    fun encode(): String = "$xDeg,$yDeg,$rotationDeg"

    companion object {
        private const val FIELDS = 3

        fun from(s: Tilt.Surface, rotationDeg: Double) =
            TiltCalibration(s.xDeg, s.yDeg, rotationDeg)

        fun decode(s: String?): TiltCalibration? {
            val p = s?.split(',')?.mapNotNull { it.toDoubleOrNull() } ?: return null
            return if (p.size == FIELDS) TiltCalibration(p[0], p[1], p[2]) else null
        }
    }
}

/** First-order low-pass filter for jittery sensors. */
class LowPass(private val alpha: Double = 0.15) {
    private var last: Vec3? = null

    fun push(v: Vec3): Vec3 {
        val l = last
        val out = if (l ==
            null
        ) {
            v
        } else {
            Vec3(
                l.x + alpha * (v.x - l.x),
                l.y + alpha * (v.y - l.y),
                l.z + alpha * (v.z - l.z)
            )
        }
        last = out
        return out
    }

    fun reset() {
        last = null
    }
}

/** H = hold the current reading; the live value keeps updating underneath and returns on the next H. */
class HoldableReading<T> {
    var held: T? = null
        private set
    var live: T? = null
        private set

    val shown: T? get() = held ?: live
    val isHeld: Boolean get() = held != null

    fun update(v: T) {
        live = v
    }

    fun toggleHold() {
        held = if (held == null) live else null
    }

    fun reset() {
        held = null
        live = null
    }
}

object Compass {
    private const val POINT_DEG = 45.0
    private val POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /**
     * Azimuth of the phone's top edge from magnetic north, 0..360, computed like SensorManager.getRotationMatrix +
     * getOrientation. Null when the vectors are degenerate (free fall, magnetic field parallel to gravity).
     */
    fun azimuth(gravity: Vec3, geomagnetic: Vec3): Double? {
        val (ax, ay, az) = Triple(gravity.x, gravity.y, gravity.z)
        val (ex, ey, ez) = Triple(geomagnetic.x, geomagnetic.y, geomagnetic.z)
        var hx = ey * az - ez * ay
        var hy = ez * ax - ex * az
        var hz = ex * ay - ey * ax
        val normH = sqrt(hx * hx + hy * hy + hz * hz)
        if (normH < MIN_H) return null
        hx /= normH
        hy /= normH
        hz /= normH
        val invA = 1.0 / gravity.norm
        val nax = ax * invA
        val naz = az * invA
        val my = naz * hx - nax * hz
        return normDeg(deg(atan2(hy, my)))
    }

    private const val MIN_H = 0.1

    fun cardinal(azimuth: Double): String = POINTS[
        (((normDeg(azimuth) + POINT_DEG / 2) / POINT_DEG).toInt()) %
            POINTS.size
    ]

    /** SensorManager accuracy: 0 unreliable, 1 low, 2 medium, 3 high. Below medium the user should calibrate. */
    fun needsCalibration(accuracy: Int): Boolean = accuracy < ACCURACY_MEDIUM

    private const val ACCURACY_MEDIUM = 2

    fun display(azimuth: Double): String =
        "${normDeg(azimuth).roundToInt() % FULL_TURN.toInt()}° ${cardinal(azimuth)} (magnetic)"
}

/** Noise meter. Uncalibrated phone microphones only give an approximate level, and the screen says so. */
object Noise {
    private const val FULL_SCALE = 32768.0
    private const val DB_PER_DECADE = 20.0
    private const val MIN_DBFS = -96.0

    /** Approximate offset from dBFS to dB SPL for phone microphones; only a label-worthy estimate. */
    const val DEFAULT_SPL_OFFSET = 90.0

    fun rmsDbfs(pcm: ShortArray, count: Int = pcm.size): Double {
        if (count <= 0) return MIN_DBFS
        var sum = 0.0
        for (i in 0 until count) {
            val v = pcm[i].toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / count)
        return if (rms <=
            0.0
        ) {
            MIN_DBFS
        } else {
            (DB_PER_DECADE * log10(rms / FULL_SCALE)).coerceAtLeast(MIN_DBFS)
        }
    }

    fun approxDb(dbfs: Double, offset: Double = DEFAULT_SPL_OFFSET): Double =
        (dbfs + offset).coerceAtLeast(0.0)

    fun label(db: Double): String = when {
        db < QUIET -> "Quiet"
        db < MODERATE -> "Moderate"
        db < LOUD -> "Loud"
        db < VERY_LOUD -> "Very loud"
        else -> "Harmful with long exposure"
    }

    private const val QUIET = 40.0
    private const val MODERATE = 60.0
    private const val LOUD = 80.0
    private const val VERY_LOUD = 100.0
}

/** Speedometer trip, fed with location fixes. Local only; nothing is stored after the tool closes. */
class SpeedTrip(private val maxAccuracyM: Double = 50.0) {
    data class Fix(
        val lat: Double,
        val lon: Double,
        val timeMs: Long,
        val accuracyM: Double,
        val speedMps: Double?
    )

    var distanceM = 0.0
        private set
    var maxSpeedMps = 0.0
        private set
    var currentMps = 0.0
        private set
    private var first: Fix? = null
    private var last: Fix? = null

    /** Returns false when the fix is too inaccurate to use. */
    fun push(f: Fix): Boolean {
        if (f.accuracyM > maxAccuracyM) return false
        val l = last
        if (l != null && f.timeMs > l.timeMs) {
            val d = Geo.distanceM(l.lat, l.lon, f.lat, f.lon)
            distanceM += d
            currentMps = f.speedMps ?: (d / ((f.timeMs - l.timeMs) / MS_PER_S))
        } else if (f.speedMps != null) {
            currentMps = f.speedMps
        }
        maxSpeedMps = maxOf(maxSpeedMps, currentMps)
        if (first == null) first = f
        last = f
        return true
    }

    val elapsedMs: Long get() = (last?.timeMs ?: 0L) - (first?.timeMs ?: 0L)

    val averageMps: Double get() = if (elapsedMs > 0) distanceM / (elapsedMs / MS_PER_S) else 0.0

    fun reset() {
        distanceM = 0.0
        maxSpeedMps = 0.0
        currentMps = 0.0
        first = null
        last = null
    }

    companion object {
        private const val MS_PER_S = 1000.0
        const val KMH_PER_MPS = 3.6
        const val MPH_PER_MPS = 2.2369362920544

        fun kmh(mps: Double) = mps * KMH_PER_MPS

        fun mph(mps: Double) = mps * MPH_PER_MPS
    }
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Haversine great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = p2 - p1
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Pedometer from the cumulative TYPE_STEP_COUNTER value (counts since boot), with R setting a new baseline. */
class StepCounterSession {
    private var baseline: Long? = null
    private var carried = 0L
    private var lastRaw = 0L

    fun push(raw: Long): Long {
        if (baseline == null) baseline = raw
        if (raw < lastRaw) {
            // The counter restarted (reboot): keep what was counted so far and start again from zero.
            carried += lastRaw - (baseline ?: 0L)
            baseline = 0L
        }
        lastRaw = raw
        return steps
    }

    val steps: Long get() = carried + (lastRaw - (baseline ?: lastRaw)).coerceAtLeast(0L)

    fun reset() {
        baseline = lastRaw
        carried = 0L
    }
}

/**
 * Accelerometer fallback step detector (PEDOMETER_ACCELEROMETER_FALLBACK=YES): a step is a rise of the acceleration
 * magnitude above [high] after it was below [low], at least [minIntervalMs] after the previous step.
 * No health claims: it is an estimate.
 */
class AccelStepDetector(
    private val high: Double = 11.2,
    private val low: Double = 9.0,
    private val minIntervalMs: Long = 250
) {
    var steps = 0L
        private set
    private var armed = false
    private var lastStepMs = Long.MIN_VALUE / 2

    fun push(magnitude: Double, timeMs: Long): Boolean {
        if (magnitude < low) armed = true
        if (armed && magnitude > high && timeMs - lastStepMs >= minIntervalMs) {
            steps++
            armed = false
            lastStepMs = timeMs
            return true
        }
        return false
    }

    fun reset() {
        steps = 0
        armed = false
    }
}

/**
 * Height estimate by two sightings along the phone's top edge: down to the object's base, then up to its top, from
 * a known eye height. Always shown as an estimate with a range (HEIGHT_MEASURE_NO_FALSE_PRECISION=YES).
 */
object HeightEstimate {
    data class Result(
        val heightM: Double,
        val lowM: Double,
        val highM: Double,
        val distanceM: Double
    )

    const val ANGLE_ERROR_DEG = 1.0
    private const val MIN_BASE_DEG = 2.0
    private const val MAX_DEG = 85.0

    /**
     * [baseElevationDeg] is negative (looking down at the base), [topElevationDeg] usually positive.
     * Returns null when the geometry cannot give a usable estimate.
     */
    fun estimate(eyeHeightM: Double, baseElevationDeg: Double, topElevationDeg: Double): Result? {
        val h = if (eyeHeightM >
            0.0
        ) {
            compute(eyeHeightM, baseElevationDeg, topElevationDeg)
        } else {
            null
        }
        val candidates = listOf(-ANGLE_ERROR_DEG, ANGLE_ERROR_DEG).flatMap { db ->
            listOf(-ANGLE_ERROR_DEG, ANGLE_ERROR_DEG).mapNotNull { dt ->
                compute(eyeHeightM, baseElevationDeg + db, topElevationDeg + dt)?.first
            }
        }
        return if (h == null || candidates.isEmpty()) {
            null
        } else {
            Result(h.first, candidates.min(), candidates.max(), h.second)
        }
    }

    private fun compute(eye: Double, base: Double, top: Double): Pair<Double, Double>? {
        val depression = -base
        if (depression < MIN_BASE_DEG || depression > MAX_DEG || abs(top) > MAX_DEG) return null
        val d = eye / tan(Math.toRadians(depression))
        val height = eye + d * tan(Math.toRadians(top))
        return if (height > 0) height to d else null
    }

    fun display(r: Result): String =
        "about %.1f m (%.1f–%.1f m), %.1f m away".format(r.heightM, r.lowM, r.highM, r.distanceM)
}
