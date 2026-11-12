package org.sableos.titan2.displaycompat.core

import org.sableos.titan2.displaycompat.core.DisplayCompatText.t

/*
 * SableOS 2.0 App Compatibility (owner, 2026-11-12): each app gets a layout (Square, Tall phone or
 * Custom), an app size and a landscape video choice, and some apps start from a Sable preset. This
 * is the front end's model on top of the 1.0 engine: the choices are fields of the same
 * [DisplayProfile], kept in the same per-app store. Pure Kotlin, unit-tested on the JVM.
 */

/**
 * App size: the display density while this app is in front, as a share of the person's own density
 * (280 on the Q25). A lower value fits more on screen, like a lower `wm density` for that app only.
 */
enum class AppSize(val percent: Int) {
    Full(HUNDRED),
    Smaller(EIGHTY_FIVE),
    Smallest(SEVENTY_FIVE);

    val id: String get() = percent.toString()
    val label: String get() = "$percent%"

    /** The density this size forces on [base], or null for [Full] (nothing forced). */
    fun density(base: Int): Int? = if (this == Full ||
        base <= 0
    ) {
        null
    } else {
        (base * percent + HALF) / HUNDRED
    }

    companion object {
        fun fromId(id: String?): AppSize = entries.firstOrNull { it.id == id } ?: Full
    }
}

private const val HUNDRED = 100
private const val EIGHTY_FIVE = 85
private const val SEVENTY_FIVE = 75
private const val HALF = 50

private const val COL_PACKAGE = 0
private const val COL_LAYOUT = 1
private const val COL_VIDEO = 2
private const val COL_SIZE = 3

/** Landscape video: Fit drops the tall layout while the app plays sideways, so the video fills the width. */
enum class LandscapeVideo(private val labelKey: String, private val helpKey: String) {
    Fit("video_fit", "video_fit_help"),
    KeepLayout("video_keep_layout", "video_keep_layout_help");

    val label: String get() = t(labelKey)
    val help: String get() = t(helpKey)

    companion object {
        fun of(p: DisplayProfile): LandscapeVideo =
            if (p.fullscreenMediaException) Fit else KeepLayout
    }
}

/** The three layouts the App Compatibility page offers. Custom is any other 1.0 profile. */
enum class AppLayout(private val labelKey: String, private val helpKey: String) {
    Square("layout_square", "layout_square_help"),
    TallPhone("layout_tall_phone", "layout_tall_phone_help"),
    Custom("layout_custom", "layout_custom_help");

    val label: String get() = t(labelKey)
    val help: String get() = t(helpKey)

    companion object {
        /** The canvas Tall phone uses: 720x1440 on the Q25, a phone's 1:2. */
        val TALL = TallCanvas.Tall1x2

        fun of(p: DisplayProfile): AppLayout {
            val shape = p.copy(appSize = AppSize.Full, fullscreenMediaException = true)
            return when (shape) {
                DisplayProfile.NATIVE -> Square
                DisplayProfile.NATIVE.copy(canvas = TALL) -> TallPhone
                else -> Custom
            }
        }

        /**
         * [p] with [layout]: Square and Tall phone replace the shape and keep the app size and the
         * video choice; Custom keeps [p] as it is (the full editor changes it).
         */
        fun apply(p: DisplayProfile, layout: AppLayout): DisplayProfile = when (layout) {
            Square -> DisplayProfile.NATIVE.copy(
                appSize = p.appSize,
                fullscreenMediaException = p.fullscreenMediaException
            )

            TallPhone -> apply(p, Square).copy(canvas = TALL)

            Custom -> p
        }
    }
}

/**
 * Out-of-box presets, read from a small tab-separated data file:
 * `package<TAB>layout<TAB>video<TAB>size`, where layout is `square` or `tall`, video is `fit` or
 * `keep` (`-` for Fit), size is 100, 85 or 75 (missing for 100). Blank lines and `#` comments are
 * skipped, and so is a line that is not understood, so a bad line never breaks the others.
 */
object Presets {
    fun parse(text: String): Map<String, DisplayProfile> {
        val out = LinkedHashMap<String, DisplayProfile>()
        text.lineSequence().map {
            it.substringBefore('#').trim()
        }.filter { it.isNotEmpty() }.forEach { line ->
            parseLine(line)?.let { (pkg, p) -> out[pkg] = p }
        }
        return out
    }

    private val LAYOUTS = mapOf("square" to AppLayout.Square, "tall" to AppLayout.TallPhone)
    private val VIDEOS = mapOf("" to true, "-" to true, "fit" to true, "keep" to false)

    /** One line, or null when any column is not understood. */
    private fun parseLine(line: String): Pair<String, DisplayProfile>? {
        val f = line.split('\t').map { it.trim() }
        val pkg = f.getOrNull(COL_PACKAGE)?.takeIf { PackageNames.isValid(it) }
        val layout = LAYOUTS[f.getOrNull(COL_LAYOUT)]
        val video = VIDEOS[f.getOrNull(COL_VIDEO).orEmpty()]
        val sizeId = f.getOrNull(COL_SIZE).orEmpty().ifEmpty { AppSize.Full.id }
        val size = AppSize.entries.firstOrNull { it.id == sizeId }
        val known = listOf(pkg, layout, video, size).all { it != null }
        if (!known) return null
        val base = DisplayProfile.NATIVE.copy(appSize = size!!, fullscreenMediaException = video!!)
        return pkg!! to AppLayout.apply(base, layout!!)
    }

    /**
     * The installed apps that get their preset now: they have one, it was never given before, and
     * the person has not set the app up already. A preset is given once, so a later change stays.
     */
    fun toSeed(
        installed: Collection<String>,
        presets: Map<String, DisplayProfile>,
        seeded: Set<String>,
        stored: Set<String>
    ): List<String> = installed.filter { it in presets && it !in seeded && it !in stored }

    /** True when the app's profile is still its preset (the list shows "Sable preset"). */
    fun isPreset(
        pkg: String,
        current: DisplayProfile,
        presets: Map<String, DisplayProfile>
    ): Boolean = presets[pkg] == current
}

/** The row text of the App Compatibility list. */
object AppCompatText {
    fun layout(p: DisplayProfile): String = AppLayout.of(p).label

    fun size(p: DisplayProfile): String = p.appSize.label

    fun video(p: DisplayProfile): String = LandscapeVideo.of(p).label

    fun source(pkg: String, p: DisplayProfile, presets: Map<String, DisplayProfile>): String =
        when {
            Presets.isPreset(pkg, p, presets) -> t("sable_preset")
            p.isNative -> t("works_as_is")
            else -> t("set_by_you")
        }
}

/** The one-app page's rows and what Left/Right do on them. */
object AppPage {
    enum class Row { Layout, Size, Video, Reset }

    private val quick = listOf(AppLayout.Square, AppLayout.TallPhone)

    /** Left/Right on Layout move between Square and Tall phone; from Custom they leave it. */
    fun layoutStep(p: DisplayProfile, dir: Int): DisplayProfile {
        val i = quick.indexOf(AppLayout.of(p))
        val next =
            if (i < 0) {
                if (dir > 0) AppLayout.TallPhone else AppLayout.Square
            } else {
                quick[(i + dir).coerceIn(0, quick.size - 1)]
            }
        return AppLayout.apply(p, next)
    }

    /** Right makes the app smaller (100, 85, 75), Left bigger; both stop at the ends. */
    fun sizeStep(p: DisplayProfile, dir: Int): DisplayProfile {
        val all = AppSize.entries
        return p.copy(appSize = all[(all.indexOf(p.appSize) + dir).coerceIn(0, all.size - 1)])
    }

    fun videoStep(p: DisplayProfile): DisplayProfile =
        p.copy(fullscreenMediaException = !p.fullscreenMediaException)

    /** The changed profile for a Left/Right on [row], or null when the row has no Left/Right. */
    fun step(p: DisplayProfile, row: Row, dir: Int): DisplayProfile? = when (row) {
        Row.Layout -> layoutStep(p, dir)
        Row.Size -> sizeStep(p, dir)
        Row.Video -> videoStep(p)
        Row.Reset -> null
    }?.takeIf { it != p }
}
