package org.sableos.reference.setupflow

import org.sableos.reference.typetofind.KeyInput
import org.sableos.reference.typetofind.KeyKind

data class FocusRef(val id: String?, val index: Int)

data class FlowState(
    val focus: Map<String, FocusRef> = emptyMap(),
    /** True after a key, false after touch. */
    val focusVisible: Boolean = false
)

sealed interface FlowEffect {
    object None : FlowEffect

    data class Activate(val controlId: String) : FlowEffect

    /** Activate (Enter) in the last text field: run the step's primary action. */
    data class Submit(val controlId: String) : FlowEffect

    object GoBack : FlowEffect

    /** Focus leaves the step in this direction; the host moves it, so nothing is trapped. */
    data class FocusLeaves(val forward: Boolean) : FlowEffect
}

data class FlowReduction(
    val state: FlowState,
    val effect: FlowEffect = FlowEffect.None,
    val consumed: Boolean = true
)

/**
 * Keyboard behaviour of one setup step, independent of who draws it. It consumes the same normalized
 * [KeyInput] as the Sable Start model and never inspects a device.
 */
class FlowKeyModel(private val step: Step) {
    private val order: List<Control> get() = step.controls.filter { it.enabled }

    /** A step with a text field starts there so typing works at once; otherwise the primary action, else the first control. */
    fun initialFocusId(): String? {
        val field = order.firstOrNull { it.kind == ControlKind.TextField }
        if (field != null) return field.id
        return (order.firstOrNull { it.id == step.primaryId } ?: order.firstOrNull())?.id
    }

    fun focusIndex(s: FlowState): Int {
        val list = order
        if (list.isEmpty()) return -1
        val ref =
            s.focus[step.id]
                ?: return list.indexOfFirst { it.id == initialFocusId() }.coerceAtLeast(0)
        val byId = ref.id?.let { id -> list.indexOfFirst { it.id == id } } ?: -1
        return if (byId >= 0) byId else ref.index.coerceIn(0, list.lastIndex)
    }

    fun focused(s: FlowState): Control? = order.getOrNull(focusIndex(s))

    fun reduce(s: FlowState, k: KeyInput): FlowReduction {
        val control = focused(s)
        val textFocused = control?.kind == ControlKind.TextField || k.editableFocused
        return when {
            k.hasCommandModifier -> FlowReduction(s, consumed = false)
            k.kind in UNHANDLED -> FlowReduction(s, consumed = false)
            textFocused && k.kind in TEXT_KINDS -> FlowReduction(s, consumed = false)
            k.repeat && k.kind in ONE_SHOT -> FlowReduction(s)
            else -> handle(s.copy(focusVisible = true), k, control)
        }
    }

    private fun handle(s: FlowState, k: KeyInput, c: Control?): FlowReduction = when (k.kind) {
        KeyKind.Up -> FlowReduction(moveTo(s, focusIndex(s) - 1))

        KeyKind.Down -> FlowReduction(moveTo(s, focusIndex(s) + 1))

        KeyKind.Tab -> tab(s, forward = !k.shift)

        KeyKind.MoveHome -> FlowReduction(moveTo(s, 0))

        KeyKind.MoveEnd -> FlowReduction(moveTo(s, order.lastIndex))

        KeyKind.Activate -> enter(s, c)

        KeyKind.Space -> c?.let { FlowReduction(s, FlowEffect.Activate(it.id)) }
            ?: FlowReduction(s)

        KeyKind.Back, KeyKind.Escape ->
            if (step.canGoBack) {
                FlowReduction(
                    s,
                    FlowEffect.GoBack
                )
            } else {
                FlowReduction(s, consumed = false)
            }

        else -> FlowReduction(s, consumed = false)
    }

    private fun enter(s: FlowState, c: Control?): FlowReduction {
        if (c == null) return FlowReduction(s)
        if (c.kind != ControlKind.TextField) return FlowReduction(s, FlowEffect.Activate(c.id))
        val list = order
        val nextText = list.drop(focusIndex(s) + 1).firstOrNull { it.kind == ControlKind.TextField }
        return when {
            nextText != null -> FlowReduction(moveTo(s, list.indexOf(nextText)))

            step.primaryId != null && list.any { it.id == step.primaryId } ->
                FlowReduction(s, FlowEffect.Submit(step.primaryId))

            else -> FlowReduction(s)
        }
    }

    private fun tab(s: FlowState, forward: Boolean): FlowReduction {
        val next = focusIndex(s) + if (forward) 1 else -1
        return if (next in
            order.indices
        ) {
            FlowReduction(moveTo(s, next))
        } else {
            FlowReduction(s, FlowEffect.FocusLeaves(forward))
        }
    }

    private fun moveTo(s: FlowState, index: Int): FlowState {
        val list = order
        if (list.isEmpty()) return s
        val i = index.coerceIn(0, list.lastIndex)
        return s.copy(focus = s.focus + (step.id to FocusRef(list[i].id, i)))
    }

    fun onTouch(s: FlowState): FlowState = s.copy(focusVisible = false)

    /** Rotation, a dialog, or coming back from another screen: focus is stored by id and re-resolved. */
    fun restore(s: FlowState): FlowState = s

    private companion object {
        val TEXT_KINDS =
            setOf(
                KeyKind.Printable,
                KeyKind.Space,
                KeyKind.Backspace,
                KeyKind.Left,
                KeyKind.Right,
                KeyKind.MoveHome,
                KeyKind.MoveEnd,
                KeyKind.PageUp,
                KeyKind.PageDown
            )

        /** System HOME (Other) and PageUp/PageDown have no Setup behaviour: they stay unhandled with the state unchanged. */
        val UNHANDLED = setOf(KeyKind.Other, KeyKind.PageUp, KeyKind.PageDown)
        val ONE_SHOT =
            setOf(
                KeyKind.Activate,
                KeyKind.Space,
                KeyKind.Back,
                KeyKind.Escape,
                KeyKind.Tab
            )
    }
}
