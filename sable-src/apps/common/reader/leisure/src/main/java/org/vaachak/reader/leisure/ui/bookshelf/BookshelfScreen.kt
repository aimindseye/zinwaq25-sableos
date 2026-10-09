package org.vaachak.reader.leisure.ui.bookshelf

// --- MAESTRO FIX 1: Import semantics ---
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
sealed class ShelfEntry {
    data class Book(val entity: BookEntity) : ShelfEntry()
    data class Stack(val name: String, val books: List<BookEntity>) : ShelfEntry()
}

// Picker types for choosing the file of a restored ("needs file") title again.
private fun relinkMimeTypes(kind: String): Array<String> = when (kind) {
    "PDF" -> arrayOf("application/pdf")
    "COMIC" -> arrayOf("application/vnd.comicbook+zip", "application/x-cbz", "application/zip", "application/octet-stream")
    "AUDIOBOOK" -> arrayOf("audio/*", "application/octet-stream")
    else -> arrayOf("application/epub+zip")
}

// EPUB, PDF, comic archives and audio files. Some providers report a .cbz as a generic zip or binary, so those are offered too
// and the importer decides by file name; anything it does not recognise is refused with a message.
private val ADD_BOOK_MIME_TYPES = arrayOf(
    "application/epub+zip",
    "application/pdf",
    "application/vnd.comicbook+zip",
    "application/x-cbz",
    "application/zip",
    "application/octet-stream",
    "audio/*",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    onBookClick: (String) -> Unit,
    onHighlightsClick: () -> Unit,
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

    val folderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        tree?.let { viewModel.importFolder(it) }
    }

    // Titles restored from a backup have no file yet: tapping one asks for the file (or folder) instead of opening.
    var relinkTarget by remember { mutableStateOf<BookEntity?>(null) }
    var relinkPicking by remember { mutableStateOf(false) }
    val finishRelink = { uri: Uri? ->
        val target = relinkTarget
        relinkTarget = null
        relinkPicking = false
        if (uri != null && target != null) viewModel.relink(target.bookHash, uri)
    }
    val relinkFileLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        finishRelink(uri)
    }
    val relinkFolderLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        finishRelink(tree)
    }
    val handleBookClick: (String) -> Unit = { hash ->
        val book = state.groupedLibrary.values.asSequence().flatten().firstOrNull { it.bookHash == hash }
        if (book != null && book.localUri == null) relinkTarget = book else onBookClick(hash)
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
        .map { ShelfEntry.Book(it) }

    // Always present the physical library count. Smart Stacks collapsed many books into a handful
    // of grouped entries, which made the library report fewer items than actually imported.
    val shelfItems = remember(state.groupedLibrary) {
        state.groupedLibrary.values
            .flatten()
            .distinctBy { it.bookHash }
            .map { ShelfEntry.Book(it) }
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

                        if (selectedTab != 0) {
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
                                IconButton(onClick = { isSearchActive = true }, modifier = Modifier.tid(Tid.Library.SEARCH)) { Icon(Icons.Default.Search, "Search", tint = contentColor) }
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
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(
                            onClick = { folderLauncher.launch(null) },
                            modifier = Modifier.tid("library_add_folder"),
                            containerColor = if (state.isEink) Color.Black else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (state.isEink) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        ) { Icon(Icons.Default.Folder, "Add folder of comic pages or audio files") }
                        FloatingActionButton(
                            onClick = { launcher.launch(ADD_BOOK_MIME_TYPES) },
                            modifier = Modifier.tid(Tid.Library.ADD),
                            containerColor = if (state.isEink) Color.Black else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (state.isEink) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        ) { Icon(Icons.Default.Add, "Add Book") }
                    }
                }
            }
        ) { paddingValues ->

            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                val cardWidth = 110.dp
                val cardHeight = if (state.bookshelfPrefs.coverAspectRatio == CoverAspectRatio.UNIFORM) 180.dp else 200.dp
                val columns = maxOf(3, (maxWidth.value / cardWidth.value).toInt())

                if (selectedTab == 0) {
                    ContinuousLibraryGrid(
                        items = readingItems,
                        columns = columns,
                        state = state,
                        contentColor = contentColor,
                        viewModel = viewModel,
                        onBookClick = handleBookClick,
                        onDeleteBook = { bookToDelete = it },
                        bottomBarTextLeft = "Recent Reading: ${readingItems.size}"
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.selectedStackName != null) {
                            val stackBooks = state.groupedLibrary[state.selectedStackName]?.map { ShelfEntry.Book(it) } ?: emptyList()

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

                            ContinuousLibraryGrid(
                                items = stackBooks,
                                        columns = columns,
                                state = state,
                                contentColor = contentColor,
                                viewModel = viewModel,
                                onBookClick = handleBookClick,
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

                            ContinuousLibraryGrid(
                                items = shelfItems,
                                        columns = columns,
                                state = state,
                                contentColor = contentColor,
                                viewModel = viewModel,
                                onBookClick = handleBookClick,
                                onDeleteBook = { bookToDelete = it },
                                bottomBarTextLeft = "Total Library: ${shelfItems.size}"
                            )
                        }
                    }
                }
            }

            relinkTarget?.takeIf { !relinkPicking }?.let { target ->
                val isFolder = target.format == "inode/directory"
                AlertDialog(
                    onDismissRequest = { relinkTarget = null },
                    title = { Text("Needs file") },
                    text = {
                        Text(
                            "'${target.title}' was restored from a backup, which holds your place and notes but not the " +
                                if (isFolder) "folder itself. Choose the same folder again to link it." else "file itself. Choose the same file again to link it."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            relinkPicking = true
                            if (isFolder) relinkFolderLauncher.launch(null) else relinkFileLauncher.launch(relinkMimeTypes(target.kind))
                        }) { Text("Choose") }
                    },
                    dismissButton = { TextButton(onClick = { relinkTarget = null }) { Text("Cancel") } }
                )
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

@Composable
fun ContinuousLibraryGrid(
    items: List<ShelfEntry>,
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

    // One continuous vertical grid (not a swipe pager): every item is reachable by keyboard focus
    // traversal and by touch scrolling.
    val firstVisibleBookHash = items.firstNotNullOfOrNull { item ->
        (item as? ShelfEntry.Book)?.entity?.bookHash
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(items) { item ->
                when (item) {
                    is ShelfEntry.Book -> NeoReaderCoverCard(
                        book = item.entity,
                        state = state,
                        isFirstVisibleBook = item.entity.bookHash == firstVisibleBookHash,
                        onClick = { onBookClick(item.entity.bookHash) },
                        onDelete = { onDeleteBook(item.entity) }
                    )
                    is ShelfEntry.Stack -> StackGridItem(stack = item, state = state, onClick = { viewModel.openStack(item.name) })
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bottomBarTextLeft, style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = 0.7f))
            Text("items: ${items.size}", style = MaterialTheme.typography.labelMedium, color = contentColor)
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
    val coverPath = book.coverPath
    var hasCover by remember(coverPath) { mutableStateOf(false) }
    LaunchedEffect(coverPath) {
        hasCover = if (coverPath != null) {
            withContext(Dispatchers.IO) { File(coverPath).exists() }
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
                if (hasCover && coverPath != null) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(coverPath)).crossfade(true).apply { if (shouldDither) transformations(EinkDitherTransformation()) }.build(), contentDescription = "Cover for ${book.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
                if (prefs.showFormatBadge || book.localUri == null) {
                    Box(
                        modifier = (if (isFirstVisibleBook) {
                            Modifier.tids(Tid.Library.formatByHash(book.bookHash), Tid.Library.FORMAT_FIRST)
                        } else {
                            Modifier.tid(Tid.Library.formatByHash(book.bookHash))
                        })
                            .align(Alignment.BottomStart)
                            .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(if (book.localUri == null) "NEEDS FILE" else book.kind, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold) }
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
fun StackGridItem(stack: ShelfEntry.Stack, state: BookshelfUiState, onClick: () -> Unit) {
    val isEink = state.isEink
    val firstBook = stack.books.firstOrNull()
    val unreadCount = stack.books.count { it.progress < 0.99f }
    val modifier = if (state.bookshelfPrefs.coverAspectRatio == CoverAspectRatio.UNIFORM) Modifier.fillMaxWidth().aspectRatio(0.7f)
    else Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 220.dp)

    // --- NON-BLOCKING FIX: Cover check moved off main thread ---
    val firstCoverPath = firstBook?.coverPath
    var hasCover by remember(firstCoverPath) { mutableStateOf(false) }
    LaunchedEffect(firstCoverPath) {
        hasCover = if (firstCoverPath != null) {
            withContext(Dispatchers.IO) { File(firstCoverPath).exists() }
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
                        if (hasCover && firstCoverPath != null) {
                            AsyncImage(model = File(firstCoverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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