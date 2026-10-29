package org.sableos.titan2.keyboard.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Keyboard shows, in English, by string resource name. res/values/strings.xml
 * repeats them (KeyboardTextTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object KeyboardText {
    val EN: Map<String, String> =
        mapOf(
            "one_entry_per_line_shortcut" to
                "One entry per line: shortcut=replacement, for example brb=be right back. A " +
                "shortcut is one word of letters or digits. Your entries work in every language " +
                "and replace a built-in one with the same shortcut. In a replacement, %D is the " +
                "date, %T the time and %V the build number. Backspace straight after an " +
                "expansion puts back what you typed.",
            "save" to "Save",
            "saved_entries" to "Saved %1\$s entries",
            "lines_skipped_not_shortcut_replacement" to
                "; %1\$s lines skipped (not shortcut=replacement)",
            "built_in_english_typing_language" to "Built in (English typing language)",
            "turn_on_clipboard_history_in" to
                "Turn on clipboard history in Sable Keyboard settings",
            "unlock_to_use_clipboard_history" to "Unlock to use clipboard history",
            "nav_mode_on_alt_space" to "Nav mode on (Alt+Space to exit)",
            "nav_mode_off" to "Nav mode off",
            "enable_more_languages_in_settings" to
                "Enable more languages in Settings > Keyboard > Sable",
            "auto_capitalise_sentences" to "Auto-capitalise sentences",
            "double_space_inserts_period" to "Double space inserts period",
            "autotext_dont_becomes_don_t" to "AutoText (dont becomes don't, ld the date)",
            "long_press_a_letter_for" to "Long-press a letter for its Alt character",
            "letters_type_digits_in_numeric" to "Letters type digits in numeric fields",
            "nav_mode_ijkl_arrows_enabled" to "Nav mode (IJKL arrows) enabled",
            "alt_space_toggles_nav_mode" to "Alt+Space toggles Nav mode",
            "show_on_screen_keyboard_for" to "Show on-screen keyboard for numeric/PIN fields",
            "always_show_on_screen_keyboard" to "Always show on-screen keyboard",
            "compact_status_strip_when_the" to
                "Compact status strip when the on-screen keyboard is hidden",
            "enter_sends_in_apps_that" to
                "Enter sends in apps that offer Send (Shift+Enter: new line)",
            "clipboard_history_alt_shift_v" to
                "Clipboard history (Alt+Shift+V; kept in memory, never saved)",
            "clear_clipboard_history" to "Clear clipboard history",
            "clipboard_history_cleared" to "Clipboard history cleared",
            "autotext_entries" to "AutoText entries",
            "key_event_probe_diagnostic" to "Key event probe (diagnostic)",
            "sable_keyboard" to "Sable Keyboard",
            "layout_provisional_until_key_legends" to
                "Layout: provisional until key legends are captured on this device.",
            "space" to "space · %1\$s%2\$s",
            "alternate" to "%1\$s, alternate %2\$s",
            "clipboard_empty" to "Clipboard empty",
            "input_method_settings" to "Input method settings",
            "keyboard_picker" to "Keyboard picker",
            "sable_keyboard_is_the_selected" to "Sable Keyboard is the selected keyboard.",
            "another_keyboard_is_selected_your" to
                "Another keyboard is selected. Your choice is kept; Sable Keyboard never " +
                "switches it for you.",
            "sable_keyboard_is_not_enabled" to "Sable Keyboard is not enabled yet.",
            "could_not_read_which_keyboard" to "Could not read which keyboard is selected.",
            "could_not_open_not_available" to "Could not open %1\$s: not available on this device.",
            "could_not_open_blocked_by" to "Could not open %1\$s: blocked by the system.",
            "could_not_open" to "Could not open %1\$s."
        )

    val t = SableTexts(EN)
}
