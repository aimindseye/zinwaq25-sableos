@file:Suppress("FunctionNaming")

package org.sableos.titan2.displaycompat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.sableos.titan2.displaycompat.android.TraitsResolver
import org.sableos.titan2.displaycompat.android.displayCompat
import org.sableos.titan2.displaycompat.android.seedPresets
import org.sableos.titan2.displaycompat.core.ProfileLink
import org.sableos.titan2.displaycompat.core.SizePx
import org.sableos.titan2.theme.SablePalette

class MainActivity : ComponentActivity() {
    /** Set when Settings > App info opened one app's profile: Done and Back return there. */
    private var linked: TraitsResolver.Entry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        C.palette = SablePalette.read(this).also { it.applyTo(this) }
        val data = intent?.data
        val pkg = ProfileLink.packageFrom(intent?.action, data?.scheme, data?.schemeSpecificPart)
        // Only apps the list could show (launcher apps); anything else opens the list.
        linked = pkg?.let { p -> TraitsResolver(this).launchable().firstOrNull { it.pkg == p } }
        // Apps installed before this build, or while presets could not be given, get theirs now.
        runCatching { displayCompat.seedPresets() }
        setContent { Root() }
    }

    @Composable
    private fun Root() {
        val app = displayCompat
        val resolver = remember { TraitsResolver(this) }
        // The whole display (this app is never given a Tall canvas, so this is the native size).
        val bounds = windowManager.maximumWindowMetrics.bounds
        val display = SizePx(bounds.width(), bounds.height())
        var editing by remember { mutableStateOf(linked) }
        var tools by remember { mutableStateOf(false) }
        // Custom: the full 1.0 editor, opened from the app's page.
        var custom by remember { mutableStateOf(false) }
        val closeEditor: () -> Unit = {
            if (linked != null) {
                finish()
            } else {
                editing = null
            }
        }
        Box(Modifier.fillMaxSize().background(C.window).safeDrawingPadding()) {
            val e = editing
            if (tools) {
                BackHandler { tools = false }
                ToolsScreen(app, display, onDone = { tools = false })
            } else if (e == null) {
                AppListScreen(
                    app,
                    resolver,
                    onOpen = { editing = it },
                    onTools = { tools = true },
                    onExit = { finish() }
                )
            } else if (custom) {
                BackHandler { custom = false }
                EditorScreen(app, resolver, e, display, onDone = { custom = false })
            } else {
                BackHandler { closeEditor() }
                val nav = PageNav(onCustom = { custom = true }, onDone = closeEditor)
                AppPageScreen(app, resolver, e, display, nav)
            }
        }
    }
}
