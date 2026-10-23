package org.sableos.titan2.displaycompat.core

/** The display settings as the platform reports them now. Null size or density: not forced. */
data class CurrentDisplay(
    val forcedSize: SizePx?,
    val forcedDensity: Int?,
    val autoRotate: Boolean,
    val userRotation: Int,
    val ignoreOrientationRequest: Boolean
)

/**
 * The user's own values, saved when Display Compat first changed each setting and kept until it is
 * given back. Persisted before any change, so a crash or a reboot can still restore them. A field is
 * null while that setting is not held; [Held.value] null means "was not forced".
 */
data class HeldState(
    val size: Held<SizePx>? = null,
    val density: Held<Int>? = null,
    val rotation: Held<RotationTarget>? = null,
    val ignoreOrientationRequest: Held<Boolean>? = null
) {
    data class Held<T>(val value: T?)

    val isEmpty: Boolean get() = this == NONE

    companion object {
        val NONE = HeldState()
    }
}

sealed interface DisplayStep {
    data class SetSize(val size: SizePx) : DisplayStep
    data object ClearSize : DisplayStep
    data class SetDensity(val density: Int) : DisplayStep
    data object ClearDensity : DisplayStep
    data class SetRotation(val rotation: RotationTarget) : DisplayStep
    data class SetIgnoreOrientationRequest(val ignore: Boolean) : DisplayStep
}

/**
 * Steps to reach a target. [heldDuring] is persisted before the steps run (it keeps every original
 * that is still needed while they run); [heldAfter] once they all succeeded.
 */
data class DisplayPlan(
    val steps: List<DisplayStep>,
    val heldDuring: HeldState,
    val heldAfter: HeldState
)

object Reconciler {
    fun plan(target: DisplayTarget, held: HeldState, now: CurrentDisplay): DisplayPlan {
        val steps = mutableListOf<DisplayStep>()
        val size = Dim(target.size, held.size, now.forcedSize) { v ->
            v?.let { DisplayStep.SetSize(it) } ?: DisplayStep.ClearSize
        }.resolve(steps)
        val density = Dim(target.density, held.density, now.forcedDensity) { v ->
            v?.let { DisplayStep.SetDensity(it) } ?: DisplayStep.ClearDensity
        }.resolve(steps)
        val nowRotation = RotationTarget(now.autoRotate, now.userRotation)
        // With auto-rotate on, the stored user rotation does not matter: keep the current one.
        val wantedRotation = target.rotation?.let {
            if (it.autoRotate) it.copy(userRotation = now.userRotation) else it
        }
        val rotation = Dim(wantedRotation, held.rotation, nowRotation) { v ->
            v?.let { DisplayStep.SetRotation(it) }
        }.resolve(steps)
        val ignore = Dim(
            target.ignoreOrientationRequest,
            held.ignoreOrientationRequest,
            now.ignoreOrientationRequest
        ) { v -> v?.let { DisplayStep.SetIgnoreOrientationRequest(it) } }.resolve(steps)
        val after = HeldState(size, density, rotation, ignore)
        val during = HeldState(
            held.size ?: size,
            held.density ?: density,
            held.rotation ?: rotation,
            held.ignoreOrientationRequest ?: ignore
        )
        return DisplayPlan(steps, during, after)
    }

    /** Everything held goes back to the user's value; nothing new is taken. */
    fun restoreAll(held: HeldState, now: CurrentDisplay): DisplayPlan =
        plan(DisplayTarget.NONE, held, now)

    /**
     * One setting; [step] makes the step that sets a value (null: clears it, when that exists).
     * Wanted: hold it (saving the original once) and set it if it differs. Not wanted: if held,
     * put the original back (a "not forced" original clears it), then let go.
     */
    private class Dim<T : Any>(
        val wanted: T?,
        val held: HeldState.Held<T>?,
        val current: T?,
        val step: (T?) -> DisplayStep?
    ) {
        fun resolve(steps: MutableList<DisplayStep>): HeldState.Held<T>? {
            if (wanted != null) {
                if (current != wanted) step(wanted)?.let { steps += it }
                return held ?: HeldState.Held(current)
            }
            if (held != null && current != held.value) step(held.value)?.let { steps += it }
            return null
        }
    }
}

/** Compact codec for [HeldState]: `s=720x720|-;d=280|-;r=1:0;i=0`. Unknown or bad fields are dropped. */
object HeldCodec {
    private const val UNFORCED = "-"

    fun encode(h: HeldState): String = buildList {
        h.size?.let { add("s=" + (it.value?.let { s -> "${s.w}x${s.h}" } ?: UNFORCED)) }
        h.density?.let { add("d=" + (it.value?.toString() ?: UNFORCED)) }
        h.rotation?.value?.let { add("r=" + (if (it.autoRotate) 1 else 0) + ":" + it.userRotation) }
        h.ignoreOrientationRequest?.value?.let { add("i=" + if (it) 1 else 0) }
    }.joinToString(";")

    fun decode(raw: String?): HeldState {
        if (raw.isNullOrBlank()) return HeldState.NONE
        val m = raw.split(';').mapNotNull { kv ->
            kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap()
        return HeldState(
            size = m["s"]?.let { v -> held(v) { parseSize(it) } },
            density = m["d"]?.let { v -> held(v) { it.toIntOrNull()?.takeIf { d -> d > 0 } } },
            rotation = m["r"]?.let(::parseRotation)?.let { HeldState.Held(it) },
            ignoreOrientationRequest = when (m["i"]) {
                "1" -> HeldState.Held(true)
                "0" -> HeldState.Held(false)
                else -> null
            }
        )
    }

    /** "-" is a held "not forced"; an unparsable value drops the field. */
    private fun <T : Any> held(v: String, parse: (String) -> T?): HeldState.Held<T>? =
        if (v == UNFORCED) HeldState.Held(null) else parse(v)?.let { HeldState.Held(it) }

    private fun parseSize(v: String): SizePx? {
        val p = v.split('x').map { it.toIntOrNull() ?: 0 }
        return if (p.size == 2 && p[0] > 0 && p[1] > 0) SizePx(p[0], p[1]) else null
    }

    private fun parseRotation(v: String): RotationTarget? {
        val p = v.split(':')
        val auto = p.getOrNull(0)
        val rot =
            p.getOrNull(1)?.toIntOrNull()?.takeIf {
                it in
                    Rotations.ROTATION_0..Rotations.ROTATION_270
            }
                ?: return null
        return when (auto) {
            "1" -> RotationTarget(true, rot)
            "0" -> RotationTarget(false, rot)
            else -> null
        }
    }
}
