package org.vaachak.reader.leisure.ui.bookshelf

// --- MAESTRO FIX 1: Import semantics ---
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.CoverAspectRatio
import org.vaachak.reader.core.domain.model.DitheringMode
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.ui.testability.tids
import org.vaachak.reader.leisure.ui.utils.EinkDitherTransformation
import java.io.File

// --- UNIFIED GRID ITEM TYPE ---
sealed class LibraryItem {
    data class Book(val entity: BookEntity) : LibraryItem()
    data class Stack(val name: String, val books: List<BookEntity>) : LibraryItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BookshelfScreen(
    onBookClick: (String) -> Unit,
    onCatalogClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    isOfflineMode: Boolean = false,
    viewModel: BookshelfViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var bookToDelete by remember { mutableStateOf<BookEntity?>(null) }
    var selectedTab by remember { mutableIntStateOf(1) }
    var isSearchActive by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBook(it) }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }

    BackHandler(enabled = isSearchActive || state.selectedStackName != null) {
        if (isSearchActive) {
            isSearchActive = false
            viewModel.updateSearchQuery("")
        } else if (state.selectedStackName != null) {
            viewModel.closeStack()
        }
    }

    val containerColor = if (state.isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (state.isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    val readingItems = state.groupedLibrary.values.flatten()
        .filter { it.progress > 0f && it.progress < .99f }
        .sortedByDescending { it.progress }
        .map { LibraryItem.Book(it) }

    val shelfItems = remember(state.groupedLibrary, state.bookshelfPrefs.groupBySeries) {
        if (state.bookshelfPrefs.groupBySeries) {
            state.groupedLibrary.map { (author, books) ->
                if (books.size > 1) LibraryItem.Stack(author, books)
                else LibraryItem.Book(books.first())
            }
        } else {
            state.groupedLibrary.values.flatten().map { LibraryItem.Book(it) }
        }
    }

    TidScreen(Tid.Screen.bookshelf) {
        Scaffold(
            containerColor = containerColor,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(modifier = Modifier.background(containerColor)) {
                    Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {

                        IconButton(onClick = { selectedTab = 0; viewModel.closeStack() }) {
                            Icon(if (selectedTab == 0) Icons.Filled.Schedule else Icons.Outlined.Schedule, "Reading", tint = contentColor)
                        }
                        IconButton(onClick = { selectedTab = 1 }) {
                            Icon(if (selectedTab == 1) Icons.Filled.Folder else Icons.Outlined.Folder, "Bookshelf", tint = contentColor)
                        }
                        Spacer(Modifier.weight(1f))

                        if (selectedTab == 0) {
                            IconButton(onClick = { viewModel.getGlobalRecap() }) {
                                if (state.loadingRecapUri == "GLOBAL") CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoAwesome, "Global Recall", tint = contentColor)
                            }
                        } else {
                            if (isSearchActive) {
                                TextField(
                                    value = state.searchQuery,
                                    onValueChange = { viewModel.updateSearchQuery(it) },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    placeholder = { Text("Search...") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { isSearchActive = false; viewModel.updateSearchQuery("") }) {
                                            // --- MAESTRO FIX 2: Added contentDescription instead of null ---
                                            Icon(Icons.Default.Close, "Clear search")
                                        }
                                    },
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                                )
                            } else {
                                if (isOfflineMode) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .background(contentColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.CloudOff, contentDescription = "Offline Mode", tint = contentColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Offline", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                                    }
                                }

                                IconButton(onClick = { isSearchActive = true }, modifier = Modifier.tid(Tid.Library.SEARCH)) { Icon(Icons.Default.Search, "Search", tint = contentColor) }
                                IconButton(onClick = { viewModel.refreshLibrary() }, modifier = Modifier.tid(Tid.Library.SYNC)) { Icon(Icons.Default.Sync, "Sync", tint = contentColor) }
                                IconButton(onClick = onCatalogClick, modifier = Modifier.tid(Tid.Library.CATALOG)) { Icon(Icons.Default.Public, "Catalog", tint = contentColor) }
                                IconButton(onClick = onHighlightsClick, modifier = Modifier.tid(Tid.Library.HIGHLIGHTS)) { Icon(Icons.Default.EditNote, "Highlights", tint = contentColor) }
                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.tid(Tid.Library.SORT)) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort", tint = contentColor) }
                                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                        DropdownMenuItem(text = { Text("Sort by Progress") }, modifier = Modifier.tid(Tid.Library.SORT_PROGRESS), onClick = { viewModel.updateSortOrder(SortOrder.PROGRESS); menuExpanded = false })
                                        DropdownMenuItem(text = { Text("Sort by Recent") }, modifier = Modifier.tid(Tid.Library.SORT_RECENT), onClick = { viewModel.updateSortOrder(SortOrder.DATE_ADDED); menuExpanded = false })
                                        DropdownMenuItem(text = { Text("Sort by Title") }, modifier = Modifier.tid(Tid.Library.SORT_TITLE), onClick = { viewModel.updateSortOrder(SortOrder.TITLE); menuExpanded = false })
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = contentColor.copy(alpha = 0.1f))
                }
            },
            floatingActionButton = {
                if (selectedTab == 1 && state.selectedStackName == null) {
                    FloatingActionButton(
                        onClick = { launcher.launch(arrayOf("application/epub+zip")) },
                        modifier = Modifier.tid(Tid.Library.ADD),
                        containerColor = if (state.isEink) Color.Black else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (state.isEink) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    ) { Icon(Icons.Default.Add, "Add Book") }
                }
            }
        ) { paddingValues ->

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                val cardWidth = 110.dp
                val cardHeight = if (state.bookshelfPrefs.coverAspectRatio == CoverAspectRatio.UNIFORM) 180.dp else 200.dp
                val columns = maxOf(3, (maxWidth.value / cardWidth.value).toInt())
                val rows = maxOf(2, ((maxHeight.value - 60f) / cardHeight.value).toInt())
                val itemsPerPage = columns * rows

                if (selectedTab == 0) {
                    PaginatedGrid(
                        items = readingItems,
                        itemsPerPage = itemsPerPage,
                        columns = columns,
                        state = state,
                        contentColor = contentColor,
                        viewModel = viewModel,
                        onBookClick = onBookClick,
                        onDeleteBook = { bookToDelete = it },
                        bottomBarTextLeft = "Recent Reading: ${readingItems.size}"
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.selectedStackName != null) {
                            val stackBooks = state.groupedLibrary[state.selectedStackName]?.map { LibraryItem.Book(it) } ?: emptyList()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.closeStack() }
                                    // --- MAESTRO FIX 3: Semantics for Stack Back Button ---
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Back to Bookshelf"
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = contentColor)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(state.selectedStackName ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                                    Text("${stackBooks.size} Books", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.6f))
                                }
                            }
                            HorizontalDivider(color = contentColor.copy(alpha = 0.1f))

                            PaginatedGrid(
                                items = stackBooks,
                                itemsPerPage = itemsPerPage,
                                columns = columns,
                                state = state,
                                contentColor = contentColor,
                                viewModel = viewModel,
                                onBookClick = onBookClick,
                                onDeleteBook = { bookToDelete = it },
                                bottomBarTextLeft = "Books in series: ${stackBooks.size}"
                            )
                        } else {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(state.availableFilters) { filter ->
                                    val isSelected = state.activeFilter == filter
                                    Text(
                                        text = filter,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) contentColor else contentColor.copy(alpha = 0.5f),
                                        modifier = Modifier.clickable { viewModel.setFilter(filter) }.padding(8.dp)
                                    )
                                }
                            }

                            PaginatedGrid(
                                items = shelfItems,
                                itemsPerPage = itemsPerPage,
                                columns = columns,
                                state = state,
                                contentColor = contentColor,
                                viewModel = viewModel,
                                onBookClick = onBookClick,
                                onDeleteBook = { bookToDelete = it },
                                bottomBarTextLeft = "Total Library: ${shelfItems.size}"
                            )
                        }
                    }
                }
            }

            bookToDelete?.let { book ->
                DeleteBookDialog(
                    bookTitle = book.title,
                    onConfirm = {
                        book.localUri?.let { viewModel.deleteBookByUri(it) }
                        bookToDelete = null
                    },
                    onDismiss = { bookToDelete = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaginatedGrid(
    items: List<LibraryItem>,
    itemsPerPage: Int,
    columns: Int,
    state: BookshelfUiState,
    contentColor: Color,
    viewModel: BookshelfViewModel,
    onBookClick: (String) -> Unit,
    onDeleteBook: (BookEntity) -> Unit,
    bottomBarTextLeft: String
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No books found", color = contentColor.copy(alpha = 0.5f)) }
        return
    }

    val pages = items.chunked(itemsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { pageIndex ->
            val pageItems = pages[pageIndex]
            val firstVisibleBookHash = pageItems.firstNotNullOfOrNull { item ->
                (item as? LibraryItem.Book)?.entity?.bookHash
            }
            val rows = pageItems.chunked(columns)

            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (rowItems in rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (item in rowItems) {
                            Box(modifier = Modifier.weight(1f)) {
                                when (item) {
                                    is LibraryItem.Book -> NeoReaderCoverCard(
                                        book = item.entity,
                                        state = state,
                                        isFirstVisibleBook = item.entity.bookHash == firstVisibleBookHash,
                                        onClick = { onBookClick(item.entity.bookHash) },
                                        onDelete = { onDeleteBook(item.entity) }
                                    )
                                    is LibraryItem.Stack -> StackGridItem(stack = item, state = state, onClick = { viewModel.openStack(item.name) })
                                }
                            }
                        }
                        repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bottomBarTextLeft, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.7f))
            Text("${pagerState.currentPage + 1}/${pages.size}", style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

@Composable
fun NeoReaderCoverCard(
    book: BookEntity,
    state: BookshelfUiState,
    isFirstVisibleBook: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val prefs = state.bookshelfPrefs
    val isEink = state.isEink
    val shouldDither = prefs.ditheringMode == DitheringMode.ALWAYS_ON || (prefs.ditheringMode == DitheringMode.AUTO && isEink)
    val hasBookmarks = state.booksWithBookmarks.contains(book.bookHash)

    val modifier = if (prefs.coverAspectRatio == CoverAspectRatio.UNIFORM) Modifier.fillMaxWidth().aspectRatio(0.7f)
    else Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp)

    // --- NON-BLOCKING FIX: Cover check moved off main thread ---
    var hasCover by remember(book.coverPath) { mutableStateOf(false) }
    LaunchedEffect(book.coverPath) {
        hasCover = if (book.coverPath != null) {
            withContext(Dispatchers.IO) { File(book.coverPath).exists() }
        } else {
            false
        }
    }

    // --- MAESTRO FIX 4: Merging Descendants on the Book Card ---
    Column(
        modifier = if (isFirstVisibleBook) {
            Modifier
                .tids(Tid.Library.bookByHash(book.bookHash), Tid.Library.FIRST)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Book: ${book.title}, Progress: ${(book.progress * 100).toInt()}%"
                }
                .clickable { onClick() }
        } else {
            Modifier
                .tid(Tid.Library.bookByHash(book.bookHash))
                .semantics(mergeDescendants = true) {
                    contentDescription = "Book: ${book.title}, Progress: ${(book.progress * 100).toInt()}%"
                }
                .clickable { onClick() }
        }
    ) {
        Card(
            modifier = modifier, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
            colors = CardDefaults.cardColors(containerColor = if (isEink) Color.White else Color.LightGray.copy(0.2f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCover && book.coverPath != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(book.coverPath)).crossfade(true).apply { if (shouldDither) transformations(EinkDitherTransformation()) }.build(), contentDescription = "Cover for ${book.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else { Box(Modifier.fillMaxSize().background(Color.LightGray.copy(0.5f)), contentAlignment = Alignment.Center) { Text(book.title.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium) } }

                if (prefs.showProgressBadge && book.progress > 0f) {
                    Box(
                        modifier = (if (isFirstVisibleBook) {
                            Modifier.tids(Tid.Library.progressByHash(book.bookHash), Tid.Library.PROGRESS_FIRST)
                        } else {
                            Modifier.tid(Tid.Library.progressByHash(book.bookHash))
                        })
                            .align(Alignment.TopStart)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(bottomEnd = 8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("${if (book.progress > 0.99) 100 else (book.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold) }
                }
                if (prefs.showFavoriteIcon && hasBookmarks) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Bookmark",
                        tint = Color.White,
                        modifier = (if (isFirstVisibleBook) {
                            Modifier.tids(Tid.Library.bookmarkByHash(book.bookHash), Tid.Library.BOOKMARK_FIRST)
                        } else {
                            Modifier.tid(Tid.Library.bookmarkByHash(book.bookHash))
                        })
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }
                if (prefs.showFormatBadge) {
                    Box(
                        modifier = (if (isFirstVisibleBook) {
                            Modifier.tids(Tid.Library.formatByHash(book.bookHash), Tid.Library.FORMAT_FIRST)
                        } else {
                            Modifier.tid(Tid.Library.formatByHash(book.bookHash))
                        })
                            .align(Alignment.BottomStart)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("EPUB", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold) }
                }
                if (prefs.showSyncStatus) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topStart = 8.dp)).padding(4.dp)) { Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = Color.Black, modifier = Modifier.size(14.dp)) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if(isEink) Color.Black else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(24.dp)
                    .background(
                        color = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .tid(Tid.Library.deleteByHash(book.bookHash))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete ${book.title}",
                    tint = if (isEink) Color.White else MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun StackGridItem(stack: LibraryItem.Stack, state: BookshelfUiState, onClick: () -> Unit) {
    val isEink = state.isEink
    val firstBook = stack.books.firstOrNull()
    val unreadCount = stack.books.count { it.progress < 0.99f }
    val modifier = if (state.bookshelfPrefs.coverAspectRatio == CoverAspectRatio.UNIFORM) Modifier.fillMaxWidth().aspectRatio(0.7f)
    else Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp)

    // --- NON-BLOCKING FIX: Cover check moved off main thread ---
    var hasCover by remember(firstBook?.coverPath) { mutableStateOf(false) }
    LaunchedEffect(firstBook?.coverPath) {
        hasCover = if (firstBook?.coverPath != null) {
            withContext(Dispatchers.IO) { File(firstBook.coverPath).exists() }
        } else {
            false
        }
    }

    // --- MAESTRO FIX 5: Merging Descendants on the Stack Card ---
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Book Series: ${stack.name}, ${stack.books.size} books"
            }
    ) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Card(modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 8.dp, end = 8.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.4f)), colors = CardDefaults.cardColors(containerColor = if (isEink) Color.LightGray else Color.Gray.copy(alpha = 0.3f))) {}

            if (firstBook != null) {
                Card(modifier = Modifier.fillMaxSize().padding(bottom = 8.dp, end = 12.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (hasCover && firstBook.coverPath != null) {
                            AsyncImage(model = File(firstBook.coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize().background(Color.LightGray), contentAlignment = Alignment.Center) { Text(firstBook.title.take(1).uppercase()) }
                        }
                    }
                }
            }

            Box(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 12.dp).background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(topEnd = 8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("${unreadCount}/${stack.books.size}", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stack.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if(isEink) Color.Black else MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun DeleteBookDialog(
    bookTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Remove Book?")
        },
        text = {
            Text(
                "Are you sure you want to remove '$bookTitle' from your library?\n\n" +
                        "WARNING: This will permanently delete your reading progress, custom tags, and highlights. " +
                        "The original file on your device will not be deleted."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}