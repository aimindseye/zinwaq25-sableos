package org.sableos.reader.ui.comic

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sableos.reader.comic.ComicViewSettings
import org.sableos.reader.comic.ComicViewSettingsCodec
import org.sableos.reader.core.comic.ComicBitmapDecoder
import org.sableos.reader.core.comic.ComicOpenOutcome
import org.sableos.reader.core.comic.ComicOpener
import org.sableos.reader.core.comic.PageDecode
import org.sableos.reader.core.data.local.ItemBookmarkDao
import org.sableos.reader.core.data.local.ItemViewSettingsDao
import org.sableos.reader.core.domain.model.ItemBookmarkEntity
import org.sableos.reader.core.domain.model.ItemViewSettingsEntity
import org.sableos.reader.engine.comic.ByteBudgetCache
import org.sableos.reader.engine.comic.ComicChapter
import org.sableos.reader.engine.comic.ComicNavigator
import org.sableos.reader.engine.comic.ComicPageException
import org.sableos.reader.engine.comic.ComicSource
import org.sableos.reader.engine.comic.ComicViewDefaults
import org.sableos.reader.engine.comic.PageFailure
import org.sableos.reader.model.LocatorCodec
import org.sableos.reader.model.PageLocator
import org.vaachak.reader.core.common.AppCoroutineConfig
import org.vaachak.reader.core.data.local.BookDao
import org.vaachak.reader.core.data.repository.VaultRepository

/** What a page slot should show. */
sealed interface PageResult {
    data class Ready(val bitmap: Bitmap) : PageResult

    data class Unavailable(val problem: ComicPageProblem) : PageResult
}

/**
 * Owns one open comic: the container, the byte-budgeted bitmap cache, position, per-title settings, bookmarks and
 * progress. Decoding is serialised under [decodeLock] so peak memory is one page in flight plus the cache budget.
 */
@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val bookmarkDao: ItemBookmarkDao,
    private val settingsDao: ItemViewSettingsDao,
    private val vaultRepository: VaultRepository,
    private val opener: ComicOpener,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ComicReaderUiState())
    val uiState: StateFlow<ComicReaderUiState> = mutableState.asStateFlow()

    private val scrollEvents =
        MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Page indices the viewer must scroll to for keyboard, buttons or the contents list (not its own swipes). */
    val scrollRequests: SharedFlow<Int> = scrollEvents.asSharedFlow()

    private val decodeLock = Mutex()
    private val cache = ByteBudgetCache<Int, Bitmap>(cacheBudgetBytes(), { it.allocationByteCount.toLong() })
    private val problems = HashMap<Int, ComicPageProblem>()
    private val aspects = HashMap<Int, Float>()
    private var source: ComicSource? = null
    private var navigator: ComicNavigator? = null
    private var openedHash: String? = null
    private var profileId: String = ""
    private var lastTarget: Pair<Int, Int>? = null

    fun open(bookHash: String, chromeInitiallyVisible: Boolean, transientChrome: Boolean) {
        if (openedHash == bookHash) return
        openedHash = bookHash
        mutableState.value =
            ComicReaderUiState(chromeVisible = chromeInitiallyVisible, transientChrome = transientChrome)
        viewModelScope.launch { load(bookHash) }
    }

    private suspend fun load(bookHash: String) {
        profileId = vaultRepository.activeVaultId.first()
        val book = bookDao.getBookByHash(bookHash, profileId)
        val localUri = book?.localUri
        if (book == null || localUri == null) {
            mutableState.update { it.copy(isLoading = false, error = ComicReaderError.NotFound) }
            return
        }
        when (val outcome = withContext(AppCoroutineConfig.io) { opener.open(localUri) }) {
            is ComicOpenOutcome.Failed ->
                mutableState.update { it.copy(isLoading = false, error = ComicReaderError.CannotOpen(outcome.failure)) }
            is ComicOpenOutcome.Opened -> {
                source = outcome.source
                val saved = settingsDao.get(bookHash, profileId)?.settingsJson?.let(ComicViewSettingsCodec::decode)
                val settings = saved ?: ComicViewDefaults.initial(outcome.source.info)
                val start = (LocatorCodec.decode(book.progressJson) as? PageLocator)?.pageIndex ?: 0
                val nav = ComicNavigator(outcome.source.pageCount, start)
                navigator = nav
                mutableState.update {
                    it.copy(
                        title = book.title,
                        pageCount = nav.pageCount,
                        pageIndex = nav.currentPage,
                        settings = settings,
                        chapters = outcome.source.catalog.chapters,
                        isLoading = false,
                    )
                }
                bookDao.markOpened(bookHash, profileId, System.currentTimeMillis())
                observeBookmarks(bookHash)
            }
        }
    }

    private fun observeBookmarks(bookHash: String) {
        viewModelScope.launch {
            bookmarkDao.getBookmarks(bookHash, profileId).collect { rows ->
                val items = rows.mapNotNull { row ->
                    (LocatorCodec.decode(row.locatorJson) as? PageLocator)
                        ?.let { ComicBookmarkUi(row.bookmarkId, it.pageIndex, row.label) }
                }
                mutableState.update { it.copy(bookmarks = items.sortedBy(ComicBookmarkUi::pageIndex)) }
            }
        }
    }

    /** Aspect ratio (width / height) of a page already decoded, so placeholders can reserve the right height. */
    fun knownAspect(index: Int): Float? = aspects[index]

    /** Called with the measured page area; a changed size invalidates renders made for the old size. */
    fun onViewportChanged(width: Int, height: Int) {
        val target = width to height
        if (width <= 0 || height <= 0 || target == lastTarget) return
        lastTarget = target
        invalidateRenders()
    }

    private fun invalidateRenders() {
        viewModelScope.launch {
            decodeLock.withLock {
                cache.clear()
                problems.clear()
            }
            mutableState.update { it.copy(layoutToken = it.layoutToken + 1) }
        }
    }

    /** Returns page [index] decoded for a [targetWidth] x [targetHeight] area, from cache when possible. */
    suspend fun pageBitmap(index: Int, targetWidth: Int, targetHeight: Int): PageResult =
        withContext(AppCoroutineConfig.io) {
            decodeLock.withLock {
                cache[index]?.let { return@withLock PageResult.Ready(it) }
                problems[index]?.let { return@withLock PageResult.Unavailable(it) }
                decodeLocked(index, targetWidth, targetHeight)
            }
        }

    private fun decodeLocked(index: Int, targetWidth: Int, targetHeight: Int): PageResult {
        val active = source
        val read = active?.let { runCatching { it.readPage(index) } }
        val error = read?.exceptionOrNull()
        if (error != null && error !is ComicPageException) throw error
        val bytes = read?.getOrNull()
        return when {
            active == null -> PageResult.Unavailable(ComicPageProblem.UNREADABLE)
            bytes == null -> fail(index, problemOf((error as ComicPageException).failure))
            else -> decodeBytes(index, bytes, targetWidth to targetHeight, active)
        }
    }

    private fun decodeBytes(index: Int, bytes: ByteArray, target: Pair<Int, Int>, active: ComicSource): PageResult =
        when (val decoded = ComicBitmapDecoder.decode(bytes, target.first, target.second, active.limits)) {
            is PageDecode.Decoded -> {
                aspects[index] = decoded.bitmap.width.toFloat() / decoded.bitmap.height.coerceAtLeast(1)
                cache.put(index, decoded.bitmap)
                PageResult.Ready(decoded.bitmap)
            }
            PageDecode.Undecodable -> fail(index, ComicPageProblem.UNDECODABLE)
            PageDecode.TooManyPixels -> fail(index, ComicPageProblem.TOO_MANY_PIXELS)
            PageDecode.OutOfMemory -> {
                cache.clear()
                PageResult.Unavailable(ComicPageProblem.OUT_OF_MEMORY)
            }
        }

    private fun fail(index: Int, problem: ComicPageProblem): PageResult {
        problems[index] = problem
        return PageResult.Unavailable(problem)
    }

    private fun problemOf(failure: PageFailure): ComicPageProblem = when (failure) {
        PageFailure.TOO_LARGE -> ComicPageProblem.TOO_LARGE
        PageFailure.COMPRESSION_BOMB -> ComicPageProblem.COMPRESSION_BOMB
        PageFailure.UNREADABLE -> ComicPageProblem.UNREADABLE
        PageFailure.ENCRYPTED -> ComicPageProblem.ENCRYPTED
    }

    private fun prefetchAround(index: Int) {
        val target = lastTarget ?: return
        val nav = navigator ?: return
        viewModelScope.launch {
            nav.window().filter { it != index }.forEach { pageBitmap(it, target.first, target.second) }
            decodeLock.withLock { cache.retain { it in nav.window(ComicNavigator.PREFETCH_RADIUS + 1) } }
        }
    }

    /** Single entry point for user intents coming from the screen, keyboard and touch. */
    fun onAction(action: ComicUiAction) {
        when (action) {
            ComicUiAction.NextPage -> move(scroll = true) { it.next() }
            ComicUiAction.PreviousPage -> move(scroll = true) { it.previous() }
            is ComicUiAction.GoToPage -> move(scroll = true) { it.goTo(action.pageNumber - 1) }
            ComicUiAction.NextChapter -> chapterMove { nav, chapters -> nav.nextChapterStart(chapters) }
            ComicUiAction.PreviousChapter -> chapterMove { nav, chapters -> nav.previousChapterStart(chapters) }
            is ComicUiAction.PageSettled -> move(scroll = false) { it.goTo(action.pageIndex) }
            ComicUiAction.ToggleBookmark -> toggleBookmark()
            is ComicUiAction.DeleteBookmark -> viewModelScope.launch { bookmarkDao.deleteBookmark(action.id) }
            is ComicUiAction.SetMode -> updateSettings { it.copy(mode = action.mode) }
            is ComicUiAction.SetFit -> updateSettings { it.copy(fit = action.fit) }
            is ComicUiAction.Show -> mutableState.update { it.copy(overlay = action.overlay) }
            ComicUiAction.ToggleChrome -> mutableState.update { it.copy(chromeVisible = !it.chromeVisible) }
        }
    }

    private fun move(scroll: Boolean, step: (ComicNavigator) -> Boolean) {
        val nav = navigator ?: return
        if (step(nav)) {
            mutableState.update { it.copy(pageIndex = nav.currentPage) }
            if (scroll) scrollEvents.tryEmit(nav.currentPage)
            persistProgress(nav)
            prefetchAround(nav.currentPage)
        }
    }

    private fun chapterMove(target: (ComicNavigator, List<ComicChapter>) -> Int?) {
        val nav = navigator ?: return
        target(nav, mutableState.value.chapters)?.let { page -> move(scroll = true) { it.goTo(page) } }
    }

    private fun updateSettings(change: (ComicViewSettings) -> ComicViewSettings) {
        val hash = openedHash ?: return
        val updated = change(mutableState.value.settings)
        mutableState.update { it.copy(settings = updated) }
        invalidateRenders()
        viewModelScope.launch {
            settingsDao.upsert(ItemViewSettingsEntity(hash, profileId, ComicViewSettingsCodec.encode(updated)))
        }
    }

    private fun persistProgress(nav: ComicNavigator) {
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
                        label = "Page ${ComicNavigator.displayNumber(nav.currentPage)}",
                    ),
                )
            }
        }
    }

    /** Closes the topmost transient surface. Returns false when nothing was open, i.e. the caller should leave. */
    fun dismissTransientSurface(): Boolean {
        val state = mutableState.value
        return when {
            state.overlay != ComicOverlay.NONE -> {
                mutableState.update { it.copy(overlay = ComicOverlay.NONE) }
                true
            }
            state.transientChrome && state.chromeVisible -> {
                mutableState.update { it.copy(chromeVisible = false) }
                true
            }
            else -> false
        }
    }

    /** Persists the position and releases the container and every cached bitmap. Safe to call repeatedly. */
    fun closeComic() {
        navigator?.let(::persistProgress)
        cache.clear()
        source?.close()
        source = null
    }

    override fun onCleared() {
        closeComic()
        super.onCleared()
    }

    private companion object {
        const val CACHE_FRACTION_OF_HEAP = 8L
        const val MIN_CACHE_BYTES = 16L * 1024 * 1024
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024

        /** A bounded share of the app heap, never more than 64 MB and never less than 16 MB. */
        fun cacheBudgetBytes(): Long =
            (Runtime.getRuntime().maxMemory() / CACHE_FRACTION_OF_HEAP).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }
}
