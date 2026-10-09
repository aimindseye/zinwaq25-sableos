package org.sableos.reference.typetofind

/** A command offered next to apps in search (id only; the host decides what it does). */
data class CommandEntry(val id: String, val label: String)

/** One focusable row: an app or a command. [id] is stable across list changes so focus can be restored. */
data class StartItem(
    val id: String,
    val label: String,
    val app: AppEntry?,
    val command: CommandEntry?
)

/** The three semantic levels. Back/Escape always goes up exactly one. */
enum class Level { Root, AllApps, Search }

/** Where focus sits in one level: the stable id, plus the last index as a fallback if that id vanishes. */
data class Focus(val id: String?, val index: Int)

data class StartState(
    val level: Level = Level.Root,
    val query: String = "",
    /** The level Search was opened from; Back returns here. */
    val origin: Level = Level.Root,
    val focus: Map<Level, Focus> = emptyMap(),
    /** True after a key, false after touch, so touch users do not see a focus ring they did not ask for. */
    val focusVisible: Boolean = false,
    /** Only a deliberate touch on the search field may ask for the soft keyboard. */
    val softImeRequested: Boolean = false,
    val lastLaunchedId: String? = null
)

sealed interface Effect {
    object None : Effect

    data class Launch(val item: StartItem) : Effect

    /** Focus moves out of this surface in the given direction; the host moves it, nothing is trapped. */
    data class FocusLeaves(val forward: Boolean) : Effect
}

/** [consumed] false means the host must deliver the key to the focused editable or let the system have it. */
data class Reduction(
    val state: StartState,
    val effect: Effect = Effect.None,
    val consumed: Boolean = true
)

/**
 * Pure keyboard-first interaction model for Launcher3-hosted Sable Start (Root, All Apps, Search). It holds no
 * Android types, creates no HOME surface, and never inspects the device: it only sees normalized [KeyInput].
 */
class StartModel(
    private val root: List<AppEntry>,
    private val apps: List<AppEntry>,
    private val commands: List<CommandEntry> = emptyList(),
    private val columns: Int = DEFAULT_COLUMNS
) {
    private val cols = columns.coerceAtLeast(1)
    private val textKinds =
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

    fun withApps(newRoot: List<AppEntry>, newApps: List<AppEntry>) =
        StartModel(newRoot, newApps, commands, columns)

    fun items(s: StartState): List<StartItem> = itemsFor(s.level, s.query)

    private fun itemsFor(level: Level, query: String): List<StartItem> = when (level) {
        Level.Root -> root.map(::appItem)

        Level.AllApps -> AppFilter.filter(apps, "").map(::appItem)

        Level.Search ->
            AppFilter.filter(apps, query).map(::appItem) +
                AppFilter.filterBy(commands, query) { it.label }.map(::commandItem)
    }

    /** Index of the focused row in the current level, or -1 when the level is empty. Always valid otherwise. */
    fun focusIndex(s: StartState): Int {
        val list = items(s)
        if (list.isEmpty()) return -1
        val f = s.focus[s.level] ?: return 0
        val byId = f.id?.let { id -> list.indexOfFirst { it.id == id } } ?: -1
        return if (byId >= 0) byId else f.index.coerceIn(0, list.lastIndex)
    }

    fun focused(s: StartState): StartItem? = items(s).getOrNull(focusIndex(s))

    fun reduce(s: StartState, k: KeyInput): Reduction = when {
        passesToEditable(k) || k.hasCommandModifier || isPaging(k) -> Reduction(s, consumed = false)
        ignoredRepeat(s, k) -> Reduction(s)
        else -> handle(s.copy(focusVisible = true), k)
    }

    private fun passesToEditable(k: KeyInput): Boolean = k.editableFocused && k.kind in textKinds

    /** PageUp/PageDown have no Start behaviour: they stay unhandled so the default (or an editor) keeps them. */
    private fun isPaging(k: KeyInput): Boolean =
        k.kind == KeyKind.PageUp || k.kind == KeyKind.PageDown

    /** Held keys: arrows repeat; one-shot actions do not; a repeat can never be the first character of a search. */
    private fun ignoredRepeat(s: StartState, k: KeyInput): Boolean {
        if (!k.repeat) return false
        return when (k.kind) {
            KeyKind.Activate, KeyKind.Back, KeyKind.Escape, KeyKind.Tab -> true
            KeyKind.Space, KeyKind.Printable, KeyKind.Backspace -> s.level != Level.Search
            else -> false
        }
    }

    private fun handle(s: StartState, k: KeyInput): Reduction = when (k.kind) {
        KeyKind.Up, KeyKind.Down, KeyKind.Left, KeyKind.Right -> move(s, k.kind)
        KeyKind.Tab -> tab(s, forward = !k.shift)
        KeyKind.Activate -> activate(s)
        KeyKind.Space -> if (s.level == Level.Search) typeSpace(s) else activate(s)
        KeyKind.Printable -> type(s, k.char)
        KeyKind.Backspace -> backspace(s)
        KeyKind.Back, KeyKind.Escape -> back(s)
        KeyKind.MoveHome -> edge(s, first = true)
        KeyKind.MoveEnd -> edge(s, first = false)
        KeyKind.PageUp, KeyKind.PageDown, KeyKind.Other -> Reduction(s, consumed = false)
    }

    private fun type(s: StartState, c: Char): Reduction {
        val base = openSearch(s)
        val q = (base.query + c).take(MAX_QUERY)
        return Reduction(
            base.copy(
                query = q,
                softImeRequested = false,
                focus =
                    base.focus - Level.Search
            )
        )
    }

    private fun openSearch(s: StartState): StartState {
        if (s.level == Level.Search) return s
        return s.copy(level = Level.Search, query = "", origin = s.level)
    }

    private fun typeSpace(s: StartState): Reduction {
        if (s.query.isEmpty() || s.query.endsWith(' ') ||
            s.query.length >= MAX_QUERY
        ) {
            return Reduction(s)
        }
        return Reduction(s.copy(query = s.query + ' ', focus = s.focus - Level.Search))
    }

    private fun backspace(s: StartState): Reduction {
        if (s.level != Level.Search) return Reduction(s, consumed = false)
        val q = s.query.dropLast(1)
        return if (q.isEmpty()) {
            Reduction(leaveSearch(s))
        } else {
            Reduction(
                s.copy(
                    query = q,
                    focus =
                        s.focus - Level.Search
                )
            )
        }
    }

    private fun back(s: StartState): Reduction = when (s.level) {
        Level.Search -> Reduction(leaveSearch(s))
        Level.AllApps -> Reduction(s.copy(level = Level.Root))
        Level.Root -> Reduction(s, consumed = false)
    }

    private fun leaveSearch(s: StartState) = s.copy(
        level = s.origin,
        query = "",
        softImeRequested = false,
        focus =
            s.focus - Level.Search
    )

    /** MoveHome/MoveEnd: first/last item of the current local collection. System Home is never handled here. */
    private fun edge(s: StartState, first: Boolean): Reduction {
        val list = items(s)
        if (list.isEmpty()) return Reduction(s)
        return Reduction(setFocus(s, list, if (first) 0 else list.lastIndex))
    }

    private fun activate(s: StartState): Reduction {
        val item = focused(s) ?: return Reduction(s)
        return Reduction(s.copy(lastLaunchedId = item.id), Effect.Launch(item))
    }

    private fun tab(s: StartState, forward: Boolean): Reduction {
        val list = items(s)
        val i = focusIndex(s)
        val next = if (forward) i + 1 else i - 1
        return if (list.isEmpty() || next !in list.indices) {
            Reduction(s, Effect.FocusLeaves(forward))
        } else {
            Reduction(setFocus(s, list, next))
        }
    }

    private fun move(s: StartState, dir: KeyKind): Reduction {
        val list = items(s)
        if (list.isEmpty()) return Reduction(s)
        val i = focusIndex(s)
        val grid = s.level != Level.Search
        val step =
            when (dir) {
                KeyKind.Up -> -(if (grid) cols else 1)
                KeyKind.Down -> if (grid) cols else 1
                KeyKind.Left -> if (grid) -1 else 0
                else -> if (grid) 1 else 0
            }
        val target = i + step
        return when {
            s.level == Level.Root && dir == KeyKind.Up && target < 0 -> Reduction(
                s,
                Effect.FocusLeaves(false)
            )

            s.level == Level.Root && dir == KeyKind.Down && i / cols >= (list.lastIndex) / cols ->
                Reduction(s, Effect.FocusLeaves(true))

            else -> Reduction(setFocus(s, list, target.coerceIn(0, list.lastIndex)))
        }
    }

    private fun setFocus(s: StartState, list: List<StartItem>, index: Int) =
        s.copy(focus = s.focus + (s.level to Focus(list[index].id, index)))

    /** The user touched the screen: hide the focus ring and keep everything else. */
    fun onTouch(s: StartState): StartState = s.copy(focusVisible = false)

    /** Touching the search field is the only path that asks for the soft keyboard. */
    fun onSearchFieldTapped(s: StartState): StartState = if (s.level == Level.Search) {
        s.copy(softImeRequested = true, focusVisible = false)
    } else {
        s.copy(
            level = Level.Search,
            query = "",
            origin = s.level,
            softImeRequested = true,
            focusVisible = false
        )
    }

    /** Back from another activity: leave Search and put focus back on the item that was launched. */
    fun onReturnFromLaunch(s: StartState): StartState {
        val level = if (s.level == Level.Search) s.origin else s.level
        val id = s.lastLaunchedId
        val restored = if (id != null &&
            itemsFor(level, "").any { it.id == id }
        ) {
            Focus(id, 0)
        } else {
            s.focus[level]
        }
        val focus = (s.focus - Level.Search) + (restored?.let { mapOf(level to it) } ?: emptyMap())
        return s.copy(level = level, query = "", softImeRequested = false, focus = focus)
    }

    /** A dialog closed: nothing changes, because focus is stored by item id and re-resolved on demand. */
    fun onDialogDismissed(s: StartState): StartState = s

    private fun appItem(a: AppEntry) = StartItem("${a.pkg}/${a.cls}", a.label, a, null)

    private fun commandItem(c: CommandEntry) = StartItem("command:${c.id}", c.label, null, c)

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val MAX_QUERY = 64
    }
}
