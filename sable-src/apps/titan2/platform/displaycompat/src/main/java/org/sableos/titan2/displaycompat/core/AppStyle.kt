package org.sableos.titan2.displaycompat.core

import org.sableos.titan2.displaycompat.core.DisplayCompatText.t

/*
 * "Sable app style" section of App display compatibility: the global corner style every Sable
 * app reads from the appearance authority (content://org.sableos.appearance/appearance, column
 * corner_style, hosted by Settings, patch 0103). Pure Kotlin, unit-tested on the JVM.
 *
 * This is a global style for Sable apps, not a per-app display profile: it does not touch the
 * profile store and it does not need the gated platform backend.
 */

/**
 * Corner styles. The stable values equal SableCornerStyle in sabledesign and
 * SableAppearancePolicy.CORNER_STYLES in the Settings patch (tests/check-sable-design.py compares
 * them). Compact is the default, so Sable apps look the same until the user picks Rounded.
 */
enum class AppCornerStyle(
    val stableValue: String,
    private val labelKey: String,
    private val summaryKey: String
) {
    Compact("compact", "compact", "tight_2_6dp_corners_the"),
    Rounded("rounded", "rounded", "n8dp_controls_12dp_cards_and");

    val label: String get() = t(labelKey)
    val summary: String get() = t(summaryKey)

    companion object {
        val DEFAULT = Compact

        fun fromStableValue(value: String?): AppCornerStyle =
            entries.firstOrNull { it.stableValue == value } ?: DEFAULT
    }
}

/** What the section can do on this build. */
enum class AppStyleAvailability {
    /** The authority answered; the style can be read and changed. */
    AVAILABLE,

    /** No appearance authority (a build before the Settings patch); the section is read-only. */
    UNAVAILABLE
}

/** Reads and writes the global corner style; the Android side talks to the authority. */
interface AppStyleStore {
    /** The current style, or null when the authority is missing or unreadable. */
    fun read(): AppCornerStyle?

    /** Stores the style; false when the authority is missing or refused the write. */
    fun write(style: AppCornerStyle): Boolean
}

/** Keys the section reacts to while it holds the selection. */
enum class AppStyleKey { LEFT, RIGHT, ENTER }

/** Snapshot shown by the section. */
data class AppStyleState(val style: AppCornerStyle, val availability: AppStyleAvailability) {
    val enabled: Boolean get() = availability == AppStyleAvailability.AVAILABLE
}

object AppStylePolicy {
    /**
     * The style a key picks, or null when the key changes nothing. The options are laid out left
     * to right in [AppCornerStyle] order: Left picks the previous one, Right the next one (both
     * stop at the ends), Enter switches to the other style.
     */
    fun styleForKey(current: AppCornerStyle, key: AppStyleKey): AppCornerStyle? {
        val all = AppCornerStyle.entries
        val i = all.indexOf(current)
        val next =
            when (key) {
                AppStyleKey.LEFT -> all[(i - 1).coerceAtLeast(0)]
                AppStyleKey.RIGHT -> all[(i + 1).coerceAtMost(all.size - 1)]
                AppStyleKey.ENTER -> all[(i + 1) % all.size]
            }
        return next.takeIf { it != current }
    }

    fun stateFrom(read: AppCornerStyle?): AppStyleState = if (read == null) {
        AppStyleState(AppCornerStyle.DEFAULT, AppStyleAvailability.UNAVAILABLE)
    } else {
        AppStyleState(read, AppStyleAvailability.AVAILABLE)
    }

    /** Status line after a change. Failure text contains "could not" (the screen tints it). */
    fun message(style: AppCornerStyle, saved: Boolean): String = if (saved) {
        t("sable_apps_now_use_corners", style.label)
    } else {
        t("sable_app_style_could_not")
    }

    val UNAVAILABLE_TEXT: String get() = t("sable_app_style_needs_the")
}

/** Applies a choice through the store and reports the resulting state and message. */
class AppStyleController(private val store: AppStyleStore) {
    fun load(): AppStyleState = AppStylePolicy.stateFrom(store.read())

    /** Handles a key; null when the key changed nothing (so the caller does not consume it). */
    fun onKey(state: AppStyleState, key: AppStyleKey): Pair<AppStyleState, String>? =
        AppStylePolicy.styleForKey(state.style, key)?.let { target -> choose(state, target) }

    /** Picks a style directly (pointer or touch on an option). */
    fun choose(state: AppStyleState, target: AppCornerStyle): Pair<AppStyleState, String>? {
        if (!state.enabled || target == state.style) return null
        val saved = store.write(target)
        // Re-read so the section shows what Sable apps will actually see.
        val after = if (saved) AppStylePolicy.stateFrom(store.read()) else state
        return after to AppStylePolicy.message(target, saved && after.style == target)
    }
}
