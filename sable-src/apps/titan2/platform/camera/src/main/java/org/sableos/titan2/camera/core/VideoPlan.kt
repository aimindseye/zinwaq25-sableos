package org.sableos.titan2.camera.core

private const val UHD_SHORT_SIDE = 2160
private const val MIN_SHORT_SIDE = 480
private const val HD_LONG_SIDE = 1920
private const val ASPECT_TOLERANCE = 0.02
private const val WIDE_ASPECT = 16.0 / 9
private const val STANDARD_ASPECT = 4.0 / 3
private const val BITS_PER_PIXEL = 0.1
private const val MIN_BITRATE = 4_000_000L
private const val MAX_BITRATE = 50_000_000L
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60

enum class AspectClass(val label: String) {
    Wide("16:9"),
    Standard("4:3"),
    Square("1:1"),
    Other("")
}

data class VideoOption(val size: Size, val aspect: AspectClass) {
    /**
     * "1080p 16:9 (1920x1080)" style label; the short side names the tier like the stock
     * "Video quality" list.
     */
    val label: String get() {
        val short = minOf(size.w, size.h)
        val tier = if (short >= UHD_SHORT_SIDE) "4K" else "${short}p"
        return "$tier${if (aspect.label.isNotEmpty()) " ${aspect.label}" else ""} ($size)"
    }
}

object VideoPlanner {
    fun aspectOf(s: Size): AspectClass {
        val r = maxOf(s.w, s.h).toDouble() / minOf(s.w, s.h)
        fun near(x: Double) = kotlin.math.abs(r - x) < ASPECT_TOLERANCE
        return when {
            near(WIDE_ASPECT) -> AspectClass.Wide
            near(STANDARD_ASPECT) -> AspectClass.Standard
            near(1.0) -> AspectClass.Square
            else -> AspectClass.Other
        }
    }

    /**
     * Recordable sizes worth offering: known aspects only (odd vendor sizes are skipped), 480p
     * and up, capped on the long side, largest first, one entry per size.
     */
    fun options(sizes: List<Size>, maxLongSide: Int = 3840): List<VideoOption> =
        sizes.asSequence().distinct()
            .filter { minOf(it.w, it.h) >= MIN_SHORT_SIDE && maxOf(it.w, it.h) <= maxLongSide }
            .map { VideoOption(it, aspectOf(it)) }
            .filter { it.aspect != AspectClass.Other }
            .sortedWith(
                compareByDescending<VideoOption> {
                    it.size.pixels
                }.thenBy { it.aspect.ordinal }
            )
            .toList()

    /**
     * Default: the 1080-class 16:9 size if reported, else the largest 4:3 at or below 1920 long
     * side, else the largest offered.
     */
    fun default(options: List<VideoOption>): VideoOption? = options.firstOrNull {
        it.aspect == AspectClass.Wide &&
            maxOf(it.size.w, it.size.h) == HD_LONG_SIDE
    }
        ?: options.filter {
            it.aspect == AspectClass.Standard &&
                maxOf(it.size.w, it.size.h) <= HD_LONG_SIDE
        }.maxByOrNull { it.size.pixels }
        ?: options.firstOrNull()

    /** Quality cycling used by the key binding: wraps around the offered list. */
    fun next(options: List<VideoOption>, cur: VideoOption?, dir: Int): VideoOption? {
        if (options.isEmpty()) return null
        val i = options.indexOf(cur).let { if (it < 0) 0 else it }
        return options[(i + dir + options.size) % options.size]
    }

    /**
     * Rough H.264 target: ~0.1 bit per pixel per frame at 30 fps, bounded so small sizes stay
     * sharp and 4K stays recordable.
     */
    fun bitrate(size: Size, fps: Int = 30): Int =
        (size.pixels * fps * BITS_PER_PIXEL).toLong().coerceIn(MIN_BITRATE, MAX_BITRATE).toInt()

    /** SBV_<date>_<time>_cam<id>_<tier>.mp4 (SBL_ is the still prefix). */
    fun fileName(
        cameraId: String,
        size: Size,
        yyyymmdd: String,
        hhmmss: String,
        seq: Int = 0
    ): String {
        val short = minOf(size.w, size.h)
        val tier = if (short >= UHD_SHORT_SIDE) "4k" else "${short}p"
        return "SBV_${yyyymmdd}_${hhmmss}_cam${cameraId}_$tier${if (seq > 0) "_$seq" else ""}.mp4"
    }
}

/**
 * The recorder's lifecycle as a pure machine so the keys (Space/V/P) can never start twice or
 * pause a stopped recording.
 */
enum class RecState { Idle, Recording, Paused }
enum class RecEvent { ToggleRecord, TogglePause, Failed }

object RecStateMachine {
    fun reduce(s: RecState, e: RecEvent): RecState = when (e) {
        RecEvent.ToggleRecord -> if (s == RecState.Idle) RecState.Recording else RecState.Idle

        RecEvent.TogglePause -> when (s) {
            RecState.Recording -> RecState.Paused
            RecState.Paused -> RecState.Recording
            RecState.Idle -> RecState.Idle
        }

        RecEvent.Failed -> RecState.Idle
    }
    fun clock(ms: Long): String {
        val t = (ms / MS_PER_SECOND).coerceAtLeast(0)
        val h = t / SECONDS_PER_HOUR
        val m = (t % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val sec = t % SECONDS_PER_MINUTE
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }
}
