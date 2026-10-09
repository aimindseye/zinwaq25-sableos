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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.vaachak.reader.leisure.ui.reader.components.VaachakHeader
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.ui.testability.tids

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogBrowserScreen(
    onBack: () -> Unit,
    onReadBook: (String) -> Unit,
    onGoToBookshelf: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    val screenTitle by viewModel.screenTitle.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isEink by viewModel.isEinkEnabled.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOfflineMode.collectAsStateWithLifecycle()

    val breadcrumbs by viewModel.breadcrumbs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val paginationItem = feedItems.filterIsInstance<CatalogItem.Pagination>().firstOrNull()
    val firstItemIndex = remember(feedItems) { feedItems.indexOfFirst { it !is CatalogItem.Pagination } }
    val firstServerIndex = remember(feedItems) { feedItems.indexOfFirst { it is CatalogItem.Server } }
    val firstFolderIndex = remember(feedItems) { feedItems.indexOfFirst { it is CatalogItem.Folder } }
    val firstBookIndex = remember(feedItems) { feedItems.indexOfFirst { it is CatalogItem.Book } }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is CatalogUiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed && event.actionLabel == "View Library") {
                        onGoToBookshelf()
                    }
                }
                is CatalogUiEvent.NavigateToReader -> {
                    onReadBook(event.bookUri)
                }
            }
        }
    }

    BackHandler { if (!viewModel.goBack()) onBack() }

    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    TidScreen(Tid.Screen.catalog) {
        Scaffold(
            topBar = {
                Column {
                    VaachakHeader(
                        title = screenTitle,
                        onBack = { if (!viewModel.goBack()) onBack() },
                        showBackButton = true,
                        isEink = isEink,
                        rootModifier = Modifier.tid(Tid.Catalog.HEADER),
                        backButtonModifier = Modifier.tid(Tid.Catalog.BACK)
                    )

                    if (!isOffline && breadcrumbs.size > 1) {
                        BreadcrumbBar(
                            breadcrumbs = breadcrumbs,
                            isEink = isEink,
                            onBreadcrumbClick = { index -> viewModel.onBreadcrumbClick(index) }
                        )
                    }

                    if (paginationItem != null) {
                        CatalogPaginationItem(
                            item = paginationItem,
                            isEink = isEink,
                            onNext = { viewModel.handlePaginationClick(it) },
                            onPrev = { viewModel.handlePaginationClick(it) },
                            isCompact = true
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.tid(Tid.Catalog.SNACKBAR)
                ) { snackbarData ->
                    Snackbar(
                        action = {
                            snackbarData.visuals.actionLabel?.let { actionLabel ->
                                TextButton(
                                    onClick = { snackbarData.performAction() },
                                    modifier = if (actionLabel == "View Library") {
                                        Modifier.tid(Tid.Catalog.SNACKBAR_ACTION_VIEW_LIBRARY)
                                    } else {
                                        Modifier
                                    }
                                ) {
                                    Text(actionLabel)
                                }
                            }
                        }
                    ) {
                        Text(snackbarData.visuals.message)
                    }
                }
            },
            containerColor = containerColor,
            contentColor = contentColor
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when {
                    isOffline -> OfflinePlaceholder(isEink)
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = if(isEink) Color.Black else MaterialTheme.colorScheme.primary)
                    feedItems.isEmpty() -> {
                        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CloudOff, contentDescription = "No items found", Modifier.size(64.dp), tint = Color.Gray)
                            Text("No items found.", color = contentColor)
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(feedItems) { index, item ->
                                when (item) {
                                    is CatalogItem.Server -> CatalogServerItem(
                                        item = item,
                                        isEink = isEink,
                                        isFirstItem = index == firstItemIndex,
                                        isFirstServer = index == firstServerIndex
                                    ) { viewModel.handleItemClick(item) }
                                    is CatalogItem.Folder -> CatalogFolderItem(
                                        item = item,
                                        isEink = isEink,
                                        isFirstItem = index == firstItemIndex,
                                        isFirstFolder = index == firstFolderIndex
                                    ) { viewModel.handleItemClick(item) }
                                    is CatalogItem.Book -> CatalogBookItem(
                                        item = item,
                                        isEink = isEink,
                                        isFirstItem = index == firstItemIndex,
                                        isFirstBook = index == firstBookIndex
                                    ) { viewModel.handleItemClick(item) }
                                    is CatalogItem.Pagination -> {
                                        CatalogPaginationItem(
                                            item,
                                            isEink,
                                            onNext = { viewModel.handlePaginationClick(it) },
                                            onPrev = { viewModel.handlePaginationClick(it) }
                                        )
                                    }
                                }
                                if (item !is CatalogItem.Pagination) {
                                    HorizontalDivider(thickness = 0.5.dp, color = if(isEink) Color.Black else MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// --- COMPONENTS ---
// adb shell uiautomator dump /sdcard/window_dump.xml
// adb shell cat /sdcard/window_dump.xml | grep -E 'screen_catalog|catalog_back|catalog_item_first|catalog_book_action_first|catalog_pagination_(next|prev)'

@Composable
fun CatalogServerItem(
    item: CatalogItem.Server,
    isEink: Boolean,
    isFirstItem: Boolean,
    isFirstServer: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
    ListItem(
        colors = ListItemDefaults.colors(containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface, headlineColor = textColor),
        headlineContent = { Text(item.title, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(Icons.Default.Dns, contentDescription = null, tint = if(isEink) Color.Black else MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray) },
        modifier = Modifier
            .then(
                when {
                    isFirstItem && isFirstServer -> Modifier.tids(
                        Tid.Catalog.item("server", item.url),
                        Tid.Catalog.ITEM_FIRST,
                        Tid.Catalog.SERVER_FIRST
                    )
                    isFirstItem -> Modifier.tids(
                        Tid.Catalog.item("server", item.url),
                        Tid.Catalog.ITEM_FIRST
                    )
                    isFirstServer -> Modifier.tids(
                        Tid.Catalog.item("server", item.url),
                        Tid.Catalog.SERVER_FIRST
                    )
                    else -> Modifier.tid(Tid.Catalog.item("server", item.url))
                }
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Server: ${item.title}, ${item.url}"
            }
    )
}

@Composable
fun CatalogPaginationItem(
    item: CatalogItem.Pagination,
    isEink: Boolean,
    onNext: (String) -> Unit,
    onPrev: (String) -> Unit,
    isCompact: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (item.prevUrl != null) {
            Button(
                onClick = { onPrev(item.prevUrl) },
                modifier = Modifier.tid(Tid.Catalog.PAGINATION_PREV),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = if (isEink) BorderStroke(1.dp, Color.Black) else null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Prev", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        if (item.nextUrl != null) {
            Button(
                onClick = { onNext(item.nextUrl) },
                modifier = Modifier.tid(Tid.Catalog.PAGINATION_NEXT),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = if (isEink) BorderStroke(1.dp, Color.Black) else null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Next", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun BreadcrumbBar(
    breadcrumbs: List<Pair<String, String>>,
    isEink: Boolean,
    onBreadcrumbClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(breadcrumbs.size) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        breadcrumbs.forEachIndexed { index, item ->
            val isLast = index == breadcrumbs.lastIndex
            val color = if (isLast) if (isEink) Color.Black else MaterialTheme.colorScheme.primary else Color.Gray
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(
                        when {
                            index == 0 -> Modifier.tid(Tid.Catalog.BREADCRUMB_FIRST)
                            isLast -> Modifier.tid(Tid.Catalog.BREADCRUMB_LAST)
                            else -> Modifier
                        }
                    )
                    .clickable(enabled = !isLast) { onBreadcrumbClick(index) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (isLast) "Current location: ${item.second}" else "Go to ${item.second}"
                    }
            ) {
                Text(item.second, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal)
                if (!isLast) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp).padding(horizontal = 2.dp))
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
}

@Composable
fun CatalogFolderItem(
    item: CatalogItem.Folder,
    isEink: Boolean,
    isFirstItem: Boolean,
    isFirstFolder: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
    ListItem(
        colors = ListItemDefaults.colors(containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface, headlineColor = textColor),
        headlineContent = { Text(item.title, fontWeight = FontWeight.SemiBold) },
        leadingContent = { Icon(Icons.Default.Folder, contentDescription = null, tint = if(isEink) Color.Black else MaterialTheme.colorScheme.primary) },
        modifier = Modifier
            .then(
                when {
                    isFirstItem && isFirstFolder -> Modifier.tids(
                        Tid.Catalog.item("folder", item.title),
                        Tid.Catalog.ITEM_FIRST,
                        Tid.Catalog.FOLDER_FIRST
                    )
                    isFirstItem -> Modifier.tids(
                        Tid.Catalog.item("folder", item.title),
                        Tid.Catalog.ITEM_FIRST
                    )
                    isFirstFolder -> Modifier.tids(
                        Tid.Catalog.item("folder", item.title),
                        Tid.Catalog.FOLDER_FIRST
                    )
                    else -> Modifier.tid(Tid.Catalog.item("folder", item.title))
                }
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Folder: ${item.title}"
            }
    )
}

@Composable
fun CatalogBookItem(
    item: CatalogItem.Book,
    isEink: Boolean,
    isFirstItem: Boolean,
    isFirstBook: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface
    val subColor = if (isEink) Color.DarkGray else MaterialTheme.colorScheme.onSurfaceVariant
    val isNavigable = item.format == "DETAIL"
    val isDownloaded = item.existingBookUri != null

    val icon = when {
        isDownloaded -> Icons.AutoMirrored.Filled.MenuBook
        isNavigable -> Icons.AutoMirrored.Filled.ArrowForwardIos
        else -> Icons.Default.CloudDownload
    }
    val iconTint = if (isEink) Color.Black else if (isDownloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
    val actionDescription = when {
        isDownloaded -> "Read Book"
        isNavigable -> "View Details"
        else -> "Download Book"
    }

    ListItem(
        colors = ListItemDefaults.colors(containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface, headlineColor = textColor, supportingColor = subColor),
        headlineContent = { Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(item.author.ifBlank { "Unknown Author" }, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                if (item.format.isNotEmpty() && item.format != "DETAIL") {
                    Spacer(Modifier.height(4.dp))
                    Surface(color = if(isEink) Color.LightGray else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(item.format, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), color = if(isEink) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 10.sp)
                    }
                }
            }
        },
        leadingContent = {
            Surface(shape = RoundedCornerShape(4.dp), border = if (isEink) BorderStroke(1.dp, Color.Black) else null, color = Color.LightGray, modifier = Modifier.size(45.dp, 70.dp)) {
                if (item.imageUrl != null) {
                    AsyncImage(model = item.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = if(isEink) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null)
                } else {
                    Box(contentAlignment = Alignment.Center) { Text(item.title.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.Black) }
                }
            }
        },
        trailingContent = {
            IconButton(
                onClick = onClick,
                modifier = if (isFirstBook) {
                    Modifier.tids(
                        Tid.Catalog.bookActionByTitle(item.title),
                        Tid.Catalog.BOOK_ACTION_FIRST
                    )
                } else {
                    Modifier.tid(Tid.Catalog.bookActionByTitle(item.title))
                }
            ) {
                Icon(icon, contentDescription = actionDescription, tint = iconTint, modifier = Modifier.size(24.dp))
            }
        },
        modifier = Modifier
            .then(
                when {
                    isFirstItem && isFirstBook -> Modifier.tids(
                        Tid.Catalog.item("book", item.title),
                        Tid.Catalog.ITEM_FIRST,
                        Tid.Catalog.BOOK_FIRST
                    )
                    isFirstItem -> Modifier.tids(
                        Tid.Catalog.item("book", item.title),
                        Tid.Catalog.ITEM_FIRST
                    )
                    isFirstBook -> Modifier.tids(
                        Tid.Catalog.item("book", item.title),
                        Tid.Catalog.BOOK_FIRST
                    )
                    else -> Modifier.tid(Tid.Catalog.item("book", item.title))
                }
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Book: ${item.title}, by ${item.author.ifBlank { "Unknown" }}"
            }
    )
}

@Composable
fun OfflinePlaceholder(isEink: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WifiOff, contentDescription = "Offline Mode", modifier = Modifier.size(64.dp), tint = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        Text("You are in Offline Mode", style = MaterialTheme.typography.titleMedium, color = if(isEink) Color.Black else MaterialTheme.colorScheme.onBackground)
        Text("Disable Offline Mode in Settings to browse catalogs.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}
