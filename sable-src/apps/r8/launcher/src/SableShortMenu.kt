package io.github.aimindseye.tenseven.ui

import android.view.KeyEvent

/**
 * The BlackBerry 7 short menu (owner decision 2026-11-19, from Bold7): a trackpad hold on the home screen
 * opens a 3 x 3 grid of the actions used most; the Menu key still opens the full menu. The list carries its
 * own type so the activity can show it as a grid and the Menu key's list stays a list.
 */
internal class SableShortMenu(items: List<Pair<String, () -> Unit>>) : ArrayList<Pair<String, () -> Unit>>(items) {
    companion object {
        const val COLUMNS = 3
        const val ROWS = 3

        /** The nine cells, in reading order. */
        val TITLES =
            listOf(
                "Switch Application",
                "Full Menu",
                "Phone",
                "Settings",
                "Connections",
                "Sound",
                "Display",
                "Bluetooth",
                "Wallpaper",
            )
    }
}

/**
 * The trackpad press that is waiting to be told apart from a hold: a press launches on release, a hold
 * opens the short menu instead. Held outside the composition so a redraw between press and release
 * does not lose it.
 */
internal object SableCenterPress {
    var pending: KeyEvent? = null
}
