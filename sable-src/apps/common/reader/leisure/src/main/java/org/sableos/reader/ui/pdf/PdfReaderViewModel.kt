package org.sableos.reader.ui.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.sableos.reader.core.pdf.PdfDocumentOpener
import org.sableos.reader.core.pdf.PdfOpenResult
import org.sableos.reader.engine.pdf.PdfDocumentSession
import org.sableos.reader.engine.pdf.PdfNavigator
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.PageLocator
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.VaultRepository
import timber.log.Timber

/**
 * Owns one open PDF: the bounded-cache render session, page position, bookmarks and progress persistence.
 * Everything touching the renderer runs under [renderLock] off the main thread.
 */
@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val bookmarkDao: ItemBookmarkDao,
    private val vaultRepository: VaultRepository,
    private val opener: PdfDocumentOpener,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = mutableState.asStateFlow()

    private val renderLock = Mutex()
    private var session: PdfDocumentSession<Bitmap>? = null
    private var navigator: PdfNavigator? = null
    private var openedHash: String? = null
    private var profileId: String = ""
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var renderJob: Job? = null

    fun open(bookHash: String, chromeInitiallyVisible: Boolean, transientChrome: Boolean) {
        if (openedHash == bookHash) return
        openedHash = bookHash
        mutableState.value = PdfReaderUiState(chromeVisible = chromeInitiallyVisible, transientChrome = transientChrome)
        viewModelScope.launch { load(bookHash) }
    }

    private suspend fun load(bookHash: String) {
        profileId = vaultRepository.activeVaultId.first()
        val book = bookDao.getBookByHash(bookHash, profileId)
        val uri = book?.localUri?.let(Uri::parse)
        if (book == null || uri == null) {
            mutableState.update { it.copy(isLoading = false, error = PdfReaderError.NotFound) }
            return
        }
        when (val result = opener.open(uri)) {
            is PdfOpenResult.Failed ->
                mutableState.update { it.copy(isLoading = false, error = PdfReaderError.CannotOpen(result.reason)) }
            is PdfOpenResult.Opened -> {
                session = result.session
                val saved = LocatorCodec.decode(book.progressJson) as? PageLocator
                val nav = PdfNavigator(result.session.pageCount, saved?.pageIndex ?: 0)
                navigator = nav
                mutableState.update {
                    it.copy(title = book.title, pageCount = nav.pageCount, pageIndex = nav.currentPage)
                }
                bookDao.markOpened(bookHash, profileId, System.currentTimeMillis())
                observeBookmarks(bookHash)
                render()
            }
        }
    }

    private fun observeBookmarks(bookHash: String) {
        viewModelScope.launch {
            bookmarkDao.getBookmarks(bookHash, profileId).collect { rows ->
                val items = rows.mapNotNull { row ->
                    (LocatorCodec.decode(row.locatorJson) as? PageLocator)
                        ?.let { PdfBookmarkUi(row.bookmarkId, it.pageIndex, row.label) }
                }
                mutableState.update { it.copy(bookmarks = items.sortedBy(PdfBookmarkUi::pageIndex)) }
            }
        }
    }

    /** Called with the measured size of the page area; a changed size invalidates stale cached renders. */
    fun onViewportChanged(width: Int, height: Int) {
        if (!isNewViewport(width, height)) return
        val resized = viewportWidth != 0
        viewportWidth = width
        viewportHeight = height
        viewModelScope.launch {
            if (resized) renderLock.withLock { session?.invalidate() }
            render()
        }
    }

    private fun hasViewport(): Boolean = viewportWidth > 0 && viewportHeight > 0

    private fun isNewViewport(width: Int, height: Int): Boolean =
        width > 0 && height > 0 && (width != viewportWidth || height != viewportHeight)

    private fun render() {
        val nav = navigator
        val active = session
        if (nav == null || active == null || !hasViewport()) return
        val index = nav.currentPage
        val width = viewportWidth
        val height = viewportHeight
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            mutableState.update { it.copy(pageIndex = index, isLoading = it.page == null) }
            val bitmap = renderPage(active, index, width, height) ?: return@launch
            mutableState.update { it.copy(page = bitmap, pageIndex = index, isLoading = false) }
            persistProgress(nav)
            prefetch(active, nav, index, width, height)
        }
    }

    private suspend fun renderPage(active: PdfDocumentSession<Bitmap>, index: Int, width: Int, height: Int): Bitmap? =
        withContext(AppCoroutineConfig.io) {
            renderLock.withLock {
                try {
                    active.page(index, width, height).also { active.retainAround(index) }
                } catch (e: IllegalStateException) {
                    Timber.d(e, "PDF closed while rendering page %d", index)
                    null
                }
            }
        }

    private suspend fun prefetch(
        active: PdfDocumentSession<Bitmap>,
        nav: PdfNavigator,
        index: Int,
        width: Int,
        height: Int,
    ) {
        nav.adjacentPages().filter { it != index }.forEach { neighbour ->
            renderPage(active, neighbour, width, height)
        }
    }

    private fun persistProgress(nav: PdfNavigator) {
        val hash = openedHash ?: return
        val progress = nav.toProgress(System.currentTimeMillis())
        viewModelScope.launch {
            bookDao.updateTypedProgress(
                bookHash = hash,
                profileId = profileId,
                progress = progress.fraction,
                progressJson = LocatorCodec.encode(progress.locator),
                finished = progress.isFinished,
                timestamp = progress.updatedAt,
            )
        }
    }

    /** Single entry point for user intents coming from the screen, keyboard and touch. */
    fun onAction(action: PdfUiAction) {
        when (action) {
            PdfUiAction.NextPage -> move { it.next() }
            PdfUiAction.PreviousPage -> move { it.previous() }
            is PdfUiAction.GoToPage -> move { it.goTo(action.pageNumber - 1) }
            PdfUiAction.ToggleBookmark -> toggleBookmark()
            is PdfUiAction.DeleteBookmark -> viewModelScope.launch { bookmarkDao.deleteBookmark(action.id) }
            is PdfUiAction.Show -> mutableState.update { it.copy(overlay = action.overlay) }
            PdfUiAction.ToggleChrome -> mutableState.update { it.copy(chromeVisible = !it.chromeVisible) }
        }
    }

    private fun move(step: (PdfNavigator) -> Boolean) {
        val nav = navigator ?: return
        if (step(nav)) render()
    }

    private fun toggleBookmark() {
        val nav = navigator ?: return
        val hash = openedHash ?: return
        val existing = mutableState.value.bookmarks.firstOrNull { it.pageIndex == nav.currentPage }
        viewModelScope.launch {
            if (existing != null) {
                bookmarkDao.deleteBookmark(existing.id)
            } else {
                bookmarkDao.upsertBookmark(
                    ItemBookmarkEntity(
                        bookHash = hash,
                        profileId = profileId,
                        locatorJson = LocatorCodec.encode(nav.locator()),
                        label = "Page ${PdfNavigator.displayNumber(nav.currentPage)}",
                    ),
                )
            }
        }
    }

    /** Closes the topmost transient surface. Returns false when nothing was open, i.e. the caller should leave. */
    fun dismissTransientSurface(): Boolean {
        val state = mutableState.value
        return when {
            state.overlay != PdfOverlay.NONE -> {
                mutableState.update { it.copy(overlay = PdfOverlay.NONE) }
                true
            }
            state.transientChrome && state.chromeVisible -> {
                mutableState.update { it.copy(chromeVisible = false) }
                true
            }
            else -> false
        }
    }

    /** Persists the position and releases the document. Safe to call repeatedly. */
    fun closeDocument() {
        renderJob?.cancel()
        navigator?.let(::persistProgress)
        session?.close()
        session = null
    }

    override fun onCleared() {
        closeDocument()
        super.onCleared()
    }
}
