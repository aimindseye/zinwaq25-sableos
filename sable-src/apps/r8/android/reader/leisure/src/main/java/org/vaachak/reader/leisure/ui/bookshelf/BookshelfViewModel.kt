package org.vaachak.reader.leisure.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.AiRepository
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.data.repository.SyncRepository
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.BookshelfPreferences
import org.vaachak.reader.core.domain.model.HighlightEntity
import javax.inject.Inject

data class BookshelfUiState(
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val activeFilter: String = "All",
    val availableFilters: List<String> = listOf("All"),
    val filterCounts: Map<String, Int> = emptyMap(),
    val sortOrder: SortOrder = SortOrder.PROGRESS,
    val isEink: Boolean = false,
    val isOfflineMode: Boolean = false,
    val lastSyncTime: Long = 0L,
    val syncUsername: String = "",
    val snackbarMessage: String? = null,
    val bookshelfPrefs: BookshelfPreferences = BookshelfPreferences(),
    val heroBook: BookEntity? = null,
    val groupedLibrary: Map<String, List<BookEntity>> = emptyMap(),
    val selectedStackName: String? = null,
    val recapState: Map<String, String> = emptyMap(),
    val loadingRecapUri: String? = null,
    val bookmarksSheetUri: String? = null,
    val selectedBookmarks: List<HighlightEntity> = emptyList(),
    val booksWithBookmarks: Set<String> = emptySet()
)

enum class SortOrder { TITLE, AUTHOR, DATE_ADDED, PROGRESS }

private data class FilterState(
    val query: String,
    val filter: String,
    val sort: SortOrder,
    val selectedStack: String?
)

private data class PrefsState(
    val isEink: Boolean,
    val isOfflineMode: Boolean,
    val lastSyncTime: Long,
    val syncUsername: String,
    val bookshelfPrefs: BookshelfPreferences
)

private data class DialogState(
    val snackbar: String?,
    val recap: Map<String, String>,
    val loadingUri: String?,
    val sheetUri: String?,
    val bookmarksSet: Set<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val libraryRepository: LibraryRepository,
    private val highlightDao: HighlightDao,
    private val aiRepository: AiRepository,
    settingsRepo: SettingsRepository,
    private val syncRepository: SyncRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val sharingStarted: SharingStarted = AppCoroutineConfig.whileSubscribed

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow("All")
    private val _sortOrder = MutableStateFlow(SortOrder.PROGRESS)
    private val _isRefreshing = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _selectedStackName = MutableStateFlow<String?>(null)
    private val _recapState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _loadingRecapUri = MutableStateFlow<String?>(null)
    private val _bookmarksSheetBookUri = MutableStateFlow<String?>(null)

    private val offlineModeState: StateFlow<Boolean> = settingsRepo.isOfflineModeEnabled
        .stateIn(viewModelScope, AppCoroutineConfig.eagerly, false)

    private val filterFlow = combine(
        _searchQuery,
        _activeFilter,
        _sortOrder,
        _selectedStackName
    ) { query, filter, sort, stack ->
        FilterState(query, filter, sort, stack)
    }.distinctUntilChanged()

    private val prefsFlow = combine(
        settingsRepo.isEinkEnabled,
        offlineModeState,
        settingsRepo.lastSyncTimestamp,
        settingsRepo.syncUsername,
        settingsRepo.bookshelfPreferences
    ) { eink, offline, lastSync, username, shelfPrefs ->
        PrefsState(eink, offline, lastSync, username, shelfPrefs)
    }.distinctUntilChanged()

    private val activeBooksFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> bookDao.getAllBooksSortedByRecent(profileId) }

    private val activeBookmarksFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> highlightDao.getBooksWithBookmarks(profileId) }

    private val dialogsFlow = combine(
        _snackbarMessage,
        _recapState,
        _loadingRecapUri,
        _bookmarksSheetBookUri,
        activeBookmarksFlow
    ) { snackbar, recap, loadingUri, sheetUri, bookHashes ->
        DialogState(
            snackbar = snackbar,
            recap = recap,
            loadingUri = loadingUri,
            sheetUri = sheetUri,
            bookmarksSet = bookHashes.toSet()
        )
    }.distinctUntilChanged()

    val uiState: StateFlow<BookshelfUiState> = combine(
        activeBooksFlow,
        filterFlow,
        prefsFlow,
        dialogsFlow
    ) { books, filters, prefs, dialogs ->
        withContext(AppCoroutineConfig.default) {
            val searchedBooks = if (filters.query.isNotBlank()) {
                books.filter {
                    it.title.contains(filters.query, ignoreCase = true) ||
                            it.author.contains(filters.query, ignoreCase = true)
                }
            } else {
                books
            }

            val filterCounts = mutableMapOf("All" to searchedBooks.size)
            searchedBooks
                .mapNotNull { it.language?.takeIf { language -> language.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .forEach { (lang, count) -> filterCounts[lang] = count }

            val allFilters = listOf("All") + filterCounts.keys
                .filter { it != "All" }
                .sorted()

            val languageFilteredBooks = if (filters.filter != "All") {
                searchedBooks.filter { it.language.equals(filters.filter, ignoreCase = true) }
            } else {
                searchedBooks
            }

            val heroBook = if (filters.query.isBlank() && filters.filter == "All") {
                languageFilteredBooks.firstOrNull { it.progress > 0.0 && it.progress < 0.99 }
            } else {
                null
            }

            val libraryList = when (filters.sort) {
                SortOrder.TITLE -> languageFilteredBooks.sortedBy { it.title }
                SortOrder.AUTHOR -> languageFilteredBooks.sortedBy { it.author }
                SortOrder.DATE_ADDED -> languageFilteredBooks.sortedByDescending { it.addedDate }
                SortOrder.PROGRESS -> languageFilteredBooks.sortedByDescending { it.progress }
            }

            val groupedLibrary = if (prefs.bookshelfPrefs.groupBySeries) {
                libraryList.groupBy { it.author }
            } else {
                mapOf("All Books" to libraryList)
            }

            BookshelfUiState(
                isLoading = _isRefreshing.value,
                searchQuery = filters.query,
                activeFilter = filters.filter,
                availableFilters = allFilters,
                filterCounts = filterCounts,
                sortOrder = filters.sort,
                isEink = prefs.isEink,
                isOfflineMode = prefs.isOfflineMode,
                lastSyncTime = prefs.lastSyncTime,
                syncUsername = prefs.syncUsername,
                snackbarMessage = dialogs.snackbar,
                bookshelfPrefs = prefs.bookshelfPrefs,
                heroBook = heroBook,
                groupedLibrary = groupedLibrary,
                selectedStackName = filters.selectedStack,
                recapState = dialogs.recap,
                loadingRecapUri = dialogs.loadingUri,
                bookmarksSheetUri = dialogs.sheetUri,
                booksWithBookmarks = dialogs.bookmarksSet
            )
        }
    }.stateIn(viewModelScope, sharingStarted, BookshelfUiState())

    init {
        viewModelScope.launch {
            val startupDelayMs = AppCoroutineConfig.bookshelfStartupSyncDelayMs
            if (startupDelayMs > 0) {
                delay(startupDelayMs)
            }
            triggerSilentSync()
        }
    }

    private suspend fun triggerSilentSync() {
        try {
            syncRepository.sync()
        } catch (_: Exception) {
        }
    }

    fun openStack(stackName: String) {
        _selectedStackName.value = stackName
    }

    fun closeStack() {
        _selectedStackName.value = null
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = syncRepository.sync()
            _isRefreshing.value = false
            result.onFailure {
                _snackbarMessage.value = "Sync failed: Check connection"
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: String) {
        _activeFilter.value = filter
    }

    fun updateSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun deleteBookByUri(uri: String) = viewModelScope.launch {
        val profileId = vaultRepository.activeVaultId.first()
        bookDao.deleteBookByUri(uri, profileId)
    }

    fun openBookmarksSheet(uri: String) {
        _bookmarksSheetBookUri.value = uri
    }

    fun dismissBookmarksSheet() {
        _bookmarksSheetBookUri.value = null
    }

    fun clearRecap(uri: String) {
        _recapState.value -= uri
    }

    fun importBook(uri: Uri) = viewModelScope.launch {
        libraryRepository.importBook(uri)
            .onSuccess { _snackbarMessage.value = it }
            .onFailure { _snackbarMessage.value = it.message ?: "Failed to import" }
    }

    fun getQuickRecap(book: BookEntity) {
        if (offlineModeState.value) {
            _snackbarMessage.value = "Offline Mode enabled. Connect to use Recall."
            return
        }

        viewModelScope.launch {
            _loadingRecapUri.value = book.localUri
            try {
                val profileId = vaultRepository.activeVaultId.first()
                val highlights = highlightDao
                    .getHighlightsForBook(book.bookHash, profileId)
                    .first()
                    .take(10)
                    .joinToString("\n") { it.text }

                val summary = aiRepository.getRecallSummary(book.title, highlights)
                _recapState.value += (book.bookHash to summary)
            } finally {
                _loadingRecapUri.value = null
            }
        }
    }

    fun getGlobalRecap() {
        if (offlineModeState.value) {
            _snackbarMessage.value = "Offline Mode enabled. Connect to use Recall."
            return
        }

        viewModelScope.launch {
            _loadingRecapUri.value = "GLOBAL"
            try {
                val profileId = vaultRepository.activeVaultId.first()
                val activeBooks = bookDao
                    .getAllBooksSortedByRecent(profileId)
                    .first()
                    .filter { it.progress > 0.05f }

                if (activeBooks.isEmpty()) {
                    _snackbarMessage.value = "No books with > 5% progress found to recap."
                    return@launch
                }

                val allHighlights = mutableListOf<String>()
                for (book in activeBooks) {
                    val highlights = highlightDao
                        .getHighlightsForBook(book.bookHash, profileId)
                        .first()
                        .take(5)

                    if (highlights.isNotEmpty()) {
                        allHighlights.add(
                            "From '${book.title}':\n" +
                                    highlights.joinToString("\n") { "- ${it.text}" }
                        )
                    }
                }

                if (allHighlights.isEmpty()) {
                    _snackbarMessage.value = "No highlights found in your active books."
                    return@launch
                }

                val summary = aiRepository.getRecallSummary(
                    "My Active Reading List",
                    allHighlights.joinToString("\n\n")
                )
                _recapState.value += ("GLOBAL" to summary)
            } catch (_: Exception) {
                _snackbarMessage.value = "Failed to generate global recall."
            } finally {
                _loadingRecapUri.value = null
            }
        }
    }
}