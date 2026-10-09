/*
 *  Copyright (c) 2026 Piyush Daiya
 *  *
 *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  * of this software and associated documentation files (the "Software"), to deal
 *  * in the Software without restriction, including without limitation the rights
 *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  * copies of the Software, and to permit persons to whom the Software is
 *  * furnished to do so, subject to the following conditions:
 *  *
 *  * The above copyright notice and this permission notice shall be included in all
 *  * copies or substantial portions of the Software.
 *  *
 *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  * SOFTWARE.
 */


package org.vaachak.reader.core.domain.model
/**
 * Represents the user's sync status for the Profile Card.
 * This decouples the UI from the raw Auth tokens or repository implementation.
 *
 * @property username The visible username (e.g. "piyush"). Null if logged out.
 * @property deviceName The name of the current device (e.g. "Pixel 6").
 * @property lastSyncTime Timestamp of the last successful sync (ms).
 * @property isAuthenticated True if valid credentials exist.
 */
data class UserProfile(
    val username: String? = null,
    val deviceName: String? = null,
    val lastSyncTime: Long = 0L,
    val isAuthenticated: Boolean = false,
    val avatarUrl: String? = null // Reserved for future use (Gravatar/Uploads)
)

/**
 * Grouping AI configuration to pass around easily as a single object.
 * This prevents the ViewModel from managing 5 separate state variables.
 */
data class AiConfig(
    val isEnabled: Boolean = false,
    val geminiKey: String = "",
    val cloudflareUrl: String = "",
    val authToken: String = "",
    val autoSaveRecaps: Boolean = false
)

/**
 * Defines which "Edit Mode" is currently active in the Hub & Spoke UI.
 * This replaces the need for boolean flags like `isEditingAi`, `isEditingSync`, etc.
 */
enum class SettingsSection {
    NONE,           // Default state (Viewing list)
    AI_CONFIG,      // Editing API keys inside the AI spoke
    SYNC_CONFIG,    // Editing Server URL inside the Sync spoke
    CATALOG_ADD,    // Adding a new OPDS feed
    CATALOG_EDIT    // Editing an existing feed
}

