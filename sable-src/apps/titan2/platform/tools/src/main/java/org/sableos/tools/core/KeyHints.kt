package org.sableos.tools.core

import org.sableos.tools.core.ToolsText.t

/**
 * The key line under a tool. It names only keys every supported keyboard has: letters, Space, Enter
 * and Back. The Q25 (BlackBerry Classic layout) has no Ctrl, Esc or Tab key, so Ctrl+C and Esc
 * still work where they exist but are never what the line asks for.
 */
object KeyHints {
    fun footer(kind: ToolKind, calibratable: Boolean): String = when (kind) {
        ToolKind.MEASUREMENT ->
            (if (calibratable) t("c_calibrate") else "") +
                t("r_reset_h_hold_m")

        ToolKind.KEY_CAPTURE -> t("every_key_is_captured_press")

        ToolKind.TEXT_TEST -> t("enter_check_back_to_leave")

        ToolKind.REPORT -> t("space_select_enter_open_m")

        else -> t("c_copy_s_save_to")
    }
}
