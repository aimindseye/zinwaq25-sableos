package org.vaachak.reader.leisure.ui.reader

// --- MAESTRO FIX 1: Import semantics ---
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.android.AndroidTtsEngine.Voice
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.vaachak.reader.core.domain.model.HighlightEntity
import org.vaachak.reader.leisure.ui.reader.components.AiBottomSheet
import org.vaachak.reader.leisure.ui.reader.components.BookHighlightsOverlay
import org.vaachak.reader.leisure.ui.reader.components.BookSearchOverlay
import org.vaachak.reader.leisure.ui.reader.components.ReaderSettingsSheet
import org.vaachak.reader.leisure.ui.reader.components.ReaderSystemFooter
import org.vaachak.reader.leisure.ui.reader.components.ReaderTopBar
import org.vaachak.reader.leisure.ui.reader.components.TableOfContents
import org.vaachak.reader.leisure.ui.reader.components.TtsSettingsBottomSheet
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.TidScreen
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.ui.testability.tids
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderScreen(
    bookHash: String,
    initialUri: String?,
    initialLocatorJson: String?,
    onBack: () -> Unit,
    onTtsStatusChange: (Boolean) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val activity = LocalActivity.current as AppCompatActivity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDebugBuild = (view.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val publication by viewModel.publication.collectAsState()
    val isEink by viewModel.isEinkEnabled.collectAsState()
    val isAiEnabled by viewModel.isAiEnabled.collectAsState()

    val showToc by viewModel.showToc.collectAsState()
    val currentLocator by viewModel.currentLocator.collectAsState()

    val showReaderSettings by viewModel.showReaderSettings.collectAsState()
    val epubPreferences by viewModel.epubPreferences.collectAsState()

    val showSearch by viewModel.showSearch.collectAsState()
    val bookSearchQuery by viewModel.bookSearchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isBookSearching by viewModel.isBookSearching.collectAsState()

    val showHighlights by viewModel.showHighlights.collectAsState()
    val highlightsList by viewModel.bookmarksList.collectAsState()

    val showBookmarks by viewModel.showBookmarks.collectAsState()
    val savedBookmarks by viewModel.savedBookmarks.collectAsState()
    val isPageBookmarked by viewModel.isCurrentPageBookmarked.collectAsState()

    val showRecapConfirmation by viewModel.showRecapConfirmation.collectAsState()
    val recapText by viewModel.recapText.collectAsState()
    val isRecapLoading by viewModel.isRecapLoading.collectAsState()

    // TTS State
    val isTtsActive by viewModel.isTtsActive.collectAsState()
    val showTtsBar by viewModel.showTtsBar.collectAsState()
    val ttsSettings by viewModel.ttsSettings.collectAsState()

    var showDeleteDialogId by remember { mutableStateOf<String?>(null) }

    val isBottomSheetVisible by viewModel.isBottomSheetVisible.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isImageResponse by viewModel.isImageResponse.collectAsState()
    val isDictionaryLookup by viewModel.isDictionaryLookup.collectAsState()
    val isDictionaryLoading by viewModel.isDictionaryLoading.collectAsState()

    val showTagSelector by viewModel.showTagSelector.collectAsState()
    val pageInfo by viewModel.currentPageInfo.collectAsState()
    val initialLocatorState by viewModel.initialLocator.collectAsState()
    val savedHighlights by viewModel.currentBookHighlights.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val scope = rememberCoroutineScope()
    var currentNavigatorFragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val showTtsSettingsSheet by viewModel.showTtsSettingsSheet.collectAsState()

    val hasChapterError by viewModel.hasChapterError.collectAsState()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(isTtsActive) {
        onTtsStatusChange(isTtsActive)
    }

    LaunchedEffect(currentNavigatorFragment) {
        if (currentNavigatorFragment != null) {
            viewModel.setVisualNavigatorProvider { currentNavigatorFragment }
        }
    }

    LaunchedEffect(currentNavigatorFragment, publication) {
        if (currentNavigatorFragment != null && publication != null) {
            viewModel.initTts()
        }
    }

    val ttsDecoration by viewModel.ttsDecoration.collectAsState()

    LaunchedEffect(ttsDecoration, currentNavigatorFragment) {
        val navigator = currentNavigatorFragment ?: return@LaunchedEffect
        if (navigator.view != null) {
            navigator.applyDecorations(ttsDecoration, "tts_highlights")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val json = currentLocator?.toJSON()?.toString() ?: ""
                viewModel.onReaderPause(json)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val json = currentLocator?.toJSON()?.toString() ?: ""
            viewModel.onReaderPause(json)
        }
    }

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { insetsController.show(WindowInsetsCompat.Type.systemBars()) }
        } else {
            onDispose { }
        }
    }

    LaunchedEffect(Unit) { viewModel.navigationEvent.collect { link -> currentNavigatorFragment?.go(link, animated = false) } }
    LaunchedEffect(Unit) { viewModel.jumpEvent.collect { locator -> currentNavigatorFragment?.go(locator, animated = false) } }

    LaunchedEffect(epubPreferences, currentNavigatorFragment) {
        if (currentNavigatorFragment?.view != null) {
            currentNavigatorFragment?.submitPreferences(epubPreferences)
        }
    }

    LaunchedEffect(savedHighlights, currentNavigatorFragment) {
        val navigator = currentNavigatorFragment ?: return@LaunchedEffect
        if (navigator.view != null) {
            navigator.applyDecorations(savedHighlights, "user_highlights")
        }
    }

    LaunchedEffect(initialUri, initialLocatorJson) {
        if (initialUri != null) {
            viewModel.setInitialLocation(initialLocatorJson)
            viewModel.onFileSelected(initialUri.toUri())
        }
    }

    val navListener = remember {
        object : EpubNavigatorFragment.Listener {
            override fun onJumpToLocator(locator: Locator) { viewModel.updateProgress(locator) }
            override fun onExternalLinkActivated(url: AbsoluteUrl) {}
        }
    }

    BackHandler {
        if (showDeleteDialogId != null) showDeleteDialogId = null
        else if (showTagSelector) viewModel.dismissTagSelector()
        else if (showReaderSettings) viewModel.dismissReaderSettings()
        else if (showToc) viewModel.toggleToc()
        else if (showSearch) viewModel.toggleSearch()
        else if (showHighlights) viewModel.toggleHighlights()
        else if (showBookmarks) viewModel.toggleBookmarksList()
        else if (showRecapConfirmation) viewModel.dismissRecapConfirmation()
        else if (recapText != null) viewModel.dismissRecapResult()
        else if (isBottomSheetVisible) viewModel.dismissBottomSheet()
        else { viewModel.closeBook(); onBack() }
    }

    val decorationListener = remember {
        object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                if (event.group == "user_highlights") {
                    showDeleteDialogId = event.decoration.id
                    return true
                }
                return false
            }
        }
    }

    val inputListener = remember {
        object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val screenWidth = view.width.toFloat()
                val x = event.point.x
                val leftZoneEnd = screenWidth * 0.2f
                val rightZoneStart = screenWidth * 0.8f
                return if (x > leftZoneEnd && x < rightZoneStart) {
                    viewModel.toggleReaderSettings()
                    true
                } else false
            }
        }
    }

    DisposableEffect(currentNavigatorFragment) {
        val navigator = currentNavigatorFragment ?: return@DisposableEffect onDispose {}
        val fm = activity.supportFragmentManager

        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                scope.launch {
                    if (navigator.isAdded && navigator.view != null) {
                        navigator.applyDecorations(savedHighlights, "user_highlights")
                    }
                }
            }
        }

        val fmCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                if (f == navigator) {
                    navigator.addDecorationListener("user_highlights", decorationListener)
                    f.lifecycle.addObserver(lifecycleObserver)
                    scope.launch {
                        navigator.applyDecorations(savedHighlights, "user_highlights")
                    }
                }
            }
        }

        if (navigator.isAdded) {
            navigator.addDecorationListener("user_highlights", decorationListener)
            navigator.addInputListener(inputListener)
            navigator.lifecycle.addObserver(lifecycleObserver)
            scope.launch {
                navigator.applyDecorations(savedHighlights, "user_highlights")
            }
        }
        fm.registerFragmentLifecycleCallbacks(fmCallbacks, false)

        onDispose {
            fm.unregisterFragmentLifecycleCallbacks(fmCallbacks)
            if (navigator.isAdded) {
                try {
                    navigator.removeDecorationListener(decorationListener)
                    navigator.lifecycle.removeObserver(lifecycleObserver)
                } catch (_: Exception) {}
            }
        }
    }

    val aiSelectionCallback = remember(isAiEnabled) {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (isAiEnabled) menu?.add(Menu.NONE, 101, 0, "Ask AI")
                menu?.add(Menu.NONE, 102, 1, "Highlight")
                menu?.add(Menu.NONE, 103, 2, "Define")
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                scope.launch {
                    val selection = currentNavigatorFragment?.currentSelection()
                    val locator = selection?.locator ?: return@launch
                    val text = locator.text.highlight ?: ""
                    when (item?.itemId) {
                        101 -> viewModel.onTextSelected(text)
                        102 -> viewModel.prepareHighlight(locator)
                        103 -> viewModel.lookupWord(text)
                    }
                    mode?.finish()
                }
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
    }

    TidScreen(Tid.Screen.reader) {
        Scaffold(
            topBar = {
                ReaderTopBar(
                    bookTitle = publication?.metadata?.title ?: "Loading...",
                    isEink = isEink,
                    showRecap = isAiEnabled,
                    showDebugHighlightButton = isDebugBuild,
                    isBookmarked = isPageBookmarked,
                    isTtsActive = isTtsActive,
                    onBack = { viewModel.closeBook(); onBack() },
                    onTocClick = { viewModel.toggleToc() },
                    onSearchClick = { viewModel.toggleSearch() },
                    onHighlightsClick = { viewModel.toggleHighlights() },
                    onBookmarksListClick = { viewModel.toggleBookmarksList() },
                    onBookmarkToggleClick = { viewModel.toggleBookmarkOnCurrentPage() },
                    onRecapClick = { viewModel.onRecapClicked() },
                    onTtsClick = { viewModel.toggleTts() },
                    onAddHighlightClick = { viewModel.addTestHighlightAtCurrentLocator() },
                    onSettingsClick = { viewModel.toggleReaderSettings() }
                )
            },
            bottomBar = {
                Column (
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom
                ){
                    if (publication != null && !showReaderSettings && !showToc && !showSearch && !showHighlights && !showBookmarks) {
                        ReaderSystemFooter(chapterTitle = pageInfo, isEink = isEink)
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = if (snackbarMessage == "Test highlight created") Modifier.tid(Tid.Reader.highlightCreatedBanner) else Modifier
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (publication == null) {
                    CircularProgressIndicator(color = Color.Black)
                } else {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                id = View.generateViewId()
                                val fm = activity.supportFragmentManager
                                val existing = fm.findFragmentByTag("EPUB_READER_FRAGMENT")
                                if (existing != null) fm.beginTransaction().remove(existing).commitNow()

                                val factory = EpubNavigatorFactory(publication!!)
                                val fragment = factory.createFragmentFactory(
                                    initialLocator = initialLocatorState,
                                    configuration = EpubNavigatorFragment.Configuration().apply {
                                        selectionActionModeCallback = aiSelectionCallback
                                    },
                                    listener = navListener
                                ).instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment

                                scope.launch { fragment.currentLocator.collect { locator -> viewModel.updateProgress(locator) } }
                                currentNavigatorFragment = fragment
                                fm.beginTransaction().replace(this.id, fragment, "EPUB_READER_FRAGMENT").commit()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (hasChapterError) {
                    ChapterErrorOverlay(
                        isEink = isEink,
                        onReturnToLibrary = { viewModel.dismissChapterErrorAndClose() }
                    )
                }

                if (isRecapLoading) {
                    AlertDialog(onDismissRequest = {}, title = { Text("Generating Recap...") }, text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }, confirmButton = {}, modifier = Modifier.zIndex(20f))
                }

                if (showRecapConfirmation) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissRecapConfirmation(); viewModel.dismissRecap() },
                        title = { Text("Quick Recap") },
                        text = { Text("Would you like to generate a quick recap summary of the book so far?") },
                        confirmButton = { TextButton(onClick = { viewModel.getQuickRecap() }) { Text("Yes", fontWeight = FontWeight.Bold) } },
                        dismissButton = { TextButton(onClick = { viewModel.dismissRecapConfirmation() }) { Text("No", color = Color.Gray) } },
                        modifier = Modifier.zIndex(20f)
                    )
                }

                recapText?.let { text ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissRecapResult() },
                        title = { Text("The Story So Far") },
                        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray) } },
                        confirmButton = { TextButton(onClick = { viewModel.saveRecapAsHighlight() }) { Text("Save to Highlights", fontWeight = FontWeight.Bold) } },
                        dismissButton = { TextButton(onClick = { viewModel.dismissRecapResult() }) { Text("Dismiss", color = Color.Gray) } },
                        modifier = Modifier.zIndex(20f)
                    )
                }

                if (showSearch) {
                    Surface(modifier = Modifier.fillMaxSize().zIndex(15f).tid(Tid.Screen.readerSearch)) {
                        BookSearchOverlay(query = bookSearchQuery, results = searchResults, isSearching = isBookSearching, onQueryChange = { viewModel.searchInBook(it) }, onSearch = { viewModel.searchInBook(it) }, onResultClick = { viewModel.onSearchResultClicked(it) }, onDismiss = { viewModel.toggleSearch() }, isEink = isEink)
                    }
                }

                if (showHighlights) {
                    Surface(modifier = Modifier.fillMaxSize().zIndex(15f)) {
                        BookHighlightsOverlay(highlights = highlightsList, onHighlightClick = { viewModel.onHighlightClicked(it) }, onDismiss = { viewModel.toggleHighlights() }, isEink = isEink)
                    }
                }

                if (showBookmarks) {
                    Surface(modifier = Modifier.fillMaxSize().zIndex(15f).tid(Tid.Screen.bookmarks)) {
                        BookBookmarksOverlay(bookmarks = savedBookmarks, onBookmarkClick = { viewModel.onBookmarkClicked(it) }, onDismiss = { viewModel.toggleBookmarksList() }, isEink = isEink)
                    }
                }

                if (showToc && publication != null) {
                    Surface(modifier = Modifier.fillMaxSize().zIndex(15f)) {
                        TableOfContents(toc = publication!!.tableOfContents, currentHref = currentLocator?.href?.toString(), onLinkSelected = { link -> viewModel.onTocItemSelected(link) }, onDismiss = { viewModel.toggleToc() }, isEink = isEink)
                    }
                }

                if (showReaderSettings) {
                    Surface(modifier = Modifier.fillMaxSize().zIndex(10f).tid(Tid.Screen.readerSettings)) {
                        ReaderSettingsSheet(viewModel = viewModel, isEink = isEink, onDismiss = { viewModel.dismissReaderSettings() })
                    }
                }

                if (isBottomSheetVisible) {
                    AiBottomSheet(responseText = aiResponse, isImage = isImageResponse, isDictionary = isDictionaryLookup, isDictionaryLoading = isDictionaryLoading, isEink = isEink, onExplain = { viewModel.onActionExplain() }, onWhoIsThis = { viewModel.onActionWhoIsThis() }, onVisualize = { viewModel.onActionVisualize() }, onDismiss = { viewModel.dismissBottomSheet() })
                }

                if (showTagSelector) {
                    TagSelectorDialog(onTagSelected = { tag -> viewModel.saveHighlightWithTag(tag) }, onDismiss = { viewModel.dismissTagSelector() })
                }

                if (showDeleteDialogId != null) {
                    AlertDialog(onDismissRequest = { showDeleteDialogId = null; viewModel.dismissRecap() }, title = { Text("Delete Highlight?") }, text = { Text("This action cannot be undone.") }, confirmButton = { TextButton(onClick = { viewModel.deleteHighlight(showDeleteDialogId!!); showDeleteDialogId = null }) { Text("Delete", color = Color.Red) } }, dismissButton = { TextButton(onClick = { showDeleteDialogId = null }) { Text("Cancel") } }, modifier = Modifier.zIndex(4f))
                }

                if (showTtsBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 16.dp)
                            .zIndex(20f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TtsVerticalFloatingPill(
                            isVisible = showTtsBar,
                            isPlaying = isTtsActive,
                            isEink = isEink,
                            onPlayPause = { viewModel.toggleTts() },
                            onStop = { viewModel.closeTts() },
                            onSkipForward = { viewModel.skipForward() },
                            onSkipBackward = { viewModel.skipBackward() },
                            onOpenSettings = { viewModel.openTtsSettings() }
                        )
                        TtsSettingsBottomSheet(
                            isVisible = showTtsSettingsSheet,
                            onDismiss = { viewModel.closeTtsSettings() },
                            ttsSettings = ttsSettings,
                            isEink = isEink,
                            onSpeedChange = { speed: Float ->
                                viewModel.updateTtsSpeed(speed)
                            },
                            onPitchChange = { pitch: Float ->
                                viewModel.updateTtsPitch(pitch)
                            },
                            onStyleChange = { style: String ->
                                viewModel.setTtsVisualStyle(style)
                            },
                            onAutoPageTurnChange = { enabled: Boolean ->
                                viewModel.setTtsAutoPageTurn(enabled)
                            },
                            onVoiceChange = { voice: Voice ->
                                viewModel.setBookSpecificVoice(voice)
                            },
                            viewModel =viewModel,
                            onLanguageChange = { languageCode: String -> viewModel.setTtsLanguage(languageCode)}
                        )
                    }
                }
            }
        }
    }
}

// Helper Composable for Bookmarks List
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookBookmarksOverlay(
    bookmarks: List<HighlightEntity>,
    onBookmarkClick: (HighlightEntity) -> Unit,
    onDismiss: () -> Unit,
    isEink: Boolean
) {
    val containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surface
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bookmarks",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss, modifier = Modifier.tid(Tid.Bookmarks.CLOSE)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Bookmarks",
                            tint = contentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = containerColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor
                )
            )
        },
        containerColor = containerColor
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (bookmarks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No bookmarks added yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    items(bookmarks.size) { i ->
                        val bookmark = bookmarks[i]
                        // --- MAESTRO FIX 2: Added mergeDescendants to Bookmark items ---
                        Card(
                            onClick = { onBookmarkClick(bookmark) },
                            modifier = if (i == 0) {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tids(Tid.Bookmarks.item(bookmark.id), Tid.Bookmarks.FIRST)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Bookmark: ${bookmark.text ?: "Empty"}"
                                    }
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .tid(Tid.Bookmarks.item(bookmark.id))
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Bookmark: ${bookmark.text ?: "Empty"}"
                                    }
                            },
                            colors = CardDefaults.cardColors(containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = bookmark.text ?: "Bookmark",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                        if (i < bookmarks.size - 1) {
                            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagSelectorDialog(
    onTagSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tags = listOf("General", "Research", "Quotes", "Characters")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Highlight As...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    // --- MAESTRO FIX 3: Explicit description for the tags ---
                    OutlinedButton(
                        onClick = { onTagSelected(tag) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Select tag: $tag"
                            },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black)
                    ) { Text(tag) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.zIndex(5f)
    )
}

@Composable
fun TtsVerticalFloatingPill(
    isVisible: Boolean,
    isPlaying: Boolean,
    isEink: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!isVisible) return

    val backgroundColor = if (isEink) Color.White else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier
            .tid(Tid.Tts.pill)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .width(56.dp)
            .wrapContentHeight(),
        shape = RoundedCornerShape(28.dp),
        color = backgroundColor,
        shadowElevation = if (isEink) 0.dp else 8.dp,
        border = if (isEink) BorderStroke(2.dp, contentColor) else null
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onStop, modifier = Modifier.tid(Tid.Tts.stop)) { Icon(Icons.Default.Close, "Exit TTS", tint = contentColor) }

            if (isEink) Spacer(modifier = Modifier.height(1.dp).width(24.dp).background(Color.Black))
            else Spacer(modifier = Modifier.height(1.dp).width(24.dp).background(Color.Gray.copy(alpha = 0.5f)))

            IconButton(onClick = onSkipBackward, modifier = Modifier.tid(Tid.Tts.prev)) { Icon(Icons.Default.SkipPrevious, "Previous Sentence", tint = contentColor) }

            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp).tid(Tid.Tts.playPause),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isEink) Color.Black else MaterialTheme.colorScheme.primary,
                    contentColor = if (isEink) Color.White else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                // --- MAESTRO FIX 4: Dynamic State-Aware Descriptions ---
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause TTS" else "Play TTS"
                )
            }

            IconButton(onClick = onSkipForward, modifier = Modifier.tid(Tid.Tts.next)) { Icon(Icons.Default.SkipNext, "Next Sentence", tint = contentColor) }

            if (isEink) Spacer(modifier = Modifier.height(1.dp).width(24.dp).background(Color.Black))
            else Spacer(modifier = Modifier.height(1.dp).width(24.dp).background(Color.Gray.copy(alpha = 0.5f)))

            IconButton(onClick = onOpenSettings, modifier = Modifier.tid(Tid.Tts.settings)) { Icon(Icons.Default.Tune, "TTS Settings", tint = contentColor) }
        }
    }
}

@Composable
fun ChapterErrorOverlay(
    isEink: Boolean,
    onReturnToLibrary: () -> Unit
) {
    val backgroundColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = "Formatting Error",
                modifier = Modifier.size(64.dp),
                tint = contentColor
            )

            Text(
                text = "Chapter Formatting Error",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            Text(
                text = "This specific section of the book contains structural formatting errors (like a missing closing tag) and cannot be rendered by the reading engine.",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- MAESTRO FIX 5: Explicit semantics for Error Exit Button ---
            OutlinedButton(
                onClick = onReturnToLibrary,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Return to Library"
                    },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = contentColor
                ),
                border = BorderStroke(2.dp, contentColor)
            ) {
                Text("Return to Library", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}