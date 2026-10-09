package org.vaachak.reader.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.sableos.reader.model.storage.RetiredRemoteSettings
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.CoverAspectRatio
import org.vaachak.reader.core.domain.model.DitheringMode
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.core.domain.model.TtsSettings
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository
) {

    companion object {
        // --- APP GLOBAL SETTINGS ---
        val IS_EINK_ENABLED = booleanPreferencesKey("is_eink_enabled")
        val THEME_KEY = stringPreferencesKey("theme_mode")
        val CONTRAST_KEY = floatPreferencesKey("eink_contrast")
        val DICTIONARY_FOLDER_KEY = stringPreferencesKey("dictionary_folder")
        val USE_EMBEDDED_DICT = booleanPreferencesKey("use_embedded_dict")

        // --- TTS SETTINGS ---
        val TTS_DEFAULT_SPEED = floatPreferencesKey("tts_default_speed")
        val TTS_AUTO_PAGE_TURN = booleanPreferencesKey("tts_auto_page_turn")
        val TTS_VISUAL_STYLE = stringPreferencesKey("tts_visual_style")
        val TTS_LANGUAGE = stringPreferencesKey("tts_language")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val TTS_BACKGROUND_PLAYBACK = booleanPreferencesKey("tts_background_playback")
        val TTS_SLEEP_TIMER = intPreferencesKey("tts_sleep_timer")
        val TTS_VOICE = stringPreferencesKey("tts_voice")

        // --- READER PREFERENCES ---
        val READER_FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val READER_FONT_SIZE = doublePreferencesKey("reader_font_size")
        val READER_TEXT_ALIGN = stringPreferencesKey("reader_text_align")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_PUBLISHER_STYLES = booleanPreferencesKey("reader_publisher_styles")
        val READER_LETTER_SPACING = doublePreferencesKey("reader_letter_spacing")
        val READER_LINE_HEIGHT = doublePreferencesKey("reader_line_height")
        val READER_PARAGRAPH_SPACING = doublePreferencesKey("reader_para_spacing")
        val READER_MARGIN_SIDE = doublePreferencesKey("reader_margin_side")
        val READER_MARGIN_TOP = doublePreferencesKey("reader_margin_top")
        val READER_MARGIN_BOTTOM = doublePreferencesKey("reader_margin_bottom")

        val READER_WORD_SPACING = doublePreferencesKey("reader_word_spacing")
        val READER_PARAGRAPH_INDENT = doublePreferencesKey("reader_paragraph_indent")
        val READER_HYPHENS = booleanPreferencesKey("reader_hyphens")
        val READER_LIGATURES = booleanPreferencesKey("reader_ligatures")

        // --- LIBRARY & COVER STYLE SETTINGS ---
        val DITHERING_MODE_KEY = stringPreferencesKey("dithering_mode")
        val GROUP_BY_SERIES_KEY = booleanPreferencesKey("group_by_series")
        val COVER_ASPECT_RATIO_KEY = stringPreferencesKey("cover_aspect_ratio")
        val SHOW_FORMAT_BADGE_KEY = booleanPreferencesKey("show_format_badge")
        val SHOW_FAVORITE_ICON_KEY = booleanPreferencesKey("show_favorite_icon")
        val SHOW_PROGRESS_BADGE_KEY = booleanPreferencesKey("show_progress_badge")
    }

    // --- 1. THE THREAD-SAFE VAULT CACHE ---
    private val activeVaults = mutableMapOf<String, DataStore<Preferences>>()
    private val vaultMutex = Mutex()

    private suspend fun getVaultDataStore(vaultId: String): DataStore<Preferences> {
        vaultMutex.withLock {
            return activeVaults.getOrPut(vaultId) {
                PreferenceDataStoreFactory.create(
                    produceFile = { context.preferencesDataStoreFile("vault_$vaultId") }
                )
            }
        }
    }

    // Helper function for the edit/save methods
    private suspend fun editCurrentVault(transform: suspend (MutablePreferences) -> Unit) {
        val currentVaultId = vaultRepository.activeVaultId.first()
        val dataStore = getVaultDataStore(currentVaultId)
        dataStore.edit { transform(it) }
    }

    // --- 2. THE MASTER DYNAMIC FLOW ---
    private val vaultPreferencesFlow: Flow<Preferences> = vaultRepository.activeVaultId
        .flatMapLatest { vaultId -> getVaultDataStore(vaultId).data }

    // --- APP FLOWS ---
    val isEinkEnabled: Flow<Boolean> = vaultPreferencesFlow.map { it[IS_EINK_ENABLED] ?: false }
    val einkContrast: Flow<Float> = vaultPreferencesFlow.map { it[CONTRAST_KEY] ?: 0.5f }

    val themeMode: Flow<ThemeMode> = vaultPreferencesFlow.map { prefs ->
        // LIGHT is the "follow SableOS" sentinel; E-Ink is an explicit, reading-only opt-in.
        val name = prefs[THEME_KEY] ?: ThemeMode.LIGHT.name
        try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.LIGHT }
    }

    // --- TTS FLOWS ---
    val ttsSettings: Flow<TtsSettings> = vaultPreferencesFlow.map { preferences ->
        TtsSettings(
            defaultSpeed = preferences[TTS_DEFAULT_SPEED] ?: 1.0f,
            pitch = preferences[TTS_PITCH] ?: 1.0f,
            visualStyle = preferences[TTS_VISUAL_STYLE] ?: "underline",
            isAutoPageTurnEnabled = preferences[TTS_AUTO_PAGE_TURN] ?: true,
            language = preferences[TTS_LANGUAGE] ?: "default",
            isBackgroundPlaybackEnabled = preferences[TTS_BACKGROUND_PLAYBACK] ?: false,
            sleepTimerMinutes = preferences[TTS_SLEEP_TIMER] ?: 0,
            voice = preferences[TTS_VOICE] ?: "default"
        )
    }

    // --- READER PREF FLOWS ---
    val readerPreferences: Flow<ReaderPreferences> = vaultPreferencesFlow.map { prefs ->
        ReaderPreferences(
            theme = prefs[READER_THEME] ?: "light",
            fontSize = prefs[READER_FONT_SIZE] ?: 1.0,
            publisherStyles = prefs[READER_PUBLISHER_STYLES] ?: true,
            fontFamily = prefs[READER_FONT_FAMILY],
            textAlign = prefs[READER_TEXT_ALIGN],
            lineHeight = prefs[READER_LINE_HEIGHT],
            letterSpacing = prefs[READER_LETTER_SPACING],
            paragraphSpacing = prefs[READER_PARAGRAPH_SPACING],
            pageMargins = prefs[READER_MARGIN_SIDE],
            wordSpacing = prefs[READER_WORD_SPACING],
            paragraphIndent = prefs[READER_PARAGRAPH_INDENT],
            hyphens = prefs[READER_HYPHENS],
            ligatures = prefs[READER_LIGATURES],
            marginTop = prefs[READER_MARGIN_TOP],
            marginBottom = prefs[READER_MARGIN_BOTTOM]
        )
    }

    // Dictionary Flows
    fun getUseEmbeddedDictionary(): Flow<Boolean> = vaultPreferencesFlow.map { it[USE_EMBEDDED_DICT] ?: false }
    fun getDictionaryFolder(): Flow<String> = vaultPreferencesFlow.map { it[DICTIONARY_FOLDER_KEY] ?: "" }

    // --- TTS ACTIONS ---
    suspend fun setTtsDefaultSpeed(speed: Float) { editCurrentVault { it[TTS_DEFAULT_SPEED] = speed } }
    suspend fun setTtsAutoPageTurn(enabled: Boolean) { editCurrentVault { it[TTS_AUTO_PAGE_TURN] = enabled } }
    suspend fun setTtsVisualStyle(style: String) { editCurrentVault { it[TTS_VISUAL_STYLE] = style } }
    suspend fun setTtsLanguage(language: String) { editCurrentVault { it[TTS_LANGUAGE] = language } }
    suspend fun setTtsPitch(pitch: Float) { editCurrentVault { it[TTS_PITCH] = pitch } }
    suspend fun setTtsSleepTimer(minutes: Int) { editCurrentVault { it[TTS_SLEEP_TIMER] = minutes } }

    // --- APP SETTINGS ACTIONS ---

    suspend fun setThemeMode(mode: ThemeMode) { editCurrentVault { it[THEME_KEY] = mode.name } }
    suspend fun setContrast(value: Float) { editCurrentVault { it[CONTRAST_KEY] = value } }
    suspend fun setUseEmbeddedDictionary(enabled: Boolean) { editCurrentVault { it[USE_EMBEDDED_DICT] = enabled } }
    suspend fun setDictionaryFolder(uri: String) { editCurrentVault { it[DICTIONARY_FOLDER_KEY] = uri } }

    fun validateStarDictFolder(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val dir = DocumentFile.fromTreeUri(context, uri)
            dir?.let { it.isDirectory && it.canRead() && it.listFiles().any { f -> f.name?.endsWith(".idx", true) == true } } ?: false
        } catch (e: Exception) { false }
    }

    suspend fun saveReaderPreferences(prefs: ReaderPreferences) {
        editCurrentVault { p ->
            p[READER_THEME] = prefs.theme
            p[READER_FONT_SIZE] = prefs.fontSize
            p[READER_PUBLISHER_STYLES] = prefs.publisherStyles

            if (prefs.publisherStyles) {
                p.remove(READER_FONT_FAMILY)
                p.remove(READER_TEXT_ALIGN)
                p.remove(READER_LINE_HEIGHT)
                p.remove(READER_LETTER_SPACING)
                p.remove(READER_PARAGRAPH_SPACING)
                p.remove(READER_MARGIN_SIDE)
                p.remove(READER_MARGIN_TOP)
                p.remove(READER_MARGIN_BOTTOM)
                p.remove(READER_WORD_SPACING)
                p.remove(READER_PARAGRAPH_INDENT)
                p.remove(READER_HYPHENS)
                p.remove(READER_LIGATURES)
            } else {
                prefs.fontFamily?.let { p[READER_FONT_FAMILY] = it } ?: p.remove(READER_FONT_FAMILY)
                prefs.textAlign?.let { p[READER_TEXT_ALIGN] = it } ?: p.remove(READER_TEXT_ALIGN)
                prefs.lineHeight?.let { p[READER_LINE_HEIGHT] = it } ?: p.remove(READER_LINE_HEIGHT)
                prefs.letterSpacing?.let { p[READER_LETTER_SPACING] = it } ?: p.remove(READER_LETTER_SPACING)
                prefs.paragraphSpacing?.let { p[READER_PARAGRAPH_SPACING] = it } ?: p.remove(READER_PARAGRAPH_SPACING)
                prefs.pageMargins?.let { p[READER_MARGIN_SIDE] = it } ?: p.remove(READER_MARGIN_SIDE)
                prefs.marginTop?.let { p[READER_MARGIN_TOP] = it } ?: p.remove(READER_MARGIN_TOP)
                prefs.marginBottom?.let { p[READER_MARGIN_BOTTOM] = it } ?: p.remove(READER_MARGIN_BOTTOM)
                prefs.wordSpacing?.let { p[READER_WORD_SPACING] = it } ?: p.remove(READER_WORD_SPACING)
                prefs.paragraphIndent?.let { p[READER_PARAGRAPH_INDENT] = it } ?: p.remove(READER_PARAGRAPH_INDENT)
                prefs.hyphens?.let { p[READER_HYPHENS] = it } ?: p.remove(READER_HYPHENS)
                prefs.ligatures?.let { p[READER_LIGATURES] = it } ?: p.remove(READER_LIGATURES)
            }
        }
    }

    suspend fun resetLayoutPreferences() {
        editCurrentVault { p ->
            p[READER_PUBLISHER_STYLES] = false
            p.remove(READER_LINE_HEIGHT)
            p.remove(READER_TEXT_ALIGN)
            p.remove(READER_PARAGRAPH_SPACING)
            p.remove(READER_MARGIN_SIDE)
            p.remove(READER_MARGIN_TOP)
            p.remove(READER_MARGIN_BOTTOM)
            p.remove(READER_LETTER_SPACING)
            p.remove(READER_WORD_SPACING)
            p.remove(READER_PARAGRAPH_INDENT)
            p.remove(READER_HYPHENS)
            p.remove(READER_LIGATURES)
        }
    }

    val bookshelfPreferences: Flow<BookshelfPreferences> = vaultPreferencesFlow.map { prefs ->
        val ditheringName = prefs[DITHERING_MODE_KEY] ?: DitheringMode.AUTO.name
        val ditheringMode = try { DitheringMode.valueOf(ditheringName) } catch (_: Exception) { DitheringMode.AUTO }

        val ratioName = prefs[COVER_ASPECT_RATIO_KEY] ?: CoverAspectRatio.UNIFORM.name
        val coverAspectRatio = try { CoverAspectRatio.valueOf(ratioName) } catch (_: Exception) { CoverAspectRatio.UNIFORM }

        BookshelfPreferences(
            ditheringMode = ditheringMode,
            groupBySeries = prefs[GROUP_BY_SERIES_KEY] ?: true,
            coverAspectRatio = coverAspectRatio,
            showFormatBadge = prefs[SHOW_FORMAT_BADGE_KEY] ?: true,
            showFavoriteIcon = prefs[SHOW_FAVORITE_ICON_KEY] ?: true,
            showProgressBadge = prefs[SHOW_PROGRESS_BADGE_KEY] ?: true
        )
    }

    suspend fun setDitheringMode(mode: DitheringMode) { editCurrentVault { it[DITHERING_MODE_KEY] = mode.name } }
    suspend fun setGroupBySeries(enabled: Boolean) { editCurrentVault { it[GROUP_BY_SERIES_KEY] = enabled } }
    suspend fun setCoverAspectRatio(ratio: CoverAspectRatio) { editCurrentVault { it[COVER_ASPECT_RATIO_KEY] = ratio.name } }

    suspend fun setCoverStyleElements(
        format: Boolean, favorite: Boolean, progress: Boolean
    ) {
        editCurrentVault { prefs ->
            prefs[SHOW_FORMAT_BADGE_KEY] = format
            prefs[SHOW_FAVORITE_ICON_KEY] = favorite
            prefs[SHOW_PROGRESS_BADGE_KEY] = progress
        }
    }

    /**
     * Removes keys written by the retired Vaachak remote features (AI keys, cloud-sync credentials,
     * endpoints, offline-mode toggle) from every profile's DataStore, so secrets from a previous
     * install do not stay on disk. Idempotent; safe to call on every launch.
     */
    suspend fun purgeRetiredRemoteSettings(profileIds: List<String>) {
        vaultRepository.removeKeys(RetiredRemoteSettings.KEY_NAMES)
        (profileIds + VaultRepository.DEFAULT_VAULT_ID).toSet().forEach { vaultId ->
            getVaultDataStore(vaultId).edit { prefs ->
                val retired = prefs.asMap().keys.filter { RetiredRemoteSettings.isRetired(it.name) }
                @Suppress("UNCHECKED_CAST")
                retired.forEach { prefs.remove(it as Preferences.Key<Any>) }
            }
        }
    }
}
