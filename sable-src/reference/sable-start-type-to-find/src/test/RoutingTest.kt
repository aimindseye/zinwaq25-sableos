package org.sableos.reference.typetofind

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutingTest {
    private val table = ShortcutTable(mapOf('f' to "find", 'a' to "all", 'h' to "help"))
    private fun route(k: KeyInput) = Routing.route(k, table)

    @Test fun aShortcutNeedsACommandModifier() {
        assertEquals(
            Routed(Layer.AppShortcut, "find"),
            route(KeyInput(KeyKind.Printable, 'f', ctrl = true))
        )
        assertEquals(
            Routed(Layer.AppShortcut, "find"),
            route(KeyInput(KeyKind.Printable, 'F', meta = true))
        )
    }

    @Test fun plainTypingIsNeverAShortcut() {
        assertEquals(Layer.Navigation, route(KeyInput(KeyKind.Printable, 'f')).layer)
        assertNull(route(KeyInput(KeyKind.Printable, 'f')).shortcutId)
    }

    @Test fun anEditableOutranksNavigationForTextKeys() {
        assertEquals(
            Layer.Editable,
            route(KeyInput(KeyKind.Printable, 'f', editableFocused = true)).layer
        )
        assertEquals(
            Layer.Editable,
            route(KeyInput(KeyKind.Backspace, editableFocused = true)).layer
        )
    }

    @Test fun anEditableKeepsItsEditChordsEvenIfBound() {
        assertEquals(
            Layer.Editable,
            route(KeyInput(KeyKind.Printable, 'a', ctrl = true, editableFocused = true)).layer
        )
        assertEquals(
            Layer.AppShortcut,
            route(KeyInput(KeyKind.Printable, 'f', ctrl = true, editableFocused = true)).layer
        )
    }

    @Test fun navigationKeysBeatShortcuts() {
        listOf(KeyKind.Up, KeyKind.Down, KeyKind.Activate, KeyKind.Back, KeyKind.Tab).forEach {
            assertEquals(Layer.Navigation, route(KeyInput(it)).layer)
        }
    }

    @Test fun anUnboundChordIsUnhandledNotTyped() {
        assertEquals(Layer.Unhandled, route(KeyInput(KeyKind.Printable, 'q', ctrl = true)).layer)
        assertEquals(Layer.Unhandled, route(KeyInput(KeyKind.Other)).layer)
    }

    @Test fun shortcutIdsAreCommandIdsNotPackages() {
        table.idFor('h')!!.let { assertEquals(false, it.contains('.')) }
    }

    @Test fun moveHomeAndMoveEndBelongToAFocusedEditorElseToNavigation() {
        listOf(KeyKind.MoveHome, KeyKind.MoveEnd).forEach {
            assertEquals(Layer.Editable, route(KeyInput(it, editableFocused = true)).layer)
            assertEquals(Layer.Navigation, route(KeyInput(it)).layer)
        }
    }

    @Test fun pagingKeysBelongToAFocusedEditorElseAreUnhandled() {
        listOf(KeyKind.PageUp, KeyKind.PageDown).forEach {
            assertEquals(Layer.Editable, route(KeyInput(it, editableFocused = true)).layer)
            assertEquals(Layer.Unhandled, route(KeyInput(it)).layer)
        }
    }

    @Test fun systemHomeAsOtherIsNeverNavigation() {
        assertEquals(Layer.Unhandled, route(KeyInput(KeyKind.Other)).layer)
    }
}
