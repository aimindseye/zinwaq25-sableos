package org.vaachak.reader.leisure.ui.bookshelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.sableos.reader.core.library.toLibraryItem
import org.sableos.reader.library.LibraryFilters
import org.sableos.reader.library.LibraryViews
import org.sableos.reader.model.PublicationKind
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.local.HighlightDao
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
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
    val snackbarMessage: String? = null,
    val bookshelfPrefs: BookshelfPreferences = BookshelfPreferences(),
    val heroBook: BookEntity? = null,
    val groupedLibrary: Map<String, List<BookEntity>> = emptyMap(),
    val selectedStackName: String? = null,
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
    val bookshelfPrefs: BookshelfPreferences
)

private data class DialogState(
    val snackbar: String?,
    val sheetUri: String?,
    val bookmarksSet: Set<String>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val libraryRepository: LibraryRepository,
    private val highlightDao: HighlightDao,
    settingsRepo: SettingsRepository,
    private val vaultRepository: VaultRepository
) : ViewModel() {

    private val sharingStarted: SharingStarted = AppCoroutineConfig.whileSubscribed

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow("All")
    private val _sortOrder = MutableStateFlow(SortOrder.PROGRESS)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _selectedStackName = MutableStateFlow<String?>(null)
    private val _bookmarksSheetBookUri = MutableStateFlow<String?>(null)

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
        settingsRepo.bookshelfPreferences
    ) { eink, shelfPrefs ->
        PrefsState(eink, shelfPrefs)
    }.distinctUntilChanged()

    private val activeBooksFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> bookDao.getAllBooksSortedByRecent(profileId) }

    private val activeBookmarksFlow = vaultRepository.activeVaultId
        .flatMapLatest { profileId -> highlightDao.getBooksWithBookmarks(profileId) }

    private val dialogsFlow = combine(
        _snackbarMessage,
        _bookmarksSheetBookUri,
        activeBookmarksFlow
    ) { snackbar, sheetUri, bookHashes ->
        DialogState(
            snackbar = snackbar,
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

            // Unified-library chips (state and kind) come first, then the language chips this shelf always had.
            val libraryCounts = LibraryViews.counts(searchedBooks.map { it.toLibraryItem() })
            val allFilters = LibraryFilters.labels(libraryCounts) + filterCounts.keys
                .filter { it != LibraryFilters.ALL }
                .sorted()

            val languageFilteredBooks = searchedBooks.filter { matchesFilter(it, filters.filter) }

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
                isLoading = false,
                searchQuery = filters.query,
                activeFilter = filters.filter,
                availableFilters = allFilters,
                filterCounts = filterCounts,
                sortOrder = filters.sort,
                isEink = prefs.isEink,
                snackbarMessage = dialogs.snackbar,
                bookshelfPrefs = prefs.bookshelfPrefs,
                heroBook = heroBook,
                groupedLibrary = groupedLibrary,
                selectedStackName = filters.selectedStack,
                bookmarksSheetUri = dialogs.sheetUri,
                booksWithBookmarks = dialogs.bookmarksSet
            )
        }
    }.stateIn(viewModelScope, sharingStarted, BookshelfUiState())

    /** One predicate for every chip: the unified-library state and kind chips, or a language. */
    private fun matchesFilter(book: BookEntity, filter: String): Boolean = when {
        filter == LibraryFilters.ALL -> true
        filter == LibraryFilters.CONTINUE -> LibraryFilters.isInProgress(book.finished, book.lastRead, book.progress)
        filter == LibraryFilters.FAVORITES -> book.favorite
        filter == LibraryFilters.FINISHED -> book.finished
        filter == LibraryFilters.NEEDS_FILE -> book.localUri == null
        LibraryFilters.kindOfLabel(filter) != null ->
            PublicationKind.fromStableValue(book.kind) == LibraryFilters.kindOfLabel(filter)
        else -> book.language.equals(filter, ignoreCase = true)
    }

    fun openStack(stackName: String) {
        _selectedStackName.value = stackName
    }

    fun closeStack() {
        _selectedStackName.value = null
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
        libraryRepository.deleteOwnedCopy(uri)
    }

    fun openBookmarksSheet(uri: String) {
        _bookmarksSheetBookUri.value = uri
    }

    fun dismissBookmarksSheet() {
        _bookmarksSheetBookUri.value = null
    }

    /** Attaches the chosen file or folder to a title that was restored from a backup and still needs its file. */
    fun relink(bookHash: String, uri: Uri) = viewModelScope.launch {
        libraryRepository.relink(bookHash, uri)
            .onSuccess { _snackbarMessage.value = it }
            .onFailure { _snackbarMessage.value = it.message ?: "Could not link that file" }
    }

    fun importFolder(tree: Uri) = viewModelScope.launch {
        libraryRepository.importFolder(tree)
            .onSuccess { _snackbarMessage.value = it }
            .onFailure { _snackbarMessage.value = it.message ?: "Failed to import" }
    }

    fun importBook(uri: Uri) = viewModelScope.launch {
        libraryRepository.importBook(uri)
            .onSuccess { _snackbarMessage.value = it }
            .onFailure { _snackbarMessage.value = it.message ?: "Failed to import" }
    }
}
