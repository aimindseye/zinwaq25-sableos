package org.sableos.reference.weathercities

import org.sableos.reference.typetofind.KeyInput
import org.sableos.reference.typetofind.KeyKind

enum class CityScreen { List, Add, ConfirmRemove, ConfirmReset }

enum class DialogButton { Cancel, Confirm }

object CityIds {
    const val ADD = "add_city"
    const val RESET = "reset_defaults"
    const val QUERY = "query"
    private const val CITY_PREFIX = "city:"
    private const val REMOVE_SUFFIX = ":remove"
    private const val RESULT_PREFIX = "result:"

    fun row(name: String) = CITY_PREFIX + name

    fun remove(name: String) = CITY_PREFIX + name + REMOVE_SUFFIX

    fun result(index: Int) = RESULT_PREFIX + index

    fun isRemove(id: String) = id.startsWith(CITY_PREFIX) && id.endsWith(REMOVE_SUFFIX)

    fun isRow(id: String) = id.startsWith(CITY_PREFIX) && !id.endsWith(REMOVE_SUFFIX)

    fun cityName(id: String) = id.removePrefix(CITY_PREFIX).removeSuffix(REMOVE_SUFFIX)
}

data class CityScreenState(
    val screen: CityScreen = CityScreen.List,
    /** List focus, by id. */
    val focusId: String? = null,
    val focusIndex: Int = 0,
    val query: String = "",
    /** -1 is the search field; 0.. are results. */
    val resultFocus: Int = -1,
    val pendingRemove: String? = null,
    val dialog: DialogButton = DialogButton.Cancel,
    val softKeyboard: Boolean = false,
    val focusVisible: Boolean = false
)

sealed interface CityEffect {
    object None : CityEffect

    data class Select(val name: String) : CityEffect

    data class AddResult(val index: Int) : CityEffect

    data class Remove(val name: String) : CityEffect

    object Reset : CityEffect

    /** The search text changed; the host runs its own search and calls the model again with the results. */
    data class Search(val text: String) : CityEffect

    object Exit : CityEffect

    data class FocusLeaves(val forward: Boolean) : CityEffect
}

data class CityReduction(
    val state: CityScreenState,
    val effect: CityEffect = CityEffect.None,
    val consumed: Boolean = true
)

/**
 * Keyboard behaviour of the manual city screen, independent of who draws it. A printable key opens the add-city
 * search with that first character kept; the soft keyboard is requested only by touching the search field.
 */
class CityScreenModel(private val cities: List<City>, private val resultCount: Int = 0) {
    private val ids: List<String> =
        cities.flatMap { listOf(CityIds.row(it.name)) } + CityIds.ADD + CityIds.RESET

    fun initial(selected: City): CityScreenState = at(CityScreenState(), CityIds.row(selected.name))

    fun focusIndex(s: CityScreenState): Int {
        val byId = s.focusId?.let { ids.indexOf(rowOf(it)) } ?: -1
        return if (byId >= 0) byId else s.focusIndex.coerceIn(0, ids.lastIndex)
    }

    /** The focused item: a row, its remove action, Add or Reset. */
    fun focusedId(s: CityScreenState): String {
        val id = s.focusId
        val remove = id != null && CityIds.isRemove(id) && ids.indexOf(rowOf(id)) >= 0
        return if (remove) id.orEmpty() else ids[focusIndex(s)]
    }

    private fun rowOf(id: String) =
        if (CityIds.isRemove(id)) CityIds.row(CityIds.cityName(id)) else id

    fun reduce(s: CityScreenState, k: KeyInput): CityReduction {
        val shown = s.copy(focusVisible = true)
        return when {
            k.hasCommandModifier -> CityReduction(s, consumed = false)
            k.kind in UNHANDLED -> CityReduction(s, consumed = false)
            k.editableFocused && k.kind in TEXT_KINDS -> CityReduction(s, consumed = false)
            s.screen == CityScreen.List -> list(shown, k)
            s.screen == CityScreen.Add -> add(shown, k)
            else -> dialog(shown, k)
        }
    }

    fun onTouch(s: CityScreenState) = s.copy(focusVisible = false)

    fun onSearchFieldTapped(s: CityScreenState) = s.copy(softKeyboard = true, focusVisible = false)

    /** The host edited the search text (typing, paste, clear). */
    fun onQueryChanged(s: CityScreenState, text: String): CityReduction =
        CityReduction(s.copy(query = text, resultFocus = -1), CityEffect.Search(text))

    /** After an add, remove, select or reset: back to the list with focus on [focusName] or the nearest row. */
    fun afterChange(s: CityScreenState, focusName: String?): CityScreenState {
        val base = s.copy(
            screen = CityScreen.List,
            query = "",
            resultFocus = -1,
            pendingRemove = null,
            softKeyboard = false
        )
        val id = focusName?.let { CityIds.row(it) }
        return if (id != null && id in ids) at(base, id) else base.copy(focusId = null)
    }

    private fun at(s: CityScreenState, id: String): CityScreenState {
        val i = ids.indexOf(rowOf(id)).coerceAtLeast(0)
        return s.copy(focusId = if (CityIds.isRemove(id)) id else ids[i], focusIndex = i)
    }

    private fun move(s: CityScreenState, delta: Int) =
        at(s, ids[(focusIndex(s) + delta).coerceIn(0, ids.lastIndex)])

    private fun list(s: CityScreenState, k: KeyInput): CityReduction {
        val id = focusedId(s)
        return when (k.kind) {
            KeyKind.Up -> CityReduction(move(s, -1))
            KeyKind.Down -> CityReduction(move(s, 1))
            KeyKind.MoveHome -> CityReduction(at(s, ids.first()))
            KeyKind.MoveEnd -> CityReduction(at(s, ids.last()))
            KeyKind.Tab -> tab(s, !k.shift)
            KeyKind.Right -> CityReduction(toRemove(s, id))
            KeyKind.Left -> CityReduction(toRow(s, id))
            KeyKind.Activate, KeyKind.Space -> if (k.repeat) CityReduction(s) else activate(s, id)
            KeyKind.Backspace -> removeRow(s, id, k)
            KeyKind.Back, KeyKind.Escape -> CityReduction(s, CityEffect.Exit)
            KeyKind.Printable -> if (k.repeat) CityReduction(s) else openAdd(s, k.char.toString())
            else -> CityReduction(s, consumed = false)
        }
    }

    private fun toRemove(s: CityScreenState, id: String): CityScreenState =
        if (CityIds.isRow(id)) at(s, CityIds.remove(CityIds.cityName(id))) else s

    private fun toRow(s: CityScreenState, id: String): CityScreenState =
        if (CityIds.isRemove(id)) at(s, CityIds.row(CityIds.cityName(id))) else s

    private fun removeRow(s: CityScreenState, id: String, k: KeyInput): CityReduction {
        val allowed = CityIds.isRow(id) && !k.repeat
        return if (allowed) askRemove(s, id) else CityReduction(s)
    }

    private fun tab(s: CityScreenState, forward: Boolean): CityReduction {
        val next = focusIndex(s) + if (forward) 1 else -1
        return if (next in
            ids.indices
        ) {
            CityReduction(at(s, ids[next]))
        } else {
            CityReduction(s, CityEffect.FocusLeaves(forward))
        }
    }

    private fun activate(s: CityScreenState, id: String): CityReduction = when {
        id == CityIds.ADD -> openAdd(s, "")

        id == CityIds.RESET -> CityReduction(
            s.copy(screen = CityScreen.ConfirmReset, dialog = DialogButton.Cancel)
        )

        CityIds.isRemove(id) -> askRemove(s, id)

        else -> CityReduction(s, CityEffect.Select(CityIds.cityName(id)))
    }

    private fun askRemove(s: CityScreenState, id: String) = CityReduction(
        s.copy(
            screen = CityScreen.ConfirmRemove,
            pendingRemove = CityIds.cityName(id),
            dialog = DialogButton.Cancel
        )
    )

    private fun openAdd(s: CityScreenState, first: String): CityReduction {
        val opened = s.copy(screen = CityScreen.Add, query = first, resultFocus = -1)
        return CityReduction(
            opened,
            if (first.isEmpty()) CityEffect.None else CityEffect.Search(first)
        )
    }

    private fun add(s: CityScreenState, k: KeyInput): CityReduction {
        val onField = s.resultFocus < 0
        val textKey = k.kind in TEXT_KINDS
        return when {
            onField && textKey -> CityReduction(s, consumed = false)

            k.kind == KeyKind.Down -> CityReduction(
                s.copy(resultFocus = (s.resultFocus + 1).coerceAtMost(resultCount - 1))
            )

            k.kind == KeyKind.Up -> CityReduction(
                s.copy(resultFocus = (s.resultFocus - 1).coerceAtLeast(-1))
            )

            k.kind == KeyKind.Activate || k.kind == KeyKind.Space -> chooseResult(s, k)

            k.kind == KeyKind.MoveHome -> CityReduction(resultEdge(s, first = true))

            k.kind == KeyKind.MoveEnd -> CityReduction(resultEdge(s, first = false))

            k.kind == KeyKind.Tab -> CityReduction(s, CityEffect.FocusLeaves(!k.shift))

            k.kind == KeyKind.Back || k.kind == KeyKind.Escape -> CityReduction(close(s))

            else -> CityReduction(s, consumed = false)
        }
    }

    /** MoveHome/MoveEnd on the results (never on the search field, which keeps them). */
    private fun resultEdge(s: CityScreenState, first: Boolean): CityScreenState {
        val index = if (first) 0 else resultCount - 1
        return s.copy(resultFocus = if (resultCount > 0) index else -1)
    }

    private fun chooseResult(s: CityScreenState, k: KeyInput): CityReduction {
        val index = if (s.resultFocus >= 0) s.resultFocus else 0
        val none = resultCount == 0 || k.repeat
        return if (none) CityReduction(s) else CityReduction(s, CityEffect.AddResult(index))
    }

    private fun close(s: CityScreenState) = afterChange(s, s.focusId?.let { CityIds.cityName(it) })

    private fun dialog(s: CityScreenState, k: KeyInput): CityReduction {
        val toggle =
            k.kind in setOf(KeyKind.Left, KeyKind.Right, KeyKind.Up, KeyKind.Down, KeyKind.Tab)
        return when {
            toggle -> CityReduction(s.copy(dialog = other(s.dialog)))
            k.kind == KeyKind.Back || k.kind == KeyKind.Escape -> CityReduction(cancel(s))
            k.kind == KeyKind.Activate || k.kind == KeyKind.Space -> confirm(s, k)
            else -> CityReduction(s)
        }
    }

    private fun other(b: DialogButton) = if (b ==
        DialogButton.Cancel
    ) {
        DialogButton.Confirm
    } else {
        DialogButton.Cancel
    }

    private fun cancel(s: CityScreenState) =
        s.copy(screen = CityScreen.List, pendingRemove = null, dialog = DialogButton.Cancel)

    private fun confirm(s: CityScreenState, k: KeyInput): CityReduction {
        if (k.repeat) return CityReduction(s)
        if (s.dialog == DialogButton.Cancel) return CityReduction(cancel(s))
        val effect = s.pendingRemove?.let { CityEffect.Remove(it) } ?: CityEffect.Reset
        return CityReduction(s.copy(screen = CityScreen.List, dialog = DialogButton.Cancel), effect)
    }

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

        /** System HOME (Other) and PageUp/PageDown have no city behaviour: they stay unhandled, state unchanged. */
        val UNHANDLED = setOf(KeyKind.Other, KeyKind.PageUp, KeyKind.PageDown)
    }
}
