package org.sableos.reference.typetofind

/** Who gets a key, highest precedence first. App shortcuts are always last. */
enum class Layer { Editable, Navigation, AppShortcut, Unhandled }

data class Routed(val layer: Layer, val shortcutId: String? = null)

/**
 * Command chords (Ctrl or Meta plus a character) that map to a command id. There are no package names and no
 * device names here; a shortcut can only ever name a command, and can never shadow plain typing.
 */
class ShortcutTable(private val bindings: Map<Char, String>) {
    fun idFor(c: Char): String? = bindings[c.lowercaseChar()]
}

object Routing {
    /** Chords an editable field keeps for itself (select all, copy, paste, cut, undo, redo). */
    private val editableChords = setOf('a', 'c', 'v', 'x', 'z', 'y')
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
    private val navKinds =
        setOf(
            KeyKind.Up, KeyKind.Down, KeyKind.Activate, KeyKind.Space, KeyKind.Back,
            KeyKind.Escape, KeyKind.MoveHome, KeyKind.MoveEnd, KeyKind.Tab, KeyKind.Backspace,
            KeyKind.Left, KeyKind.Right
        )

    fun route(k: KeyInput, shortcuts: ShortcutTable): Routed {
        val chord = k.hasCommandModifier && k.kind == KeyKind.Printable
        return when {
            chord && k.editableFocused && k.char.lowercaseChar() in editableChords -> Routed(
                Layer.Editable
            )

            chord -> shortcuts.idFor(k.char)?.let { Routed(Layer.AppShortcut, it) }
                ?: Routed(Layer.Unhandled)

            k.editableFocused && k.kind in textKinds -> Routed(Layer.Editable)

            k.kind == KeyKind.Printable || k.kind in navKinds -> Routed(Layer.Navigation)

            else -> Routed(Layer.Unhandled)
        }
    }
}
