package org.vaachak.reader.leisure.ui.reader

import android.app.Application
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.android.AndroidTtsEngine.Voice
import org.readium.navigator.media.tts.android.AndroidTtsEngineProvider
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.navigator.media.tts.android.AndroidTtsSettings
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchService
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.AiRepository
import org.vaachak.reader.core.data.repository.DictionaryRepository
import org.vaachak.reader.core.data.repository.ReadiumManager
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.core.domain.model.ReaderPreferences
import org.vaachak.reader.core.domain.model.TtsSettings
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalReadiumApi::class, FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val application: Application,
    private val aiRepository: AiRepository,
    private val readiumManager: ReadiumManager,
    private val highlightDao: HighlightDao,
    private val settingsRepo: SettingsRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val syncRepository: SyncRepository,
    private val bookDao: BookDao,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val _currentTtsVoice = MutableStateFlow<AndroidTtsEngine.Voice?>(null)
    val currentTtsVoiceId: StateFlow<String?> = _currentTtsVoice
        .map { it?.id?.value }
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, null)

    private val _currentTtsLanguage = MutableStateFlow("default")
    val currentTtsLanguage = _currentTtsLanguage.asStateFlow()

    val isEinkEnabled: StateFlow<Boolean> = settingsRepo.isEinkEnabled
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    val isOfflineModeEnabled: StateFlow<Boolean> = settingsRepo.isOfflineModeEnabled
        .stateIn(viewModelScope, AppCoroutineConfig.whileSubscribed, false)

    private val _bookAiEnabled = MutableStateFlow(true)
    val isAiEnabled = combine(isOfflineModeEnabled, _bookAiEnabled) { _, local ->
        local
    }.stateIn(viewModelScope, AppCoroutineConfig.lazily, true)

    val epubPreferences: StateFlow<EpubPreferences> = settingsRepo.readerPreferences
        .map { prefs -> buildEpubPreferences(prefs) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, EpubPreferences())

    private var currentTotalPositions: Int = 0
    private var isFirstLocator = true
    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    private val _currentPageInfo = MutableStateFlow("Page 1")
    val currentPageInfo = _currentPageInfo.asStateFlow()

    private val _initialLocator = MutableStateFlow<Locator?>(null)
    val initialLocator: StateFlow<Locator?> = _initialLocator.asStateFlow()

    private val _showToc = MutableStateFlow(false)
    val showToc: StateFlow<Boolean> = _showToc.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<Link>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    private val _hasChapterError = MutableStateFlow(false)
    val hasChapterError: StateFlow<Boolean> = _hasChapterError.asStateFlow()

    private val _showReaderSettings = MutableStateFlow(false)
    val showReaderSettings: StateFlow<Boolean> = _showReaderSettings.asStateFlow()

    private val _showSearch = MutableStateFlow(false)
    val showSearch: StateFlow<Boolean> = _showSearch.asStateFlow()

    private val _bookSearchQuery = MutableStateFlow("")
    val bookSearchQuery: StateFlow<String> = _bookSearchQuery.asStateFlow()

    private val _isBookSearching = MutableStateFlow(false)
    val isBookSearching: StateFlow<Boolean> = _isBookSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Locator>>(emptyList())
    val searchResults: StateFlow<List<Locator>> = _searchResults.asStateFlow()

    private val _jumpEvent = MutableSharedFlow<Locator>()
    val jumpEvent = _jumpEvent.asSharedFlow()

    private val _showHighlights = MutableStateFlow(false)
    val showHighlights: StateFlow<Boolean> = _showHighlights.asStateFlow()

    private val _showBookmarks = MutableStateFlow(false)
    val showBookmarks: StateFlow<Boolean> = _showBookmarks.asStateFlow()

    private val _isBottomSheetVisible = MutableStateFlow(false)
    val isBottomSheetVisible = _isBottomSheetVisible.asStateFlow()

    private val _aiResponse = MutableStateFlow("")
    val aiResponse = _aiResponse.asStateFlow()

    private val _isImageResponse = MutableStateFlow(false)
    val isImageResponse = _isImageResponse.asStateFlow()

    private val _isDictionaryLookup = MutableStateFlow(false)
    val isDictionaryLookup = _isDictionaryLookup.asStateFlow()

    private val _isDictionaryLoading = MutableStateFlow(false)
    val isDictionaryLoading = _isDictionaryLoading.asStateFlow()

    private val _showTagSelector = MutableStateFlow(false)
    val showTagSelector = _showTagSelector.asStateFlow()

    private val _showRecapConfirmation = MutableStateFlow(false)
    val showRecapConfirmation: StateFlow<Boolean> = _showRecapConfirmation.asStateFlow()

    private val _recapText = MutableStateFlow<String?>(null)
    val recapText: StateFlow<String?> = _recapText.asStateFlow()

    private val _isRecapLoading = MutableStateFlow(false)
    val isRecapLoading: StateFlow<Boolean> = _isRecapLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    private val _currentBookHash = MutableStateFlow<String?>(null)
    private val _currentLocalUri = MutableStateFlow<String?>(null)
    private var currentSelectedText = ""
    private var pendingJumpLocator: String? = null
    private var pendingHighlightLocator: Locator? = null

    private val _showTtsSettingsSheet = MutableStateFlow(false)
    val showTtsSettingsSheet: StateFlow<Boolean> = _showTtsSettingsSheet.asStateFlow()

    private val allBookItems = combine(
        _currentBookHash.filterNotNull(),
        vaultRepository.activeVaultId
    ) { id, profileId ->
        id to profileId
    }.flatMapLatest { (id, profileId) ->
        highlightDao.getHighlightsForBook(id, profileId)
    }.stateIn(viewModelScope, AppCoroutineConfig.lazily, emptyList())

    val bookmarksList: StateFlow<List<HighlightEntity>> = allBookItems
        .map { list -> list.filter { it.tag != "BOOKMARK" && it.tag != "recap" } }
        .stateIn(viewModelScope, AppCoroutineConfig.lazily, emptyList())

    val savedBookmarks: StateFlow<List<HighlightEntity>> = allBookItems
        .map { list -> list.filter { it.tag == "BOOKMARK" } }
        .stateIn(viewModelScope, AppCoroutineConfig.lazily, emptyList())

    val isCurrentPageBookmarked: StateFlow<Boolean> = combine(
        currentLocator,
        savedBookmarks
    ) { locator, bookmarks ->
        if (locator == null) return@combine false

        withContext(AppCoroutineConfig.default) {
            bookmarks.any { entity ->
                try {
                    val bLoc = Locator.fromJSON(JSONObject(entity.locatorJson))
                    bLoc?.href == locator.href &&
                            abs(
                                (bLoc.locations.totalProgression ?: 0.0) -
                                        (locator.locations.totalProgression ?: 0.0)
                            ) < 0.01
                } catch (_: Exception) {
                    false
                }
            }
        }
    }.distinctUntilChanged()
        .stateIn(viewModelScope, AppCoroutineConfig.lazily, false)

    val currentBookHighlights: StateFlow<List<Decoration>> = combine(
        allBookItems,
        isEinkEnabled
    ) { items, isEink ->
        withContext(AppCoroutineConfig.default) {
            items.filter { it.tag != "BOOKMARK" }.mapNotNull { entity ->
                try {
                    val locator = Locator.fromJSON(JSONObject(entity.locatorJson))
                    if (locator != null) {
                        Decoration(
                            id = entity.id.toString(),
                            locator = locator,
                            style = Decoration.Style.Highlight(
                                tint = if (isEink) Color.LTGRAY else entity.color
                            )
                        )
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }.stateIn(viewModelScope, AppCoroutineConfig.lazily, emptyList())

    private val _isTtsActive = MutableStateFlow(false)
    val isTtsActive = _isTtsActive.asStateFlow()

    private val _ttsDecoration = MutableStateFlow<List<Decoration>>(emptyList())
    val ttsDecoration: StateFlow<List<Decoration>> = _ttsDecoration.asStateFlow()

    private var visualNavigatorProvider: (() -> DecorableNavigator?)? = null
    private var ttsNavigator: TtsNavigator<
            AndroidTtsSettings,
            AndroidTtsPreferences,
            AndroidTtsEngine.Error,
            AndroidTtsEngine.Voice
            >? = null

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var lockedOutUtteranceText: String? = null

    val ttsSettings: StateFlow<TtsSettings> = settingsRepo.ttsSettings.stateIn(
        scope = viewModelScope,
        started = AppCoroutineConfig.lazily,
        initialValue = TtsSettings(1.0f, true, "underline", "default", false, 0, 0.5f, "default")
    )

    private val _showTtsBar = MutableStateFlow(false)
    val showTtsBar: StateFlow<Boolean> = _showTtsBar.asStateFlow()

    fun setInitialLocation(json: String?) {
        this.pendingJumpLocator = json
    }

    fun setVisualNavigatorProvider(provider: () -> DecorableNavigator?) {
        this.visualNavigatorProvider = provider
    }

    fun onFileSelected(uri: Uri) {
        _bookSearchQuery.value = ""
        _searchResults.value = emptyList()
        _showSearch.value = false
        _isBookSearching.value = false
        _showHighlights.value = false
        _showBookmarks.value = false
        _recapText.value = null
        _showRecapConfirmation.value = false
        _showReaderSettings.value = false

        viewModelScope.launch {
            val isGlobalOffline = settingsRepo.isOfflineModeEnabled.first()
            _bookAiEnabled.value = !isGlobalOffline
        }

        val uriString = uri.toString()
        _currentLocalUri.value = uriString
        isFirstLocator = true

        viewModelScope.launch {
            syncRepository.sync()
            val profileId = vaultRepository.activeVaultId.first()
            bookDao.updateLastRead(uri.toString(), profileId, System.currentTimeMillis())
            val book = bookDao.getBookByUri(uri.toString(), profileId)
            _currentBookHash.value = book?.bookHash

            val targetJson = pendingJumpLocator ?: book?.lastLocationJson
            val locator = targetJson?.let {
                try {
                    Locator.fromJSON(JSONObject(it))
                } catch (_: Exception) {
                    null
                }
            }
            _initialLocator.value = locator

            val pub = readiumManager.openEpubFromUri(uri)
            _publication.value = pub
            currentTotalPositions = pub?.positions()?.size ?: 0
            pendingJumpLocator = null

            closeTts()
        }
    }

    fun toggleBookmarksList() {
        _showBookmarks.value = !_showBookmarks.value
        if (_showBookmarks.value) {
            _showHighlights.value = false
            _showToc.value = false
        }
    }

    fun toggleBookmarkOnCurrentPage() {
        val locator = _currentLocator.value ?: return
        val bookId = _currentBookHash.value ?: return
        val isBookmarked = isCurrentPageBookmarked.value

        viewModelScope.launch {
            if (isBookmarked) {
                val bookmarks = savedBookmarks.value
                val toDelete = bookmarks.find { entity ->
                    try {
                        val bLoc = Locator.fromJSON(JSONObject(entity.locatorJson))
                        bLoc?.href == locator.href &&
                                abs(
                                    (bLoc.locations.totalProgression ?: 0.0) -
                                            (locator.locations.totalProgression ?: 0.0)
                                ) < 0.01
                    } catch (_: Exception) {
                        false
                    }
                }

                if (toDelete != null) {
                    highlightDao.deleteHighlightById(toDelete.id)
                    _snackbarMessage.value = "Bookmark removed"
                }
            } else {
                val profileId = vaultRepository.activeVaultId.first()
                val bookmark = HighlightEntity(
                    bookHashId = bookId,
                    profileId = profileId,
                    locatorJson = locator.toJSON().toString(),
                    text = "Bookmark at ${currentPageInfo.value}",
                    color = Color.TRANSPARENT,
                    tag = "BOOKMARK"
                )
                highlightDao.insertHighlight(bookmark)
                _snackbarMessage.value = "Bookmark added"
            }
        }
    }

    fun onBookmarkClicked(entity: HighlightEntity) {
        viewModelScope.launch {
            try {
                val l = Locator.fromJSON(JSONObject(entity.locatorJson))
                if (l != null) {
                    _showBookmarks.value = false
                    _jumpEvent.emit(l)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun toggleReaderSettings() {
        _showReaderSettings.value = !_showReaderSettings.value
    }

    fun dismissReaderSettings() {
        _showReaderSettings.value = false
    }

    fun toggleBookAi(enabled: Boolean) {
        _bookAiEnabled.value = enabled
    }

    fun savePreferences(newPrefs: EpubPreferences) = viewModelScope.launch {
        val readerPrefs = ReaderPreferences(
            theme = newPrefs.theme?.name?.lowercase() ?: "light",
            fontSize = newPrefs.fontSize ?: 1.0,
            publisherStyles = newPrefs.publisherStyles ?: true,
            fontFamily = newPrefs.fontFamily?.name,
            textAlign = newPrefs.textAlign?.name?.lowercase(),
            lineHeight = newPrefs.lineHeight,
            letterSpacing = newPrefs.letterSpacing,
            paragraphSpacing = newPrefs.paragraphSpacing,
            pageMargins = newPrefs.pageMargins,
            wordSpacing = newPrefs.wordSpacing,
            paragraphIndent = newPrefs.paragraphIndent,
            hyphens = newPrefs.hyphens,
            ligatures = newPrefs.ligatures
        )
        settingsRepo.saveReaderPreferences(readerPrefs)
    }

    private fun buildEpubPreferences(prefs: ReaderPreferences): EpubPreferences {
        val rTheme = when (prefs.theme) {
            "dark" -> Theme.DARK
            "sepia" -> Theme.SEPIA
            else -> Theme.LIGHT
        }

        val rFont = prefs.fontFamily?.let { FontFamily(it) }

        val rAlign = when (prefs.textAlign) {
            "left" -> TextAlign.LEFT
            "justify" -> TextAlign.JUSTIFY
            else -> TextAlign.START
        }

        return EpubPreferences(
            theme = rTheme,
            fontSize = prefs.fontSize,
            publisherStyles = prefs.publisherStyles,
            fontFamily = if (!prefs.publisherStyles) rFont else null,
            textAlign = if (!prefs.publisherStyles) rAlign else null,
            letterSpacing = if (!prefs.publisherStyles) prefs.letterSpacing else null,
            paragraphSpacing = if (!prefs.publisherStyles) prefs.paragraphSpacing else null,
            pageMargins = if (!prefs.publisherStyles) prefs.pageMargins else null,
            lineHeight = if (!prefs.publisherStyles) prefs.lineHeight else null,
            wordSpacing = if (!prefs.publisherStyles) prefs.wordSpacing else null,
            paragraphIndent = if (!prefs.publisherStyles) prefs.paragraphIndent else null,
            hyphens = if (!prefs.publisherStyles) prefs.hyphens else null,
            ligatures = if (!prefs.publisherStyles) prefs.ligatures else null
        )
    }

    fun onRecapClicked() {
        _showRecapConfirmation.value = true
    }

    fun dismissRecapConfirmation() {
        _showRecapConfirmation.value = false
    }

    fun getQuickRecap() {
        if (!_bookAiEnabled.value) return

        _showRecapConfirmation.value = false
        _isRecapLoading.value = true

        viewModelScope.launch {
            val title = _publication.value?.metadata?.title ?: "Unknown Book"
            val currentContext = "Current Position: ${_currentPageInfo.value}"
            val profileId = vaultRepository.activeVaultId.first()
            val highlights = highlightDao
                .getHighlightsForBook(_currentBookHash.value ?: "", profileId)
                .first()
                .take(10)
                .joinToString("\n") { "- ${it.text}" }

            val summary = aiRepository.generateRecap(title, highlights, currentContext)
            _recapText.value = summary
            _isRecapLoading.value = false
        }
    }

    fun saveRecapAsHighlight() {
        val summary = _recapText.value ?: return
        val currentUri = _currentBookHash.value ?: return
        val locator = _currentLocator.value ?: return
        val isEink = isEinkEnabled.value

        viewModelScope.launch {
            val profileId = vaultRepository.activeVaultId.first()
            val highlight = HighlightEntity(
                bookHashId = currentUri,
                profileId = profileId,
                locatorJson = locator.toJSON().toString(),
                text = "RECAP: $summary",
                color = if (isEink) Color.DKGRAY else Color.CYAN,
                tag = "recap"
            )
            highlightDao.insertHighlight(highlight)
            _recapText.value = null
            _snackbarMessage.value = "Recap saved to highlights."
        }
    }

    fun dismissRecapResult() {
        _recapText.value = null
    }

    fun toggleToc() {
        _showToc.value = !_showToc.value
    }

    fun onTocItemSelected(link: Link) {
        val currentHref = _currentLocator.value?.href.toString().substringBefore('#')
        val linkHref = link.href.toString().substringBefore('#')

        if (currentHref == linkHref && !link.href.toString().contains('#')) {
            _showToc.value = false
        } else {
            viewModelScope.launch {
                _navigationEvent.emit(link)
            }
            _showToc.value = false
        }
    }

    fun toggleSearch() {
        if (_showSearch.value) {
            _bookSearchQuery.value = ""
            _searchResults.value = emptyList()
        }
        _showSearch.value = !_showSearch.value
    }

    fun toggleHighlights() {
        _showHighlights.value = !_showHighlights.value
    }

    fun searchInBook(query: String) {
        val pub = _publication.value ?: return
        val sanitized = query.filter { !it.isISOControl() }.trim()

        if (sanitized.length > 100) {
            _snackbarMessage.value = "Query too long"
            return
        }

        _bookSearchQuery.value = sanitized
        if (sanitized.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isBookSearching.value = true
        _searchResults.value = emptyList()

        viewModelScope.launch {
            try {
                val svc = pub.findService(SearchService::class)
                if (svc == null) {
                    _snackbarMessage.value = "Search not supported"
                    _isBookSearching.value = false
                    return@launch
                }

                val iter = svc.search(sanitized)
                val res = mutableListOf<Locator>()
                while (res.size < 50) {
                    val c = iter.next().getOrNull() ?: break
                    res.addAll(c.locators)
                    if (c.locators.isEmpty()) break
                }
                _searchResults.value = res
            } catch (e: Exception) {
                _snackbarMessage.value = e.message
            } finally {
                _isBookSearching.value = false
            }
        }
    }

    fun onSearchResultClicked(l: Locator) {
        viewModelScope.launch {
            _showSearch.value = false
            _jumpEvent.emit(l)
            _bookSearchQuery.value = ""
            _searchResults.value = emptyList()
        }
    }

    fun onHighlightClicked(h: HighlightEntity) {
        viewModelScope.launch {
            try {
                val l = Locator.fromJSON(JSONObject(h.locatorJson))
                if (l != null) {
                    _showHighlights.value = false
                    _jumpEvent.emit(l)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun updateProgress(l: Locator) {
        if (isFirstLocator) {
            isFirstLocator = false
            val targetProg = _initialLocator.value?.locations?.totalProgression ?: 0.0
            val emittedProg = l.locations.totalProgression ?: 0.0
            if (emittedProg < 0.01 && targetProg > 0.01) return
        }

        _currentLocator.value = l
        val pos = l.locations.position ?: 0
        var prog = if (currentTotalPositions > 0 && pos > 0) {
            pos.toDouble() / currentTotalPositions.toDouble()
        } else {
            l.locations.totalProgression ?: 0.0
        }

        if (currentTotalPositions in 1..pos) {
            prog = 1.0
        } else if (prog >= 0.996) {
            prog = 1.0
        }

        val pct = if (prog >= 1.0) 100 else (prog * 100).toInt()

        _currentPageInfo.value = if (pos > 0 && currentTotalPositions > 0) {
            "Page $pos of $currentTotalPositions ($pct%)"
        } else if (pos > 0) {
            "Page $pos ($pct%)"
        } else {
            "$pct% completed"
        }

        val uri = _currentLocalUri.value ?: return
        val json = l.toJSON().toString()

        viewModelScope.launch {
            val profileId = vaultRepository.activeVaultId.first()
            bookDao.updateBookProgressByUri(uri, profileId, prog, json, System.currentTimeMillis())
        }
    }

    fun closeBook() {
        val finalUri = _currentLocalUri.value
        val finalLocator = _currentLocator.value

        viewModelScope.launch {
            if (finalUri != null && finalLocator != null) {
                val json = finalLocator.toJSON().toString()
                val prog = finalLocator.locations.totalProgression ?: 0.0
                val profileId = vaultRepository.activeVaultId.first()
                bookDao.updateBookProgressByUri(
                    finalUri,
                    profileId,
                    prog,
                    json,
                    System.currentTimeMillis()
                )
            }

            closeTts()
            readiumManager.closePublication()
            _publication.value = null
            _currentLocator.value = null
            _initialLocator.value = null
            _bookSearchQuery.value = ""
            _searchResults.value = emptyList()
            _showHighlights.value = false
            _showBookmarks.value = false
            _currentLocalUri.value = null
            _currentBookHash.value = null
        }
    }

    fun prepareHighlight(l: Locator) {
        pendingHighlightLocator = l
        _showTagSelector.value = true
    }

    fun addTestHighlightAtCurrentLocator() {
        val locator = _currentLocator.value ?: return
        val bookHash = _currentBookHash.value ?: return
        val einkEnabled = isEinkEnabled.value

        viewModelScope.launch {
            val profileId = vaultRepository.activeVaultId.first()
            highlightDao.insertHighlight(
                HighlightEntity(
                    bookHashId = bookHash,
                    profileId = profileId,
                    locatorJson = locator.toJSON().toString(),
                    text = locator.text.highlight ?: "Test Highlight",
                    color = if (einkEnabled) Color.DKGRAY else Color.YELLOW,
                    tag = "Test"
                )
            )
            _snackbarMessage.value = "Test highlight created"
        }
    }

    fun saveHighlightWithTag(tag: String) {
        val l = pendingHighlightLocator ?: return
        val u = _currentBookHash.value ?: return
        val e = isEinkEnabled.value

        viewModelScope.launch {
            val profileId = vaultRepository.activeVaultId.first()
            highlightDao.insertHighlight(
                HighlightEntity(
                    bookHashId = u,
                    profileId = profileId,
                    locatorJson = l.toJSON().toString(),
                    text = l.text.highlight ?: "Selected",
                    color = if (e) Color.DKGRAY else Color.YELLOW,
                    tag = tag
                )
            )
            dismissTagSelector()
        }
    }

    fun deleteHighlight(id: String) {
        viewModelScope.launch {
            highlightDao.deleteHighlightById(id)
        }
    }

    fun dismissTagSelector() {
        _showTagSelector.value = false
        pendingHighlightLocator = null
    }

    fun onTextSelected(t: String) {
        currentSelectedText = t
        _isBottomSheetVisible.value = true
    }

    fun onActionExplain() {
        _isDictionaryLookup.value = false
        _isDictionaryLoading.value = false
        _isImageResponse.value = false
        if (!_bookAiEnabled.value) return

        viewModelScope.launch {
            performAiAction("Thinking...") {
                aiRepository.explainContext(currentSelectedText)
            }
        }
    }

    fun onActionWhoIsThis() {
        _isDictionaryLookup.value = false
        _isDictionaryLoading.value = false
        _isImageResponse.value = false
        if (!_bookAiEnabled.value) return

        viewModelScope.launch {
            performAiAction("Investigating...") {
                aiRepository.whoIsThis(
                    currentSelectedText,
                    _publication.value?.metadata?.title ?: "",
                    ""
                )
            }
        }
    }

    fun onActionVisualize() {
        _isDictionaryLookup.value = false
        _isDictionaryLoading.value = false
        if (!_bookAiEnabled.value) return

        viewModelScope.launch {
            performAiAction("Drawing...") {
                aiRepository.visualizeText(currentSelectedText)
            }
            _isImageResponse.value = true
        }
    }

    private suspend fun performAiAction(message: String, action: suspend () -> String) {
        _aiResponse.value = message
        try {
            _aiResponse.value = action()
        } catch (e: Exception) {
            _aiResponse.value = e.message ?: ""
        }
    }

    fun dismissRecap() {
        _recapText.value = null
    }

    fun dismissBottomSheet() {
        _isBottomSheetVisible.value = false
        clearAiState()
    }

    private fun clearAiState() {
        _aiResponse.value = ""
        _isImageResponse.value = false
        _isDictionaryLookup.value = false
        _isDictionaryLoading.value = false
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun lookupWord(word: String) {
        val trimmedWord = word.trim()
        val wordCount = trimmedWord.split(Regex("\\s+")).size

        if (wordCount > 5 || trimmedWord.length > 50) {
            _isBottomSheetVisible.value = true
            _isDictionaryLookup.value = true
            _isDictionaryLoading.value = false
            _aiResponse.value = "Selection too long for dictionary."
            return
        }

        viewModelScope.launch {
            val isEmbedded = settingsRepo.getUseEmbeddedDictionary().first()
            _isDictionaryLookup.value = true

            val definition: String?
            if (isEmbedded) {
                _isBottomSheetVisible.value = true
                _aiResponse.value = "Searching device dictionaries..."
                _isDictionaryLoading.value = true
                try {
                    definition = dictionaryRepository.getDefinition(word)
                    _aiResponse.value = definition ?: "No definition found in local dictionary."
                } catch (_: Exception) {
                    _aiResponse.value = "Error searching local dictionaries."
                } finally {
                    _isDictionaryLoading.value = false
                }
            } else {
                _isBottomSheetVisible.value = true
                _aiResponse.value = "Searching embedded directory..."
                _isDictionaryLoading.value = true
                definition = dictionaryRepository.getjsonDefinition(word)
                _aiResponse.value = definition ?: "No definition found in embedded json."
                _isDictionaryLoading.value = false
            }
        }
    }

    fun onReaderPause(locationJson: String) {
        val uri = _currentLocalUri.value ?: return
        if (locationJson.isBlank() && _currentLocator.value == null) return

        viewModelScope.launch(AppCoroutineConfig.io) {
            val finalJson = if (locationJson.isNotBlank()) {
                locationJson
            } else {
                _currentLocator.value?.toJSON()?.toString() ?: return@launch
            }

            val prog = try {
                JSONObject(finalJson).optJSONObject("locations")?.optDouble("totalProgression") ?: 0.0
            } catch (_: Exception) {
                0.0
            }

            val profileId = vaultRepository.activeVaultId.first()
            bookDao.updateBookProgressByUri(uri, profileId, prog, finalJson, System.currentTimeMillis())
            yield()
            withContext(NonCancellable) {
                syncRepository.sync()
            }
        }
    }

    fun resetLayoutToDefaults() {
        viewModelScope.launch {
            settingsRepo.resetLayoutPreferences()
        }
    }

    private val ttsListener = object : TtsNavigator.Listener {
        override fun onStopRequested() {
            ttsNavigator?.pause()
        }
    }

    suspend fun initTts() {
        if (ttsNavigator != null) return
        val currentPub = _publication.value ?: return

        val factory = AndroidTtsNavigatorFactory(
            application = application,
            publication = currentPub,
            ttsEngineProvider = AndroidTtsEngineProvider(application)
        )

        factory?.createNavigator(ttsListener)?.onSuccess { navigator ->
            this.ttsNavigator = navigator

            viewModelScope.launch {
                combine(
                    settingsRepo.ttsSettings,
                    _currentTtsVoice,
                    _currentTtsLanguage
                ) { prefs: TtsSettings, voice: AndroidTtsEngine.Voice?, lang: String ->
                    Triple(prefs, voice, lang)
                }.collect { (prefs, voice, langCode) ->
                    val activeLangObj = if (langCode == "default" || langCode.isEmpty()) {
                        voice?.language
                    } else {
                        org.readium.r2.shared.util.Language(langCode)
                    }

                    val voiceMap = if (voice != null && activeLangObj != null) {
                        mapOf(activeLangObj to voice.id)
                    } else {
                        emptyMap()
                    }

                    val newPrefs = AndroidTtsPreferences(
                        speed = prefs.defaultSpeed.toDouble(),
                        pitch = prefs.pitch.toDouble(),
                        language = activeLangObj,
                        voices = voiceMap
                    )
                    navigator.submitPreferences(newPrefs)

                    val isPlaying = navigator.playback.value.playWhenReady &&
                            navigator.playback.value.state is TtsNavigator.State.Ready
                    manageSleepTimer(prefs.sleepTimerMinutes, isPlaying)
                }
            }

            viewModelScope.launch {
                navigator.playback.collect { playback ->
                    val isPlaying = playback.playWhenReady &&
                            playback.state is TtsNavigator.State.Ready

                    _isTtsActive.value = isPlaying
                    if (!isPlaying) {
                        _ttsDecoration.value = emptyList()
                    }

                    val currentMins = settingsRepo.ttsSettings.first().sleepTimerMinutes
                    manageSleepTimer(currentMins, isPlaying)
                }
            }

            viewModelScope.launch {
                navigator.location.collect { ttsLocation ->
                    val currentPrefs = settingsRepo.ttsSettings.first()
                    val utteranceText = ttsLocation.utterance
                    val locator = ttsLocation.utteranceLocator

                    if (locator != null && _isTtsActive.value) {
                        if (lockedOutUtteranceText != null) {
                            if (utteranceText == lockedOutUtteranceText) {
                                return@collect
                            } else {
                                lockedOutUtteranceText = null
                            }
                        }

                        if (currentPrefs.isAutoPageTurnEnabled) {
                            _jumpEvent.emit(locator)
                        }

                        val ttsStyle = if (currentPrefs.visualStyle == "underline") {
                            Decoration.Style.Underline(tint = android.graphics.Color.BLACK)
                        } else {
                            Decoration.Style.Highlight(
                                tint = android.graphics.Color.argb(80, 255, 255, 0)
                            )
                        }

                        _ttsDecoration.value = listOf(
                            Decoration(
                                id = "tts-active-utterance",
                                locator = locator,
                                style = ttsStyle
                            )
                        )
                    } else {
                        _ttsDecoration.value = emptyList()
                    }
                }
            }
        }?.onFailure {
            _snackbarMessage.value = "TTS Initialization failed"
        }
    }

    fun getHumanVoiceName(voiceId: String, index: Int): String? {
        if (!voiceId.endsWith("local", ignoreCase = true)) return null

        val parts = voiceId.split("-")
        return try {
            val lang = parts.getOrNull(0) ?: return voiceId
            val region = parts.getOrNull(1) ?: return voiceId
            val locale = Locale.Builder().setLanguage(lang).setRegion(region).build()
            "${locale.displayCountry} (${locale.displayLanguage}) • Voice $index"
        } catch (_: Exception) {
            voiceId
        }
    }

    fun toggleTts() {
        _showTtsBar.value = true
        viewModelScope.launch {
            if (ttsNavigator == null) initTts()
            val nav = ttsNavigator ?: return@launch
            val playback = nav.playback.value
            val isPlaying = playback.playWhenReady &&
                    playback.state is TtsNavigator.State.Ready

            if (isPlaying) {
                nav.pause()
            } else {
                val visualNav =
                    visualNavigatorProvider?.invoke() as? org.readium.r2.navigator.epub.EpubNavigatorFragment
                val screenLocator = visualNav?.firstVisibleElementLocator() ?: currentLocator.value
                lockedOutUtteranceText = nav.location.value?.utterance
                if (screenLocator != null) nav.go(screenLocator)
                nav.play()
            }
        }
    }

    fun closeTts() {
        ttsNavigator?.close()
        ttsNavigator = null
        _isTtsActive.value = false
        _ttsDecoration.value = emptyList()
        _showTtsBar.value = false
    }

    override fun onCleared() {
        super.onCleared()
        closeTts()
    }

    fun updateTtsSpeed(newSpeed: Float) {
        viewModelScope.launch {
            settingsRepo.setTtsDefaultSpeed(newSpeed.coerceIn(0.5f, 2.5f))
        }
    }

    fun updateTtsPitch(newPitch: Float) {
        viewModelScope.launch {
            settingsRepo.setTtsPitch(newPitch.coerceIn(0.5f, 2.0f))
        }
    }

    fun setTtsAutoPageTurn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setTtsAutoPageTurn(enabled)
        }
    }

    fun setTtsVisualStyle(style: String) {
        viewModelScope.launch {
            settingsRepo.setTtsVisualStyle(style)
        }
    }

    fun openTtsSettings() {
        _showTtsSettingsSheet.value = true
    }

    fun closeTtsSettings() {
        _showTtsSettingsSheet.value = false
    }

    fun skipForward() {
        viewModelScope.launch {
            val visualNav =
                visualNavigatorProvider?.invoke() as? org.readium.r2.navigator.epub.EpubNavigatorFragment
                    ?: return@launch
            val nav = ttsNavigator ?: return@launch
            val oldLocator = visualNav.firstVisibleElementLocator()

            lockedOutUtteranceText = nav.location.value.utterance
            nav.pause()
            visualNav.goForward(animated = false)

            var newLocator = visualNav.firstVisibleElementLocator()
            var attempts = 0
            while (newLocator == oldLocator && attempts < 20) {
                kotlinx.coroutines.delay(50)
                newLocator = visualNav.firstVisibleElementLocator()
                attempts++
            }

            if (newLocator != null && newLocator != oldLocator) {
                nav.go(newLocator)
                nav.play()
            } else {
                lockedOutUtteranceText = null
            }
        }
    }

    fun skipBackward() {
        viewModelScope.launch {
            val visualNav =
                visualNavigatorProvider?.invoke() as? org.readium.r2.navigator.epub.EpubNavigatorFragment
                    ?: return@launch
            val nav = ttsNavigator ?: return@launch
            val oldLocator = visualNav.firstVisibleElementLocator()

            lockedOutUtteranceText = nav.location.value.utterance
            nav.pause()
            visualNav.goBackward(animated = false)

            var newLocator = visualNav.firstVisibleElementLocator()
            var attempts = 0
            while (newLocator == oldLocator && attempts < 20) {
                kotlinx.coroutines.delay(50)
                newLocator = visualNav.firstVisibleElementLocator()
                attempts++
            }

            if (newLocator != null && newLocator != oldLocator) {
                nav.go(newLocator)
                nav.play()
            } else {
                lockedOutUtteranceText = null
            }
        }
    }

    fun dismissChapterErrorAndClose() {
        _hasChapterError.value = false
        closeBook()
    }

    private fun manageSleepTimer(minutes: Int, isPlaying: Boolean) {
        sleepTimerJob?.cancel()
        if (minutes > 0 && isPlaying) {
            sleepTimerJob = viewModelScope.launch {
                val delayMillis = minutes * 60 * 1000L
                kotlinx.coroutines.delay(delayMillis)
                ttsNavigator?.pause()
                settingsRepo.setTtsSleepTimer(0)
            }
        }
    }

    fun setBookSpecificVoice(voice: AndroidTtsEngine.Voice) {
        _currentTtsVoice.value = voice
    }

    fun setTtsLanguage(languageCode: String) {
        viewModelScope.launch {
            _currentTtsVoice.value = null
            _currentTtsLanguage.value = languageCode
        }
    }

    fun getVoicesForLanguage(languageCode: String): List<Voice> {
        val nav = ttsNavigator ?: return emptyList()
        val targetLangCode = if (languageCode == "default" || languageCode.isEmpty()) {
            Locale.getDefault().language
        } else {
            languageCode.take(2)
        }

        return nav.voices.filter { it.language.code.startsWith(targetLangCode) }
    }
}