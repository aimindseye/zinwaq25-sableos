package org.vaachak.reader.leisure.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Highlights : Screen("highlights")
    data object Settings : Screen("settings")

    // OPTIMIZATION & BUG FIX: Ensure the JSON string is URL encoded
    data object Reader : Screen("reader/{bookId}?locator={locator}") {
        fun createRoute(bookHash: String, locator: String? = null): String {
            return if (locator != null) {
                // Safely encode the JSON to prevent Compose Navigation crashes
                "reader/$bookHash?locator=${Uri.encode(locator)}"
            } else {
                "reader/$bookHash"
            }
        }
    }

    data object PdfReader : Screen("pdf/{bookId}") {
        fun createRoute(bookHash: String): String = "pdf/$bookHash"
    }

    data object ComicReader : Screen("comic/{bookId}") {
        fun createRoute(bookHash: String): String = "comic/$bookHash"
    }

    data object AudiobookReader : Screen("audiobook/{bookId}") {
        fun createRoute(bookHash: String): String = "audiobook/$bookHash"
    }

    data object Backup : Screen("settings/backup")
    data object Appearance : Screen("settings/appearance")
    data object AppAppearance : Screen("settings/app_appearance")
    data object Dictionary : Screen("settings/dictionary")
    data object TTS : Screen("settings/tts")
    data object Bookshelf : Screen("settings/bookshelf")
    data object ReaderProfiles : Screen("reader_profiles")
    data object About : Screen("settings/about")
}
