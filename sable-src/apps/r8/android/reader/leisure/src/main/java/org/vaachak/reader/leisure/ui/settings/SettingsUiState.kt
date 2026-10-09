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

package org.vaachak.reader.leisure.ui.settings

import org.vaachak.reader.core.domain.model.AiConfig
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.core.domain.model.SettingsSection
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.core.domain.model.TtsSettings
import org.vaachak.reader.core.domain.model.UserProfile

/**
 * Represents the single source of truth for the Settings Screen UI.
 * This state object is immutable and updated via the ViewModel.
 */
data class SettingsUiState(
    // --- 1. PROFILE & SYNC ---
    val userProfile: UserProfile = UserProfile(),
    val isSyncLoading: Boolean = false,

    // --- 2. GLOBAL CONTROLS ---
    val isOfflineMode: Boolean = false,

    // --- 3. CONTENT (Catalogs) ---
    val catalogs: List<OpdsEntity> = emptyList(),

    // --- 4. INTELLIGENCE (AI) ---
    val aiConfig: AiConfig = AiConfig(),

    // --- 5. UI LOGIC (View vs Edit Mode) ---
    val activeEditSection: SettingsSection = SettingsSection.NONE,
    val isAiMasked: Boolean = true, // Masks API keys by default (e.g., •••••)
    val errorMessage: String? = null,

    //--6. Theme
    // --- ADD THESE TWO FIELDS ---
    val themeMode: ThemeMode = ThemeMode.E_INK,
    val einkContrast: Float = 0.5f,
    val ttsSettings: TtsSettings = TtsSettings(
        defaultSpeed = 1.0f,
        isAutoPageTurnEnabled = true,
        visualStyle = "underline",
        pitch = 0.5f,
        voice = "default"
    ),

    val bookshelfPreferences: BookshelfPreferences = BookshelfPreferences()
)