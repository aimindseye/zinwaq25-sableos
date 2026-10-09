package org.sableos.titan2.keyboard.core

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Compact keyboard-first status strip (D7B). Pure Kotlin so every rule is JVM-testable. It shows
 * only state the IME already has: the active language, Shift/Alt/Sym/Nav state, the composer's
 * existing candidates, the existing Sym+letter accent cycle, and symbols already present in the
 * layout tables. It owns no new input path, no stored text and no network. The interaction
 * pattern is a behaviour reference only; no Pastiera or Plektra source is used.
 */
object SoftKeyboardGeometry {
    const val ROW_HEIGHT_DP = 52
    const val MIN_ROWS = 4
    const val PAD_V_DP = 6

    /** Lower bound of the full on-screen keyboard height, before the navigation bar. */
    fun minHeightDp(): Int = MIN_ROWS * ROW_HEIGHT_DP + 2 * PAD_V_DP
}

object StripGeometry {
    /** Touch target of a candidate/symbol chip. */
    const val CHIP_HEIGHT_DP = 40

    /** State indicators are not tap targets, so they are smaller. */
    const val INDICATOR_HEIGHT_DP = 32
    const val PAD_V_DP = 4
    const val PAD_H_DP = 4

    /** One row: chips plus vertical padding. */
    const val HEIGHT_DP = CHIP_HEIGHT_DP + 2 * PAD_V_DP

    /**
     * Horizontal distance a rounded display corner of [radiusPx] cuts into a row whose lowest
     * content sits [heightAboveEdgePx] above the screen edge. Zero when the content is above the
     * corner or the display is square. Derived from the circle, never from a device constant.
     */
    fun cornerInset(radiusPx: Int, heightAboveEdgePx: Int): Int {
        if (radiusPx <= 0 || heightAboveEdgePx >= radiusPx) return 0
        val rise = (radiusPx - heightAboveEdgePx.coerceAtLeast(0)).toDouble()
        val r = radiusPx.toDouble()
        return ceil(r - sqrt(r * r - rise * rise)).toInt()
    }

    data class Padding(val left: Int, val right: Int, val bottom: Int)

    /** Live window insets in pixels. Zero for anything the display does not report. */
    data class Insets(
        val cutoutLeft: Int = 0,
        val cutoutRight: Int = 0,
        val cornerLeftRadius: Int = 0,
        val cornerRightRadius: Int = 0,
        val navBottom: Int = 0
    )

    /**
     * Content padding that keeps the strip inside the display cutout, the rounded corners and
     * above the navigation bar. All inputs come from the live window insets.
     */
    fun contentPadding(basePx: Int, i: Insets): Padding {
        val bottom = i.navBottom.coerceAtLeast(0)
        return Padding(
            left = basePx + maxOf(i.cutoutLeft, cornerInset(i.cornerLeftRadius, bottom)),
            right = basePx + maxOf(i.cutoutRight, cornerInset(i.cornerRightRadius, bottom)),
            bottom = bottom
        )
    }
}

enum class StripMode {
    /** Nothing to show. */
    Hidden,

    /** Full on-screen keyboard is up: keep the old behaviour, candidates only. */
    CandidatesOnly,

    /** Full on-screen keyboard hidden: language, modifier state and one content area. */
    Compact
}

enum class ChipState { Off, Once, Locked, Held, Active, Suspended }

data class ModChip(val id: String, val label: String, val state: ChipState)

/** The Sym+letter accent cycle as the resolver reports it, with the time of the last press. */
data class VariationInfo(val base: Char, val options: List<String>, val index: Int, val atMs: Long)

data class StripInputs(
    val field: InputClass,
    val softKeyboardShown: Boolean,
    val enabled: Boolean,
    val language: Language,
    val shift: ModState,
    val alt: ModState,
    val sym: ModState,
    val navOn: Boolean,
    val navEffective: Boolean,
    val candidates: List<Cand>,
    val variation: VariationInfo?,
    val layout: KeyLayout,
    val nowMs: Long
)

sealed interface StripContent {
    data object None : StripContent
    data class Candidates(val items: List<Cand>) : StripContent
    data class Variation(val base: Char, val options: List<String>, val selected: Int) :
        StripContent
    data class Symbols(val source: String, val items: List<String>) : StripContent
}

data class StripModel(
    val mode: StripMode,
    val languageLabel: String,
    val languageDescription: String,
    val mods: List<ModChip>,
    val content: StripContent
) {
    val visible: Boolean get() = mode != StripMode.Hidden
}

object StripPolicy {
    const val VARIATION_TTL_MS = 3000L
    const val MAX_CANDIDATES = 8
    const val MAX_SYMBOLS = 12
}

object CompactStrip {
    private val HIDDEN = StripModel(StripMode.Hidden, "", "", emptyList(), StripContent.None)

    /** Languages' short chip text: "EN", "ES", "HI·ph" for a phonetic layout. Derived from the tag. */
    fun languageChip(l: Language): String = l.tag.substringBefore('_').uppercase() +
        if (l.layout.isNotEmpty()) "·" + l.layout.take(2) else ""

    fun mode(i: StripInputs): StripMode = when {
        i.field == InputClass.None -> StripMode.Hidden

        i.softKeyboardShown || !i.enabled ->
            if (i.candidates.isEmpty()) StripMode.Hidden else StripMode.CandidatesOnly

        else -> StripMode.Compact
    }

    fun build(i: StripInputs): StripModel {
        val mode = mode(i)
        if (mode == StripMode.Hidden) return HIDDEN
        val content = if (mode == StripMode.Compact) compactContent(i) else candidatesOf(i)
        return StripModel(
            mode,
            languageChip(i.language),
            i.language.label,
            if (mode == StripMode.Compact) mods(i) else emptyList(),
            content
        )
    }

    private fun candidatesOf(i: StripInputs): StripContent =
        i.candidates.take(StripPolicy.MAX_CANDIDATES).let {
            if (it.isEmpty()) StripContent.None else StripContent.Candidates(it)
        }

    /**
     * Candidates, variation and symbols can reveal or inject typed text, so they appear only in
     * ordinary text fields. Secret and numeric fields get language and modifier state only.
     */
    private fun compactContent(i: StripInputs): StripContent {
        val cands = candidatesOf(i)
        val v = i.variation
        return when {
            i.field != InputClass.Text -> StripContent.None

            cands != StripContent.None -> cands

            v != null && liveVariation(v, i.nowMs) ->
                StripContent.Variation(v.base, v.options, v.index.coerceIn(0, v.options.lastIndex))

            else -> symbols(i)
        }
    }

    private fun liveVariation(v: VariationInfo, nowMs: Long): Boolean =
        v.options.size > 1 && nowMs - v.atMs in 0 until StripPolicy.VARIATION_TTL_MS

    private fun symbols(i: StripInputs): StripContent {
        val (source, table) = when {
            i.sym != ModState.Off && i.layout.sym.isNotEmpty() -> "Sym" to i.layout.sym
            i.alt != ModState.Off && i.layout.alt.isNotEmpty() -> "Alt" to i.layout.alt
            else -> return StripContent.None
        }
        val items = table.values.distinct().take(StripPolicy.MAX_SYMBOLS)
        return if (items.isEmpty()) StripContent.None else StripContent.Symbols(source, items)
    }

    private fun mods(i: StripInputs): List<ModChip> = listOf(
        ModChip("shift", "Shift", chip(i.shift)),
        ModChip("alt", "Alt", chip(i.alt)),
        ModChip("sym", "Sym", chip(i.sym)),
        ModChip("nav", "Nav", navChip(i))
    )

    private fun chip(s: ModState): ChipState = when (s) {
        ModState.Off -> ChipState.Off
        ModState.OneShot -> ChipState.Once
        ModState.Lock -> ChipState.Locked
        ModState.Held -> ChipState.Held
    }

    private fun navChip(i: StripInputs): ChipState = when {
        i.navEffective -> ChipState.Active
        i.navOn -> ChipState.Suspended
        else -> ChipState.Off
    }
}
