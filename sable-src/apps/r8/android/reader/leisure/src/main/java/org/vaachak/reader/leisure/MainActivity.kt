/*
 * Copyright (c) 2026 Piyush Daiya
 * ...
 */

package org.vaachak.reader.leisure

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.vaachak.reader.core.data.repository.VaultRepository
import org.vaachak.reader.core.domain.model.BookEntity
import org.vaachak.reader.core.domain.model.ThemeMode
import org.vaachak.reader.leisure.navigation.Screen
import org.vaachak.reader.leisure.ui.bookshelf.BookshelfScreen
import org.vaachak.reader.leisure.ui.bookshelf.BookshelfUiState
import org.vaachak.reader.leisure.ui.bookshelf.BookshelfViewModel
import org.vaachak.reader.leisure.ui.catalog.CatalogBrowserScreen
import org.vaachak.reader.leisure.ui.catalog.CatalogManagerScreen
import org.vaachak.reader.leisure.ui.highlights.AllHighlightsScreen
import org.vaachak.reader.leisure.ui.login.LoginScreen
import org.vaachak.reader.leisure.ui.reader.ReaderScreen
import org.vaachak.reader.leisure.ui.settings.AiConfigScreen
import org.vaachak.reader.leisure.ui.settings.AppAppearanceScreen
import org.vaachak.reader.leisure.ui.settings.DefaultAppearanceScreen
import org.vaachak.reader.leisure.ui.settings.DictionarySettingsScreen
import org.vaachak.reader.leisure.ui.settings.LibrarySettingsScreen
import org.vaachak.reader.leisure.ui.settings.ReaderProfilesScreen
import org.vaachak.reader.leisure.ui.settings.SettingsScreen
import org.vaachak.reader.leisure.ui.settings.SettingsViewModel
import org.vaachak.reader.leisure.ui.settings.SyncSettingsScreen
import org.vaachak.reader.leisure.ui.settings.TtsSettingsScreen
import org.vaachak.reader.leisure.ui.testability.Tid
import org.vaachak.reader.leisure.ui.testability.tid
import org.vaachak.reader.leisure.ui.testability.tids
import org.vaachak.reader.leisure.ui.theme.VaachakTheme
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // OPTIMIZATION: Ultra-lightweight ViewModel for routing and theme
    private val rootViewModel: RootViewModel by viewModels()

    // Kept at activity scope so it isn't destroyed when transitioning to the Reader.
    private val bookshelfViewModel: BookshelfViewModel by viewModels()

    private var isTtsActive = false

    private fun BookshelfUiState.findBookByHash(hash: String): BookEntity? {
        if (heroBook?.bookHash == hash) return heroBook
        for (list in groupedLibrary.values) {
            val found = list.find { it.bookHash == hash }
            if (found != null) return found
        }
        return null
    }

    private fun BookshelfUiState.findBookByUri(uri: String): BookEntity? {
        if (heroBook?.localUri == uri) return heroBook
        for (list in groupedLibrary.values) {
            val found = list.find { it.localUri == uri }
            if (found != null) return found
        }
        return null
    }

    private fun BookshelfUiState.findBookByTitle(title: String): BookEntity? {
        val cleanTitle = title.trim()
        if (heroBook?.title?.trim().equals(cleanTitle, ignoreCase = true)) return heroBook
        for (list in groupedLibrary.values) {
            val found = list.find { it.title.trim().equals(cleanTitle, ignoreCase = true) }
            if (found != null) return found
        }
        return null
    }

    private fun BookshelfUiState.isLibraryEmpty(): Boolean {
        return heroBook == null && groupedLibrary.all { it.value.isEmpty() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null && intent?.action == Intent.ACTION_VIEW) {
            processExternalIntent(intent)
        }

        setContent {
            val navController = rememberNavController()

            // Read from the ultra-lightweight RootViewModel
            val currentTheme by rootViewModel.appThemeMode.collectAsState()
            val einkContrast by rootViewModel.einkContrastVal.collectAsState()
            val hasCompletedOnboarding by rootViewModel.hasCompletedOnboarding.collectAsState(initial = false)
            val isMultiUser by rootViewModel.isMultiUserMode.collectAsState(initial = false)
            val activeVaultId by rootViewModel.activeVaultId.collectAsState(initial = VaultRepository.DEFAULT_VAULT_ID)
            val isOffline by rootViewModel.isOfflineMode.collectAsState(initial = true)

            val isEink = currentTheme == ThemeMode.E_INK

            val startDestination = remember(hasCompletedOnboarding, isMultiUser, activeVaultId) {
                if (!hasCompletedOnboarding) "profile_picker"
                else if (isMultiUser && activeVaultId == VaultRepository.DEFAULT_VAULT_ID) "profile_picker"
                else Screen.Library.route
            }

            CompositionLocalProvider(
                LocalIndication provides if (isEink) NoIndication else LocalIndication.current
            ) {
                // Maestro's `id:` selector maps to Android resource-id, so this must wrap
                // the full Compose hierarchy to surface all test tags from screens and overlays.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { testTagsAsResourceId = true }
                ) {
                    VaachakTheme(themeMode = currentTheme, contrast = einkContrast) {
                        Scaffold(
                            containerColor = if (isEink) Color.White else MaterialTheme.colorScheme.background
                        ) { padding ->
                            Surface(
                                modifier = Modifier.padding(padding).fillMaxSize(),
                                color = if (isEink) Color.White else MaterialTheme.colorScheme.background
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = startDestination
                                ) {
                                composable("welcome") {
                                    val settingsViewModel: SettingsViewModel = hiltViewModel()
                                    WelcomeScreen(
                                        onComplete = { multi, offline ->
                                            settingsViewModel.completeOnboarding(multi, offline)
                                            val nextScreen = if (multi) "profile_picker" else Screen.Library.route
                                            navController.navigate(nextScreen) {
                                                popUpTo("welcome") { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable("profile_picker") {
                                    // INJECT THE VIEWMODEL HERE
                                    val profileViewModel: org.vaachak.reader.leisure.ui.settings.ProfileViewModel = hiltViewModel()

                                    ProfilePickerScreen(
                                        isEink = isEink,
                                        viewModel = profileViewModel,
                                        onProfileSelected = {
                                            navController.navigate(Screen.Library.route) {
                                                popUpTo("profile_picker") { inclusive = true }
                                            }
                                        }
                                    )
                                }

                                composable(Screen.Library.route) {
                                    MainAppScreen(
                                        navController = navController,
                                        bookshelfViewModel = bookshelfViewModel,
                                        isEink = isEink,
                                        isMultiUser = isMultiUser, // <-- 1. PASS IT DOWN HERE
                                        onBookClick = { bookHash ->
                                            val book = bookshelfViewModel.uiState.value.findBookByHash(bookHash)
                                            if (book != null) {
                                                navController.navigate(Screen.Reader.createRoute(book.bookHash, book.lastCfiLocation))
                                            }
                                        },
                                        onCatalogClick = { navController.navigate(Screen.CatalogBrowser.route) },
                                        onHighlightsClick = { navController.navigate(Screen.Highlights.route) },
                                        onSwitchProfileClick = {
                                            navController.navigate("profile_picker")
                                        }
                                    )
                                }
                                composable(
                                    route = Screen.Reader.route,
                                    arguments = listOf(
                                        navArgument("bookId") { type = NavType.StringType },
                                        navArgument("locator") { type = NavType.StringType; nullable = true }
                                    )
                                ) { backStackEntry ->
                                    val bookHash = backStackEntry.arguments?.getString("bookId") ?: ""
                                    val locatorJson = backStackEntry.arguments?.getString("locator")

                                    val book by remember(bookHash) {
                                        bookshelfViewModel.uiState.map { it.findBookByHash(bookHash) }.distinctUntilChanged()
                                    }.collectAsState(initial = null)

                                    if (book != null) {
                                        ReaderScreen(
                                            bookHash = bookHash,
                                            initialUri = book?.localUri,
                                            initialLocatorJson = locatorJson,
                                            onBack = {
                                                isTtsActive = false
                                                navController.popBackStack()
                                            },
                                            onTtsStatusChange = { active -> isTtsActive = active }
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize()) { Text("Loading Book...", Modifier.align(Alignment.Center)) }
                                        LaunchedEffect(Unit) {
                                            delay(1000)
                                            if (bookshelfViewModel.uiState.value.findBookByHash(bookHash) == null) {
                                                navController.popBackStack()
                                            }
                                        }
                                    }
                                }

                                composable(Screen.Highlights.route) { AllHighlightsScreen(onBack = { navController.popBackStack() }, onHighlightClick = { bookId, locator -> navController.navigate(Screen.Reader.createRoute(bookId, locator)) }) }
                                composable(Screen.CatalogBrowser.route) { CatalogBrowserScreen(onBack = { navController.popBackStack() }, onReadBook = { uri -> val book = bookshelfViewModel.uiState.value.findBookByUri(uri); if (book != null) { navController.popBackStack(Screen.Library.route, false); navController.navigate(Screen.Reader.createRoute(book.bookHash)) } }, onGoToBookshelf = { navController.popBackStack(Screen.Library.route, false) }) }
                                composable(Screen.Settings.route) { SettingsScreen(navController = navController) }
                                composable(Screen.Login.route) { LoginScreen(navController = navController) }
                                composable(Screen.ReaderProfiles.route) { ReaderProfilesScreen(navController = navController) }
                                composable(Screen.CatalogManage.route) { CatalogManagerScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.AiConfig.route) { AiConfigScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.SyncSettings.route) { SyncSettingsScreen(navController = navController) }
                                composable(Screen.Appearance.route) { DefaultAppearanceScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.TTS.route) { TtsSettingsScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.AppAppearance.route) { AppAppearanceScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.Dictionary.route) { DictionarySettingsScreen(onBack = { navController.popBackStack() }) }
                                composable(Screen.Bookshelf.route) { LibrarySettingsScreen(navController = navController) }
                                }
                            }
                        }
                    }
                }
            }
        }
    } // <--- THIS WAS THE MISSING BRACE!

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processExternalIntent(intent)
    }

    private fun processExternalIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            val contentUri = intent.data!!

            lifecycleScope.launch {
                try {
                    Toast.makeText(this@MainActivity, "Importing book...", Toast.LENGTH_SHORT).show()
                    val localFile = withContext(Dispatchers.IO) { copyToExternalStorage(contentUri) }

                    if (localFile != null && localFile.exists() && localFile.length() > 0) {
                        val tempTitle = withContext(Dispatchers.IO) { getEpubTitle(localFile) } ?: ""
                        waitForDatabaseToLoad()

                        val existingBook = bookshelfViewModel.uiState.value.findBookByTitle(tempTitle)

                        if (existingBook != null) {
                            Timber.d("Duplicate found: '${existingBook.title}'")
                            withContext(Dispatchers.IO) { localFile.delete() }
                            Toast.makeText(this@MainActivity, "Book already exists", Toast.LENGTH_SHORT).show()
                        } else {
                            Timber.d("Importing New Book")
                            val localUri = Uri.fromFile(localFile)
                            bookshelfViewModel.importBook(localUri)
                            Toast.makeText(this@MainActivity, "Import Successful", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to load file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error opening book")
                    Toast.makeText(this@MainActivity, "Error opening book", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun waitForDatabaseToLoad() {
        withTimeoutOrNull(2000) {
            while (bookshelfViewModel.uiState.value.isLibraryEmpty()) {
                delay(100)
            }
        }
    }

    private fun copyToExternalStorage(uri: Uri): File? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val booksDir = getExternalFilesDir("imported_books") ?: return null
            if (!booksDir.exists()) booksDir.mkdirs()

            val fileName = "book_${System.currentTimeMillis()}_${UUID.randomUUID()}.epub"
            val destFile = File(booksDir, fileName)

            FileOutputStream(destFile).use { output -> inputStream.copyTo(output) }
            destFile
        } catch (e: Exception) {
            Timber.e(e, "Error copying to external storage")
            null
        }
    }

    private fun getEpubTitle(epubFile: File): String? {
        return try {
            ZipFile(epubFile).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                val opfPathMatch = Regex("full-path=\"([^\"]+)\"").find(containerXml)
                val opfPath = opfPathMatch?.groupValues?.get(1) ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfXml = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                val titleMatch = Regex("<dc:title[^>]*>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(opfXml)
                titleMatch?.groupValues?.get(1)
            }
        } catch (e: Exception) { null }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isVolumeDown = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        val isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isPageDown = keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        val isPageUp = keyCode == KeyEvent.KEYCODE_PAGE_UP

        if (isVolumeDown || isVolumeUp || isPageDown || isPageUp) {
            if (isTtsActive && (isVolumeDown || isVolumeUp)) return super.onKeyDown(keyCode, event)
            val fragment = supportFragmentManager.findFragmentByTag("EPUB_READER_FRAGMENT") as? EpubNavigatorFragment
            fragment?.let { navigator ->
                if (isVolumeDown || isPageDown) navigator.goForward(animated = false) else navigator.goBackward(animated = false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private object NoIndication : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode { return object : Modifier.Node(), DelegatableNode {} }
        override fun hashCode(): Int = -1
        override fun equals(other: Any?): Boolean = other === this
    }
}

// --- NEW COMPOSABLES FOR LAUNCH FLOW ---

@Composable
fun WelcomeScreen(onComplete: (isMultiUser: Boolean, isOffline: Boolean) -> Unit) {
    var isMultiUser by remember { mutableStateOf(false) }
    var isOffline by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Vaachak", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Let's set up your reading environment.\nYou can always change these later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Will multiple people use this app?")
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isMultiUser, onCheckedChange = { isMultiUser = it })
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Prefer to use the app entirely offline?")
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = isOffline, onCheckedChange = { isOffline = it })
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { onComplete(isMultiUser, isOffline) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue to Library")
        }
    }
}

@Composable
fun MainAppScreen(
    navController: androidx.navigation.NavController,
    bookshelfViewModel: BookshelfViewModel,
    profileViewModel: org.vaachak.reader.leisure.ui.settings.ProfileViewModel = hiltViewModel(),
    isEink: Boolean,
    isMultiUser: Boolean, // <-- 2. ACCEPT IT HERE
    onBookClick: (String) -> Unit,
    onCatalogClick: () -> Unit,
    onHighlightsClick: () -> Unit,
    onSwitchProfileClick: () -> Unit
) {
    val tabs = listOf("Books", "Comics", "Audio", "Settings")
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    val activeProfile by profileViewModel.activeProfile.collectAsState()
    val contentColor = if (isEink) Color.Black else MaterialTheme.colorScheme.onSurface

    androidx.activity.compose.BackHandler(enabled = selectedTabIndex != 0) { selectedTabIndex = 0 }

    Scaffold(
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Vaachak",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )

                    // 3. ONLY RENDER THIS IF THEY ARE IN MULTI-USER MODE
                    if (isMultiUser) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .tid(Tid.Vault.switchProfile)
                                .clickable { onSwitchProfileClick() }
                                .padding(4.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "Switch Profile, Current: ${activeProfile?.name ?: "Guest"}"
                                }
                        ) {
                            Text(
                                text = activeProfile?.name ?: "Guest",
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = contentColor)
                        }
                    }
                }

                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    tabs.forEachIndexed { index, title ->
                        val icon = when (index) {
                            0 -> Icons.Default.Book
                            1 -> Icons.Outlined.Photo
                            2 -> Icons.Default.Audiotrack
                            3 -> Icons.Default.Settings
                            else -> Icons.Default.Book
                        }
                        val tabTid = when (index) {
                            0 -> Tid.Tabs.books
                            1 -> Tid.Tabs.comics
                            2 -> Tid.Tabs.audio
                            3 -> Tid.Tabs.settings
                            else -> Tid.Tabs.books
                        }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            modifier = if (index == 3) {
                                Modifier.tids(Tid.Bookshelf.SETTINGS_TAB, Tid.Bookshelf.OPEN_SETTINGS)
                            } else {
                                Modifier.tid(tabTid)
                            }.semantics(mergeDescendants = true) {
                                contentDescription = "$title Tab, ${if (selectedTabIndex == index) "Selected" else "Not Selected"}"
                            },
                            text = { Text(title) },
                            icon = { Icon(icon, contentDescription = null) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTabIndex) {
                0 -> BookshelfScreen(viewModel = bookshelfViewModel, onBookClick = onBookClick, onCatalogClick = onCatalogClick, onHighlightsClick = onHighlightsClick)
                1 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ComicShelf (Coming Soon)") }
                2 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("AudioShelf (Coming Soon)") }
                3 -> SettingsScreen(navController = navController)
            }
        }
    }
}
