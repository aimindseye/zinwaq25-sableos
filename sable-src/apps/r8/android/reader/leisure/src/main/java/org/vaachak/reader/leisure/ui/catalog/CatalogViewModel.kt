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


package org.vaachak.reader.leisure.ui.catalog

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.readium.r2.shared.opds.Feed
import org.readium.r2.shared.publication.Contributor
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.vaachak.reader.core.data.repository.GutendexRepository
import org.vaachak.reader.core.data.repository.LibraryRepository
import org.vaachak.reader.core.data.repository.OpdsRepository
import org.vaachak.reader.core.data.repository.SettingsRepository
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.core.utils.OpdsUrlUtils
import timber.log.Timber
import java.io.File
import java.net.URL
import javax.inject.Inject

// --- UI MODELS ---
sealed class CatalogItem {
    data class Server(val title: String, val url: String, val entity: OpdsEntity) : CatalogItem()
    data class Folder(val title: String, val url: String) : CatalogItem()
    data class Book(
        val title: String, val author: String, val imageUrl: String?,
        val format: String, val publication: Publication, val navigationUrl: String? = null,
        val existingBookUri: String? = null
    ) : CatalogItem()
    data class Pagination(val nextUrl: String?, val prevUrl: String?) : CatalogItem()
}

sealed interface CatalogUiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : CatalogUiEvent
    data class NavigateToReader(val bookUri: String) : CatalogUiEvent
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val gutendexRepository: GutendexRepository,
    private val settingsRepo: SettingsRepository,
    private val libraryRepository: LibraryRepository,
    private val application: Application
) : ViewModel() {

    // --- STATE FLOWS ---
    val catalogs: StateFlow<List<OpdsEntity>> = opdsRepository.catalogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _feedItems = MutableStateFlow<List<CatalogItem>>(emptyList())
    val feedItems = _feedItems.asStateFlow()

    private val _screenTitle = MutableStateFlow("Catalogs")
    val screenTitle = _screenTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val breadcrumbs = _breadcrumbs.asStateFlow()

    private val _uiEvent = Channel<CatalogUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    val isEinkEnabled = settingsRepo.isEinkEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isOfflineMode = settingsRepo.isOfflineModeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val historyStack = ArrayDeque<Pair<String, String>>()
    private var currentContextUrl: String? = null
    private var isGutendexSession = false

    init {
        viewModelScope.launch {
            loadServerList()
        }
    }


    // --- NAVIGATION LOGIC ---

    private fun loadServerList() {
        viewModelScope.launch {
            _isLoading.value = true
            _screenTitle.value = "Catalogs"
            historyStack.clear()
            _breadcrumbs.value = emptyList()
            currentContextUrl = null

            val feeds = opdsRepository.allFeeds.first()
            _feedItems.value = feeds.map {
                CatalogItem.Server(it.title, it.url, it)
            }
            _isLoading.value = false
        }
    }

    fun handleItemClick(item: CatalogItem) {
        when (item) {
            is CatalogItem.Server -> openCatalog(item.entity)
            is CatalogItem.Folder -> {
                if (isGutendexSession) {
                    loadGutendexFeed(item.url, item.title, isPagination = false)
                } else {
                    loadFeed(item.url, item.title, addToHistory = true)
                }
            }
            is CatalogItem.Book -> {
                if (item.existingBookUri != null) {
                    viewModelScope.launch { _uiEvent.send(CatalogUiEvent.NavigateToReader(item.existingBookUri)) }
                } else {
                    if (item.navigationUrl != null) loadFeed(item.navigationUrl, item.title, addToHistory = true)
                    else downloadBook(item.publication)
                }
            }
            is CatalogItem.Pagination -> { /* UI Handles this */ }
        }
    }

    fun openCatalog(feed: OpdsEntity) {
        historyStack.clear()
        _feedItems.value = emptyList()
        currentContextUrl = feed.url
        _screenTitle.value = feed.title

        isGutendexSession = feed.url.contains("gutenberg.org") ||
                feed.url.contains("gutendex") ||
                feed.url.endsWith("/books")

        if (isGutendexSession) {
            loadGutendexRoot()
        } else {
            loadFeed(feed.url, "Home", addToHistory = true)
        }
    }

    fun handlePaginationClick(url: String) {
        if (isGutendexSession) {
            loadGutendexFeed(url, null, isPagination = true)
        } else {
            loadFeed(url, null, addToHistory = false)
        }
    }

    fun goBack(): Boolean {
        if (historyStack.isEmpty()) {
            if (currentContextUrl != null) {
                loadServerList()
                return true
            }
            return false
        }

        historyStack.removeLast()

        if (historyStack.isEmpty()) {
            loadServerList()
            return true
        }

        val previous = historyStack.last()
        if (isGutendexSession) {
            loadGutendexFeed(previous.first, previous.second, isPagination = false, addToHistory = false)
        } else {
            loadFeed(previous.first, previous.second, addToHistory = false)
        }
        return true
    }

    fun onBreadcrumbClick(index: Int) {
        if (index < 0 || index >= historyStack.size) return
        while (historyStack.size > index + 1) historyStack.removeLast()

        val target = historyStack.last()
        if (isGutendexSession) {
            if (index == 0) loadGutendexRoot(addToHistory = false)
            else loadGutendexFeed(target.first, target.second, isPagination = false, addToHistory = false)
        } else {
            loadFeed(target.first, target.second, addToHistory = false)
        }
    }

    private fun rollbackBreadcrumb() {
        if (historyStack.isNotEmpty()) {
            historyStack.removeLast()
            updateBreadcrumbs()
        }
    }

    private fun updateBreadcrumbs() {
        _breadcrumbs.value = historyStack.toList()
    }

    // --- GUTENDEX LOGIC ---

    private fun loadGutendexRoot(addToHistory: Boolean = true) {
        viewModelScope.launch {
            if (addToHistory) {
                historyStack.clear()
                historyStack.addLast("" to "Home")
            }
            updateBreadcrumbs()

            val base = currentContextUrl?.trimEnd('/') ?: "https://gutendex.com/books"
            fun makeUrl(params: String): String {
                val sep = if (base.contains("?")) "&" else "?"
                return "$base$sep$params"
            }

            val rootItems = listOf(
                CatalogItem.Folder("Recent Uploads", makeUrl("sort=descending")),
                CatalogItem.Folder("Popular Books", makeUrl("sort=popular")),
                CatalogItem.Folder("Topic: Fiction", makeUrl("topic=fiction")),
                CatalogItem.Folder("Topic: Sci-Fi", makeUrl("topic=science%20fiction")),
                CatalogItem.Folder("Topic: Fantasy", makeUrl("topic=fantasy"))
            )
            _feedItems.value = rootItems
        }
    }

    private fun loadGutendexFeed(url: String?, title: String?, isPagination: Boolean = false, addToHistory: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true

            val actualUrl = url ?: currentContextUrl ?: "https://gutendex.com/books"

            if (isPagination && historyStack.isNotEmpty()) {
                val current = historyStack.removeLast()
                historyStack.addLast((actualUrl) to current.second)
            }
            else if (addToHistory) {
                historyStack.addLast((actualUrl) to (title ?: "Section"))
            }
            updateBreadcrumbs()

            val localBookMap = libraryRepository.getLocalBookMap()
            val result = gutendexRepository.fetchBooks(actualUrl)

            result.onSuccess { response ->
                val items = mutableListOf<CatalogItem>()
                response.results.forEach { gBook ->
                    val epubLink = gBook.formats.entries.firstOrNull { it.key.contains("application/epub+zip") }?.value
                    val coverLink = gBook.formats.entries.firstOrNull { it.key.contains("image/jpeg") }?.value

                    if (epubLink != null) {
                        val epubUrl = Url(epubLink)
                        val epubType = MediaType("application/epub+zip")
                        val coverUrl = if (coverLink != null) Url(coverLink) else null
                        val coverType = MediaType("image/jpeg")

                        if (epubUrl != null) {
                            val manifest = Manifest(
                                metadata = Metadata(
                                    localizedTitle = LocalizedString(gBook.title),
                                    authors = gBook.authors.map { Contributor(name = it.name) }
                                ),
                                links = listOf(Link(href = epubUrl, mediaType = epubType)),
                                resources = if (coverUrl != null && coverType != null) listOf(
                                    Link(href = coverUrl, mediaType = coverType, rels = setOf("image"))
                                ) else emptyList()
                            )
                            val existingUri = localBookMap[gBook.title]
                            items.add(CatalogItem.Book(
                                title = gBook.title,
                                author = gBook.authors.firstOrNull()?.name ?: "Unknown",
                                imageUrl = coverLink,
                                format = "EPUB",
                                publication = Publication(manifest = manifest),
                                existingBookUri = existingUri
                            ))
                        }
                    }
                }
                if (response.next != null || response.previous != null) {
                    items.add(CatalogItem.Pagination(response.next, response.previous))
                }
                _feedItems.value = items

            }.onFailure {
                if (addToHistory && !isPagination) rollbackBreadcrumb()
                _uiEvent.send(CatalogUiEvent.ShowSnackbar("Failed to load: ${it.message}"))
            }
            _isLoading.value = false
        }
    }

    // --- OPDS LOGIC ---

    private fun loadFeed(rawUrl: String, breadcrumbTitle: String? = null, addToHistory: Boolean = true) {
        val finalUrl = try {
            val base = currentContextUrl ?: rawUrl
            if (rawUrl.startsWith("http")) rawUrl else URL(URL(base), rawUrl).toString()
        } catch (e: Exception) { rawUrl }

        viewModelScope.launch {
            _isLoading.value = true

            if (addToHistory) {
                val titleForStack = breadcrumbTitle ?: "Unknown"
                historyStack.addLast(finalUrl to titleForStack)
                updateBreadcrumbs()
            }

            val localBookMap = libraryRepository.getLocalBookMap()

            when (val result = opdsRepository.parseFeed(finalUrl)) {
                is Try.Success -> {
                    val parseData = result.value
                    currentContextUrl = finalUrl

                    if (addToHistory && breadcrumbTitle == null) {
                        val realTitle = parseData.feed?.metadata?.title ?: "Section"
                        if (historyStack.isNotEmpty()) historyStack.removeLast()
                        historyStack.addLast(finalUrl to realTitle)
                        updateBreadcrumbs()
                    }

                    parseData.feed?.let { _feedItems.value = processFeed(it, localBookMap) }
                        ?: run { _feedItems.value = emptyList() }
                }
                is Try.Failure -> {
                    if (addToHistory) rollbackBreadcrumb()
                    _uiEvent.send(CatalogUiEvent.ShowSnackbar("Error: ${result.value.message}"))
                }
            }
            _isLoading.value = false
        }
    }

    private fun processFeed(feed: Feed, localBookMap: Map<String, String>): List<CatalogItem> {
        val items = mutableListOf<CatalogItem>()
        feed.navigation.forEach { link ->
            val title = link.title
            val rel = link.rels.firstOrNull().toString()
            val isSystem = rel.contains("search", true) || rel.contains("self", true) || rel.contains("start", true) || rel.contains("up", true) || rel.contains("next", true) || rel.contains("previous", true)
            if (!title.isNullOrBlank() && !isSystem) items.add(CatalogItem.Folder(title, link.href.toString()))
        }
        feed.publications.forEach { pub ->
            val isEpub = pub.links.any { it.mediaType?.toString()?.contains("epub") == true }
            val navLink = pub.links.firstOrNull { it.mediaType?.toString()?.contains("atom+xml") == true || it.mediaType?.toString()?.contains("xml") == true }?.href?.toString()

            if (isEpub || navLink != null) {
                val format = if (isEpub) "EPUB" else "DETAIL"
                var cover = pub.images.firstOrNull()?.href?.toString()
                if (cover == null) {
                    cover = pub.links.firstOrNull { l -> l.mediaType?.toString()?.startsWith("image/") == true || l.rels.any { it.contains("image") || it.contains("thumbnail") || it.contains("cover") } }?.href?.toString()
                }

                val title = pub.metadata.title ?: "Unknown"
                val existingUri = localBookMap[title]
                items.add(CatalogItem.Book(title, pub.metadata.authors.firstOrNull()?.name?.toString() ?: "Unknown", cover, format, pub, if (isEpub) null else navLink, existingUri))
            }
        }
        return items
    }

    // --- DOWNLOADER & CRUD ---

    fun downloadBook(publication: Publication) {
        viewModelScope.launch {
            val title = publication.metadata.title ?: "Unknown"
            if (libraryRepository.isBookDuplicate(title)) {
                _uiEvent.send(CatalogUiEvent.ShowSnackbar("Duplicate: '$title' exists."))
                return@launch
            }
            val link = publication.links.firstOrNull { it.mediaType?.toString()?.contains("epub") == true }
            if (link == null) {
                _uiEvent.send(CatalogUiEvent.ShowSnackbar("No compatible download link (EPUB only)."))
                return@launch
            }

            _isLoading.value = true
            val ext = "epub"
            val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_").take(50)

            // --- FIX: Append Timestamp to guarantee a unique file name ---
            val uniqueSuffix = System.currentTimeMillis().toString()
            val bookFile = File(application.filesDir, "${safeTitle}_$uniqueSuffix.$ext")
            val coverFile = File(application.filesDir, "${safeTitle}_$uniqueSuffix.jpg")

            val downloadUrl = link.href.toString()
            val success = if (isGutendexSession) {
                gutendexRepository.downloadBook(downloadUrl, bookFile)
            } else {
                opdsRepository.downloadPublication(downloadUrl, bookFile, currentContextUrl)
            }

            if (success) {
                var coverUrl = publication.images.firstOrNull()?.href?.toString()
                if (coverUrl == null && !isGutendexSession) {
                    coverUrl = publication.links.firstOrNull { it.mediaType?.toString()?.startsWith("image/") == true }?.href?.toString()
                }

                var savedCover: File? = null
                if (coverUrl != null) {
                    if (isGutendexSession) {
                        if (gutendexRepository.downloadBook(coverUrl, coverFile)) savedCover = coverFile
                    } else {
                        if (opdsRepository.downloadPublication(coverUrl, coverFile, currentContextUrl)) savedCover = coverFile
                    }
                }
                libraryRepository.addDownloadedBook(bookFile, title, publication.metadata.authors.firstOrNull()?.name?.toString() ?: "Unknown", savedCover)
                _uiEvent.send(CatalogUiEvent.ShowSnackbar("Downloaded '$title'", "View Library"))

                val currentBreadcrumb = breadcrumbs.value.lastOrNull()
                if (isGutendexSession) {
                    loadGutendexFeed(currentBreadcrumb?.first ?: currentContextUrl, currentBreadcrumb?.second, isPagination = false, addToHistory = false)
                } else {
                    loadFeed(currentContextUrl ?: "", null, addToHistory = false)
                }

            } else {
                _uiEvent.send(CatalogUiEvent.ShowSnackbar("Download failed."))
                if(bookFile.exists()){ val isDeleted = bookFile.delete()
                    if (!isDeleted) {
                        Timber.w("Failed to delete file: ${bookFile.absolutePath}")
                    }}
            }
            _isLoading.value = false
        }
    }

    fun addCatalog(title: String, url: String, user: String?, pass: String?) {
        viewModelScope.launch {
            val cleanUrl = OpdsUrlUtils.cleanCatalogUrl(url)
            val newFeed = OpdsEntity(
                title = title.ifBlank { "Library" },
                url = cleanUrl,
                username = user?.ifBlank { null },
                password = pass?.ifBlank { null }
            )
            opdsRepository.insertFeed(newFeed)
        }
    }

    fun updateCatalog(feed: OpdsEntity, title: String, url: String, user: String?, pass: String?) {
        viewModelScope.launch {
            val cleanUrl = OpdsUrlUtils.cleanCatalogUrl(url)
            val updatedFeed = feed.copy(
                title = title,
                url = cleanUrl,
                username = user?.ifBlank { null },
                password = pass?.ifBlank { null }
            )
            opdsRepository.updateFeed(updatedFeed)
        }
    }

    fun deleteCatalog(feed: OpdsEntity) {
        viewModelScope.launch { opdsRepository.deleteFeed(feed) }
    }

}