package org.sableos.tools.core

import kotlin.math.roundToInt

/**
 * Daily step totals built from samples of the cumulative step counter (steps since boot). Each sample adds what
 * the counter gained since the previous one to the day it was taken; a lower value means the counter restarted at
 * a reboot, so the new value is all gain. Counting starts at the first sample. Kept on the phone only.
 */
class StepLedger private constructor(
    private var lastRaw: Long?,
    private val days: MutableMap<Long, Long>
) {
    constructor() : this(null, mutableMapOf())

    fun sample(raw: Long, day: Long) {
        val previous = lastRaw
        lastRaw = raw
        if (previous == null) return
        val gained = if (raw >= previous) raw - previous else raw
        if (gained > 0) days[day] = (days[day] ?: 0L) + gained
        days.keys.removeAll { it <= day - KEEP_DAYS }
    }

    fun steps(day: Long): Long = days[day] ?: 0L

    /** The last [count] days ending at [today], newest first, days without steps included. */
    fun recent(today: Long, count: Int): List<Pair<Long, Long>> =
        (0 until count).map { today - it }.map { it to steps(it) }

    fun encode(): String = (lastRaw?.toString() ?: "") + "|" +
        days.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        const val KEEP_DAYS = 30L

        /** Reads what [encode] wrote; anything damaged is dropped, never thrown. */
        fun decode(text: String?): StepLedger {
            val head = text.orEmpty().substringBefore('|')
            val tail = text.orEmpty().substringAfter('|', "")
            val days = mutableMapOf<Long, Long>()
            tail.split(',').forEach { part ->
                val day = part.substringBefore(':').toLongOrNull()
                val count = part.substringAfter(':', "").toLongOrNull()
                if (day != null && count != null && count > 0) days[day] = count
            }
            return StepLedger(head.toLongOrNull()?.takeIf { it >= 0 }, days)
        }
    }
}

/** The numbers the distance and energy estimates use. Always shown as estimates. */
data class StepProfile(
    val heightCm: Int = DEFAULT_HEIGHT,
    val weightKg: Int = DEFAULT_WEIGHT,
    val goal: Int = DEFAULT_GOAL
) {
    val safe: StepProfile
        get() = StepProfile(
            heightCm.coerceIn(HEIGHT_RANGE),
            weightKg.coerceIn(WEIGHT_RANGE),
            goal.coerceIn(GOAL_RANGE)
        )

    companion object {
        const val DEFAULT_HEIGHT = 170
        const val DEFAULT_WEIGHT = 70
        const val DEFAULT_GOAL = 8000
        val HEIGHT_RANGE = 100..250
        val WEIGHT_RANGE = 30..250
        val GOAL_RANGE = 1000..100_000
    }
}

object StepMath {
    private const val STRIDE_PER_HEIGHT = 0.415
    private const val KCAL_PER_KG_KM = 0.5
    private const val CM_PER_KM = 100_000.0

    fun distanceKm(steps: Long, heightCm: Int): Double =
        steps * (heightCm * STRIDE_PER_HEIGHT) / CM_PER_KM

    fun kcal(distanceKm: Double, weightKg: Int): Int =
        (distanceKm * weightKg * KCAL_PER_KG_KM).roundToInt()
}
