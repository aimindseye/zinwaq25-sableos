package org.sableos.tools.core

/** One tick on the on-screen ruler: where it sits in pixels and how long it is drawn. */
data class RulerTick(val px: Float, val size: RulerTick.Size, val label: String?) {
    enum class Size { SHORT, MEDIUM, LONG }
}

/**
 * Tick marks for Sable Tools' ruler, from the panel's physical density (pixels per inch along
 * the ruler). Pure Kotlin. Metric: a tick every millimetre, a medium one every 5 mm and a long,
 * numbered one every centimetre. Imperial: a tick every 1/8 inch, medium every 1/2, long and
 * numbered every inch.
 */
object RulerScale {
    private const val MM_PER_INCH = 25.4f
    private const val MM_PER_CM = 10
    private const val MM_PER_HALF_CM = 5
    private const val EIGHTHS = 8
    private const val HALF = 4

    fun metric(lengthPx: Float, pxPerInch: Float): List<RulerTick> {
        if (lengthPx <= 0f || pxPerInch <= 0f) return emptyList()
        val pxPerMm = pxPerInch / MM_PER_INCH
        val count = (lengthPx / pxPerMm).toInt()
        return (0..count).map { mm ->
            when {
                mm % MM_PER_CM == 0 ->
                    RulerTick(mm * pxPerMm, RulerTick.Size.LONG, (mm / MM_PER_CM).toString())
                mm % MM_PER_HALF_CM == 0 -> RulerTick(mm * pxPerMm, RulerTick.Size.MEDIUM, null)
                else -> RulerTick(mm * pxPerMm, RulerTick.Size.SHORT, null)
            }
        }
    }

    fun imperial(lengthPx: Float, pxPerInch: Float): List<RulerTick> {
        if (lengthPx <= 0f || pxPerInch <= 0f) return emptyList()
        val pxPerEighth = pxPerInch / EIGHTHS
        val count = (lengthPx / pxPerEighth).toInt()
        return (0..count).map { e ->
            when {
                e % EIGHTHS == 0 ->
                    RulerTick(e * pxPerEighth, RulerTick.Size.LONG, (e / EIGHTHS).toString())
                e % HALF == 0 -> RulerTick(e * pxPerEighth, RulerTick.Size.MEDIUM, null)
                else -> RulerTick(e * pxPerEighth, RulerTick.Size.SHORT, null)
            }
        }
    }
}
