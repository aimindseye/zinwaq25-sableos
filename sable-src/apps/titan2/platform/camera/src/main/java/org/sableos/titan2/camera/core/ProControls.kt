package org.sableos.titan2.camera.core

/** The five stock Pro-mode fields (WB, ISO, EV, S, F), in the order the stock strip shows them. */
enum class ProField(val label: String) { Wb("WB"), Iso("ISO"), Ev("EV"), Shutter("S"), Focus("F") }

private const val AWB_MODE_AUTO = 1
private const val AWB_MODE_INCANDESCENT = 2
private const val AWB_MODE_FLUORESCENT = 3
private const val AWB_MODE_DAYLIGHT = 5
private const val AWB_MODE_CLOUDY_DAYLIGHT = 6

/** Raw CONTROL_AWB_MODE_* ints are kept here so the core stays JVM-testable. */
enum class WbPreset(val label: String, val awbMode: Int) {
    Auto("Auto", AWB_MODE_AUTO),
    Tungsten("Tungsten", AWB_MODE_INCANDESCENT),
    Fluorescent("Fluor", AWB_MODE_FLUORESCENT),
    Daylight("Daylight", AWB_MODE_DAYLIGHT),
    Cloudy("Cloudy", AWB_MODE_CLOUDY_DAYLIGHT)
}

/** Limits read from one camera. `null`/empty means "this control is not offered", never "assume a default". */
data class ProLimits(
    val iso: IntBounds?,
    val exposureNs: LongBounds?,
    val evSteps: IntBounds?,
    val evStep: Float,
    val minFocusDiopters: Float,
    val awbModes: Set<Int>
) {
    companion object {
        fun of(c: CameraInfo) = ProLimits(
            c.isoRange,
            c.exposureNsRange,
            c.aeCompensationSteps,
            c.aeCompensationStep,
            c.minFocusDistance,
            c.awbModes
        )
    }
    fun offers(f: ProField): Boolean = when (f) {
        ProField.Wb -> true

        // Auto is always available
        ProField.Iso -> iso != null && exposureNs != null

        // manual exposure needs both ends
        ProField.Shutter -> iso != null && exposureNs != null

        ProField.Ev -> evSteps != null && evSteps.hi > evSteps.lo

        ProField.Focus -> minFocusDiopters > 0f
    }
}

/**
 * Last auto-exposure/auto-focus values reported by the preview, used so the first manual step
 * starts near what the camera was doing.
 */
data class AutoSeed(
    val iso: Int? = null,
    val exposureNs: Long? = null,
    val focusDiopters: Float? = null
)

/** null = Auto. */
data class ProState(
    val wb: WbPreset = WbPreset.Auto,
    val iso: Int? = null,
    val shutterNs: Long? = null,
    val evSteps: Int = 0,
    val focusDiopters: Float? = null
) {
    val manualExposure: Boolean get() = iso != null || shutterNs != null
}

/** What the controller must apply. Pure data: the Android layer maps it onto CaptureRequest keys. */
data class ProRequest(
    val aeOn: Boolean,
    val evSteps: Int,
    val iso: Int,
    val exposureNs: Long,
    val frameDurationNs: Long,
    val awbMode: Int,
    val afOn: Boolean,
    val focusDiopters: Float
)

object ProControls {
    private val ISO_STOPS = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400, 12800)
    private const val NS = 1_000_000_000L

    /** Shutter denominators 8000..2 then whole seconds, as photographers expect them. */
    private val SHUTTER_STOPS_NS: List<Long> =
        listOf(8000, 4000, 2000, 1000, 500, 250, 125, 60, 30, 15, 8, 4, 2).map { NS / it } +
            listOf(1L, 2L, 4L, 8L).map { it * NS }

    // fraction of min-focus diopters; 0 = infinity
    private val FOCUS_FRACTIONS = listOf(0f, 0.1f, 0.2f, 0.35f, 0.5f, 0.7f, 1f)
    private const val MIN_FRAME_NS = 33_333_333L
    private const val DEFAULT_ISO = 100
    private const val DEFAULT_EXPOSURE_NS = NS / 60
    private const val INFINITY_DIOPTERS = 0.0001f

    fun isoLadder(r: IntBounds): List<Int> {
        val inRange = ISO_STOPS.filter { it in r.lo..r.hi }
        return if (inRange.isEmpty()) listOf(r.lo, r.hi).distinct().sorted() else inRange
    }

    fun shutterLadder(r: LongBounds): List<Long> {
        val inRange = SHUTTER_STOPS_NS.filter { it in r.lo..r.hi }
        return if (inRange.isEmpty()) listOf(r.lo, r.hi).distinct().sorted() else inRange
    }

    fun focusLadder(minFocus: Float): List<Float> =
        if (minFocus <= 0f) emptyList() else FOCUS_FRACTIONS.map { it * minFocus }

    fun wbChoices(l: ProLimits): List<WbPreset> = WbPreset.entries.filter {
        it == WbPreset.Auto ||
            it.awbMode in l.awbModes
    }

    private fun <T : Comparable<T>> nearestIndex(
        ladder: List<T>,
        v: T?,
        dist: (T, T) -> Double
    ): Int = if (v == null) ladder.size / 2 else ladder.indices.minBy { dist(ladder[it], v) }

    /**
     * Move one field one step (dir = +1 / -1). From Auto, the first step starts at the ladder
     * value nearest the camera's own choice.
     */
    fun adjust(
        s: ProState,
        f: ProField,
        dir: Int,
        l: ProLimits,
        seed: AutoSeed = AutoSeed()
    ): ProState {
        if (!l.offers(f)) return s
        val d = if (dir >= 0) 1 else -1
        return when (f) {
            ProField.Wb -> {
                val c = wbChoices(l)
                val i = c.indexOf(s.wb).coerceAtLeast(0)
                s.copy(wb = c[(i + d + c.size) % c.size])
            }

            ProField.Iso -> {
                val lad = isoLadder(l.iso!!)
                val i =
                    s.iso?.let { cur ->
                        nearestIndex(lad, cur) { a, b -> kotlin.math.abs(a - b).toDouble() }
                    }
                        ?: nearestIndex(lad, seed.iso) { a, b -> kotlin.math.abs(a - b).toDouble() }
                s.copy(iso = lad[(i + d).coerceIn(0, lad.lastIndex)])
            }

            ProField.Shutter -> {
                val lad = shutterLadder(l.exposureNs!!)
                val i = (s.shutterNs ?: seed.exposureNs).let { cur ->
                    nearestIndex(lad, cur) { a, b ->
                        kotlin.math.abs(kotlin.math.ln(a.toDouble() / b))
                    }
                }
                s.copy(shutterNs = lad[(i + d).coerceIn(0, lad.lastIndex)])
            }

            ProField.Ev -> s.copy(evSteps = (s.evSteps + d).coerceIn(l.evSteps!!.lo, l.evSteps.hi))

            ProField.Focus -> {
                val lad = focusLadder(l.minFocusDiopters)
                val cur = s.focusDiopters ?: seed.focusDiopters
                val i = if (cur == null) 0 else lad.indices.minBy { kotlin.math.abs(lad[it] - cur) }
                s.copy(focusDiopters = lad[(i + d).coerceIn(0, lad.lastIndex)])
            }
        }
    }

    fun auto(s: ProState, f: ProField): ProState = when (f) {
        ProField.Wb -> s.copy(wb = WbPreset.Auto)
        ProField.Iso -> s.copy(iso = null)
        ProField.Shutter -> s.copy(shutterNs = null)
        ProField.Ev -> s.copy(evSteps = 0)
        ProField.Focus -> s.copy(focusDiopters = null)
    }

    /**
     * Camera2 cannot do shutter- or ISO-priority, so either one manual turns auto-exposure off and
     * the other is filled from the last auto value (or a sane default). EV only applies while
     * auto-exposure is on.
     */
    fun toRequest(s: ProState, l: ProLimits, seed: AutoSeed = AutoSeed()): ProRequest {
        val manual = s.manualExposure && l.iso != null && l.exposureNs != null
        val iso = (s.iso ?: seed.iso ?: DEFAULT_ISO)
            .coerceIn(l.iso?.lo ?: DEFAULT_ISO, l.iso?.hi ?: DEFAULT_ISO)
        val ns = (s.shutterNs ?: seed.exposureNs ?: DEFAULT_EXPOSURE_NS).coerceIn(
            l.exposureNs?.lo ?: DEFAULT_EXPOSURE_NS,
            l.exposureNs?.hi ?: DEFAULT_EXPOSURE_NS
        )
        val awb = effectiveAwb(s.wb, l)
        val focusManual = s.focusDiopters != null && l.minFocusDiopters > 0f
        return ProRequest(
            aeOn = !manual,
            evSteps = if (manual) 0 else s.evSteps,
            iso = iso,
            exposureNs = ns,
            frameDurationNs = maxOf(ns, MIN_FRAME_NS),
            awbMode = awb,
            afOn = !focusManual,
            focusDiopters = (s.focusDiopters ?: 0f).coerceIn(
                0f,
                l.minFocusDiopters.coerceAtLeast(0f)
            )
        )
    }

    private fun effectiveAwb(wb: WbPreset, l: ProLimits): Int =
        if (wb.awbMode in l.awbModes || wb == WbPreset.Auto) {
            wb.awbMode
        } else {
            WbPreset.Auto.awbMode
        }

    fun shutterLabel(ns: Long): String = when {
        ns >= NS -> "${trim(ns.toDouble() / NS)}\""
        else -> "1/${(NS.toDouble() / ns).let { Math.round(it) }}"
    }
    private fun trim(d: Double) = if (d ==
        Math.floor(d)
    ) {
        d.toLong().toString()
    } else {
        "%.1f".format(java.util.Locale.US, d)
    }

    fun focusLabel(diopters: Float): String = if (diopters <=
        INFINITY_DIOPTERS
    ) {
        "∞"
    } else {
        "%.2f m".format(java.util.Locale.US, 1f / diopters)
    }

    /** Text for one HUD cell: "Auto" or the manual value. */
    fun valueLabel(s: ProState, f: ProField, l: ProLimits): String = when (f) {
        ProField.Wb -> s.wb.label
        ProField.Iso -> s.iso?.toString() ?: "Auto"
        ProField.Shutter -> s.shutterNs?.let { shutterLabel(it) } ?: "Auto"
        ProField.Ev -> if (s.manualExposure) "–" else EvLabels.of(s.evSteps, l.evStep)
        ProField.Focus -> s.focusDiopters?.let { focusLabel(it) } ?: "Auto"
    }
}

/** Exposure-compensation text shared by the Pro HUD and the transient EV strip. */
object EvLabels {
    fun of(steps: Int, stepSize: Float): String {
        val v = steps * (if (stepSize > 0f) stepSize else 1f)
        return if (steps == 0) "0" else "%+.1f".format(java.util.Locale.US, v)
    }
}
