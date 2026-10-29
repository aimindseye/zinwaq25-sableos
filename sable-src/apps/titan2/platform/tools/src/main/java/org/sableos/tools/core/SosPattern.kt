package org.sableos.tools.core

/**
 * The SOS signal as alternating on/off durations (three short, three long, three short, then a pause),
 * and the siren sound. Pure data: the SOS tool plays them with the camera light, the screen and the alarm stream.
 */
object SosPattern {
    const val UNIT_MS = 250L

    // On, off, on, off ...: S (. . .), O (- - -), S (. . .), then a long gap before it repeats.
    private val UNITS = listOf(1, 1, 1, 1, 1, 3, 3, 1, 3, 1, 3, 3, 1, 1, 1, 1, 1, 7)

    val STEPS: List<Long> = UNITS.map { it * UNIT_MS }

    /** Even steps are "on", odd steps are "off". */
    fun isOn(step: Int): Boolean = step % 2 == 0

    fun next(step: Int): Int = (step + 1) % STEPS.size
}

/** One loop of a rising and falling siren, 16-bit mono, meant to be repeated without a gap. */
object SirenWave {
    const val RATE = 22_050
    const val SECONDS = 2
    private const val LOW_HZ = 600.0
    private const val HIGH_HZ = 1400.0
    private const val LEVEL = 0.8
    private const val HALF = 0.5

    fun samples(): ShortArray {
        val n = RATE * SECONDS
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val tri = if (t < HALF) t / HALF else (1 - t) / HALF
            val hz = LOW_HZ + (HIGH_HZ - LOW_HZ) * tri
            phase += 2 * Math.PI * hz / RATE
            out[i] = (Math.sin(phase) * LEVEL * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
