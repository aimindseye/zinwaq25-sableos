package org.sableos.tools.core

/**
 * The key line under a tool. It names only keys every supported keyboard has: letters, Space, Enter
 * and Back. The Q25 (BlackBerry Classic layout) has no Ctrl, Esc or Tab key, so Ctrl+C and Esc
 * still work where they exist but are never what the line asks for.
 */
object KeyHints {
    fun footer(kind: ToolKind, calibratable: Boolean): String = when (kind) {
        ToolKind.MEASUREMENT ->
            (if (calibratable) "C calibrate · " else "") +
                "R reset · H hold · M more · Back to leave"

        ToolKind.KEY_CAPTURE -> "Every key is captured. Press Back twice to leave."

        ToolKind.TEXT_TEST -> "Enter check · Back to leave"

        ToolKind.REPORT -> "Space select · Enter open · M more · Back to leave"

        else -> "C copy · S save to phone · M more · Back to leave"
    }
}
